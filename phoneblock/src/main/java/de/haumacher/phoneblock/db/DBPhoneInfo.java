/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import de.haumacher.phoneblock.app.api.model.PhoneInfo;
import de.haumacher.phoneblock.app.api.model.Rating;

/**
 * {@link PhoneInfo} usable for direct DB access.
 */
public class DBPhoneInfo extends PhoneInfo {

	/** 
	 * Creates a {@link DBPhoneInfo}.
	 */
	public DBPhoneInfo(String phone, int votes, Rating rating) {
		setPhone(phone).setVotes(votes).setRating(rating);
	}
}
