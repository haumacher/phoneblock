/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.mailcheck.db;

/**
 * POJO for the {@code MX_HOST_STATUS} and {@code MX_IP_STATUS} tables.
 */
public class DBMxStatus {

	/** Classification of an MX host or IP based on the domains it serves. */
	public enum MxStatus {
		/** Only associated with disposable domains. */
		disposable,
		/** Only associated with legitimate domains. */
		safe,
		/** Associated with both disposable and legitimate domains. */
		mixed;

		/** Returns the status for the given disposable flag. */
		public static MxStatus of(boolean disposable) {
			return disposable ? MxStatus.disposable : MxStatus.safe;
		}

		/** Returns the merged status when a new observation is made. */
		public MxStatus merge(boolean disposable) {
			return this == of(disposable) ? this : mixed;
		}
	}

	/** Minimum number of domains backing an MX classification before it is trusted. */
	public static final int MIN_DOMAIN_COUNT = 5;

	private final String _key;
	private final MxStatus _status;
	private final int _domainCount;
	private final long _lastUpdated;

	public DBMxStatus(String key, String status, int domainCount, long lastUpdated) {
		_key = key;
		_status = MxStatus.valueOf(status);
		_domainCount = domainCount;
		_lastUpdated = lastUpdated;
	}

	public String getKey() {
		return _key;
	}

	public MxStatus getStatus() {
		return _status;
	}

	public int getDomainCount() {
		return _domainCount;
	}

	public long getLastUpdated() {
		return _lastUpdated;
	}

	/** Whether this entry is trustworthy (backed by enough domains). */
	public boolean isTrusted() {
		return _domainCount >= MIN_DOMAIN_COUNT;
	}

	public boolean isDisposable() {
		return _status == MxStatus.disposable;
	}

	public boolean isSafe() {
		return _status == MxStatus.safe;
	}
}
