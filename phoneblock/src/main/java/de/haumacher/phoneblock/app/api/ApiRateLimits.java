/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.jndi.JNDIProperties;

/**
 * Configurable per-subject rate limits for expensive API calls.
 *
 * <p>Every limit is a {@code (count, intervalMs)} pair describing a fixed window:
 * at most {@code count} requests per {@code intervalMs} milliseconds and subject
 * (API key, or account for password-authenticated access). All values can be
 * overridden via JNDI (e.g. {@code api/ratelimit/blocklist/full/count}) or the
 * equivalent system property ({@code api.ratelimit.blocklist.full.count}); see
 * JNDI-CONFIGURATION.md. The defaults below apply when nothing is configured.</p>
 */
public final class ApiRateLimits {

	private static final Logger LOG = LoggerFactory.getLogger(ApiRateLimits.class);

	private static final long DAY_MS = 24L * 60 * 60 * 1000;

	private static volatile ApiRateLimits INSTANCE;

	/** Full blocklist download (and the Heat-ranked snapshot). */
	public final int blocklistFullCount;
	public final long blocklistFullIntervalMs;

	/** Incremental blocklist sync ({@code ?since=}). */
	public final int blocklistIncrementalCount;
	public final long blocklistIncrementalIntervalMs;

	/** CardDAV address-book synchronization. */
	public final int carddavCount;
	public final long carddavIntervalMs;

	/** Number-test lookups shared by {@code /api/num}, {@code /api/check}, {@code /api/check-prefix}. */
	public final int numberQueryCount;
	public final long numberQueryIntervalMs;

	private ApiRateLimits() {
		JNDIProperties jndi = openJndi();

		blocklistFullCount = intProp(jndi, "api.ratelimit.blocklist.full.count", 14);
		blocklistFullIntervalMs = longProp(jndi, "api.ratelimit.blocklist.full.intervalMs", 7 * DAY_MS);

		blocklistIncrementalCount = intProp(jndi, "api.ratelimit.blocklist.incremental.count", 4);
		blocklistIncrementalIntervalMs = longProp(jndi, "api.ratelimit.blocklist.incremental.intervalMs", DAY_MS);

		carddavCount = intProp(jndi, "api.ratelimit.carddav.count", 6);
		carddavIntervalMs = longProp(jndi, "api.ratelimit.carddav.intervalMs", DAY_MS);

		numberQueryCount = intProp(jndi, "api.ratelimit.number.count", 200);
		numberQueryIntervalMs = longProp(jndi, "api.ratelimit.number.intervalMs", DAY_MS);
	}

	/** The configured rate limits, loaded once on first access. */
	public static ApiRateLimits getInstance() {
		ApiRateLimits result = INSTANCE;
		if (result == null) {
			synchronized (ApiRateLimits.class) {
				result = INSTANCE;
				if (result == null) {
					result = new ApiRateLimits();
					INSTANCE = result;
				}
			}
		}
		return result;
	}

	private static JNDIProperties openJndi() {
		try {
			return new JNDIProperties();
		} catch (Exception ex) {
			// No JNDI context (e.g. tests, standalone) — fall back to system properties only.
			return null;
		}
	}

	private static int intProp(JNDIProperties jndi, String name, int defaultValue) {
		String value = lookup(jndi, name);
		if (value != null) {
			try {
				return Integer.parseInt(value.trim());
			} catch (NumberFormatException ex) {
				LOG.warn("Invalid integer for '{}': '{}', using default {}.", name, value, defaultValue);
			}
		}
		return defaultValue;
	}

	private static long longProp(JNDIProperties jndi, String name, long defaultValue) {
		String value = lookup(jndi, name);
		if (value != null) {
			try {
				return Long.parseLong(value.trim());
			} catch (NumberFormatException ex) {
				LOG.warn("Invalid number for '{}': '{}', using default {}.", name, value, defaultValue);
			}
		}
		return defaultValue;
	}

	private static String lookup(JNDIProperties jndi, String name) {
		return jndi != null ? jndi.lookupString(name) : System.getProperty(name);
	}

}
