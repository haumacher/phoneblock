/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import de.haumacher.phoneblock.db.model.Rating;
import de.haumacher.phoneblock.db.model.RatingInfo;

/**
 * {@link RatingInfo} for usage from the DB layer.
 */
public class DBRatingInfo extends RatingInfo {
	
	/** 
	 * Creates a {@link DBRatingInfo}.
	 */
	public DBRatingInfo(String phone, Rating rating, int votes) {
		setPhone(phone).setRating(rating).setVotes(votes);
	}

}
