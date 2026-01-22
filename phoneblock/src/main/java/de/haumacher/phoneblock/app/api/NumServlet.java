/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.api;

import java.io.IOException;

import org.apache.ibatis.session.SqlSession;

import de.haumacher.phoneblock.analysis.NumberAnalyzer;
import de.haumacher.phoneblock.app.LoginFilter;
import de.haumacher.phoneblock.app.SearchServlet;
import de.haumacher.phoneblock.app.api.model.PhoneInfo;
import de.haumacher.phoneblock.app.api.model.PhoneNumer;
import de.haumacher.phoneblock.app.api.model.Rating;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.SpamReports;
import de.haumacher.phoneblock.db.Users;
import de.haumacher.phoneblock.location.LocationService;
import de.haumacher.phoneblock.meta.MetaSearchService;
import de.haumacher.phoneblock.util.ServletUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Servlet for live API lookup of ratings.
 */
@WebServlet(urlPatterns = NumServlet.PREFIX + "/*")
public class NumServlet extends HttpServlet {

	static final String PREFIX = "/api/num";

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String pathInfo = req.getPathInfo();
		if (pathInfo == null || pathInfo.length() < 1) {
			ServletUtil.sendError(resp, "Missing phone number.");
			return;
		}
		
		String phone = NumberAnalyzer.normalizeNumber(pathInfo.substring(1));
		if (phone.isEmpty() || phone.contains("*")) {
			ServletUtil.sendError(resp, "Invalid phone number.");
			return;
		}
		
		String dialPrefix = ServletUtil.lookupDialPrefix(req);
		PhoneNumer number = NumberAnalyzer.analyze(phone, dialPrefix);
		if (number == null) {
			ServletUtil.sendError(resp, "Invalid phone number.");
			return;
		}

		ServletUtil.sendResult(req, resp, lookup(req, number));
	}

	static PhoneInfo lookup(HttpServletRequest req, PhoneNumer number) {
		String phoneId = NumberAnalyzer.getPhoneId(number);
		
		DB db = DBService.getInstance();
		MetaSearchService.getInstance().scheduleMetaSearch(number);
		
		try (SqlSession session = db.openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			PhoneInfo result = db.getPhoneApiInfo(reports, phoneId);
			
			// Do not hand out any information for non-spam numbers, even if they have been
			// (accidentally) recorded in the DB.
			if (result.getVotes() == 0 && result.getVotesWildcard() == 0) {
				result.setArchived(false);
				result.setDateAdded(0);
				result.setLastUpdate(0);
				result.setRating(Rating.A_LEGITIMATE);
				return result;
			}
			
			// Only record search hits, for numbers that are suspicious to SPAM.
			if (!SearchServlet.isBot(req)) {
				String dialPrefix;

				String userName = LoginFilter.getAuthenticatedUser(req);
				if (userName == null) {
					dialPrefix = LocationService.getInstance().getDialPrefix(req);
				} else {
					dialPrefix = session.getMapper(Users.class).getDialPrefix(userName);
				}
				
				db.addSearchHit(number, dialPrefix);
			}
			
			return result;
		}
	}

}
