/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.analysis.NumberAnalyzer;
import de.haumacher.phoneblock.app.api.model.PhoneNumer;
import de.haumacher.phoneblock.app.api.model.Rating;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Servlet accepting ratings from the web UI.
 */
@WebServlet(urlPatterns = RatingServlet.PATH)
public class RatingServlet extends HttpServlet {
	
	public static final String PATH = "/rating";
	private static final Logger LOG = LoggerFactory.getLogger(RatingServlet.class);
	public static final String CAPTCHA_ERROR_ATTR = "captchaError";
	public static final String ENTERED_RATING_ATTR = "enteredRating";
	public static final String ENTERED_COMMENT_ATTR = "enteredComment";

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

		String userName = LoginFilter.getAuthenticatedUser(req);
        if (userName == null) {
    		String captcha = req.getParameter("captcha");
    		if (captcha == null || captcha.trim().isEmpty()) {
    			sendFailure(req, resp, phoneId, ratingName, comment, "Du musst den Sicherheitscode eingeben.");
    			return;
    		}

    		HttpSession session = req.getSession();
    		String captchaExpected = (String) session.getAttribute("captcha");
    		session.removeAttribute("captcha");
    		if (!captcha.trim().equals(captchaExpected)) {
    			sendFailure(req, resp, phoneId, ratingName, comment, "Der Sicherheitscode stimmt nicht überein.");
    			return;
    		}
        }
        
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

	private void sendFailure(HttpServletRequest req, HttpServletResponse resp, String phoneId, String rating, String comment, String message)
			throws ServletException, IOException {
		HttpSession session = req.getSession();
		session.setAttribute(CAPTCHA_ERROR_ATTR, message);
		session.setAttribute(ENTERED_RATING_ATTR, rating);
		session.setAttribute(ENTERED_COMMENT_ATTR, comment);
		resp.sendRedirect(req.getContextPath() + SearchServlet.NUMS_PREFIX + "/" + phoneId + "/?link=true#writeRating" );
	}

	/**
	 * The session attribute that stores whether a rating for a certain phone number has been recorded.
	 */
	public static String ratingAttribute(String phone) {
		return "rating/" + phone;
	}

}
