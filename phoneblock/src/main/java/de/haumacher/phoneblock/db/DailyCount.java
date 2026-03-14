/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

/**
 * Result row for daily registration count queries.
 */
public class DailyCount {

	private long dayEpoch;
	private int cnt;

	public long getDayEpoch() {
		return dayEpoch;
	}

	public void setDayEpoch(long dayEpoch) {
		this.dayEpoch = dayEpoch;
	}

	public int getCnt() {
		return cnt;
	}

	public void setCnt(int cnt) {
		this.cnt = cnt;
	}

}
