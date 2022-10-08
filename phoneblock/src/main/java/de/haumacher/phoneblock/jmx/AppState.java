/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.jmx;

import de.haumacher.phoneblock.db.DBService;

/**
 * {@link AppStateMBean} implementation.
 */
public class AppState implements AppStateMBean {

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

}
