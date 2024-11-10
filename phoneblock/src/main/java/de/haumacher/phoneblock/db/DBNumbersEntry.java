/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import de.haumacher.phoneblock.db.model.NumberInfo;

/**
 * Data class for DB access.
 */
public class DBNumbersEntry extends NumberInfo {

	/** 
	 * Creates a {@link DBBlockListEntry}.
	 */
	public DBNumbersEntry(String phone, long added, long updated, boolean active, int calls, int votes, int legitimate, int ping, int poll, int advertising, int gamble, int fraud, int searches) {
		setPhone(phone)
		.setAdded(added)
		.setUpdated(updated)
		.setActive(active)
		.setCalls(calls)
		.setVotes(votes)
		.setRatingLegitimate(legitimate)
		.setRatingPing(ping)
		.setRatingPoll(poll)
		.setRatingAdvertising(advertising)
		.setRatingGamble(gamble)
		.setRatingFraud(fraud)
		.setSearches(searches);
	}

}
