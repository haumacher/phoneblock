/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.mailcheck.db;

/**
 * POJO for the {@code MX_HOST_STATUS} and {@code MX_IP_STATUS} tables.
 */
public class DBMxStatus {

	/** MX host or IP is only associated with disposable domains. */
	public static final String DISPOSABLE = "disposable";

	/** MX host or IP is only associated with legitimate domains. */
	public static final String SAFE = "safe";

	/** MX host or IP is associated with both disposable and legitimate domains. */
	public static final String MIXED = "mixed";

	private final String _key;
	private final String _status;
	private final long _lastUpdated;

	public DBMxStatus(String key, String status, long lastUpdated) {
		_key = key;
		_status = status;
		_lastUpdated = lastUpdated;
	}

	public String getKey() {
		return _key;
	}

	public String getStatus() {
		return _status;
	}

	public long getLastUpdated() {
		return _lastUpdated;
	}

	public boolean isDisposable() {
		return DISPOSABLE.equals(_status);
	}

	public boolean isSafe() {
		return SAFE.equals(_status);
	}

	/**
	 * Returns the updated status when a new observation is made.
	 * If the new observation matches the current status, returns the current status.
	 * Otherwise, returns {@link #MIXED}.
	 */
	public static String mergeStatus(String existing, boolean disposable) {
		String observed = disposable ? DISPOSABLE : SAFE;
		if (existing.equals(observed)) {
			return existing;
		}
		return MIXED;
	}
}
