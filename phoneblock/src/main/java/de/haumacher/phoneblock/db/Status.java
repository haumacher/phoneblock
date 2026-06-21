/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import java.util.List;

/**
 * Status information.
 */
public class Status {

	private final List<Statistics> _statistics;
	private final int _totalVotes;

	/**
	 * Creates a {@link Status}.
	 */
	public Status(List<Statistics> statistics, int totalVotes) {
		_statistics = statistics;
		_totalVotes = totalVotes;
	}

	/**
	 * Statistics of spam reports currently active.
	 */
	public List<Statistics> getStatistics() {
		return _statistics;
	}

	/**
	 * Number of total votes.
	 */
	public int getTotalVotes() {
		return _totalVotes;
	}

	/**
	 * Number of archived inactive reports. Always 0 since #342 (the ACTIVE
	 * flag is gone — decay-aware visibility replaces the soft-delete
	 * concept). Kept as a getter so the status-page template doesn't need
	 * touching ahead of an auto-translate run.
	 */
	public int getArchivedReports() {
		return 0;
	}

}
