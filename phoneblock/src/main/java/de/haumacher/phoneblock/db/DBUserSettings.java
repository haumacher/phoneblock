/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import de.haumacher.phoneblock.db.settings.UserSettings;

/**
 * {@link UserSettings} able to fetch from the {@link DB}.
 */
public class DBUserSettings extends UserSettings {
	
	/** 
	 * Creates a {@link DBUserSettings}.
	 */
	public DBUserSettings(long id, String login, String displayName, String lang, String dialPrefix, boolean nationalOnly, String email, int minVotes, int maxLength, boolean wildcards, long lastAccess, int credit) {
		setId(id);
		setLogin(login);
		setDisplayName(displayName);
		setLang(lang);
		setDialPrefix(dialPrefix);
		setNationalOnly(nationalOnly);
		setEmail(email);
		setMinVotes(minVotes);
		setMaxLength(maxLength);
		setWildcards(wildcards);
		setLastAccess(lastAccess);
		setCredit(credit);
	}

}
