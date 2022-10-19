/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.jmx;

/**
 * MBean for observing application state.
 */
public interface AppStateMBean {
	
	/**
	 * The total number of registered users.
	 */
	int getUsers();

	/**
	 * The number of users that did not update their blocklist within 24h.
	 */
	int getInactiveUsers();

	/**
	 * The total number of active votes (votes on active phone numbers).
	 */
	int getVotes();

	/**
	 * The total number of ratings received so far.
	 */
	int getRatings();

	/**
	 * The total number of web search requests answered so far.
	 */
	int getSearches();
	
	/**
	 * The total numbers with active spam reports.
	 */
	int getActiveNumbers();
	
	/**
	 * The total numbers with archived spam reports.
	 */
	int getArchivedNumbers();

	/**
	 * Triggers an update of the page with the given path in the indexing services.
	 *
	 * @param path
	 *        The path starting with a `/` character relative to the context path of the application.
	 */
	void triggerIndexUpdate(String path);
	
}
