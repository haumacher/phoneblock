/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.diag;

/**
 * A projection of {@code DIAG_ORIGIN_SIGNATURE} for one origin that crosses a
 * rule's persistence threshold.
 */
public class OriginRow {
	private String originId;
	private String userId;
	private long lastSeen;
	private long eventCount;
	private int distinctDays;

	public String getOriginId() { return originId; }
	public void setOriginId(String originId) { this.originId = originId; }
	public String getUserId() { return userId; }
	public void setUserId(String userId) { this.userId = userId; }
	public long getLastSeen() { return lastSeen; }
	public void setLastSeen(long lastSeen) { this.lastSeen = lastSeen; }
	public long getEventCount() { return eventCount; }
	public void setEventCount(long eventCount) { this.eventCount = eventCount; }
	public int getDistinctDays() { return distinctDays; }
	public void setDistinctDays(int distinctDays) { this.distinctDays = distinctDays; }
}
