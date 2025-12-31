/*
 * Copyright (c) 2025 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.msgbuf.json.JsonReader;
import de.haumacher.msgbuf.json.JsonWriter;
import de.haumacher.msgbuf.server.io.ReaderAdapter;
import de.haumacher.msgbuf.server.io.WriterAdapter;
import de.haumacher.phoneblock.analysis.NumberAnalyzer;
import de.haumacher.phoneblock.app.LoginFilter;
import de.haumacher.phoneblock.app.api.model.NumberList;
import de.haumacher.phoneblock.app.api.model.PersonalizedNumber;
import de.haumacher.phoneblock.app.api.model.Rating;
import de.haumacher.phoneblock.db.BlockList;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBPhoneComment;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.DBUserComment;
import de.haumacher.phoneblock.db.DBUserSettings;
import de.haumacher.phoneblock.db.Ratings;
import de.haumacher.phoneblock.db.SpamReports;
import de.haumacher.phoneblock.db.Users;
import de.haumacher.phoneblock.util.ServletUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * API servlet for managing user's personalized black and white lists.
 *
 * <p>
 * Allows mobile apps to retrieve and delete personalized blocked (blacklist) and
 * legitimate (whitelist) phone numbers. Requires Bearer token authentication.
 * </p>
 *
 * <p>
 * <b>GET /api/blacklist</b> - Retrieve user's blocked phone numbers
 * </p>
 *
 * <p>
 * <b>DELETE /api/blacklist/{phone}</b> - Remove phone number from blacklist
 * </p>
 *
 * <p>
 * <b>GET /api/whitelist</b> - Retrieve user's legitimate phone numbers
 * </p>
 *
 * <p>
 * <b>DELETE /api/whitelist/{phone}</b> - Remove phone number from whitelist
 * </p>
 */
@WebServlet(urlPatterns = {
	PersonalizationServlet.BLACKLIST_PATTERN,
	PersonalizationServlet.WHITELIST_PATTERN
})
public class PersonalizationServlet extends HttpServlet {

	private static final Logger LOG = LoggerFactory.getLogger(PersonalizationServlet.class);

	/** Servlet path for blacklist API. */
	public static final String BLACKLIST_PATH = "/api/blacklist";

	public static final String BLACKLIST_PATTERN = PersonalizationServlet.BLACKLIST_PATH + "/*";

	/** Servlet path for whitelist API. */
	public static final String WHITELIST_PATH = "/api/whitelist";

	public static final String WHITELIST_PATTERN = PersonalizationServlet.WHITELIST_PATH + "/*";

	@Override
	protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		try {
			super.service(req, resp);
		} catch (Exception e) {
			ServletUtil.sendMessage(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getClass().getName() + ": " + e.getMessage());
		}
	}
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String userName = LoginFilter.getAuthenticatedUser(req);
		if (userName == null) {
			ServletUtil.sendAuthenticationRequest(resp);
			return;
		}

		DB db = DBService.getInstance();
		try (SqlSession session = db.openSession()) {
			Users users = session.getMapper(Users.class);
			Long userId = users.getUserId(userName);

			if (userId == null) {
				ServletUtil.sendMessage(resp, HttpServletResponse.SC_NOT_FOUND, "User not found");
				return;
			}

			BlockList blockList = session.getMapper(BlockList.class);
			List<String> phoneNumbers;

			String servletPath = req.getServletPath();
			if (BLACKLIST_PATH.equals(servletPath)) {
				phoneNumbers = blockList.getPersonalizations(userId);
				LOG.debug("Retrieved {} blocked numbers for user '{}'", phoneNumbers.size(), userName);
			} else if (WHITELIST_PATH.equals(servletPath)) {
				phoneNumbers = blockList.getWhiteList(userId);
				LOG.debug("Retrieved {} whitelisted numbers for user '{}'", phoneNumbers.size(), userName);
			} else {
				ServletUtil.sendMessage(resp, HttpServletResponse.SC_NOT_FOUND, "Unknown endpoint");
				return;
			}

			// Fetch comments for the numbers
			SpamReports spamReports = session.getMapper(SpamReports.class);
			Map<String, String> commentsMap = new HashMap<>();
			if (!phoneNumbers.isEmpty()) {
				List<DBPhoneComment> userComments = spamReports.getUserComments(userId, phoneNumbers);
				for (DBPhoneComment entry : userComments) {
					commentsMap.put(entry.getPhone(), entry.getComment());
				}
			}

			// Build PersonalizedNumber list with comments
			List<PersonalizedNumber> numbers = new ArrayList<>();
			for (String phone : phoneNumbers) {
				PersonalizedNumber pn = PersonalizedNumber.create()
					.setPhone(phone)
					.setComment(commentsMap.get(phone));
				numbers.add(pn);
			}

			sendNumberList(resp, numbers);
		}
	}

	@Override
	protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String userName = LoginFilter.getAuthenticatedUser(req);
		if (userName == null) {
			ServletUtil.sendAuthenticationRequest(resp);
			return;
		}

		// Extract phone number from path: /api/blacklist/{phone} or /api/whitelist/{phone}
		String pathInfo = req.getPathInfo();
		if (pathInfo == null || pathInfo.length() <= 1) {
			ServletUtil.sendError(resp, "Phone number required in path (e.g., /api/blacklist/+491234567890)");
			return;
		}

		String phoneText = pathInfo.substring(1); // Remove leading '/'

		// Read comment from request body
		PersonalizedNumber update = PersonalizedNumber.readPersonalizedNumber(new JsonReader(new ReaderAdapter(req.getReader())));
		String comment = update.getComment();

		DB db = DBService.getInstance();
		try (SqlSession session = db.openSession()) {
			Users users = session.getMapper(Users.class);
			Long userId = users.getUserId(userName);

			if (userId == null) {
				ServletUtil.sendMessage(resp, HttpServletResponse.SC_NOT_FOUND, "User not found");
				return;
			}

			// Get user's dial prefix and normalize phone number
			String dialPrefix = users.getDialPrefix(userName);
			String phone = NumberAnalyzer.toId(phoneText, dialPrefix);

			if (phone == null) {
				ServletUtil.sendError(resp, "Invalid phone number format");
				return;
			}

			// Check if personalization exists for this phone number
			BlockList blockList = session.getMapper(BlockList.class);
			Boolean personalizationState = blockList.getPersonalizationState(userId, phone);

			if (personalizationState == null) {
				ServletUtil.sendMessage(resp, HttpServletResponse.SC_NOT_FOUND, "Phone number not found in personalization list");
				return;
			}

			SpamReports spamReports = session.getMapper(SpamReports.class);
			int updated = spamReports.updateUserComment(userId, phone, comment);

			// If no existing comment was updated, create a new one
			if (updated == 0) {
				// Determine rating based on whether it's blacklist or whitelist
				boolean isBlacklist = req.getServletPath().equals(BLACKLIST_PATH);
				Rating rating = isBlacklist ? Rating.B_MISSED : Rating.A_LEGITIMATE;

				// Get user's language setting
				DBUserSettings settings = users.getSettingsById(userId);
				String lang = settings.getLang();

				// Create new comment
				String commentId = java.util.UUID.randomUUID().toString();
				long now = System.currentTimeMillis();
				spamReports.addComment(commentId, phone, rating, comment, lang, null, now, userId);

				LOG.info("Created comment for {} in {} for user '{}'", phone, isBlacklist ? "blacklist" : "whitelist", userName);
			} else {
				LOG.info("Updated comment for {} in {} for user '{}'", phone, req.getServletPath().equals(BLACKLIST_PATH) ? "blacklist" : "whitelist", userName);
			}

			session.commit();
			resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
		}
	}

	@Override
	protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String userName = LoginFilter.getAuthenticatedUser(req);
		if (userName == null) {
			LOG.debug("Unauthenticated delete request: {}", req.getRequestURI());

			ServletUtil.sendAuthenticationRequest(resp);
			return;
		}

		// Extract phone number from path: /api/blacklist/{phone} or /api/whitelist/{phone}
		String pathInfo = req.getPathInfo();
		if (pathInfo == null || pathInfo.length() <= 1) {
			ServletUtil.sendError(resp, "Phone number required in path (e.g., /api/blacklist/+491234567890)");
			return;
		}

		String phoneText = pathInfo.substring(1); // Remove leading '/'
		LOG.debug("Received delete request from user {} for {}.", userName, phoneText);

		DB db = DBService.getInstance();
		try (SqlSession session = db.openSession()) {
			Users users = session.getMapper(Users.class);
			Long userId = users.getUserId(userName);

			if (userId == null) {
				ServletUtil.sendMessage(resp, HttpServletResponse.SC_NOT_FOUND, "User not found");
				return;
			}

			// Get user's dial prefix and normalize phone number
			String dialPrefix = users.getDialPrefix(userName);
			String phone = NumberAnalyzer.toId(phoneText, dialPrefix);

			if (phone == null) {
				ServletUtil.sendError(resp, "Invalid phone number format");
				return;
			}

			BlockList blockList = session.getMapper(BlockList.class);
			boolean deleted = blockList.removePersonalization(userId, phone);
			String listType = req.getServletPath().equals(BLACKLIST_PATH) ? "blacklist" : "whitelist";

			if (deleted) {
				// Also delete the user's comment and decrement vote counts
				SpamReports spamReports = session.getMapper(SpamReports.class);
				DBUserComment userComment = spamReports.getUserComment(userId, phone);

				if (userComment != null) {
					// Delete the comment
					spamReports.deleteUserComment(userId, phone);

					// Decrement the vote counts and rating counters based on the rating
					Rating rating = userComment.getRating();
					int voteDelta = -Ratings.getVotes(rating);
					long now = System.currentTimeMillis();

					// Update vote counts in NUMBERS table
					spamReports.addVote(phone, voteDelta, now);

					// Decrement the specific rating counter (LEGITIMATE, PING, etc.) by -1
					spamReports.updateRating(phone, rating, -1, now);

					LOG.debug("Decremented rating {} for {} (vote delta: {})", rating, phone, voteDelta);
				}

				session.commit();
				LOG.info("Removed {} from {} for user '{}'", phone, listType, userName);

				resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
			} else {
				LOG.info("Invalid delete request for {} for user '{}': {}", listType, userName, phone);

				ServletUtil.sendMessage(resp, HttpServletResponse.SC_NOT_FOUND, "Phone number not found in personalization list");
			}
		}
	}

	/**
	 * Sends number list as JSON response.
	 */
	private void sendNumberList(HttpServletResponse resp, List<PersonalizedNumber> numbers) throws IOException {
		NumberList response = NumberList.create()
				.setNumbers(numbers);

		resp.setContentType("application/json");
		resp.setCharacterEncoding("UTF-8");
		response.writeContent(new JsonWriter(new WriterAdapter(resp.getWriter())));
	}
}
