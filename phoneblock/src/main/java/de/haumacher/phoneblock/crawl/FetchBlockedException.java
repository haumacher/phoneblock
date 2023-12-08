/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.crawl;

import java.io.IOException;

/**
 * Exception signaling that a meta search has been blocked.
 */
public class FetchBlockedException extends Exception {

	/** 
	 * Creates a {@link FetchBlockedException}.
	 */
	public FetchBlockedException(IOException ex) {
		super(ex);
	}
}
