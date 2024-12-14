package de.haumacher.phoneblock.db;

import de.haumacher.phoneblock.db.settings.AuthToken;

public class DBAuthToken extends AuthToken {
	
	public DBAuthToken(long id, long userId, long created, 
			byte[] pwHash, boolean implicit, 
			boolean accessQuery, boolean accessDownload, boolean accessCarddav, boolean accessRate, boolean accessLogin, 
			long lastAccess, String userAgent) {
		setId(id);
		setUserId(id);
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
