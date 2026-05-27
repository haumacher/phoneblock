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

	private final double _publishedSpamEvidence;

	private final double _heat;

	private final double _spamEvidence;

	private final double _legitEvidence;

	public DBNumberInfo(String phone, long added, long updated, long lastSearch, boolean active, int calls, int rawVotes, int legitimate, int ping, int poll, int advertising, int gamble, int fraud, int searches, long lastPing, double publishedSpamEvidence,
			double heat, double spamEvidence, double legitEvidence) {
		setPhone(phone)
		.setAdded(added)
		.setUpdated(updated)
		.setLastSearch(lastSearch)
		.setActive(active)
		.setCalls(calls)
		.setRawVotes(rawVotes)
		.setRatingLegitimate(legitimate)
		.setRatingPing(ping)
		.setRatingPoll(poll)
		.setRatingAdvertising(advertising)
		.setRatingGamble(gamble)
		.setRatingFraud(fraud)
		.setSearches(searches);
		_lastPing = lastPing;
		_publishedSpamEvidence = publishedSpamEvidence;
		_heat = heat;
		_spamEvidence = spamEvidence;
		_legitEvidence = legitEvidence;
	}

	/**
	 * Backwards-compatible 16-arg constructor used by legacy debug/stats
	 * queries that have no snapshot context — leaves the EMA columns at zero.
	 */
	public DBNumberInfo(String phone, long added, long updated, long lastSearch, boolean active, int calls, int rawVotes, int legitimate, int ping, int poll, int advertising, int gamble, int fraud, int searches, long lastPing, double publishedSpamEvidence) {
		this(phone, added, updated, lastSearch, active, calls, rawVotes, legitimate, ping, poll, advertising, gamble, fraud, searches, lastPing, publishedSpamEvidence, 0.0, 0.0, 0.0);
	}

	/**
	 * The last activity timestamp (from PUBLISHED_LASTPING).
	 */
	public long getLastPing() {
		return _lastPing;
	}

	/**
	 * Snapshot of the projected SPAM_EVIDENCE EMA at the time of the last
	 * blocklist version assignment (#342). Decode with {@link Ema#decode} to
	 * compare against the visibility threshold.
	 */
	public double getPublishedSpamEvidence() {
		return _publishedSpamEvidence;
	}

	/** Raw projected-EMA {@code HEAT} value (confidence model, #300). Decode with {@link Ema#decode}. */
	public double getHeat() {
		return _heat;
	}

	/** Raw projected-EMA {@code SPAM_EVIDENCE} value. */
	public double getSpamEvidence() {
		return _spamEvidence;
	}

	/** Raw projected-EMA {@code LEGIT_EVIDENCE} value. */
	public double getLegitEvidence() {
		return _legitEvidence;
	}

}
