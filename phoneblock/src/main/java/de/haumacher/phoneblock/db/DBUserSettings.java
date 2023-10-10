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
	public DBUserSettings(long id, String displayName, String email, int minVotes, int maxLength, boolean wildcards) {
		setId(id);
		setDisplayName(displayName);
		setEmail(email);
		setMinVotes(minVotes);
		setMaxLength(maxLength);
		setWildcards(wildcards);
	}

}
