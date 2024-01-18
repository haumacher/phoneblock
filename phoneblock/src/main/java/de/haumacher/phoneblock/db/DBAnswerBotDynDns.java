/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import de.haumacher.phoneblock.db.settings.AnswerBotDynDns;

/**
 * Variant of {@link AnswerBotDynDns} for fetching from DB.
 *
 * @author <a href="mailto:haui@haumacher.de">Bernhard Haumacher</a>
 */
public class DBAnswerBotDynDns extends AnswerBotDynDns {
	
	/** 
	 * Creates a {@link DBAnswerBotDynDns}.
	 */
	public DBAnswerBotDynDns(long id, long userId, long created, long updated, String user, String passwd, String ip4, String ip6) {
		setId(id);
		setUserId(userId);
		setCreated(created);
		setUpdated(updated);
		setDyndnsUser(user);
		setDynDnsPasswd(passwd);
		setIpv4(ip4);
		setIpv6(ip6);
	}

}
