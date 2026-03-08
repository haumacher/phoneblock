/*
 * Copyright (c) 2024 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

/**
 * Data class for FTC number information from the FTC_NUMBERS table.
 */
public class DBFtcNumberInfo {

	private String _phone;
	private int _votes;
	private int _robocalls;
	private long _firstReported;
	private long _lastReported;

	/**
	 * Creates a {@link DBFtcNumberInfo}.
	 */
	public DBFtcNumberInfo(String phone, int votes, int robocalls, long firstReported, long lastReported) {
		_phone = phone;
		_votes = votes;
		_robocalls = robocalls;
		_firstReported = firstReported;
		_lastReported = lastReported;
	}

	/**
	 * The phone number.
	 */
	public String getPhone() {
		return _phone;
	}

	/** @see #getPhone() */
	public void setPhone(String phone) {
		_phone = phone;
	}

	/**
	 * The total number of complaint votes for this number.
	 */
	public int getVotes() {
		return _votes;
	}

	/** @see #getVotes() */
	public void setVotes(int votes) {
		_votes = votes;
	}

	/**
	 * The number of robocall complaints for this number.
	 */
	public int getRobocalls() {
		return _robocalls;
	}

	/** @see #getRobocalls() */
	public void setRobocalls(int robocalls) {
		_robocalls = robocalls;
	}

	/**
	 * Timestamp of the first report for this number.
	 */
	public long getFirstReported() {
		return _firstReported;
	}

	/** @see #getFirstReported() */
	public void setFirstReported(long firstReported) {
		_firstReported = firstReported;
	}

	/**
	 * Timestamp of the last report for this number.
	 */
	public long getLastReported() {
		return _lastReported;
	}

	/** @see #getLastReported() */
	public void setLastReported(long lastReported) {
		_lastReported = lastReported;
	}
}
