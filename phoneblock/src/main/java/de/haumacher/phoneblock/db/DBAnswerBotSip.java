/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import de.haumacher.phoneblock.db.settings.AnswerBotSip;

/**
 * Record representing a single answer bot.
 */
public class DBAnswerBotSip extends AnswerBotSip {
	
	/** 
	 * Creates a {@link DBAnswerBotSip}.
	 */
	public DBAnswerBotSip(long userId, String host, String ip4, String ip6, String registrar, String realm, String userName, String passwd) {
		setUserId(userId);
		setHost(host);
		setIpv4(ip4);
		setIpv6(ip6);
		setRegistrar(registrar);
		setRealm(realm);
		setUserName(userName);
		setPasswd(passwd);
	}

}
