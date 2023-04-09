/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
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

import org.apache.ibatis.session.SqlSession;

import de.haumacher.phoneblock.analysis.NumberAnalyzer;
import de.haumacher.phoneblock.analysis.PhoneNumer;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.Ratings;
import de.haumacher.phoneblock.db.SpamReport;
import de.haumacher.phoneblock.db.SpamReports;
import de.haumacher.phoneblock.db.model.Rating;
import de.haumacher.phoneblock.db.model.RatingInfo;
import de.haumacher.phoneblock.db.model.SearchInfo;
import de.haumacher.phoneblock.db.model.UserComment;
import de.haumacher.phoneblock.meta.MetaSearchService;

/**
 * Servlet displaying information about a telephone number in the DB.
 */
@WebServlet(urlPatterns = SearchServlet.NUMS_PREFIX + "/*")
public class SearchServlet extends HttpServlet {
	
	private static final int ONE_SECOND = 1000;

	private static final int ONE_MINUTE = ONE_SECOND * 60;

	private static final int ONE_HOUR = ONE_MINUTE * 60;

	private static final long ONE_DAY = ONE_HOUR * 24;
	
	public static final long ONE_MONTH = ONE_DAY * 30;

	static final String NUMS_PREFIX = "/nums";
	
	private static final Pattern BOT_PATTERN = Pattern.compile(
			or("Googlebot"
			, "AdsBot-Google"
			, "AhrefsBot"
			, "bingbot"
			, "BingPreview"
			, "CFNetwork"
			, "Curl"
			, "custo"
			, "DuckDuckGo-Favicons-Bot"
			, "facebookexternalhit"
			, "Googlebot-Image"
			, "libwww-perl"
			, "LinkedInBot"
			, "PetalBot"
			, "python"
			, "SemrushBot"
			, "SeznamBot"
			, "TelegramBot"
			, "YandexBot"
			, "YandexImages"
			));

	private static final Comparator<? super UserComment> COMMENT_ORDER = new Comparator<>() {
		@Override
		public int compare(UserComment c1, UserComment c2) {
			int v = votes(c2) - votes(c1);
			if (v != 0) {
				return v;
			}
			
			int r = rating(c2) - rating(c1);
			if (r != 0) {
				return r;
			}
			
			int l = length(c2) - length(c1);
			if (l != 0) {
				return l;
			}
			
			return Long.compare(c2.getCreated(), c1.getCreated());
		}

		private int length(UserComment c1) {
			int length = c1.getComment().length();
			if (length < 20) {
				return 0;
			}
			if (length < 40) {
				return 1;
			}
			if (length < 80) {
				return 2;
			}
			return 3;
		}

		private int rating(UserComment c1) {
			return c1.getRating() == Rating.A_LEGITIMATE ? 1 : 0;
		}

		private int votes(UserComment c1) {
			return c1.getUp() - c1.getDown();
		}
	};

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String pathInfo = req.getPathInfo();
		if (pathInfo == null || pathInfo.length() < 1) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		
		String phone = NumberAnalyzer.normalizeNumber(pathInfo.substring(1));
		if (phone.isEmpty() || phone.contains("*")) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		
		PhoneNumer number = NumberAnalyzer.analyze(phone);
		if (number == null) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		String phoneId = NumberAnalyzer.getPhoneId(number);

		// Note: Search for comments first, since new comments may change the state of the number.
		List<UserComment> comments = MetaSearchService.getInstance().fetchComments(phoneId);
		comments.sort(COMMENT_ORDER);
		
		SpamReport info;
		List<? extends SearchInfo> searches;
		List<? extends RatingInfo> ratingInfos;
		String simpleSummary;
		List<String> relatedNumbers;
		
		DB db = DBService.getInstance();
		try (SqlSession session = db.openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			boolean commit = false;
			
			long now = System.currentTimeMillis();
			
			if (!isBot(req) && req.getParameter("link") == null) {
				db.addSearchHit(reports, phoneId, now);
				commit = true;
			}
			
			
			info = db.getPhoneInfo(reports, phone);
			searches = db.getSearches(reports, phoneId);
			ratingInfos = reports.getRatings(phoneId);
			simpleSummary = reports.getSummary(phoneId);
			
			relatedNumbers = reports.getRelatedNumbers(phoneId);
			
			if (commit) {
				session.commit();
			}
		}
		
		Rating topRating = Rating.B_MISSED;
		int maxVotes = 0;
		Map<Rating, Integer> ratings = new HashMap<>();
		for (RatingInfo ratingInfo : ratingInfos) {
			Rating currentRating = ratingInfo.getRating();
			int ratingVotes = ratingInfo.getVotes();
			ratings.put(currentRating, ratingVotes);
			
			if (ratingVotes > maxVotes && currentRating != Rating.A_LEGITIMATE) {
				topRating = currentRating;
				maxVotes = ratingVotes;
			}
		}
		
		int votes = info.getVotes();
		if (votes == 0) {
			topRating = Rating.A_LEGITIMATE;
		}
		
		String ratingAttribute = RatingServlet.ratingAttribute(phoneId);
		if (getSessionAttribute(req, ratingAttribute) != null) {
			req.setAttribute("thanks", Boolean.TRUE);
		}
		
		String status = status(votes);
		
		String defaultSummary = defaultSummary(req, info);
		
		String pageSummary;
		if (simpleSummary == null || simpleSummary.isBlank()) {
			pageSummary = defaultSummary;
			
			simpleSummary = defaultSimpleSummary(info);
		} else {
			pageSummary = simpleSummary + " " + defaultSummary;
		}
		
		req.setAttribute("comments", comments);

		// The canonical path of this page.
		req.setAttribute("path", req.getServletPath() + '/' + phoneId);
		
		req.setAttribute("info", info);
		req.setAttribute("number", number);
		req.setAttribute("summary", pageSummary);
		req.setAttribute("rating", topRating);
		req.setAttribute("ratings", ratings);
		req.setAttribute("searches", searches);
		req.setAttribute("relatedNumbers", relatedNumbers);
		req.setAttribute("title", status + ": Rufnummer ☎ " + phoneId + " - PhoneBlock");
		req.setAttribute("description", simpleSummary + ". Mit PhoneBlock Werbeanrufe automatisch blockieren, kostenlos und ohne Zusatzhardware.");
		
		StringBuilder keywords = new StringBuilder();
		keywords.append("Anrufe, Bewertung");
		if (number.getShortcut() != null) {
			keywords.append(", ");
			keywords.append(number.getShortcut());
		}
		keywords.append(", ");
		keywords.append(number.getZeroZero());

		keywords.append(", ");
		keywords.append(number.getPlus());
		
		if (topRating != Rating.B_MISSED) {
			keywords.append(", ");
			keywords.append(Ratings.getLabel(topRating));
		}
		
		keywords.append(", ");
		keywords.append(status);

		if (number.getCity() != null) {
			keywords.append(", ");
			keywords.append(number.getCity());
		}
		
		req.setAttribute("keywords", keywords.toString());
		
		req.getRequestDispatcher("/phone-info.jsp").forward(req, resp);
	}

	private String defaultSummary(HttpServletRequest req, SpamReport info) {
		int votes = info.getVotes();
		if (votes == 0) {
			return "Die Telefonnummer ist nicht in der <a href=\"" + req.getContextPath() +
					"/\">PhoneBlock</a>-Datenbank vorhanden. Es gibt bisher keine Stimmen, die für eine Sperrung von ☎ <code>" + 
					info.getPhone() + "</code> sprechen.";
		} else if (votes < DB.MIN_VOTES || info.isArchived()) {
			return "Es gibt bereits " + (votes == 1 ? "eine Stimme" : votes + " Stimmen") 
					+ " die für eine Sperrung von ☎ <code>" + info.getPhone() + "</code> sprechen. Die Nummer wird aber noch nicht blockiert.";
		} else {
			return "Die Telefonnummer ☎ <code>" + info.getPhone() + "</code> is eine mehrfach berichtete Quelle von <a href=\"" + req.getContextPath()
					+ "/status.jsp\">unerwünschten Telefonanrufen</a>. " + votes + " Stimmen sprechen sich für eine Sperrung der Nummer aus.";
		}
	}

	private String defaultSimpleSummary(SpamReport info) {
		int votes = info.getVotes();
		if (votes == 0) {
			return "Es gibt keine Beschwerden über die Telefonnummer ☎ " + info.getPhone() + ".";
		} else if (votes < DB.MIN_VOTES || info.isArchived()) {
			return "Es gibt bereits " + (votes == 1 ? "eine Stimme" : votes + " Stimmen") 
					+ " die für eine Sperrung von ☎ " + info.getPhone() + " sprechen. Die Nummer wird aber noch nicht blockiert.";
		} else {
			return "Die Telefonnummer ☎ " + info.getPhone() + " is eine mehrfach berichtete Quelle von unerwünschten Telefonanrufen. " + 
					votes + " Stimmen sprechen sich für eine Sperrung der Nummer aus.";
		}
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
