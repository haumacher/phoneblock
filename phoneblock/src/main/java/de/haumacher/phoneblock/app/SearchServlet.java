/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.apache.ibatis.session.SqlSession;

import de.haumacher.phoneblock.analysis.NumberAnalyzer;
import de.haumacher.phoneblock.db.AggregationInfo;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBNumberInfo;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.Ratings;
import de.haumacher.phoneblock.db.SpamReports;
import de.haumacher.phoneblock.db.model.NumberInfo;
import de.haumacher.phoneblock.db.model.PhoneInfo;
import de.haumacher.phoneblock.db.model.PhoneNumer;
import de.haumacher.phoneblock.db.model.Rating;
import de.haumacher.phoneblock.db.model.RatingInfo;
import de.haumacher.phoneblock.db.model.SearchInfo;
import de.haumacher.phoneblock.db.model.SearchResult;
import de.haumacher.phoneblock.db.model.SpamReport;
import de.haumacher.phoneblock.db.model.UserComment;
import de.haumacher.phoneblock.meta.MetaSearchService;
import de.haumacher.phoneblock.util.JspUtil;
import de.haumacher.phoneblock.util.ServletUtil;

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
			, "Amazonbot"
			, "Applebot"
			, "bingbot"
			, "BingPreview"
			, "Bytespider"
			, "CFNetwork"
			, "Curl"
			, "custo"
			, "DataForSeoBot"
			, "DotBot"
			, "DuckDuckGo-Favicons-Bot"
			, "facebookexternalhit"
			, "Googlebot-Image"
			, "libwww-perl"
			, "LinkedInBot"
			, "MJ12bot"
			, "Monit"
			, "PetalBot"
			, "python"
			, "SemrushBot"
			, "SeznamBot"
			, "TelegramBot"
			, "YandexBot"
			, "YandexImages"
			, "YandexRenderResourcesBot"
			));

	public static final Comparator<? super UserComment> COMMENT_ORDER = new Comparator<>() {
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
		
		String query = pathInfo.substring(1);
		
		boolean bot = isBot(req);
		boolean isSeachHit = !bot && req.getParameter("link") == null;
		
		SearchResult searchResult = analyze(query, bot, isSeachHit);
		if (searchResult == null) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		
		sendResult(req, resp, searchResult);
	}

	public static SearchResult analyze(String query) {
		return analyze(query, false, true);
	}

	private static SearchResult analyze(String query, boolean isBot, boolean isSeachHit) {
		String phone = NumberAnalyzer.normalizeNumber(query);
		if (phone.isEmpty() || phone.contains("*")) {
			return null;
		}
		
		PhoneNumer number = NumberAnalyzer.analyze(phone);
		if (number == null) {
			return null;
		}
		
		String phoneId = NumberAnalyzer.getPhoneId(number);

		NumberInfo numberInfo;
		PhoneInfo info;
		List<Integer> searches;
		String aiSummary;
		List<String> relatedNumbers;
		
		String prev;
		String next;
		
		List<? extends UserComment> comments;
		
		DB db = DBService.getInstance();
		try (SqlSession session = db.openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			
			// Note: Search for comments first, since new comments may change the state of the number.
			if (isSeachHit) {
				comments = MetaSearchService.getInstance().fetchComments(phoneId);
			} else {
				comments = reports.getComments(phoneId);
			}
			
			boolean commit = false;
			
			long now = System.currentTimeMillis();
			
			if (isSeachHit) {
				db.addSearchHit(reports, phoneId, now);
				commit = true;
			}
			
			numberInfo = db.getPhoneInfo(reports, phone);
			
			if (reports.isWhiteListed(phone)) {
				info = PhoneInfo.create().setPhone(phone).setWhiteListed(true).setRating(Rating.A_LEGITIMATE);
				
				numberInfo.setCalls(0);
				numberInfo.setVotes(0);
				numberInfo.setRatingPing(0);
				numberInfo.setRatingPoll(0);
				numberInfo.setRatingAdvertising(0);
				numberInfo.setRatingGamble(0);
				numberInfo.setRatingFraud(0);
				
				relatedNumbers = Collections.emptyList();
			} else {
				AggregationInfo aggregation10 = db.getAggregation10(reports, phone);
				AggregationInfo aggregation100 = db.getAggregation100(reports, phone);
				
				info = db.getPhoneInfo(numberInfo, aggregation10, aggregation100);
				
				if (aggregation100.getCnt() >= DB.MIN_AGGREGATE_100) {
					relatedNumbers = reports.getRelatedNumbers(aggregation100.getPrefix());
					
					if (comments.isEmpty()) {
						comments = reports.getAllComments(aggregation100.getPrefix());
					}
				} else {
					if (aggregation10.getCnt() >= DB.MIN_AGGREGATE_10) {
						relatedNumbers = reports.getRelatedNumbers(aggregation10.getPrefix());

						if (comments.isEmpty()) {
							comments = reports.getAllComments(aggregation10.getPrefix());
						}
					} else {
						relatedNumbers = Collections.emptyList();
					}
				}
			}
			
			searches = db.getSearchHistory(reports, phoneId, 7);
			aiSummary = reports.getSummary(phoneId);
			
			prev = reports.getPrevPhone(phoneId);
			next = reports.getNextPhone(phoneId);
			
			if (commit) {
				session.commit();
			}
		}
		
		// Ensure that equal number of positive and negative comments are shown (white-listed numbers are an exception).
		List<UserComment> positive = comments.stream().filter(c -> c.getRating() == Rating.A_LEGITIMATE).sorted(COMMENT_ORDER).collect(Collectors.toList());
		List<UserComment> negative = comments.stream().filter(c -> c.getRating() != Rating.A_LEGITIMATE).sorted(COMMENT_ORDER).collect(Collectors.toList());
		
		int positiveCnt = positive.size();
		int negativeCnt = negative.size();
		if (info.isWhiteListed()) {
			positiveCnt = Math.min(10, positiveCnt);
			negativeCnt = 0;
		} else {
			if (positiveCnt > 5) {
				positiveCnt = Math.min(10, Math.max(5, positiveCnt - negativeCnt));
			}
			negativeCnt = Math.min(10 - positiveCnt, negativeCnt);
		}
		List<UserComment> shownComments = new ArrayList<>(positive.subList(0, positiveCnt));
		shownComments.addAll(negative.subList(0, negativeCnt));
		shownComments.sort(COMMENT_ORDER);
		
		Map<Rating, Integer> ratings = new HashMap<>();
		ratings.put(Rating.A_LEGITIMATE, numberInfo.getRatingLegitimate());
		ratings.put(Rating.C_PING, numberInfo.getRatingPing());
		ratings.put(Rating.D_POLL, numberInfo.getRatingPoll());
		ratings.put(Rating.E_ADVERTISING, numberInfo.getRatingAdvertising());
		ratings.put(Rating.F_GAMBLE, numberInfo.getRatingGamble());
		ratings.put(Rating.G_FRAUD, numberInfo.getRatingFraud());
		
		Rating topRating = info.getRating();
		
		return SearchResult.create()
				.setPhoneId(phoneId)
				.setNumber(number)
				.setComments(shownComments)
				.setInfo(info)
				.setSearches(searches)
				.setAiSummary(aiSummary)
				.setRelatedNumbers(relatedNumbers)
				.setPrev(prev)
				.setNext(next)
				.setTopRating(topRating)
				.setRatings(ratings);
	}

	private void sendResult(HttpServletRequest req, HttpServletResponse resp, SearchResult searchResult) throws ServletException, IOException {
		String ratingAttribute = RatingServlet.ratingAttribute(searchResult.getPhoneId());
		if (getSessionAttribute(req, ratingAttribute) != null) {
			req.setAttribute("thanks", Boolean.TRUE);
		}
		
		String status = status(searchResult.getInfo().getVotes());
		
		String defaultSummary = defaultSummary(req, searchResult.getInfo());
		
		String summary;
		String description;
		if (isEmpty(searchResult.getAiSummary())) {
			summary = null;
			description = defaultSimpleSummary(searchResult.getInfo());
		} else {
			description = summary = JspUtil.quote(searchResult.getAiSummary());
		}
		
		req.setAttribute("comments", searchResult.getComments());

		// The canonical path of this page.
		req.setAttribute("path", req.getServletPath() + '/' + searchResult.getPhoneId());
		
		req.setAttribute("info", searchResult.getInfo());
		req.setAttribute("number", searchResult.getNumber());
		req.setAttribute("prev", searchResult.getPrev());
		req.setAttribute("next", searchResult.getNext());
		req.setAttribute("summary", summary);
		req.setAttribute("defaultSummary", defaultSummary);
		req.setAttribute("rating", searchResult.getTopRating());
		req.setAttribute("ratings", searchResult.getRatings());
		req.setAttribute("searches", searchResult.getSearches());
		req.setAttribute("relatedNumbers", searchResult.getRelatedNumbers());
		req.setAttribute("title", status + ": Rufnummer ☎ " + searchResult.getPhoneId() + " - PhoneBlock");
		req.setAttribute("description", description + ". Mit PhoneBlock Werbeanrufe automatisch blockieren, kostenlos und ohne Zusatzhardware.");
		
		StringBuilder keywords = new StringBuilder();
		keywords.append("Anrufe, Bewertung");
		if (searchResult.getNumber().getShortcut() != null) {
			keywords.append(", ");
			keywords.append(searchResult.getNumber().getShortcut());
		}
		keywords.append(", ");
		keywords.append(searchResult.getNumber().getZeroZero());

		keywords.append(", ");
		keywords.append(searchResult.getNumber().getPlus());
		
		if (searchResult.getTopRating() != Rating.B_MISSED) {
			keywords.append(", ");
			keywords.append(Ratings.getLabel(searchResult.getTopRating()));
		}
		
		keywords.append(", ");
		keywords.append(status);

		if (searchResult.getNumber().getCity() != null) {
			keywords.append(", ");
			keywords.append(searchResult.getNumber().getCity());
		}
		
		req.setAttribute("keywords", keywords.toString());
		ServletUtil.display(req, resp, "/phone-info.jsp");
	}

	private static boolean isEmpty(String aiSummary) {
		return aiSummary == null || aiSummary.isBlank();
	}

	private String defaultSummary(HttpServletRequest req, PhoneInfo info) {
		int votes = info.getVotes();
		if (info.isWhiteListed()) {
			return "Die Telefonnummer steht auf der weißen Liste und kann von PhoneBlock nicht gesperrt werden. Wenn Du dich trotzdem von dieser Nummer belästigt fühlst, richte bitte eine private Sperre für diese Nummer ein.";
		}
		if (votes == 0) {
			return "Die Telefonnummer ist nicht in der <a href=\"" + req.getContextPath() +
					"/\">PhoneBlock</a>-Datenbank vorhanden. Es gibt bisher keine Stimmen, die für eine Sperrung von ☎ <code>" + 
					info.getPhone() + "</code> sprechen.";
		} else if (votes < DB.MIN_VOTES || info.isArchived()) {
			return "Es gibt bereits " + (votes == 1 ? "eine Stimme" : votes + " Stimmen") 
					+ " die für eine Sperrung von ☎ <code>" + info.getPhone() + "</code> sprechen. Die Nummer wird aber noch nicht blockiert.";
		} else {
			return "Die Telefonnummer ☎ <code>" + info.getPhone() + "</code> ist eine mehrfach berichtete Quelle von <a href=\"" + req.getContextPath()
					+ "/status.jsp\">unerwünschten Telefonanrufen</a>. " + votes + " Stimmen sprechen sich für eine Sperrung der Nummer aus.";
		}
	}

	private String defaultSimpleSummary(PhoneInfo info) {
		int votes = info.getVotes();
		if (votes == 0) {
			return "Es gibt keine Beschwerden über die Telefonnummer ☎ " + info.getPhone() + ".";
		} else if (votes < DB.MIN_VOTES || info.isArchived()) {
			return "Es gibt bereits " + (votes == 1 ? "eine Stimme" : votes + " Stimmen") 
					+ " die für eine Sperrung von ☎ " + info.getPhone() + " sprechen. Die Nummer wird aber noch nicht blockiert.";
		} else {
			return "Die Telefonnummer ☎ " + info.getPhone() + " ist eine mehrfach berichtete Quelle von unerwünschten Telefonanrufen. " + 
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
