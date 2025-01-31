/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.api;

import java.io.IOException;

import org.apache.ibatis.session.SqlSession;

import com.google.api.client.http.HttpResponse;

import de.haumacher.phoneblock.analysis.NumberAnalyzer;
import de.haumacher.phoneblock.app.SearchServlet;
import de.haumacher.phoneblock.app.api.model.PhoneInfo;
import de.haumacher.phoneblock.app.api.model.PhoneNumer;
import de.haumacher.phoneblock.app.api.model.Rating;
import de.haumacher.phoneblock.app.api.model.SearchResult;
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
		
		if (phoneId == null) {
			// Not in the DB.
			PhoneInfo info = PhoneInfo.create().setRating(Rating.A_LEGITIMATE);
			ServletUtil.sendResult(req, resp, info);
			return;
		}
		
		NumServlet.process(req, resp, phoneId);
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
