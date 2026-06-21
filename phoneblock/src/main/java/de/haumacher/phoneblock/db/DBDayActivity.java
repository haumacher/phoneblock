/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

/**
 * Per-day activity counts (searches/calls/votes) for a single UTC day, read from
 * the {@code NUMBER_ACTIVITY} ledger.
 *
 * <p>
 * Result row of both the global activity chart and the per-number history.
 * Mapped by constructor position, so the {@code SELECT} must list
 * {@code EPOCH_DAY, SEARCHES, CALLS, VOTES} in that order.
 * </p>
 */
public class DBDayActivity {

	private final int epochDay;
	private final int searches;
	private final int calls;
	private final int votes;

	public DBDayActivity(int epochDay, int searches, int calls, int votes) {
		this.epochDay = epochDay;
		this.searches = searches;
		this.calls = calls;
		this.votes = votes;
	}

	/** UTC epoch-day (floor(epochMillis / 86400000)) this row belongs to. */
	public int getEpochDay() {
		return epochDay;
	}

	/** Searches recorded for the number(s) on this day. */
	public int getSearches() {
		return searches;
	}

	/** Calls recorded for the number(s) on this day. */
	public int getCalls() {
		return calls;
	}

	/** Votes (ratings cast) recorded for the number(s) on this day. */
	public int getVotes() {
		return votes;
	}

}
