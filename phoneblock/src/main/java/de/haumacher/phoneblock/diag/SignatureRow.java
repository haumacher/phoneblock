/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.diag;

/**
 * A projection of {@code DIAG_SIGNATURE} used by the matcher and the
 * introspection API.
 */
public class SignatureRow {
	private String sigId;
	private String source;
	private String signature;
	private String tag;
	private String category;
	private long totalEvents;
	private long firstSeen;
	private long lastSeen;

	public String getSigId() { return sigId; }
	public void setSigId(String sigId) { this.sigId = sigId; }
	public String getSource() { return source; }
	public void setSource(String source) { this.source = source; }
	public String getSignature() { return signature; }
	public void setSignature(String signature) { this.signature = signature; }
	public String getTag() { return tag; }
	public void setTag(String tag) { this.tag = tag; }
	public String getCategory() { return category; }
	public void setCategory(String category) { this.category = category; }
	public long getTotalEvents() { return totalEvents; }
	public void setTotalEvents(long totalEvents) { this.totalEvents = totalEvents; }
	public long getFirstSeen() { return firstSeen; }
	public void setFirstSeen(long firstSeen) { this.firstSeen = firstSeen; }
	public long getLastSeen() { return lastSeen; }
	public void setLastSeen(long lastSeen) { this.lastSeen = lastSeen; }
}
