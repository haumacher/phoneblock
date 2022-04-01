/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

/**
 * TODO
 *
 * @author <a href="mailto:haui@haumacher.de">Bernhard Haumacher</a>
 */
public class SpamReport {
	
	private final String _phone;
	private final int _votes;
	private final long _lastUpdate;
	
	/** 
	 * Creates a {@link SpamReport}.
	 */
	public SpamReport(String phone, int votes, long lastUpdate) {
		_phone = phone;
		_votes = votes;
		_lastUpdate = lastUpdate;
	}

	/**
	 * TODO
	 */
	public String getPhone() {
		return _phone;
	}

	/**
	 * TODO
	 */
	public long getLastUpdate() {
		return _lastUpdate;
	}

	/**
	 * TODO
	 */
	public int getVotes() {
		return _votes;
	}
	
}
