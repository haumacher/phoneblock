/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.analysis.NumberAnalyzer;
import de.haumacher.phoneblock.app.LoginFilter;
import de.haumacher.phoneblock.app.api.model.PhoneInfo;
import de.haumacher.phoneblock.app.api.model.PrefixCheckResult;
import de.haumacher.phoneblock.app.api.model.RangeMatch;
import de.haumacher.phoneblock.app.api.model.Rating;
import de.haumacher.phoneblock.db.AggregationInfo;
import de.haumacher.phoneblock.db.BlockList;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.Confidence;
import de.haumacher.phoneblock.db.Ema;
import de.haumacher.phoneblock.db.DBNumberInfo;
import de.haumacher.phoneblock.db.DBPersonalization;
import de.haumacher.phoneblock.db.DBPhoneComment;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.SpamReports;
import de.haumacher.phoneblock.db.settings.AuthToken;
import de.haumacher.phoneblock.util.ServletUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Privacy-preserving k-anonymity lookup: the client sends only a short SHA-1
 * prefix of the phone number (optionally also prefixes of the range hashes),
 * and the server returns every matching entry so that the client can decide
 * locally whether its own number is on the blocklist.
 *
 * <p>The server never sees the full number the client is asking about — the
 * configured minimum prefix length keeps the anonymity set large enough to
 * make identification infeasible.</p>
 */
@WebServlet(urlPatterns = PrefixCheckServlet.PATH)
public class PrefixCheckServlet extends HttpServlet {

	private static final Logger LOG = LoggerFactory.getLogger(PrefixCheckServlet.class);

	public static final String PATH = "/api/check-prefix";

	/** Minimum prefix length in hex characters (16 bit → ~3 000 German candidates per bucket). */
	public static final int MIN_PREFIX_HEX = 4;

	/** Maximum prefix length = full SHA-1. */
	public static final int MAX_PREFIX_HEX = 40;

	/**
	 * A lookup slower than this (wall-clock, ms) is logged at INFO with its
	 * full phase breakdown; faster ones go to DEBUG. Keeps production logs
	 * focused on the latency outliers worth investigating (issue #329).
	 */
	private static final long SLOW_LOOKUP_MS = 250;

	// The prefix is interpreted as whole bytes — the length must be even. This keeps the
	// range-bound arithmetic to pure byte[] decoding with a ripple-carry increment and
	// avoids any BigInteger / bit-shift shenanigans on the request path.

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		long tStart = System.nanoTime();

		AuthToken auth = LoginFilter.getAuthorization(req);
		if (auth == null) {
			ServletUtil.sendAuthenticationRequest(resp);
			return;
		}

		String sha1Hex = req.getParameter("sha1");
		String prefix10Hex = req.getParameter("prefix10");
		String prefix100Hex = req.getParameter("prefix100");

		if (sha1Hex == null || sha1Hex.isEmpty()) {
			ServletUtil.sendError(resp, "Missing 'sha1' parameter.");
			return;
		}
		if (!isValidHexPrefix(sha1Hex)) {
			ServletUtil.sendError(resp,
				"Invalid 'sha1' prefix: expected an even number of hex characters, "
					+ MIN_PREFIX_HEX + "–" + MAX_PREFIX_HEX + ".");
			return;
		}
		if (prefix10Hex != null && !isValidHexPrefix(prefix10Hex)) {
			ServletUtil.sendError(resp, "Invalid 'prefix10' prefix.");
			return;
		}
		if (prefix100Hex != null && !isValidHexPrefix(prefix100Hex)) {
			ServletUtil.sendError(resp, "Invalid 'prefix100' prefix.");
			return;
		}

		ApiRateLimits limits = ApiRateLimits.getInstance();
		if (!ServletUtil.enforceQuota(req, resp, DB.QUOTA_BUCKET_NUMBER_QUERY,
				limits.numberQueryIntervalMs, limits.numberQueryCount)) {
			return;
		}

		byte[] sha1Low = prefixLow(sha1Hex);
		byte[] sha1High = prefixHigh(sha1Hex);

		PrefixCheckResult result = PrefixCheckResult.create();
		DB db = DBService.getInstance();

		// Phase checkpoints for issue #329 — consumed by serverTiming()/logTiming().
		long tSession, tCommunity, tPersonal, tComments, tRange10, tRange100;

		try (SqlSession session = db.openSession()) {
			tSession = System.nanoTime();
			SpamReports reports = session.getMapper(SpamReports.class);
			BlockList blocklist = session.getMapper(BlockList.class);

			// Exclude numbers whose decoded net evidence rounds to 0 displayed votes
			// (#300) — only return numbers that show at least one vote, matching the
			// per-row votes computed below.
			List<DBNumberInfo> communityMatches = reports.getPhoneInfosByHashPrefix(sha1Low, sha1High, db.maxRawSpam(1));
			tCommunity = System.nanoTime();
			List<DBPersonalization> personalMatches = blocklist.getPersonalizationsByHashPrefix(
				auth.getUserId(), sha1Low, sha1High);
			tPersonal = System.nanoTime();

			Map<String, PhoneInfo> byPhone = new LinkedHashMap<>();
			long now = System.currentTimeMillis();
			for (DBNumberInfo n : communityMatches) {
				// #342: same decay-aware vote-equivalent as /api/check and
				// the blocklist API — decoded SPAM_EVIDENCE minus decoded
				// LEGIT_EVIDENCE, floored at 0, rounded to int. Clients
				// filtering `votes >= minVotes` see the same view here.
				double decodedSpam = Ema.decode(n.getSpamEvidence(), now, Ema.CLASSIFICATION_TAU_MILLIS);
				double decodedLegit = Ema.decode(n.getLegitEvidence(), now, Ema.CLASSIFICATION_TAU_MILLIS);
				int votes = (int) Math.round(Math.max(0.0, decodedSpam - decodedLegit));
				PhoneInfo pi = NumberAnalyzer.phoneInfoFromId(n.getPhone())
					.setVotes(votes)
					.setRating(DB.rating(n))
					// Confidence-model surface (#334): populate spamConfidence and heat here
					// too — this servlet builds PhoneInfo by hand and would otherwise leave
					// them at the default 0, unlike the getPhoneApiInfo path.
					.setSpamConfidence(Confidence.spamConfidence(decodedSpam, decodedLegit))
					.setHeat(Ema.decodeRate(n.getRawHeat(), now, Ema.HEAT_TAU_MILLIS))
					.setDateAdded(n.getAdded())
					.setLastUpdate(n.getUpdated());
				byPhone.put(n.getPhone(), pi);
			}
			for (DBPersonalization p : personalMatches) {
				PhoneInfo pi = byPhone.get(p.getPhone());
				if (pi == null) {
					// Personal entry with no community match. By construction the
					// community row is either missing or below the visibility
					// threshold (the community query filters net evidence ≥ floor),
					// so leave the vote counts at zero — they are not relevant either way.
					pi = NumberAnalyzer.phoneInfoFromId(p.getPhone())
						.setRating(p.isBlocked() ? Rating.B_MISSED : Rating.A_LEGITIMATE);
					byPhone.put(p.getPhone(), pi);
				}
				if (p.isBlocked()) {
					pi.setBlackListed(true);
				} else {
					// Personal whitelist overrides the community rating for this user.
					//
					// The alternative would be to drop the community entry entirely so the
					// client sees no direct match (treating the number as legitimate by
					// default). That is not enough on its own: any range10/range100
					// aggregation covering the whitelisted number stays in the response and
					// would push the client's wildcard-vote computation past the block
					// threshold for numbers in a hot SPAM range — re-blocking a number the
					// user explicitly whitelisted. Scrubbing the ranges server-side would
					// require re-hashing the whitelisted number, adds complexity, and still
					// only buys a cleaner response payload.
					//
					// Returning an explicit A_LEGITIMATE record short-circuits that: a
					// well-behaved client treats a direct rating of A_LEGITIMATE as a hard
					// override of any wildcard signal, exactly as the non-prefix /api/check
					// path already relies on.
					pi.setWhiteListed(true);
					pi.setRating(Rating.A_LEGITIMATE);
				}
			}
			// Annotate each matching number with the user's own previously-submitted comment, if any.
			if (!byPhone.isEmpty()) {
				List<DBPhoneComment> ownComments = reports.getUserComments(auth.getUserId(), byPhone.keySet());
				for (DBPhoneComment c : ownComments) {
					PhoneInfo pi = byPhone.get(c.getPhone());
					if (pi != null) {
						pi.setUserComment(c.getComment());
					}
				}
			}
			tComments = System.nanoTime();
			result.setNumbers(new ArrayList<>(byPhone.values()));

			if (prefix10Hex != null) {
				byte[] low = prefixLow(prefix10Hex);
				byte[] high = prefixHigh(prefix10Hex);
				result.setRange10(toRangeMatches(
					reports.getAggregation10ByHashPrefix(low, high, DB.MIN_AGGREGATE_10), now));
			}
			tRange10 = System.nanoTime();
			if (prefix100Hex != null) {
				byte[] low = prefixLow(prefix100Hex);
				byte[] high = prefixHigh(prefix100Hex);
				result.setRange100(toRangeMatches(
					reports.getAggregation100ByHashPrefix(low, high, DB.MIN_AGGREGATE_100), now));
			}
			tRange100 = System.nanoTime();
		}

		// Diagnostic for issue #329: surface the server-side phase breakdown
		// as a Server-Timing response header (visible per-call to any client)
		// and in the log. The header must be set before the body is written.
		resp.setHeader("Server-Timing",
			serverTiming(tStart, tSession, tCommunity, tPersonal, tComments, tRange10, tRange100));

		ServletUtil.sendResult(req, resp, result);

		logTiming(tStart, tSession, tCommunity, tPersonal, tComments, tRange10, tRange100,
			System.nanoTime());
	}

	/** Elapsed milliseconds between two {@link System#nanoTime()} readings. */
	private static double ms(long fromNanos, long toNanos) {
		return (toNanos - fromNanos) / 1_000_000.0;
	}

	/**
	 * Builds the {@code Server-Timing} header value from the request phase
	 * checkpoints. {@code session} also covers the (trivial) parameter
	 * validation; {@code db-total} spans everything up to — but excluding —
	 * the JSON serialization, which happens while the response body is
	 * written and so cannot be reflected in a header.
	 */
	private static String serverTiming(long tStart, long tSession, long tCommunity,
			long tPersonal, long tComments, long tRange10, long tRange100) {
		return String.format(Locale.ROOT,
			"session;dur=%.1f, community;dur=%.1f, personal;dur=%.1f, comments;dur=%.1f, "
				+ "range10;dur=%.1f, range100;dur=%.1f, db-total;dur=%.1f",
			ms(tStart, tSession), ms(tSession, tCommunity), ms(tCommunity, tPersonal),
			ms(tPersonal, tComments), ms(tComments, tRange10), ms(tRange10, tRange100),
			ms(tStart, tRange100));
	}

	/**
	 * Logs the full phase breakdown of one lookup, including the JSON
	 * serialization. Outliers (slower than {@link #SLOW_LOOKUP_MS}) go to
	 * INFO so they surface in production logs without enabling DEBUG; the
	 * rest stay at DEBUG.
	 */
	private static void logTiming(long tStart, long tSession, long tCommunity, long tPersonal,
			long tComments, long tRange10, long tRange100, long tDone) {
		boolean slow = ms(tStart, tDone) >= SLOW_LOOKUP_MS;
		if (slow ? !LOG.isInfoEnabled() : !LOG.isDebugEnabled()) {
			return;
		}
		String breakdown = String.format(Locale.ROOT,
			"check-prefix timing [ms]: total=%.0f session=%.0f community=%.0f personal=%.0f "
				+ "comments=%.0f range10=%.0f range100=%.0f serialize=%.0f",
			ms(tStart, tDone), ms(tStart, tSession), ms(tSession, tCommunity),
			ms(tCommunity, tPersonal), ms(tPersonal, tComments), ms(tComments, tRange10),
			ms(tRange10, tRange100), ms(tRange100, tDone));
		if (slow) {
			LOG.info(breakdown);
		} else {
			LOG.debug(breakdown);
		}
	}

	static List<RangeMatch> toRangeMatches(List<AggregationInfo> aggs, long now) {
		List<RangeMatch> result = new ArrayList<>(aggs.size());
		for (AggregationInfo a : aggs) {
			// Return the prefix in international (E.164) format so it lines up with
			// the E.164 form returned in `numbers[]` and the form clients hash.
			// Aggregation rows are stored as national keys ("0163…", "0018…") but
			// the SHA-1 columns are computed over the international form, so the
			// client's prefix-length comparison must also see the +-form here.
			//
			// #300 follow-up: VOTES is no longer maintained; the row carries the summed projected
			// member evidence. votes = its decoded net magnitude (the wildcard-vote value), cnt =
			// the current member count.
			double spam = Ema.decode(a.getSpamEvidence(), now, Ema.CLASSIFICATION_TAU_MILLIS);
			double legit = Ema.decode(a.getLegitEvidence(), now, Ema.CLASSIFICATION_TAU_MILLIS);
			result.add(RangeMatch.create()
				.setPrefix(NumberAnalyzer.toInternationalFormat(a.getPrefix()))
				.setVotes((int) Math.round(Math.max(0.0, spam - legit)))
				.setCnt(a.getCnt()));
		}
		return result;
	}

	static boolean isValidHexPrefix(String s) {
		if (s == null) {
			return false;
		}
		int len = s.length();
		if (len < MIN_PREFIX_HEX || len > MAX_PREFIX_HEX || (len & 1) != 0) {
			return false;
		}
		for (int i = 0; i < len; i++) {
			char c = s.charAt(i);
			boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
			if (!hex) {
				return false;
			}
		}
		return true;
	}

	/** Inclusive lower bound: the prefix bytes, padded with zeros to a 20-byte SHA-1. */
	static byte[] prefixLow(String hexPrefix) {
		int prefixBytes = hexPrefix.length() / 2;
		byte[] result = new byte[20];
		for (int i = 0; i < prefixBytes; i++) {
			int hi = Character.digit(hexPrefix.charAt(i * 2), 16);
			int lo = Character.digit(hexPrefix.charAt(i * 2 + 1), 16);
			result[i] = (byte) ((hi << 4) | lo);
		}
		return result;
	}

	/** Exclusive upper bound: the first SHA-1 value just past the prefix. */
	static byte[] prefixHigh(String hexPrefix) {
		byte[] result = prefixLow(hexPrefix);
		int prefixBytes = hexPrefix.length() / 2;
		for (int i = prefixBytes - 1; i >= 0; i--) {
			int v = (result[i] & 0xff) + 1;
			result[i] = (byte) v;
			if (v < 0x100) {
				return result;
			}
			// Overflow of this byte — carry into the more significant one.
		}
		// Every prefix byte was 0xff: saturate to the 20-byte maximum. The only SHA-1
		// value this excludes is the (astronomically unlikely) all-ones hash itself.
		for (int i = 0; i < 20; i++) {
			result[i] = (byte) 0xff;
		}
		return result;
	}

}
