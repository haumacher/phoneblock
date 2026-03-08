/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.api;

import java.io.IOException;

import org.apache.ibatis.session.SqlSession;

import de.haumacher.phoneblock.analysis.NumberAnalyzer;
import de.haumacher.phoneblock.app.LoginFilter;
import de.haumacher.phoneblock.app.api.model.PhoneInfo;
import de.haumacher.phoneblock.app.api.model.PhoneNumer;
import de.haumacher.phoneblock.app.api.model.Rating;
import de.haumacher.phoneblock.db.AggregationInfo;
import de.haumacher.phoneblock.db.BlockList;
import de.haumacher.phoneblock.db.DB;
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
 * API servlet for deciding whether a number is in the database without revealing the calling number to the service.
 *
 * <p>
 * This API provides more privacy than the regular {@link NumServlet search API}.
 * </p>
 */
@WebServlet(urlPatterns = SpamCheckServlet.PATH)
public class SpamCheckServlet extends HttpServlet {

	public static final String PATH = "/api/check";

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String userName = LoginFilter.getAuthenticatedUser(req);
		if (userName == null) {
			ServletUtil.sendAuthenticationRequest(resp);
			return;
		}

		String encodedHash = req.getParameter("sha1");

		int length = encodedHash.length();
		if (encodedHash == null || length != 40) {
			ServletUtil.sendError(resp, "Not a valid hash, 40 hex digits required.");
			return;
		}

		byte[] hash;
		try {
			hash = decodeHex(encodedHash);
		} catch (Exception ex) {
			ServletUtil.sendError(resp, "Not a valid hash, 40 hex digits required: " + ex.getMessage());
			return;
		}

		DB db = DBService.getInstance();

		// Check user's personal block/whitelist by hash before global lookup.
		AuthToken auth = LoginFilter.getAuthorization(req);
		if (auth != null) {
			try (SqlSession session = db.openSession()) {
				BlockList blocklist = session.getMapper(BlockList.class);
				DBPersonalization personalized = blocklist.resolvePersonalizationByHash(auth.getUserId(), hash);
				if (personalized != null) {
					if (personalized.isBlocked()) {
						// User has personally blocked this number — look up actual community data too.
						PhoneInfo info = db.getPhoneApiInfo(personalized.getPhone());
						info.setBlackListed(true);
						// Ensure votes are at least 1000 so the call is always blocked
						// regardless of the user's minVotes setting.
						if (info.getVotes() < 1000) {
							info.setVotes(1000);
						}
						ServletUtil.sendResult(req, resp, info);
					} else {
						// User has personally whitelisted this number.
						PhoneInfo info = NumberAnalyzer.phoneInfoFromId(personalized.getPhone())
							.setRating(Rating.A_LEGITIMATE);
						ServletUtil.sendResult(req, resp, info);
					}
					return;
				}
			}
		}

		String phoneId;

		try (SqlSession session = db.openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);

			phoneId = reports.resolvePhoneHash(hash);
		}

		PhoneInfo info;

		PhoneNumer number = phoneId == null ? null : NumberAnalyzer.analyzePhoneID(phoneId);
		if (number == null) {
			info = createUnknownResult();
		} else {
			info = NumServlet.lookup(req, number);

			if (info.getVotes() <= 0 && info.getVotesWildcard() <= 0) {
				// Provide no additional information for non-SPAM numbers that have an
				// accidental match in the DB (because they were recorded by a non-hashed
				// lookup).
				info = createUnknownResult();
			}
		}

		// If no match found (or number has no votes), try prefix hash lookup for range detection.
		if (info.getVotes() <= 0 && info.getVotesWildcard() <= 0) {
			PhoneInfo prefixInfo = lookupPrefixHashes(req, db);
			if (prefixInfo != null) {
				info = prefixInfo;
			}
		}

		ServletUtil.sendResult(req, resp, info);
	}

	/**
	 * Looks up prefix hashes to detect spam ranges for numbers not individually in the DB.
	 *
	 * @return A {@link PhoneInfo} with wildcard votes if a spam range is detected, or {@code null}.
	 */
	private PhoneInfo lookupPrefixHashes(HttpServletRequest req, DB db) {
		String prefix10Hex = req.getParameter("prefix10");
		String prefix100Hex = req.getParameter("prefix100");

		if (prefix10Hex == null && prefix100Hex == null) {
			return null;
		}

		byte[] prefix10Hash = null;
		byte[] prefix100Hash = null;

		try {
			if (prefix10Hex != null && prefix10Hex.length() == 40) {
				prefix10Hash = decodeHex(prefix10Hex);
			}
			if (prefix100Hex != null && prefix100Hex.length() == 40) {
				prefix100Hash = decodeHex(prefix100Hex);
			}
		} catch (Exception ex) {
			// Invalid prefix hashes are silently ignored.
			return null;
		}

		if (prefix10Hash == null && prefix100Hash == null) {
			return null;
		}

		try (SqlSession session = db.openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);

			AggregationInfo a10 = prefix10Hash != null
				? db.notNull("", reports.getAggregation10ByHash(prefix10Hash))
				: new AggregationInfo("", 0, 0);

			AggregationInfo a100 = prefix100Hash != null
				? db.notNull("", reports.getAggregation100ByHash(prefix100Hash))
				: new AggregationInfo("", 0, 0);

			int votesWildcard = db.computeWildcardVotes(a10, a100);
			if (votesWildcard > 0) {
				return PhoneInfo.create()
					.setPhone("unknown")
					.setRating(Rating.B_MISSED)
					.setVotes(0)
					.setVotesWildcard(votesWildcard);
			}
		}

		return null;
	}

	private PhoneInfo createUnknownResult() {
		return PhoneInfo.create()
			.setPhone("unknown")
			.setRating(Rating.A_LEGITIMATE);
	}

	/**
	 * Decodes a 40-character hex string into a 20-byte array.
	 */
	static byte[] decodeHex(String hexString) {
		int length = hexString.length();
		byte[] result = new byte[length / 2];
		int pos = 0;
		for (int n = 0; n < length; n += 2) {
			int msb = hex(hexString.charAt(n));
			int lsb = hex(hexString.charAt(n + 1));
			result[pos++] = (byte) (msb << 4 | lsb);
		}
		return result;
	}

	private static int hex(char ch) {
		return switch (Character.toUpperCase(ch)) {
		case '0' -> 0;
		case '1' -> 1;
		case '2' -> 2;
		case '3' -> 3;
		case '4' -> 4;
		case '5' -> 5;
		case '6' -> 6;
		case '7' -> 7;
		case '8' -> 8;
		case '9' -> 9;
		case 'A' -> 10;
		case 'B' -> 11;
		case 'C' -> 12;
		case 'D' -> 13;
		case 'E' -> 14;
		case 'F' -> 15;
		default -> throw new IllegalArgumentException("Unexpected hex character: " + ch);
		};
	}

}
