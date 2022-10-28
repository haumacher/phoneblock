/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import de.haumacher.phoneblock.analysis.NumberAnalyzer;
import de.haumacher.phoneblock.analysis.PhoneNumer;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.Ratings;
import de.haumacher.phoneblock.db.SpamReport;
import de.haumacher.phoneblock.db.model.Rating;
import de.haumacher.phoneblock.db.model.RatingInfo;
import de.haumacher.phoneblock.db.model.SearchInfo;

/**
 * Servlet displaying information about a telephone number in the DB.
 */
@WebServlet(urlPatterns = SearchServlet.NUMS_PREFIX + "/*")
public class SearchServlet extends HttpServlet {
	
	static final String NUMS_PREFIX = "/nums";
	
	private static final Pattern BOT_PATTERN = Pattern.compile(
			or("Googlebot"
			, "YandexBot"
			, "bingbot"
			, "SemrushBot"
			, "facebookexternalhit"
			, "CFNetwork"
			, "Googlebot-Image"
			, "BingPreview"
			, "custo"
			, "AdsBot-Google"
			, "libwww-perl"
			, "Curl"
			, "YandexImages"
			, "DuckDuckGo-Favicons-Bot"
			, "LinkedInBot"
			, "python"));

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String pathInfo = req.getPathInfo();
		if (pathInfo == null || pathInfo.length() < 1) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		
		String phone = pathInfo.substring(1).replaceAll("[^\\+0-9]", "");
		if (phone.isEmpty()) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		
		PhoneNumer number = NumberAnalyzer.analyze(phone);
		if (number == null) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		String phoneId = getPhoneId(number);
		
		DB db = DBService.getInstance();
		if (!isBot(req) && req.getParameter("link") == null) {
			db.addSearchHit(phoneId);
		}
		
		SpamReport info = db.getPhoneInfo(phoneId);
		List<? extends SearchInfo> searches = db.getSearches(phoneId);
		int votes = info.getVotes();
		List<? extends RatingInfo> ratingInfos = db.getRatings(phoneId);
		Rating rating = ratingInfos.stream().max(Ratings::compare).map(i -> i.getRating()).orElse(Rating.B_MISSED);
		if (rating == Rating.A_LEGITIMATE && votes > 0) {
			rating = Rating.B_MISSED;
		}
		Map<Rating, Integer> ratings = ratingInfos.stream().collect(Collectors.toMap(i -> i.getRating(), i -> i.getVotes()));
		
		int ratingVotes = ratingInfos.stream().mapToInt(i -> Ratings.getVotes(i.getRating()) * i.getVotes()).reduce(0, (a, b) -> a + b);
		int unknownVotes = votes - ratingVotes;
		if (unknownVotes > 0) {
			ratings.put(Rating.B_MISSED, 
				Integer.valueOf(unknownVotes / Ratings.getVotes(Rating.B_MISSED) + ratings.getOrDefault(Rating.B_MISSED, Integer.valueOf(0)).intValue()));
		}
		
		String ratingAttribute = RatingServlet.ratingAttribute(phoneId);
		if (getSessionAttribute(req, ratingAttribute) != null) {
			req.setAttribute("thanks", Boolean.TRUE);
		}
		
		// The canonical path of this page.
		req.setAttribute("path", req.getServletPath() + '/' + phoneId);
		
		req.setAttribute("info", info);
		req.setAttribute("number", number);
		req.setAttribute("rating", rating);
		req.setAttribute("ratings", ratings);
		req.setAttribute("searches", searches);
		req.setAttribute("title", status(votes) + ": Rufnummer ☎ " + phoneId + " - PhoneBlock");
		if (votes > 0) {
			req.setAttribute("description", votes + " Beschwerden über unerwünschte Anrufe von " + number.getPlus() + ". Mit PhoneBlock Werbeanrufe automatisch blockieren, kostenlos und ohne Zusatzhardware.");
		}
		
		req.getRequestDispatcher("/phone-info.jsp").forward(req, resp);
	}

	/** 
	 * Whether the request is from a known bot.
	 */
	public static boolean isBot(HttpServletRequest req) {
		String userAgent = req.getHeader("User-Agent");
		return userAgent == null || BOT_PATTERN.matcher(userAgent).find();
	}

	private static String or(String... strs) {
		return Stream.of(strs).map(Pattern::quote).collect(Collectors.joining("|"));
	}

	private static Object getSessionAttribute(HttpServletRequest req, String attribute) {
		HttpSession session = req.getSession(false);
		if (session == null) {
			return null;
		}
		return session.getAttribute(attribute);
	}

	public static String getPhoneId(PhoneNumer number) {
		String shortcut = number.getShortcut();
		return shortcut == null ? number.getZeroZero() : shortcut;
	}
	
	private String status(int votes) {
		if (votes == 0) {
			return "Keine Beschwerden";
		} else if (votes < DB.MIN_VOTES) {
			return "Spamverdacht";
		} else {
			return "Blockiert";
		}
	}

}
