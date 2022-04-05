/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.carddav;

/**
 * A depth specifier for retrieving parts of the CardDAV resource tree.
 */
public enum Depth {
	
	/**
	 * Only the resource whose URL was requested.
	 */
	EMPTY, 
	
	/**
	 * The requested collection and all of its children.
	 */
	IMMEDIATES, 
	
	/**
	 * The whole sub-tree rooted at the requested resource.
	 */
	INFINITY;

	/** 
	 * Converts from a <code>depth</code> header value of the DAV protocol.
	 */
	public static Depth fromHeader(String depthValue) {
		if (depthValue == null) {
			return INFINITY;
		}
		switch (depthValue) {
		case "0": return EMPTY;
		case "1": return IMMEDIATES;
		case "infinity": return INFINITY;
		}
		throw new IllegalArgumentException("Invalid depth: " + depthValue);
	}
}
