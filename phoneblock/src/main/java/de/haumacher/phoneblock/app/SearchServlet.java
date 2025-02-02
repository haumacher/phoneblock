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

import org.apache.ibatis.session.SqlSession;

import de.haumacher.phoneblock.analysis.NumberAnalyzer;
import de.haumacher.phoneblock.app.api.model.NumberInfo;
import de.haumacher.phoneblock.app.api.model.PhoneInfo;
import de.haumacher.phoneblock.app.api.model.PhoneNumer;
import de.haumacher.phoneblock.app.api.model.Rating;
import de.haumacher.phoneblock.app.api.model.SearchResult;
import de.haumacher.phoneblock.app.api.model.UserComment;
import de.haumacher.phoneblock.db.AggregationInfo;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBNumberInfo;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.Ratings;
import de.haumacher.phoneblock.db.SpamReports;
import de.haumacher.phoneblock.meta.MetaSearchService;
import de.haumacher.phoneblock.util.JspUtil;
import de.haumacher.phoneblock.util.ServletUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Servlet displaying information about a telephone number in the DB.
 */
@WebServlet(urlPatterns = SearchServlet.NUMS_PREFIX + "/*")
public class SearchServlet extends HttpServlet {
	
	public static final String KEYWORDS_ATTR = "keywords";

	public static final String THANKS_ATTR = "thanks";

	public static final String DESCRIPTION_ATTR = "description";

	public static final String TITLE_ATTR = "title";

	public static final String RELATED_NUMBERS_ATTR = "relatedNumbers";

	public static final String SEARCHES_ATTR = "searches";

	public static final String RATINGS_ATTR = "ratings";

	public static final String RATING_ATTR = "rating";

	public static final String DEFAULT_SUMMARY_ATTR = "defaultSummary";

	public static final String SUMMARY_ATTR = "summary";

	public static final String NEXT_ATTR = "next";

	public static final String PREV_ATTR = "prev";

	public static final String NUMBER_ATTR = "number";

	public static final String INFO_ATTR = "info";

	public static final String PATH_ATTR = "path";

	public static final String COMMENTS_ATTR = "comments";

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

		PhoneNumer number = extractNumber(query);
		if (number == null) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		
		String phone = NumberAnalyzer.getPhoneId(number);
		
		if (!phone.equals(query)) {
			// Normalize the URL in the browser.
			resp.sendRedirect(req.getContextPath() +  SearchServlet.NUMS_PREFIX + "/" + phone);
			return;
		}
		
		SearchResult searchResult = analyze(number, isSeachHit);
		sendResult(req, resp, searchResult);
	}

	public static SearchResult analyze(String query) {
		PhoneNumer number = extractNumber(query);
		return number == null ? null : analyze(number, true);
	}

	private static PhoneNumer extractNumber(String query) {
		String rawPhoneNumber = NumberAnalyzer.normalizeNumber(query);
		if (rawPhoneNumber.isEmpty() || rawPhoneNumber.contains("*")) {
			return null;
		}
		
		PhoneNumer number = NumberAnalyzer.analyze(rawPhoneNumber);
		if (number == null) {
			return null;
		}
		return number;
	}

	private static SearchResult analyze(PhoneNumer number, boolean isSeachHit) {
		String phone = NumberAnalyzer.getPhoneId(number);

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
				comments = MetaSearchService.getInstance().fetchComments(number);
			} else {
				comments = reports.getComments(phone);
			}
			
			boolean commit = false;
			
			// An explicit search from the web front-end is recorded as search, since
			// multiple users searching for the same unknown number are an indication for a
			// potential SPAM call.
			if (isSeachHit) {
				long now = System.currentTimeMillis();
				db.addSearchHit(reports, number, now);
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
					relatedNumbers = reports.getRelatedNumbers(aggregation100.getPrefix(), phone.length());
					
					if (comments.isEmpty()) {
						comments = reports.getAllComments(aggregation100.getPrefix(), phone.length());
					}
					
					if (info.getRating() == Rating.B_MISSED) {
						DBNumberInfo aggregateInfo = reports.getPhoneInfoAggregate(aggregation100.getPrefix(), phone.length());
						info.setRating(DB.rating(aggregateInfo));
					}
				} else {
					if (aggregation10.getCnt() >= DB.MIN_AGGREGATE_10) {
						relatedNumbers = reports.getRelatedNumbers(aggregation10.getPrefix(), phone.length());

						if (comments.isEmpty()) {
							comments = reports.getAllComments(aggregation10.getPrefix(), phone.length());
						}

						if (info.getRating() == Rating.B_MISSED) {
							DBNumberInfo aggregateInfo = reports.getPhoneInfoAggregate(aggregation10.getPrefix(), phone.length());
							info.setRating(DB.rating(aggregateInfo));
						}
					} else {
						relatedNumbers = Collections.emptyList();
					}
				}
			}
			
			searches = db.getSearchHistory(reports, phone, 7);
			aiSummary = reports.getSummary(phone);
			
			prev = reports.getPrevPhone(phone);
			next = reports.getNextPhone(phone);
			
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
				.setPhoneId(phone)
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
			req.setAttribute(THANKS_ATTR, Boolean.TRUE);
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
		
		req.setAttribute(COMMENTS_ATTR, searchResult.getComments());

		// The canonical path of this page.
		req.setAttribute(PATH_ATTR, req.getServletPath() + '/' + searchResult.getPhoneId());
		
		req.setAttribute(INFO_ATTR, searchResult.getInfo());
		req.setAttribute(NUMBER_ATTR, searchResult.getNumber());
		req.setAttribute(PREV_ATTR, searchResult.getPrev());
		req.setAttribute(NEXT_ATTR, searchResult.getNext());
		req.setAttribute(SUMMARY_ATTR, summary);
		req.setAttribute(DEFAULT_SUMMARY_ATTR, defaultSummary);
		req.setAttribute(RATING_ATTR, searchResult.getTopRating());
		req.setAttribute(RATINGS_ATTR, searchResult.getRatings());
		req.setAttribute(SEARCHES_ATTR, searchResult.getSearches());
		req.setAttribute(RELATED_NUMBERS_ATTR, searchResult.getRelatedNumbers());
		req.setAttribute(TITLE_ATTR, status + ": Rufnummer ☎ " + searchResult.getPhoneId() + " - PhoneBlock");
		req.setAttribute(DESCRIPTION_ATTR, description + ". Mit PhoneBlock Werbeanrufe automatisch blockieren, kostenlos und ohne Zusatzhardware.");
		
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
		
		req.setAttribute(KEYWORDS_ATTR, keywords.toString());
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
		if (votes <= 0) {
			int votesWildcard = info.getVotesWildcard();
			if (votesWildcard <= 0) {
				return "Die Telefonnummer ist nicht in der <a href=\"" + req.getContextPath() +
						"/\">PhoneBlock</a>-Datenbank enthalten. Es gibt bisher keine Stimmen, die für eine Sperrung von ☎ <code>" + 
						info.getPhone() + "</code> sprechen.";
			} else {
				return "Die Telefonnummer selbst ist nicht in der <a href=\"" + req.getContextPath() +
						"/\">PhoneBlock</a>-Datenbank enthalten. Die Nummer ☎ <code>" + info.getPhone() + "</code> stammt aber aus einem Nummernblock mit Spam-Verdacht. " +  
						"Es sprechen " + votesText(votesWildcard) + " für die Sperrung des Nummernblocks.";
			}
		} else if (votes < DB.MIN_VOTES) {
			return "Es gibt bereits " + votesText(votes) 
					+ " die für eine Sperrung von ☎ <code>" + info.getPhone() + "</code> sprechen. Die Nummer wird aber noch nicht blockiert.";
		} else if (info.isArchived()) {
			return "Es gibt " + votesText(votes) 
					+ " die für eine Sperrung von ☎ <code>" + info.getPhone() + "</code> sprechen. Die Nummer wird aber nicht mehr blockiert.";
		} else {
			return "Die Telefonnummer ☎ <code>" + info.getPhone() + "</code> ist eine mehrfach berichtete Quelle von <a href=\"" + req.getContextPath()
					+ "/status.jsp\">unerwünschten Telefonanrufen</a>. " + votes + " Stimmen sprechen sich für eine Sperrung der Nummer aus.";
		}
	}

	private String votesText(int votes) {
		return votes == 1 ? "eine Stimme" : votes + " Stimmen";
	}

	private String defaultSimpleSummary(PhoneInfo info) {
		int votes = info.getVotes();
		if (votes <= 0) {
			int votesWildcard = info.getVotesWildcard();
			if (votesWildcard <= 0) {
				return "Es gibt keine Beschwerden über die Telefonnummer ☎ " + info.getPhone() + ".";
			} else {
				return "Die Telefonnummer ☎ " + info.getPhone() + " stammt aber aus einem Nummernblock mit Spam-Verdacht.";
			}
		} else if (votes < DB.MIN_VOTES) {
			return "Es gibt bereits " + votesText(votes) 
					+ " die für eine Sperrung von ☎ " + info.getPhone() + " sprechen. Die Nummer wird aber noch nicht blockiert.";
		} else if (votes < DB.MIN_VOTES || info.isArchived()) {
			return "Es gibt " + votesText(votes) 
					+ " die für eine Sperrung von ☎ " + info.getPhone() + " sprechen. Die Nummer wird aber nicht mehr blockiert.";
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
		if (votes <= 0) {
			return "Keine Beschwerden";
		} else if (votes < DB.MIN_VOTES) {
			return "Spamverdacht";
		} else {
			return "Blockiert";
		}
	}

}
