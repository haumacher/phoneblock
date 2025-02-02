/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import java.io.IOException;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.analysis.NumberAnalyzer;
import de.haumacher.phoneblock.app.api.model.PhoneNumer;
import de.haumacher.phoneblock.app.api.model.Rating;
import de.haumacher.phoneblock.db.BlockList;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.Users;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Servlet accepting ratings from the web UI.
 */
@WebServlet(urlPatterns = "/rating")
public class RatingServlet extends HttpServlet {
	
	private static final Logger LOG = LoggerFactory.getLogger(RatingServlet.class);

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("utf-8");
        
		String phoneText = req.getParameter("phone");
		
		PhoneNumer number = NumberAnalyzer.parsePhoneNumber(phoneText);
		if (number == null) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		String phoneId = NumberAnalyzer.getPhoneId(number);
		
		String ratingName = req.getParameter("rating");
		
		String comment = req.getParameter("comment");
		if (comment != null) {
			comment = comment.trim();
			if (comment.isEmpty()) {
				comment = null;
			}
		}
		
		HttpSession session = req.getSession();
		String ratingAttr = ratingAttribute(phoneId);
		if (session.getAttribute(ratingAttr) == null) {
			if (ratingName != null || comment != null) {
				Rating rating = ratingName != null ? Rating.valueOf(ratingName) : Rating.B_MISSED;
				String userName = LoginFilter.getAuthenticatedUser(req.getSession());

				DB db = DBService.getInstance();
				db.addRating(userName, number, rating, comment, System.currentTimeMillis());
				
				LOG.info("Recorded rating: " + phoneId + " (" + rating + ")");
				
				session.setAttribute(ratingAttr, Boolean.TRUE);
			}
		} else {
			LOG.warn("Ignored rating for the same number: " + phoneId + " (" + ratingName + "): " + comment);
		}
		
		resp.sendRedirect(req.getContextPath() + SearchServlet.NUMS_PREFIX + "/" + phoneId + "?link=true");
	}

	/**
	 * The session attribute that stores whether a rating for a certain phone number has been recorded.
	 */
	public static String ratingAttribute(String phone) {
		return "rating/" + phone;
	}

}
