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
	private final int _archivedReports;
	
	/** 
	 * Creates a {@link Status}.
	 */
	public Status(List<Statistics> statistics, int totalVotes, int archivedReports) {
		_statistics = statistics;
		_totalVotes = totalVotes;
		_archivedReports = archivedReports;
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
	 * Number of archived inactive reports. 
	 */
	public int getArchivedReports() {
		return _archivedReports;
	}
	
}
