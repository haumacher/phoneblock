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
 * community list; {@code ?format=binary&type=personal} streams the user's
 * personal black/white list. The community variant is filtered by two
 * thresholds the dongle passes as {@code &minDirect=}/{@code &minRange=}
 * (its {@code min_direct_votes} / {@code min_range_votes} settings): exact
 * entries need net votes {@code >= minDirect}, wildcard blocks need net
 * evidence {@code >= minRange} — the same gates the live {@code /num} API
 * applies, so the download and the dongle's API-fallback verdict agree. They
 * default to the account {@code minVotes} when omitted. The split + the
 * threshold-pair cache key let the community variant be shared across all
 * users requesting the same {@code (minDirect, minRange)} (the personal
 * variant is per-user and small). Both binary variants are authenticated;
 * the {@code since} parameter is not supported in this format.
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

	/** Query parameter: the dongle's exact-entry vote threshold ({@code min_direct_votes}). */
	public static final String PARAM_MIN_DIRECT = "minDirect";

	/** Query parameter: the dongle's wildcard net-vote threshold ({@code min_range_votes}). */
	public static final String PARAM_MIN_RANGE = "minRange";

	/**
	 * Allowed {@code minDirect} values, ascending. Must mirror the client
	 * pickers (mobile app + dongle UI). A requested value is snapped up to the
	 * smallest of these that is {@code >=} it (capped at the largest), so the
	 * {@code (minDirect, minRange)} cache key only ever takes a handful of
	 * distinct values across the whole fleet — see {@link #clampToAllowed}.
	 */
	static final int[] MIN_DIRECT_OPTIONS = { 2, 4, 10, 20, 50, 100 };

	/**
	 * Allowed {@code minRange} values, ascending. {@code 0} disables wildcard
	 * blocking entirely. Must mirror the client pickers. See
	 * {@link #MIN_DIRECT_OPTIONS} and {@link #clampToAllowed}.
	 */
	static final int[] MIN_RANGE_OPTIONS = { 0, 10, 20, 50, 100, 500 };

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

		// Heat-ranked variant (#336) for space-constrained clients. The two
		// modes are mutually exclusive: ?limit= asks for the top-N by current
		// Heat (no diff semantics — list changes when Heat shifts), ?since=
		// asks for an incremental diff against a previous version. We reject
		// the combination rather than picking one silently.
		String limitParam = req.getParameter("limit");
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
			// The binary file does not carry per-entry vote counts, so both
			// thresholds must be applied server-side. They are the dongle's
			// own min_direct_votes / min_range_votes (query params), so the
			// encoded list is exactly the set its API-fallback path would
			// decide SPAM; they fall back to the account minVotes when a
			// caller omits them. minDirect is floored at the server's
			// published minVisibleVotes. Both are then snapped to the allowed
			// option sets so the (minDirect, minRange) cache key stays shared
			// across the fleet regardless of what a client sends.
			int minDirect = clampToAllowed(
				Math.max(intParam(req, PARAM_MIN_DIRECT, cachedSettings.getMinVotes()),
					db.getMinVisibleVotes()),
				MIN_DIRECT_OPTIONS);
			int minRange = clampToAllowed(intParam(req, PARAM_MIN_RANGE, minDirect), MIN_RANGE_OPTIONS);
			byte[] bytes = BinaryBlocklistCache.getInstance().getOrCompute(minDirect, minRange,
					(d, r) -> encodeCommunity(db, d, r));
			LOG.info("Sending binary community blocklist ({} bytes, minDirect {}, minRange {}) to user '{}' (agent '{}')",
				bytes.length, minDirect, minRange, userName, userAgent);
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

	private static byte[] encodeCommunity(DB db, int minDirect, int minRange) {
		long now = System.currentTimeMillis();
		Blocklist blocklist = db.getBlockListAPI();
		DB.CommunityBinarySources sources = db.getCommunityBinarySources(minRange, now);
		List<Entry> entries = CommunityEntries.from(blocklist, sources, minDirect, minRange, now);
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		try {
			BlocklistBinaryEncoder.write(buf, entries);
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		return buf.toByteArray();
	}

	/**
	 * Snaps {@code v} up to the smallest entry of {@code allowed} that is
	 * {@code >= v}, or the largest entry when {@code v} exceeds all of them
	 * (ceil-clamp). {@code allowed} must be sorted ascending and non-empty.
	 * Keeps the community cache key constrained to a small shared set even if
	 * a client requests an off-grid threshold.
	 */
	static int clampToAllowed(int v, int[] allowed) {
		for (int a : allowed) {
			if (v <= a) {
				return a;
			}
		}
		return allowed[allowed.length - 1];
	}

	/**
	 * Parses an optional non-negative integer query parameter, returning
	 * {@code fallback} when the parameter is absent, empty or malformed. The
	 * binary path is consumed by the dongle's daily sync, so a stray value
	 * falls back rather than failing the download.
	 */
	private static int intParam(HttpServletRequest req, String name, int fallback) {
		String value = req.getParameter(name);
		if (value == null || value.isEmpty()) {
			return fallback;
		}
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException ex) {
			return fallback;
		}
	}

}
