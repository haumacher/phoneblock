/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.jmx;

/**
 * MBean for observing application state.
 */
public interface AppStateMBean {
	
	int getUsers();
	
	int getInactiveUsers();
	
	int getVotes();
	
	int getRatings();
	
	int getSearches();

}
