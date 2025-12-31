/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
import de.haumacher.phoneblock.app.render.DefaultController;
import de.haumacher.phoneblock.app.render.TemplateRenderer;
import de.haumacher.phoneblock.db.AggregationInfo;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBNumberInfo;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.DBUserSettings;
import de.haumacher.phoneblock.db.Ratings;
import de.haumacher.phoneblock.db.SpamReports;
import de.haumacher.phoneblock.db.Users;
import de.haumacher.phoneblock.location.LocationService;
import de.haumacher.phoneblock.meta.MetaSearchService;
import de.haumacher.phoneblock.shared.Language;
import de.haumacher.phoneblock.util.I18N;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Servlet displaying information about a telephone number in the DB.
 */
@WebServlet(urlPatterns = {SearchServlet.NUMS_PREFIX, SearchServlet.NUMS_PREFIX + "/*"})
public class SearchServlet extends HttpServlet {
	
	public static final String KEYWORDS_ATTR = "keywords";

	public static final String THANKS_ATTR = "thanks";

	public static final String TITLE_ATTR = "title";

	public static final String RELATED_NUMBERS_ATTR = "relatedNumbers";

	public static final String SEARCHES_ATTR = "searches";

	public static final String RATINGS_ATTR = "ratings";

	public static final String RATING_ATTR = "rating";

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

	public static final String NUMS_PREFIX = "/nums";
	
	private static final Pattern GOOD_BOT_PATTERN = Pattern.compile(
			// Search engines
			or("Googlebot"
			, "AdsBot-Google"
			, "Googlebot-Image"
			, "Google-Read-Aloud"

			, "bingbot"
			, "BingPreview"

			, "YandexBot"
			, "YandexImages"
			, "YandexRenderResourcesBot"
			
			, "Qwantbot" // French search engine.
			
			, "Amazonbot"
			, "Applebot"
			, "archive.org_bot"

			, "OAI-SearchBot" // OpenAI's web crawler
			, "ChatGPT-User" // OpenAI's web agent
			, "Bytespider" // Crawler operated by ByteDance
			
			// Social media
			, "DuckDuckGo-Favicons-Bot"
			, "facebookexternalhit"
			, "LinkedInBot"
			, "meta-externalagent"
			, "TikTokSpider"
			, "TelegramBot"
			, "WhatsApp"

			// Monitoring
			, "upz-bot"
			, "statuscake-monitoring"
			, "Monit"

			// Automated tools
			, "Basilisk"
			, "Curl"
			, "python"
			, "libwww-perl"));

	private static final Pattern BAD_BOT_PATTERN = Pattern.compile(
			or("custo"
			, "Barkrowler"
			, "CFNetwork"
			, "Nutch"
			, "Owler"
			
			// Anything that sounds like a bot.
			, "bot"
			, "seo"
			, "spider"
			, "crawler"

			// , "SEOkicks"
			// , "AhrefsBot"
			// , "DataForSeoBot"
			// , "DotBot"
			// , "ImagesiftBot"
			// , "MJ12bot"
			// , "PetalBot"
			// , "SemrushBot"
			// , "SeznamBot"
			), Pattern.CASE_INSENSITIVE);

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

	/**
	 * The user has pressed the search button on the web page, guess the dial prefix or national numbers.
	 */
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String query = req.getParameter("num");
		if (query == null || query.isBlank()) {
			req.setAttribute(NUMBER_ATTR, query);
			TemplateRenderer.getInstance(req).process("/no-such-number", req, resp);
			return;
		}
		
		String dialPrefix = lookupDialPrefix(req);
		PhoneNumer number = extractNumber(query, dialPrefix);
		if (number == null) {
			req.setAttribute(NUMBER_ATTR, query);
			TemplateRenderer.getInstance(req).process("/no-such-number", req, resp);
			return;
		}
		
		// Send to display page.
		String phone = NumberAnalyzer.getPhoneId(number);
		resp.sendRedirect(req.getContextPath() +  SearchServlet.NUMS_PREFIX + "/" + phone);
	}

	private String lookupDialPrefix(HttpServletRequest req) {
		String dialPrefix;
		String userName = LoginFilter.getAuthenticatedUser(req);
		if (userName == null) {
			dialPrefix = LocationService.getInstance().getDialPrefix(req);
		} else {
			DB db = DBService.getInstance();
			try (SqlSession session = db.openSession()) {
				DBUserSettings settings = db.getUserSettingsRaw(session, userName);
				dialPrefix = settings.getDialPrefix();
			}
		}
		return dialPrefix;
	}
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String pathInfo = req.getPathInfo();
		if (pathInfo == null || pathInfo.length() < 1) {
			req.setAttribute(NUMBER_ATTR, "");
			TemplateRenderer.getInstance(req).process("/no-such-number", req, resp);
			return;
		}
		
		String query = pathInfo.substring(1);
		
		boolean bot = isBot(req);
		boolean isSeachHit = !bot && req.getParameter("link") == null;

		String dialPrefix = lookupDialPrefix(req);
		PhoneNumer number = extractNumber(query, dialPrefix);
		if (number == null) {
			req.setAttribute(NUMBER_ATTR, query);
			TemplateRenderer.getInstance(req).process("/no-such-number", req, resp);
			return;
		}
		
		String phone = NumberAnalyzer.getPhoneId(number);
		
		if (!phone.equals(query)) {
			// Normalize the URL in the browser.
			resp.sendRedirect(req.getContextPath() +  SearchServlet.NUMS_PREFIX + "/" + phone);
			return;
		}

		DB db = DBService.getInstance();
		
		SearchResult searchResult;
		int minVotes;
		try (SqlSession session = db.openSession()) {
			String userName = LoginFilter.getAuthenticatedUser(req);
			
			if (userName == null) {
				minVotes = DB.MIN_VOTES;
			} else {
				DBUserSettings settings = db.getUserSettingsRaw(session, userName);
				minVotes = settings.getMinVotes();
			}
			
			Set<String> langs = new HashSet<>();
			langs.add(DefaultController.selectLanguage(req).tag);
			for (Enumeration<Locale> locales = req.getLocales(); locales.hasMoreElements(); ) {
				Locale locale = locales.nextElement();
				Language language = DefaultController.selectLanguage(locale);
				langs.add(language.tag);
			}
			
			searchResult = analyzeDb(db, session, number, dialPrefix, isSeachHit, langs);
		}
		
		sendResult(req, resp, searchResult, minVotes);
	}

	public static SearchResult analyze(String query, String userName, String dialPrefix) {
		PhoneNumer number = extractNumber(query);
		if (number == null) {
			return null;
		}
		DB db = DBService.getInstance();
		try (SqlSession session = db.openSession()) {
			Set<String> langs;
			if (userName == null) {
				langs = Collections.singleton(Language.getDefault().tag);
			} else {
				Users users = session.getMapper(Users.class);
				String lang = users.getLang(userName);
				dialPrefix = users.getDialPrefix(userName);
				langs = Collections.singleton(lang);
			}

			return analyzeDb(db, session, number, dialPrefix, true, langs);
		}
	}

	private static PhoneNumer extractNumber(String query) {
		return extractNumber(query, NumberAnalyzer.GERMAN_DIAL_PREFIX);
	}
	
	private static PhoneNumer extractNumber(String query, String dialPrefix) {
		String rawPhoneNumber = NumberAnalyzer.normalizeNumber(query);
		if (rawPhoneNumber.isEmpty() || rawPhoneNumber.contains("*")) {
			return null;
		}
		
		PhoneNumer number = NumberAnalyzer.analyze(rawPhoneNumber, dialPrefix);
		if (number == null) {
			return null;
		}
		return number;
	}

	private static SearchResult analyzeDb(DB db, SqlSession session, PhoneNumer number, String dialPrefix, boolean isSeachHit, Set<String> langs) {
		SpamReports reports = session.getMapper(SpamReports.class);
		
		String phone = NumberAnalyzer.getPhoneId(number);
		
		NumberInfo numberInfo;
		PhoneInfo info;
		List<Integer> searches;
		String aiSummary;
		List<String> relatedNumbers;
		
		String prev;
		String next;
		
		List<? extends UserComment> comments;
		
		// Note: Search for comments first, since new comments may change the state of the number.
		if (isSeachHit) {
			comments = MetaSearchService.getInstance().fetchComments(number, dialPrefix).stream().filter(c -> langs.contains(c.getLang())).toList();
		} else {
			comments = reports.getComments(phone, langs);
		}
		
		boolean commit = false;
		
		// An explicit search from the web front-end is recorded as search, since
		// multiple users searching for the same unknown number are an indication for a
		// potential SPAM call.
		if (isSeachHit) {
			long now = System.currentTimeMillis();
			db.addSearchHit(reports, number, dialPrefix, now);
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
					comments = reports.getAllComments(aggregation100.getPrefix(), phone.length(), langs);
				}
				
				if (info.getRating() == Rating.B_MISSED) {
					DBNumberInfo aggregateInfo = reports.getPhoneInfoAggregate(aggregation100.getPrefix(), phone.length());
					info.setRating(DB.rating(aggregateInfo));
				}
			} else {
				if (aggregation10.getCnt() >= DB.MIN_AGGREGATE_10) {
					relatedNumbers = reports.getRelatedNumbers(aggregation10.getPrefix(), phone.length());

					if (comments.isEmpty()) {
						comments = reports.getAllComments(aggregation10.getPrefix(), phone.length(), langs);
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

	private void sendResult(HttpServletRequest req, HttpServletResponse resp, SearchResult searchResult, int minVotes) throws ServletException, IOException {
		String ratingAttribute = RatingServlet.ratingAttribute(searchResult.getPhoneId());
		if (getSessionAttribute(req, ratingAttribute) != null) {
			req.setAttribute(THANKS_ATTR, Boolean.TRUE);
		}
		
		PhoneInfo info = searchResult.getInfo();
		String status = status(info.getVotes(), minVotes);
		
		// Limit to 10 comments.
		if (searchResult.getComments().size() > 10) {
			searchResult.setComments(searchResult.getComments().subList(0, 10));
		}
		
		// Shorten comments from meta search.
		for (UserComment comment : searchResult.getComments()) {
			if (comment.getService() != null && comment.getComment().length() > 280) {
				comment.setComment(comment.getComment().substring(0, 277) + "...");
			}

			// Normalize votes.
        	int up = Math.max(0, comment.getUp() - comment.getDown());
          	int down = Math.max(0, comment.getDown() - comment.getUp());
          	comment.setUp(up).setDown(down);
		}
		
		req.setAttribute(COMMENTS_ATTR, searchResult.getComments());

		// The canonical path of this page.
		req.setAttribute(PATH_ATTR, req.getServletPath() + '/' + searchResult.getPhoneId());
		
		req.setAttribute(INFO_ATTR, info);
		req.setAttribute("searchResult", searchResult);
		req.setAttribute(NUMBER_ATTR, searchResult.getNumber());
		req.setAttribute(PREV_ATTR, searchResult.getPrev());
		req.setAttribute(NEXT_ATTR, searchResult.getNext());
		req.setAttribute(RATING_ATTR, searchResult.getTopRating());
		Map<Rating, Integer> ratings = searchResult.getRatings();
		req.setAttribute(RATINGS_ATTR, ratings);
		List<Integer> searches = searchResult.getSearches();
		req.setAttribute(SEARCHES_ATTR, searches);
		req.setAttribute(RELATED_NUMBERS_ATTR, searchResult.getRelatedNumbers());
		req.setAttribute(TITLE_ATTR, status + ": Rufnummer ☎ " + searchResult.getPhoneId() + " - PhoneBlock");
		
        Language lang = DefaultController.selectLanguage(req);

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
			keywords.append(I18N.getMessage(lang.locale, Ratings.getLabelKey(searchResult.getTopRating())));
		}
		
		keywords.append(", ");
		keywords.append(status);

		if (searchResult.getNumber().getCity() != null) {
			keywords.append(", ");
			keywords.append(searchResult.getNumber().getCity());
		}
		
		req.setAttribute(KEYWORDS_ATTR, keywords.toString());
		
		req.setAttribute("ratingCssClass", Ratings.getCssClass(searchResult.getTopRating()));
		req.setAttribute("ratingLabelKey", Ratings.getLabelKey(searchResult.getTopRating()));
		
  		String state = info.isWhiteListed() ? 
  			"whitelisted" : 
  			(
  				info.getVotes() <= 0 ? 
  				(
  					info.getVotesWildcard() <= 0 ? 
  						"legitimate" : 
  						"wildcard"
  				) : (
  					info.getVotes() < minVotes ? 
  						"suspicious" : 
  						(
  							info.isArchived() ? 
  								"archived" : 
  								"blocked"
  						)
  				)
  			);
		
		req.setAttribute("state", state);
		
		// Create ratings chart
		StringBuilder ratingLabels = new StringBuilder();
		StringBuilder ratingData = new StringBuilder();
		StringBuilder ratingBackground = new StringBuilder();
		StringBuilder ratingBorder = new StringBuilder();

		boolean firstRating = true;
		ratingLabels.append('[');
		ratingData.append('[');
		ratingBackground.append('[');
		ratingBorder.append('[');
		for (Rating r : Rating.values()) {
			if (r == Rating.B_MISSED) {
				continue;
			}
			if (firstRating) {
				firstRating = false;
			} else {
				ratingLabels.append(',');
				ratingData.append(',');
				ratingBackground.append(',');
				ratingBorder.append(',');
			}
			jsString(ratingLabels, I18N.getMessage(lang.locale, Ratings.getLabelKey(r)));

			ratingData.append(ratings.getOrDefault(r, 0));

			String rgb = Ratings.getRGB(r);
			jsString(ratingBackground, "rgba(" + rgb + ", 0.2)");

			jsString(ratingBorder, "rgba(" + rgb + ", 1)");
		}
		ratingLabels.append(']');
		ratingData.append(']');
		ratingBackground.append(']');
		ratingBorder.append(']');
		
		req.setAttribute("ratingLabels", ratingLabels.toString());
		req.setAttribute("ratingData", ratingData.toString());
		req.setAttribute("ratingBackground", ratingBackground.toString());
		req.setAttribute("ratingBorder", ratingBorder.toString());
		
		// Create search chart
		SimpleDateFormat fmt = new SimpleDateFormat("dd.MM.");
		Calendar date = new GregorianCalendar();
		date.add(Calendar.DAY_OF_MONTH, -(searches.size() - 1));

		StringBuilder searchLabels = new StringBuilder();
		StringBuilder searchData = new StringBuilder();

		boolean first = true;

		searchLabels.append('[');
		searchData.append('[');
		for (Integer cnt : searches) {
			if (first) {
				first = false;
			} else {
				searchLabels.append(',');
				searchData.append(',');
			}
			jsString(searchLabels, fmt.format(date.getTime()));
			date.add(Calendar.DAY_OF_MONTH, 1);
			
			searchData.append(cnt);
		}
		searchLabels.append(']');
		searchData.append(']');

		req.setAttribute("searchLabels", searchLabels.toString());
		req.setAttribute("searchData", searchData.toString());
		
		TemplateRenderer.getInstance(req).process("/phone-info", req, resp);
	}

	private static void jsString(StringBuilder js, String txt) {
		js.append('"');
		js.append(txt.replace("\"", "\\\""));
		js.append('"');
	}

	/** 
	 * Whether the request is from a known bot.
	 */
	public static boolean isBot(HttpServletRequest req) {
		String userAgent = req.getHeader("User-Agent");
		return userAgent == null || GOOD_BOT_PATTERN.matcher(userAgent).find() || BAD_BOT_PATTERN.matcher(userAgent).find();
	}

	/** 
	 * Whether the request is from "good" bot that is allowed to access the contents without proof of work.
	 */
	public static boolean isGoodBot(HttpServletRequest req) {
		String userAgent = req.getHeader("User-Agent");
		return userAgent != null && GOOD_BOT_PATTERN.matcher(userAgent).find();
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

	private String status(int votes, int minVotes) {
		if (votes <= 0) {
			return "Keine Beschwerden";
		} else if (votes < minVotes) {
			return "Spamverdacht";
		} else {
			return "Blockiert";
		}
	}

}
