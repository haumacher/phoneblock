/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

/**
 * Data object representing a single spam report.
 */
public class SpamReport {
	
	private final String _phone;
	private final int _votes;
	private final long _lastUpdate;
	private final long _dateAdded;
	private boolean _archived;
	
	/** 
	 * Creates a {@link SpamReport}.
	 */
	public SpamReport(String phone, int votes, long lastUpdate, long dateAdded) {
		_phone = phone;
		_votes = votes;
		_lastUpdate = lastUpdate;
		_dateAdded = dateAdded;
	}
	
	/**
	 * Whether this entry is not in the active list.
	 */
	public boolean isArchived() {
		return _archived;
	}
	
	public void setArchived(boolean archived) {
		_archived = archived;
	}

	/**
	 * The phone number reported as spam.
	 */
	public String getPhone() {
		return _phone;
	}

	/**
	 * Time in milliseconds since epoch when the last vote for this phone number was seen.
	 */
	public long getLastUpdate() {
		return _lastUpdate;
	}

	/**
	 * The total number of votes that report this phone number as spam.
	 */
	public int getVotes() {
		return _votes;
	}
	
	/**
	 * Time in milliseconds since epoch when the first spam report for this number was received.
	 */
	public long getDateAdded() {
		return _dateAdded;
	}
	
}
