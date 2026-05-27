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
import de.haumacher.phoneblock.db.settings.UserSettings;
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
 * <b>Vote Counts:</b> Actual vote counts are transmitted. Numbers with fewer votes than the
 * configured minimum threshold ({@link DB#getMinVisibleVotes()}) are excluded from the full
 * blocklist and returned with votes=0 in incremental updates (indicating removal).
 * </p>
 *
 * <p>
 * <b>Incremental Synchronization:</b> Updates are sent when numbers cross the minimum
 * threshold or when recent activity is detected on already-published numbers.
 * Numbers dropping below the minimum threshold are sent with votes=0, indicating removal.
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

	/**
	 * Hard cap on the {@code ?limit=N} request parameter (#336).
	 *
	 * <p>Caps the Heat-ranked blocklist response at a sensible upper bound so
	 * a client cannot ask for more than is reasonable. Calibrated for
	 * Fritz!Box phonebook (~300 entries) and dongle local blocklists (varies
	 * by hardware). A client that genuinely needs the full list omits the
	 * parameter and gets the regular blocklist.</p>
	 */
	public static final int MAX_HEAT_LIMIT = 5000;

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

		// Heat-ranked variant (#336) for space-constrained clients. The two
		// modes are mutually exclusive: ?limit= asks for the top-N by current
		// Heat (no diff semantics — list changes when Heat shifts), ?since=
		// asks for an incremental diff against a previous version. We reject
		// the combination rather than picking one silently.
		String limitParam = req.getParameter("limit");
		String sinceParam = req.getParameter("since");
		if (limitParam != null && !limitParam.isEmpty()) {
			if (sinceParam != null && !sinceParam.isEmpty()) {
				ServletUtil.sendError(resp,
					"Parameters 'limit' and 'since' are mutually exclusive: "
						+ "the Heat-ranked blocklist (?limit=N) is a snapshot, not a diff.");
				return;
			}
			int limit;
			try {
				limit = Integer.parseInt(limitParam);
			} catch (NumberFormatException ex) {
				ServletUtil.sendError(resp, "Invalid 'limit' parameter: must be a positive integer.");
				return;
			}
			if (limit <= 0) {
				ServletUtil.sendError(resp, "Parameter 'limit' must be positive.");
				return;
			}
			if (limit > MAX_HEAT_LIMIT) {
				limit = MAX_HEAT_LIMIT;
			}
			UserSettings cachedSettings = LoginFilter.getUserSettings(req);
			// Region-aware Heat ranking (#340): a user's dial prefix scopes the
			// top-N to numbers active in their region. Falls back to the global
			// ranking when no dial is configured (legacy clients, unmapped
			// regions).
			String dialPrefix = (cachedSettings != null) ? cachedSettings.getDialPrefix() : null;
			if (dialPrefix != null && dialPrefix.isEmpty()) {
				dialPrefix = null;
			}
			result = db.getBlockListByHeatAPI(dialPrefix, limit);
			LOG.info("Sending Heat-ranked blocklist (dial={}, limit={}, minVotes {}) to user '{}' (agent '{}')",
				dialPrefix, limit, db.getMinVisibleVotes(), userName, userAgent);

			db.updateLastAccess(userName, System.currentTimeMillis(), userAgent, cachedSettings);
			ServletUtil.sendResult(req, resp, result);
			return;
		}

		// Check if incremental sync is requested via "since" parameter
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

			if (sinceVersion == 0) {
				// Treat since=0 as a full sync request - client has nothing, so no deletions needed
				result = db.getBlockListAPI();
				LOG.info("Sending blocklist (since=0 treated as full sync, minVotes {}) to user '{}' (agent '{}')", db.getMinVisibleVotes(), userName, userAgent);
			} else {
				result = db.getBlocklistUpdateAPI(sinceVersion);
				LOG.info("Sending blocklist update (since {}, minVotes {}) to user '{}' (agent '{}')", sinceVersion, db.getMinVisibleVotes(), userName, userAgent);
			}
		} else {
			// Full blocklist
			result = db.getBlockListAPI();
			LOG.info("Sending blocklist (minVotes {}) to user '{}' (agent '{}')", db.getMinVisibleVotes(), userName, userAgent);
		}

		UserSettings cachedSettings = LoginFilter.getUserSettings(req);
		db.updateLastAccess(userName, System.currentTimeMillis(), userAgent, cachedSettings);
		ServletUtil.sendResult(req, resp, result);
	}

}
