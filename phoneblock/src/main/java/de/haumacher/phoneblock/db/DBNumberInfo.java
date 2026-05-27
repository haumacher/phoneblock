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

	private final double _publishedLegitEvidence;

	private final double _heat;

	private final double _spamEvidence;

	private final double _legitEvidence;

	public DBNumberInfo(String phone, long added, long updated, long lastSearch, int calls, int rawVotes, int legitimate, int ping, int poll, int advertising, int gamble, int fraud, int searches, long lastPing,
			double publishedSpamEvidence, double publishedLegitEvidence,
			double heat, double spamEvidence, double legitEvidence) {
		setPhone(phone)
		.setAdded(added)
		.setUpdated(updated)
		.setLastSearch(lastSearch)
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
		_publishedLegitEvidence = publishedLegitEvidence;
		_heat = heat;
		_spamEvidence = spamEvidence;
		_legitEvidence = legitEvidence;
	}

	/**
	 * Backwards-compatible constructor used by legacy debug/stats queries
	 * that have no snapshot context — leaves the EMA columns at zero.
	 */
	public DBNumberInfo(String phone, long added, long updated, long lastSearch, int calls, int rawVotes, int legitimate, int ping, int poll, int advertising, int gamble, int fraud, int searches, long lastPing,
			double publishedSpamEvidence, double publishedLegitEvidence) {
		this(phone, added, updated, lastSearch, calls, rawVotes, legitimate, ping, poll, advertising, gamble, fraud, searches, lastPing, publishedSpamEvidence, publishedLegitEvidence, 0.0, 0.0, 0.0);
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

	/**
	 * Snapshot of the projected LEGIT_EVIDENCE EMA at the time of the last
	 * blocklist version assignment (#342). Paired with
	 * {@link #getPublishedSpamEvidence} so callers can compute the published
	 * net evidence (spam minus legit) and compare it to the visibility
	 * threshold the way the live filter does.
	 */
	public double getPublishedLegitEvidence() {
		return _publishedLegitEvidence;
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

	/**
	 * Decoded vote-equivalent at the current moment (#342). Same shape as
	 * {@code toBlocklistEntry} and {@code PhoneInfo.votes}: rounded net
	 * decoded evidence, floored at 0. The Thymeleaf templates that iterate
	 * over raw {@link DBNumberInfo} rows (status page) read this so the
	 * displayed vote count matches what {@code /api/check} returns —
	 * {@link #getRawVotes()} remains the explicit opt-in to the cumulative
	 * counter for the few callers that need it.
	 */
	public int getVotes() {
		long now = System.currentTimeMillis();
		double decodedSpam = Ema.decode(_spamEvidence, now, Ema.CLASSIFICATION_TAU_MILLIS);
		double decodedLegit = Ema.decode(_legitEvidence, now, Ema.CLASSIFICATION_TAU_MILLIS);
		return (int) Math.round(Math.max(0.0, decodedSpam - decodedLegit));
	}

}
