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

	private final double _heat;

	private final double _spamEvidence;

	private final double _legitEvidence;

	/**
	 * Creates a {@link DBBlockListEntry}.
	 *
	 * <p>The trailing {@code heat}, {@code spamEvidence}, {@code legitEvidence}
	 * parameters are the raw projected EMA values from the confidence-model
	 * columns (#300). Existing call sites that pre-date #334 use the
	 * 16-argument constructor and get zero defaults — fine for callers that
	 * only need cnt/votes.</p>
	 */
	public DBNumberInfo(String phone, long added, long updated, long lastSearch, boolean active, int calls, int votes, int legitimate, int ping, int poll, int advertising, int gamble, int fraud, int searches, long lastPing, int publishedVotes,
			double heat, double spamEvidence, double legitEvidence) {
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
		_heat = heat;
		_spamEvidence = spamEvidence;
		_legitEvidence = legitEvidence;
	}

	/** Backwards-compatible constructor (pre-#334) — leaves EMAs at zero. */
	public DBNumberInfo(String phone, long added, long updated, long lastSearch, boolean active, int calls, int votes, int legitimate, int ping, int poll, int advertising, int gamble, int fraud, int searches, long lastPing, int publishedVotes) {
		this(phone, added, updated, lastSearch, active, calls, votes, legitimate, ping, poll, advertising, gamble, fraud, searches, lastPing, publishedVotes, 0.0, 0.0, 0.0);
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
