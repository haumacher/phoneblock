/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.mailcheck;

/**
 * Simple key-value store for service properties (e.g. ETag caching).
 */
public interface PropertyStore {

	/**
	 * Retrieves the value for the given key, or {@code null} if not set.
	 */
	String getProperty(String key);

	/**
	 * Sets the value for the given key, inserting or updating as needed.
	 */
	void setProperty(String key, String value);

}
