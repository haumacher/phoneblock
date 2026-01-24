/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.api;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.app.LoginFilter;
import de.haumacher.phoneblock.app.api.model.Blocklist;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.util.ServletUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * {@link HttpServlet} serving the blocklist with optional incremental synchronization.
 */
@WebServlet(urlPatterns = BlocklistServlet.PATH)
public class BlocklistServlet extends HttpServlet {

	public static final String PATH = "/api/blocklist";

	private static final Logger LOG = LoggerFactory.getLogger(BlocklistServlet.class);

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String userName = LoginFilter.getAuthenticatedUser(req);
		if (userName == null) {
			ServletUtil.sendAuthenticationRequest(resp);
			return;
		}

		// Parse optional "minVotes" parameter (default 4)
		int minVotes = 4;
		String minVotesParam = req.getParameter("minVotes");
		if (minVotesParam != null) {
			try {
				minVotes = Integer.parseInt(minVotesParam);
			} catch (NumberFormatException ex) {
				ServletUtil.sendError(resp, "Invalid minVotes parameter.");
				return;
			}

			if (!DB.isValidBlocklistThreshold(minVotes)) {
				ServletUtil.sendError(resp,
					"Invalid minVotes parameter. Must be one of the predefined thresholds: " +
					DB.getBlocklistThresholdsString() + ". " +
					"These are the only values that guarantee consistent incremental synchronization.");
				return;
			}
		}

		DB db = DBService.getInstance();
		Blocklist result;
		String userAgent = req.getHeader("User-Agent");

		// Check if incremental sync is requested via "since" parameter
		String sinceParam = req.getParameter("since");
		if (sinceParam != null && !sinceParam.isEmpty()) {
			// Incremental sync: return only changes since the specified version
			long sinceVersion;
			try {
				sinceVersion = Long.parseLong(sinceParam);
			} catch (NumberFormatException ex) {
				ServletUtil.sendError(resp, "Invalid 'since' parameter: must be a number.");
				return;
			}

			if (sinceVersion < 0) {
				ServletUtil.sendError(resp, "Parameter 'since' must be non-negative.");
				return;
			}

			result = db.getBlocklistUpdateAPI(sinceVersion, minVotes);
			LOG.info("Sending blocklist update (since {}) to user '{}' (agent '{}')", sinceVersion, userName, userAgent);
		} else {
			// Full blocklist
			result = db.getBlockListAPI(minVotes);
			LOG.info("Sending blocklist to user '{}' (agent '{}')", userName, userAgent);
		}

		db.updateLastAccess(userName, System.currentTimeMillis(), userAgent);
		ServletUtil.sendResult(req, resp, result);
	}

}
