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
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.SpamReports;
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
			hash = new byte[20];
			int pos = 0;
			for (int n = 0; n < length; n+=2) {
				int msb = hex(encodedHash.charAt(n));
				int lsb = hex(encodedHash.charAt(n + 1));
				hash[pos++] = (byte) (msb << 4 | lsb);
			}
		} catch (Exception ex) {
			ServletUtil.sendError(resp, "Not a valid hash, 40 hex digits required: " + ex.getMessage());
			return;
		}

		String phoneId;
		
		DB db = DBService.getInstance();
		try (SqlSession session = db.openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			
			phoneId = reports.resolvePhoneHash(hash);
		}
		
		PhoneInfo info;
		
		PhoneNumer number = phoneId == null ? null : NumberAnalyzer.analyze(phoneId);
		if (number == null) {
			info = createUnknownResult();
		} else {
			info = NumServlet.lookup(req, number);
			
			if (info.getVotes() == 0 && info.getVotesWildcard() == 0) {
				// Provide no additional information for non-SPAM numbers that have an
				// accidental match in the DB (because they were recorded by a non-hashed
				// lookup).
				info = createUnknownResult();
			}
		}
		
		ServletUtil.sendResult(req, resp, info);
	}

	private PhoneInfo createUnknownResult() {
		return PhoneInfo.create().setPhone("unknown").setRating(Rating.A_LEGITIMATE);
	}

	private int hex(char ch) {
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
