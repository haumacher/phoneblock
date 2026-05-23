/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.app.LoginFilter;
import de.haumacher.phoneblock.app.api.model.Blocklist;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.settings.UserSettings;
import de.haumacher.phoneblock.sync.binary.BlocklistBinaryAdapter;
import de.haumacher.phoneblock.sync.binary.BlocklistBinaryEncoder;
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
 * <b>Binary format:</b> {@code ?format=binary&type=community} streams the
 * community list filtered by the user's {@code minVotes} preference;
 * {@code ?format=binary&type=personal} streams the user's personal
 * black/white list. The split lets the community variant be pre-generated
 * once per {@code (minVotes, dialPrefix)} combination (the personal variant
 * is per-user and small). Both binary variants are authenticated; the
 * {@code since} parameter is not supported in this format.
 * </p>
 */
@WebServlet(urlPatterns = BlocklistServlet.PATH)
public class BlocklistServlet extends HttpServlet {

	public static final String PATH = "/api/blocklist";

	/** Value of the {@code format} query parameter requesting the on-device binary format. */
	public static final String FORMAT_BINARY = "binary";

	/** Value of the {@code type} query parameter selecting the community list. */
	public static final String TYPE_COMMUNITY = "community";

	/** Value of the {@code type} query parameter selecting the user's personal list. */
	public static final String TYPE_PERSONAL = "personal";

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
		String userAgent = req.getHeader("User-Agent");
		UserSettings cachedSettings = LoginFilter.getUserSettings(req);

		boolean binary = FORMAT_BINARY.equals(req.getParameter("format"));
		String sinceParam = req.getParameter("since");

		if (binary) {
			if (sinceParam != null && !sinceParam.isEmpty()) {
				ServletUtil.sendError(resp,
					"Incremental sync is not supported with format=binary; omit the 'since' parameter.");
				return;
			}
			serveBinary(req, resp, db, userName, userAgent, cachedSettings);
			return;
		}

		Blocklist result;
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

		db.updateLastAccess(userName, System.currentTimeMillis(), userAgent, cachedSettings);
		ServletUtil.sendResult(req, resp, result);
	}

	private void serveBinary(HttpServletRequest req, HttpServletResponse resp, DB db, String userName,
			String userAgent, UserSettings cachedSettings) throws IOException {
		String type = req.getParameter("type");
		if (type == null || type.isEmpty()) {
			type = TYPE_COMMUNITY;
		}

		db.updateLastAccess(userName, System.currentTimeMillis(), userAgent, cachedSettings);

		if (TYPE_COMMUNITY.equals(type)) {
			// The binary file does not carry per-entry vote counts, so the
			// user's minVotes preference must be applied server-side here —
			// for both exact entries and aggregation-driven wildcards. The
			// resulting bytes are shared across all users with the same
			// minVotes via BinaryBlocklistCache.
			int userMinVotes = Math.max(cachedSettings.getMinVotes(), db.getMinVisibleVotes());
			byte[] bytes = BinaryBlocklistCache.getInstance().getOrCompute(userMinVotes,
					mv -> encodeCommunity(db, mv));
			LOG.info("Sending binary community blocklist ({} bytes, minVotes {}) to user '{}' (agent '{}')",
				bytes.length, userMinVotes, userName, userAgent);
			resp.setContentType(BINARY_CONTENT_TYPE);
			resp.setContentLength(bytes.length);
			resp.getOutputStream().write(bytes);
		} else if (TYPE_PERSONAL.equals(type)) {
			DB.PersonalLists personal = db.getPersonalLists(userName);
			List<Entry> entries = PersonalEntries.from(personal.blacklist(), personal.whitelist());
			LOG.info("Sending binary personal blocklist ({} entries) to user '{}' (agent '{}')",
				entries.size(), userName, userAgent);
			resp.setContentType(BINARY_CONTENT_TYPE);
			BlocklistBinaryEncoder.write(resp.getOutputStream(), entries);
		} else {
			ServletUtil.sendError(resp,
				"Unknown 'type' value '" + type + "': expected '" + TYPE_COMMUNITY + "' or '" + TYPE_PERSONAL + "'.");
		}
	}

	private static byte[] encodeCommunity(DB db, int minVotes) {
		Blocklist blocklist = db.getBlockListAPI();
		DB.CommunityBinarySources sources = db.getCommunityBinarySources(minVotes);
		List<Entry> entries = CommunityEntries.from(blocklist, sources, minVotes);
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		try {
			BlocklistBinaryEncoder.write(buf, entries);
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		return buf.toByteArray();
	}

}
