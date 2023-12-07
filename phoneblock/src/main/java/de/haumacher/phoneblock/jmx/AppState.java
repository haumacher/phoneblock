/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.jmx;

import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.index.IndexUpdateService;

/**
 * {@link AppStateMBean} implementation.
 */
public class AppState implements AppStateMBean {

	private IndexUpdateService _updater;
	private DB _db;

	/** 
	 * Creates a {@link AppState}.
	 */
	public AppState(IndexUpdateService updater, DB db) {
		_updater = updater;
		_db = db;
	}

	@Override
	public int getUsers() {
		return _db.getUsers();
	}

	@Override
	public int getInactiveUsers() {
		return _db.getInactiveUsers();
	}

	@Override
	public int getVotes() {
		return _db.getVotes();
	}

	@Override
	public int getRatings() {
		return _db.getRatings();
	}

	@Override
	public int getSearches() {
		return _db.getSearches();
	}
	
	@Override
	public int getActiveNumbers() {
		return _db.getActiveReportCount();
	}
	
	@Override
	public int getArchivedNumbers() {
		return _db.getArchivedReportCount();
	}
	
	@Override
	public void triggerIndexUpdate(String path) {
		_updater.publishPathUpdate(path);
	}
	
	@Override
	public void triggerWelcomeMails() {
		_db.sendWelcomeMails();
	}

	@Override
	public void triggerServiceMails() {
		_db.sendSupportMails();
	}
	
}
