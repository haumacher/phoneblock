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
 *
 * <p>
 * <b>Vote Count Normalization:</b> All vote counts are normalized to predefined threshold
 * values (see {@link DB#BLOCKLIST_THRESHOLDS}). For example, a number with 5-9 votes is
 * transmitted as having 4 votes. This ensures consistency between when updates are triggered
 * (only at threshold crossings) and the vote counts clients receive.
 * </p>
 *
 * <p>
 * <b>Incremental Synchronization:</b> Updates are only sent when numbers cross threshold
 * boundaries. A number going from 5 to 6 votes triggers no update (both normalize to 4),
 * but crossing from 9 to 10 votes triggers an update (4 → 10). Numbers dropping below
 * the minimum threshold are sent with 0 votes, indicating removal from the blocklist.
 * </p>
 *
 * <p>
 * <b>Client-Side Filtering:</b> Clients should filter by one of the predefined threshold
 * values to ensure consistency with the update mechanism. Filtering by arbitrary values
 * (e.g., minVotes=7) is not meaningful since no updates occur at non-threshold values.
 * </p>
 *
 * <p>
 * <b>Caching:</b> The response contains no user-specific data and can be cached efficiently.
 * All authenticated users receive identical responses for the same version.
 * </p>
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

			result = db.getBlocklistUpdateAPI(sinceVersion);
			LOG.info("Sending blocklist update (since {}) to user '{}' (agent '{}')", sinceVersion, userName, userAgent);
		} else {
			// Full blocklist
			result = db.getBlockListAPI();
			LOG.info("Sending blocklist to user '{}' (agent '{}')", userName, userAgent);
		}

		db.updateLastAccess(userName, System.currentTimeMillis(), userAgent);
		ServletUtil.sendResult(req, resp, result);
	}

}
