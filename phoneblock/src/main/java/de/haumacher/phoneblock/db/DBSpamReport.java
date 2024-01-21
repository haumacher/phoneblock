/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import de.haumacher.phoneblock.db.model.SpamReport;

/**
 * Data object representing a single spam report.
 */
public class DBSpamReport extends SpamReport {
	
	/** 
	 * Creates a {@link DBSpamReport}.
	 */
	public DBSpamReport(String phone, int votes, long lastUpdate, long dateAdded) {
		setPhone(phone);
		setVotes(votes);
		setLastUpdate(lastUpdate);
		setDateAdded(dateAdded);
	}
	
}
