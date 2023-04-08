/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.jmx;

import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.index.IndexUpdateService;

/**
 * {@link AppStateMBean} implementation.
 */
public class AppState implements AppStateMBean {

	private IndexUpdateService _updater;

	/** 
	 * Creates a {@link AppState}.
	 */
	public AppState(IndexUpdateService updater) {
		_updater = updater;
	}

	@Override
	public int getUsers() {
		return DBService.getInstance().getUsers();
	}

	@Override
	public int getInactiveUsers() {
		return DBService.getInstance().getInactiveUsers();
	}

	@Override
	public int getVotes() {
		return DBService.getInstance().getVotes();
	}

	@Override
	public int getRatings() {
		return DBService.getInstance().getRatings();
	}

	@Override
	public int getSearches() {
		return DBService.getInstance().getSearches();
	}
	
	@Override
	public int getActiveNumbers() {
		return DBService.getInstance().getActiveReportCount();
	}
	
	@Override
	public int getArchivedNumbers() {
		return DBService.getInstance().getArchivedReportCount();
	}
	
	@Override
	public void triggerIndexUpdate(String path) {
		_updater.publishPathUpdate(path);
	}

}
