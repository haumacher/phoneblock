/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.analysis.NumberAnalyzer;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.model.Rating;

/**
 * Servlet accepting ratings from the web UI.
 */
@WebServlet(urlPatterns = "/rating")
public class RatingServlet extends HttpServlet {
	
	private static final Logger LOG = LoggerFactory.getLogger(RatingServlet.class);

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String phoneText = req.getParameter("phone");
		
		String phoneId = NumberAnalyzer.toId(phoneText);
		if (phoneId == null) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		
		String ratingName = req.getParameter("rating");
		
		String comment = req.getParameter("comment");
		if (comment != null) {
			comment = comment.trim();
		}
		
		HttpSession session = req.getSession();
		String ratingAttr = ratingAttribute(phoneId);
		if (session.getAttribute(ratingAttr) == null) {
			if (ratingName != null || comment != null) {
				Integer ratingCnt = (Integer) session.getAttribute("ratingCnt");
				int ratings = ratingCnt == null ? 0 : ratingCnt.intValue();
				if (ratings < 5) {
					session.setAttribute("ratingCnt", Integer.valueOf(ratings + 1));
					
					Rating rating = ratingName != null ? Rating.valueOf(ratingName) : Rating.B_MISSED;
					DB db = DBService.getInstance();
					db.addRating(phoneId, rating, comment, System.currentTimeMillis());
					
					LOG.info("Recorded rating: " + phoneId + " (" + rating + ")");
				} else {
					LOG.warn("Ignored rating, exceeded max rating count: " + phoneId + " (" + ratingName + ")");
				}
				
				session.setAttribute(ratingAttr, Boolean.TRUE);
			}
		} else {
			LOG.warn("Ignored rating for the same number: " + phoneId + " (" + ratingName + "): " + comment);
		}
		
		resp.sendRedirect(req.getContextPath() + SearchServlet.NUMS_PREFIX + "/" + phoneId + "?link=true");
	}

	public static String ratingAttribute(String phone) {
		return "rating/" + phone;
	}

}
