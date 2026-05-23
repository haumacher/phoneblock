/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.api;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import de.haumacher.phoneblock.app.LoginFilter;
import de.haumacher.phoneblock.app.api.model.Blocklist;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.settings.UserSettings;
import de.haumacher.phoneblock.sync.binary.BlocklistBinaryAdapter;
import de.haumacher.phoneblock.sync.binary.BlocklistBinaryEncoder.Entry;
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
 * <b>Caching:</b> JSON and XML responses contain no user-specific data and
 * can be cached efficiently across users for a given version. The binary
 * response is user-specific (it applies the user's {@code minVotes}
 * preference and embeds the personal black/white list) and must not be
 * shared across users.
 * </p>
 */
@WebServlet(urlPatterns = BlocklistServlet.PATH)
public class BlocklistServlet extends HttpServlet {

	public static final String PATH = "/api/blocklist";

	/** Value of the {@code format} query parameter requesting the on-device binary format. */
	public static final String FORMAT_BINARY = "binary";

	/** Content type emitted for {@link #FORMAT_BINARY}. */
	public static final String BINARY_CONTENT_TYPE = "application/octet-stream";

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

		boolean binary = FORMAT_BINARY.equals(req.getParameter("format"));

		// Check if incremental sync is requested via "since" parameter
		String sinceParam = req.getParameter("since");
		if (binary && sinceParam != null && !sinceParam.isEmpty()) {
			ServletUtil.sendError(resp,
				"Incremental sync is not supported with format=binary; omit the 'since' parameter.");
			return;
		}
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

		if (binary) {
			// The binary file does not carry per-entry vote counts, so the
			// user's minVotes preference must be applied server-side here.
			int userMinVotes = Math.max(cachedSettings.getMinVotes(), db.getMinVisibleVotes());
			DB.PersonalLists personal = db.getPersonalLists(userName);
			List<Entry> personalEntries = PersonalEntries.from(personal.blacklist(), personal.whitelist());
			resp.setContentType(BINARY_CONTENT_TYPE);
			BlocklistBinaryAdapter.write(resp.getOutputStream(), result, personalEntries, userMinVotes);
		} else {
			ServletUtil.sendResult(req, resp, result);
		}
	}

}
