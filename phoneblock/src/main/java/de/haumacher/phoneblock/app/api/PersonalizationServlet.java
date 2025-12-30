/*
 * Copyright (c) 2025 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.api;

import java.io.IOException;
import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.msgbuf.json.JsonWriter;
import de.haumacher.msgbuf.server.io.WriterAdapter;
import de.haumacher.phoneblock.app.LoginFilter;
import de.haumacher.phoneblock.app.api.model.NumberList;
import de.haumacher.phoneblock.db.BlockList;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
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
@WebServlet(urlPatterns = {PersonalizationServlet.BLACKLIST_PATH, PersonalizationServlet.WHITELIST_PATH})
public class PersonalizationServlet extends HttpServlet {

	private static final Logger LOG = LoggerFactory.getLogger(PersonalizationServlet.class);

	/** Servlet path for blacklist API. */
	public static final String BLACKLIST_PATH = "/api/blacklist";

	/** Servlet path for whitelist API. */
	public static final String WHITELIST_PATH = "/api/whitelist";

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
				resp.sendError(HttpServletResponse.SC_NOT_FOUND, "User not found");
				return;
			}

			BlockList blockList = session.getMapper(BlockList.class);
			List<String> numbers;

			String servletPath = req.getServletPath();
			if (BLACKLIST_PATH.equals(servletPath)) {
				numbers = blockList.getPersonalizations(userId);
				LOG.debug("Retrieved {} blocked numbers for user '{}'", numbers.size(), userName);
			} else if (WHITELIST_PATH.equals(servletPath)) {
				numbers = blockList.getWhiteList(userId);
				LOG.debug("Retrieved {} whitelisted numbers for user '{}'", numbers.size(), userName);
			} else {
				resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Unknown endpoint");
				return;
			}

			sendNumberList(resp, numbers);
		}
	}

	@Override
	protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
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

		String phone = pathInfo.substring(1); // Remove leading '/'

		DB db = DBService.getInstance();
		try (SqlSession session = db.openSession()) {
			Users users = session.getMapper(Users.class);
			Long userId = users.getUserId(userName);

			if (userId == null) {
				resp.sendError(HttpServletResponse.SC_NOT_FOUND, "User not found");
				return;
			}

			BlockList blockList = session.getMapper(BlockList.class);
			boolean deleted = blockList.removePersonalization(userId, phone);

			if (deleted) {
				session.commit();
				String listType = req.getServletPath().equals(BLACKLIST_PATH) ? "blacklist" : "whitelist";
				LOG.info("Removed {} from {} for user '{}'", phone, listType, userName);

				resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
			} else {
				resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Phone number not found in personalization list");
			}
		}
	}

	/**
	 * Sends number list as JSON response.
	 */
	private void sendNumberList(HttpServletResponse resp, List<String> numbers) throws IOException {
		NumberList response = NumberList.create()
				.setNumbers(numbers);

		resp.setContentType("application/json");
		resp.setCharacterEncoding("UTF-8");
		response.writeContent(new JsonWriter(new WriterAdapter(resp.getWriter())));
	}
}
