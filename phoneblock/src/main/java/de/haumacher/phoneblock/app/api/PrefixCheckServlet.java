/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.api;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.session.SqlSession;

import de.haumacher.phoneblock.analysis.NumberAnalyzer;
import de.haumacher.phoneblock.app.LoginFilter;
import de.haumacher.phoneblock.app.api.model.PhoneInfo;
import de.haumacher.phoneblock.app.api.model.PrefixCheckResult;
import de.haumacher.phoneblock.app.api.model.RangeMatch;
import de.haumacher.phoneblock.app.api.model.Rating;
import de.haumacher.phoneblock.db.AggregationInfo;
import de.haumacher.phoneblock.db.BlockList;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBNumberInfo;
import de.haumacher.phoneblock.db.DBPersonalization;
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

	public static final String PATH = "/api/check-prefix";

	/** Minimum prefix length in hex characters (16 bit → ~3 000 German candidates per bucket). */
	public static final int MIN_PREFIX_HEX = 4;

	/** Maximum prefix length = full SHA-1. */
	public static final int MAX_PREFIX_HEX = 40;

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
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
				"Invalid 'sha1' prefix: " + MIN_PREFIX_HEX + "–" + MAX_PREFIX_HEX + " hex characters required.");
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

		byte[] sha1Low = prefixLow(sha1Hex);
		byte[] sha1High = prefixHigh(sha1Hex);

		PrefixCheckResult result = PrefixCheckResult.create();
		DB db = DBService.getInstance();

		try (SqlSession session = db.openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			BlockList blocklist = session.getMapper(BlockList.class);

			List<DBNumberInfo> communityMatches = reports.getPhoneInfosByHashPrefix(sha1Low, sha1High);
			List<DBPersonalization> personalMatches = blocklist.getPersonalizationsByHashPrefix(
				auth.getUserId(), sha1Low, sha1High);

			Map<String, PhoneInfo> byPhone = new LinkedHashMap<>();
			for (DBNumberInfo n : communityMatches) {
				PhoneInfo pi = NumberAnalyzer.phoneInfoFromId(n.getPhone())
					.setVotes(n.getVotes())
					.setRating(DB.rating(n))
					.setArchived(!n.isActive())
					.setDateAdded(n.getAdded())
					.setLastUpdate(n.getUpdated());
				byPhone.put(n.getPhone(), pi);
			}
			for (DBPersonalization p : personalMatches) {
				PhoneInfo pi = byPhone.get(p.getPhone());
				if (pi == null) {
					// Personal-only entry: synthesize a minimal PhoneInfo.
					pi = NumberAnalyzer.phoneInfoFromId(p.getPhone())
						.setRating(p.isBlocked() ? Rating.B_MISSED : Rating.A_LEGITIMATE);
					byPhone.put(p.getPhone(), pi);
				}
				if (p.isBlocked()) {
					pi.setBlackListed(true);
				} else {
					// Personal whitelist overrides the community rating for this user.
					pi.setWhiteListed(true);
					pi.setRating(Rating.A_LEGITIMATE);
				}
			}
			result.setNumbers(new ArrayList<>(byPhone.values()));

			if (prefix10Hex != null) {
				byte[] low = prefixLow(prefix10Hex);
				byte[] high = prefixHigh(prefix10Hex);
				result.setRange10(toRangeMatches(reports.getAggregation10ByHashPrefix(low, high)));
			}
			if (prefix100Hex != null) {
				byte[] low = prefixLow(prefix100Hex);
				byte[] high = prefixHigh(prefix100Hex);
				result.setRange100(toRangeMatches(reports.getAggregation100ByHashPrefix(low, high)));
			}
		}

		ServletUtil.sendResult(req, resp, result);
	}

	private static List<RangeMatch> toRangeMatches(List<AggregationInfo> aggs) {
		List<RangeMatch> result = new ArrayList<>(aggs.size());
		for (AggregationInfo a : aggs) {
			result.add(RangeMatch.create()
				.setPrefix(a.getPrefix())
				.setVotes(a.getVotes())
				.setCnt(a.getCnt()));
		}
		return result;
	}

	static boolean isValidHexPrefix(String s) {
		if (s == null) {
			return false;
		}
		int len = s.length();
		if (len < MIN_PREFIX_HEX || len > MAX_PREFIX_HEX) {
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

	/** Inclusive lower bound: the prefix, left-aligned in a 20-byte SHA-1, padded with zeros. */
	static byte[] prefixLow(String hexPrefix) {
		int bits = hexPrefix.length() * 4;
		BigInteger value = new BigInteger(hexPrefix, 16).shiftLeft(160 - bits);
		return toSha1Bytes(value);
	}

	/** Exclusive upper bound: the first SHA-1 value just past the prefix. */
	static byte[] prefixHigh(String hexPrefix) {
		int bits = hexPrefix.length() * 4;
		BigInteger next = new BigInteger(hexPrefix, 16).add(BigInteger.ONE).shiftLeft(160 - bits);
		if (next.bitLength() > 160) {
			// Prefix is all-ones — saturate to the 20-byte maximum. The only SHA-1
			// this excludes is the (astronomically unlikely) all-ones hash itself.
			byte[] max = new byte[20];
			for (int i = 0; i < 20; i++) {
				max[i] = (byte) 0xff;
			}
			return max;
		}
		return toSha1Bytes(next);
	}

	private static byte[] toSha1Bytes(BigInteger value) {
		byte[] result = new byte[20];
		byte[] raw = value.toByteArray();
		int srcOff = Math.max(0, raw.length - 20);
		int copyLen = raw.length - srcOff;
		System.arraycopy(raw, srcOff, result, 20 - copyLen, copyLen);
		return result;
	}

}
