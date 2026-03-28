/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.mailcheck.db;

import de.haumacher.mailcheck.model.DomainStatus;

/**
 * POJO for the {@code MX_HOST_STATUS} and {@code MX_IP_STATUS} tables.
 */
public class DBMxStatus {

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
		return DomainStatus.DISPOSABLE.protocolName().equals(_status);
	}

	public boolean isSafe() {
		return DomainStatus.SAFE.protocolName().equals(_status);
	}

	/**
	 * Returns the updated status when a new observation is made.
	 * If the new observation matches the current status, returns the current status.
	 * Otherwise, returns {@link #MIXED}.
	 */
	public static String mergeStatus(String existing, boolean disposable) {
		String observed = statusFor(disposable);
		if (existing.equals(observed)) {
			return existing;
		}
		return MIXED;
	}

	/**
	 * Returns the MX status string for the given disposable flag.
	 */
	public static String statusFor(boolean disposable) {
		return disposable ? DomainStatus.DISPOSABLE.protocolName() : DomainStatus.SAFE.protocolName();
	}
}
