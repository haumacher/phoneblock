/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import de.haumacher.phoneblock.db.model.BlockListEntry;
import de.haumacher.phoneblock.db.model.Rating;

/**
 * Data class for DB access.
 */
public class DBBlockListEntry extends BlockListEntry {

	/** 
	 * Creates a {@link DBBlockListEntry}.
	 */
	public DBBlockListEntry(String phone, int votes, Rating rating, int count) {
		setPhone(phone).setVotes(votes).setRating(rating).setCount(count);
	}

}
