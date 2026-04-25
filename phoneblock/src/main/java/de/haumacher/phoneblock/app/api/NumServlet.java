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
import de.haumacher.phoneblock.db.BlockList;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.SpamReports;
import de.haumacher.phoneblock.db.Users;
import de.haumacher.phoneblock.db.settings.AuthToken;
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

	public static final String PREFIX = "/api/num";

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

		// For authenticated requests, honor the user's personal blacklist/whitelist
		// (same semantics as SpamCheckServlet, but by plain phone number instead of
		// SHA1 hash). Anonymous requests fall through to the public lookup.
		AuthToken auth = LoginFilter.getAuthorization(req);
		if (auth != null) {
			String phoneId = NumberAnalyzer.getPhoneId(number);
			DB db = DBService.getInstance();
			try (SqlSession session = db.openSession()) {
				BlockList blocklist = session.getMapper(BlockList.class);
				Boolean state = blocklist.getPersonalizationState(auth.getUserId(), phoneId);
				if (state != null) {
					PhoneInfo info;
					if (state.booleanValue()) {
						// User has personally blocked this number — return real community data
						// with blackListed flag so the client can force-block.
						SpamReports reports = session.getMapper(SpamReports.class);
						info = db.getPhoneApiInfo(reports, phoneId);
						info.setBlackListed(true);
					} else {
						// User has personally whitelisted this number.
						info = NumberAnalyzer.phoneInfoFromId(phoneId)
							.setRating(Rating.A_LEGITIMATE);
					}
					info.setLabel(number.getShortcut());
					if (number.hasCity()) {
						info.setLocation(number.getCity());
					}
					ServletUtil.sendResult(req, resp, info);
					return;
				}
			}
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

			// Add display-friendly label and location from the analyzed number
			result.setLabel(number.getShortcut());
			if (number.hasCity()) {
				result.setLocation(number.getCity());
			}

			// Do not hand out any information for non-spam numbers, even if they have been
			// (accidentally) recorded in the DB.
			if (result.getVotes() <= 0 && result.getVotesWildcard() <= 0) {
				result.setArchived(false);
				result.setDateAdded(0);
				result.setLastUpdate(0);
				result.setRating(Rating.A_LEGITIMATE);
				return result;
			}

			// Only record search hits, for numbers that are suspicious to SPAM.
			if (!SearchServlet.isBot(req)) {
				String dialPrefix = ServletUtil.lookupDialPrefix(req);

				db.addSearchHit(number, dialPrefix);
			}

			return result;
		}
	}

}
