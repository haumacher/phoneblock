/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.diag;

/**
 * The persisted reader checkpoint for one log stream (a row of
 * {@code DIAG_INGEST_CURSOR}). A {@link #getSegmentCount()} of {@code -1} means
 * "fresh — start at the oldest retained segment".
 */
public class IngestCursor {

	private long segmentCount = -1;
	private long byteOffset = 0;
	private long lastLineTs = 0;

	public long getSegmentCount() {
		return segmentCount;
	}

	public void setSegmentCount(long segmentCount) {
		this.segmentCount = segmentCount;
	}

	public long getByteOffset() {
		return byteOffset;
	}

	public void setByteOffset(long byteOffset) {
		this.byteOffset = byteOffset;
	}

	public long getLastLineTs() {
		return lastLineTs;
	}

	public void setLastLineTs(long lastLineTs) {
		this.lastLineTs = lastLineTs;
	}
}
