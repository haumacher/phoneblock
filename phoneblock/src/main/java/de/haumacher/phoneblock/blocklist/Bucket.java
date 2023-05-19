/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.blocklist;

public interface Bucket extends Iterable<String> {

	/**
	 * The hash index of this Bucket. 
	 */
	int getIndex();

	/** 
	 * The number of entries in this {@link Bucket}.
	 */
	int size();
	
}