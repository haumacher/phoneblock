package de.haumacher.phoneblock.db;

import de.haumacher.phoneblock.db.settings.AuthToken;

public class DBAuthToken extends AuthToken {

	public DBAuthToken(long id, long userId, String label, long created,
			byte[] pwHash, boolean implicit,
			boolean accessQuery, boolean accessDownload, boolean accessCarddav, boolean accessRate, boolean accessLogin,
			long lastAccess, String userAgent) {
		setId(id);
		setUserId(id);
		setLabel(label);
		setCreated(created);
		setPwHash(pwHash);
		setImplicit(implicit);
		setAccessQuery(accessQuery);
		setAccessDownload(accessDownload);
		setAccessCarddav(accessCarddav);
		setAccessRate(accessRate);
		setAccessLogin(accessLogin);
		setLastAccess(lastAccess);
		setUserAgent(userAgent);
	}

	/**
	 * Returns a user-friendly label for the token's purpose based on its permissions.
	 */
	public String getPurposeLabel() {
		int permissionCount = 0;
		if (isAccessCarddav()) permissionCount++;
		if (isAccessDownload() || isAccessQuery() || isAccessRate()) permissionCount++;
		if (isAccessLogin()) permissionCount++;

		// Multi-purpose token
		if (permissionCount > 1) {
			return "MULTI";
		}

		// Single purpose tokens
		if (isAccessCarddav()) return "CARDDAV";
		if (isAccessDownload() || isAccessQuery() || isAccessRate()) return "API";
		if (isAccessLogin()) return "LOGIN";

		return "NONE";
	}

	/**
	 * Returns a CSS class for styling the purpose badge.
	 */
	public String getPurposeBadgeClass() {
		switch (getPurposeLabel()) {
			case "CARDDAV": return "is-info";
			case "API": return "is-success";
			case "MULTI": return "is-warning";
			case "LOGIN": return "is-light";
			default: return "";
		}
	}

}
