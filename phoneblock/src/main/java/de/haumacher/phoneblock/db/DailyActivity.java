/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

/**
 * Result row for the daily calls/votes/searches activity query.
 *
 * <p>
 * Each row holds the per-revision increments of the cumulative
 * {@code NUMBERS} counters, reconstructed from {@code NUMBERS_HISTORY}, plus
 * the creation time of the revision the increment belongs to.
 * </p>
 */
public class DailyActivity {

	private long created;
	private int calls;
	private int votes;
	private int searches;

	/** Creation time of the revision (Unix milliseconds) this increment belongs to. */
	public long getCreated() {
		return created;
	}

	public void setCreated(long created) {
		this.created = created;
	}

	/** Number of calls intercepted on this day. */
	public int getCalls() {
		return calls;
	}

	public void setCalls(int calls) {
		this.calls = calls;
	}

	/** Number of votes cast on this day. */
	public int getVotes() {
		return votes;
	}

	public void setVotes(int votes) {
		this.votes = votes;
	}

	/** Number of searches performed on this day. */
	public int getSearches() {
		return searches;
	}

	public void setSearches(int searches) {
		this.searches = searches;
	}

}
