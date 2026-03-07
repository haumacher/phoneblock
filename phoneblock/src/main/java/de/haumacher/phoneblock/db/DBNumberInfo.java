/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import de.haumacher.phoneblock.app.api.model.NumberInfo;

/**
 * Data class for DB access.
 */
public class DBNumberInfo extends NumberInfo {

	private final long _lastPing;

	private final int _publishedVotes;

	/**
	 * Creates a {@link DBBlockListEntry}.
	 */
	public DBNumberInfo(String phone, long added, long updated, long lastSearch, boolean active, int calls, int votes, int legitimate, int ping, int poll, int advertising, int gamble, int fraud, int searches, long lastPing, int publishedVotes) {
		setPhone(phone)
		.setAdded(added)
		.setUpdated(updated)
		.setLastSearch(lastSearch)
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
		_lastPing = lastPing;
		_publishedVotes = publishedVotes;
	}

	/**
	 * The last activity timestamp (from PUBLISHED_LASTPING).
	 */
	public long getLastPing() {
		return _lastPing;
	}

	/**
	 * The snapshot of votes at the time of version assignment (from PUBLISHED_VOTES).
	 */
	public int getPublishedVotes() {
		return _publishedVotes;
	}

}
