/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.crawl;

/**
 * Callback interface to report spam calls.
 */
public interface SpamReporter {

	/** 
	 * Report a caller as potential source of nuisance.
	 *
	 * @param caller The phone number.
	 * @param rating a value between 1 and 5. A low value indicates a nuisance caller. 
	 * @param time The time of the spam report. 
	 */
	void reportCaller(String caller, int rating, long time);

}
