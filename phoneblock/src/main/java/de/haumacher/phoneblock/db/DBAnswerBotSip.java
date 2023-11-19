/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import de.haumacher.phoneblock.db.settings.AnswerBotSip;

/**
 * TODO
 *
 * @author <a href="mailto:haui@haumacher.de">Bernhard Haumacher</a>
 */
public class DBAnswerBotSip extends AnswerBotSip {
	
	/** 
	 * Creates a {@link DBAnswerBotSip}.
	 */
	public DBAnswerBotSip(long userId, String host, String registrar, String realm, String userName, String passwd) {
		setUserId(userId);
		setHost(host);
		setRegistrar(registrar);
		setRealm(realm);
		setUserName(userName);
		setPasswd(passwd);
	}

}
