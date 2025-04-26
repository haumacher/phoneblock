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

}
