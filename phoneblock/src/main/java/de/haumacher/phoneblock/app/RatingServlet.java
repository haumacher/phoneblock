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
import de.haumacher.phoneblock.analysis.PhoneNumer;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;

/**
 * Servlet accepting ratings from the web UI.
 */
@WebServlet(urlPatterns = "/rating")
public class RatingServlet extends HttpServlet {
	
	private static final Logger LOG = LoggerFactory.getLogger(RatingServlet.class);

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String phoneParam = req.getParameter("phone");
		PhoneNumer phoneNumer = NumberAnalyzer.analyze(phoneParam);
		if (phoneNumer == null) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		
		String phoneId = SearchServlet.getPhoneId(phoneNumer);
		
		String ratingName = req.getParameter("rating");
		
		HttpSession session = req.getSession();
		String ratingAttr = ratingAttribute(phoneId);
		if (ratingName != null && session.getAttribute(ratingAttr) == null) {
			Integer ratingCnt = (Integer) session.getAttribute("ratingCnt");
			int ratings = ratingCnt == null ? 0 : ratingCnt.intValue();
			if (ratings < 5) {
				session.setAttribute("ratingCnt", Integer.valueOf(ratings + 1));
				
				Rating rating = Rating.valueOf(ratingName);
				DB db = DBService.getInstance();
				db.addRating(phoneId, rating, System.currentTimeMillis());
				
				LOG.info("Recorded rating: " + phoneId + " (" + rating + ")");
			} else {
				LOG.warn("Ignored rating, exceeded max rating count: " + phoneId + " (" + ratingName + ")");
			}
			
			session.setAttribute(ratingAttr, Boolean.TRUE);
		} else {
			LOG.warn("Ignored rating for the same number: " + phoneId + " (" + ratingName + ")");
		}
		
		resp.sendRedirect(req.getContextPath() + SearchServlet.NUMS_PREFIX + "/" + phoneId + "?link=true");
	}

	public static String ratingAttribute(String phone) {
		return "rating/" + phone;
	}

}
