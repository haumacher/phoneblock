/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.diag;

/**
 * A dongle whose most-recent auth token has not been used for a while — i.e. the
 * device has stopped its daily self-test / token checks. Derived from the
 * {@code TOKENS} table (the {@code User-Agent} carries the stable device id), not
 * from the log pipeline: this is an <em>absence</em> signal, which no
 * log-signature rule can catch.
 */
public class SilentDongle {
	private String deviceId;
	private long userId;
	private long lastAccess;

	public String getDeviceId() { return deviceId; }
	public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
	public long getUserId() { return userId; }
	public void setUserId(long userId) { this.userId = userId; }
	public long getLastAccess() { return lastAccess; }
	public void setLastAccess(long lastAccess) { this.lastAccess = lastAccess; }
}
