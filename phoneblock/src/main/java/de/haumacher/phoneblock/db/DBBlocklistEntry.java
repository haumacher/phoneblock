/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

/**
 * One row of the published BLOCKLIST table (#342).
 *
 * <p>
 * {@link #getVotes()} is the bucket floor (2, 4, 10, 20, 50, 100) frozen at
 * publication time — independent of the read moment, unlike the decay-exposed
 * EMA columns on NUMBERS. {@code votes = 0} marks a tombstone: the removal
 * signal served to incremental-sync clients. The category counters are joined
 * live from NUMBERS (zero for tombstones whose NUMBERS row is gone) and only
 * decide which spam category dominates in the entry's rating.
 * </p>
 */
public class DBBlocklistEntry {

	private final String _phone;

	private final int _votes;

	private final long _lastPing;

	private final int _legitimate;

	private final int _ping;

	private final int _poll;

	private final int _advertising;

	private final int _gamble;

	private final int _fraud;

	/**
	 * Creates a {@link DBBlocklistEntry} including the live category counters.
	 */
	public DBBlocklistEntry(String phone, int votes, long lastPing,
			int legitimate, int ping, int poll, int advertising, int gamble, int fraud) {
		_phone = phone;
		_votes = votes;
		_lastPing = lastPing;
		_legitimate = legitimate;
		_ping = ping;
		_poll = poll;
		_advertising = advertising;
		_gamble = gamble;
		_fraud = fraud;
	}

	/**
	 * Creates a {@link DBBlocklistEntry} without category counters (CardDAV
	 * pipeline — only phone, votes and last activity are rendered).
	 */
	public DBBlocklistEntry(String phone, int votes, long lastPing) {
		this(phone, votes, lastPing, 0, 0, 0, 0, 0, 0);
	}

	/** The number in PhoneBlock ID format (see NumberAnalyzer). */
	public String getPhone() {
		return _phone;
	}

	/** The published vote bucket floor, {@code 0} for a tombstone. */
	public int getVotes() {
		return _votes;
	}

	/** Last activity timestamp, frozen at publication time. */
	public long getLastPing() {
		return _lastPing;
	}

	/** Live {@code A_LEGITIMATE} rating count. */
	public int getLegitimate() {
		return _legitimate;
	}

	/** Live {@code C_PING} rating count. */
	public int getPing() {
		return _ping;
	}

	/** Live {@code D_POLL} rating count. */
	public int getPoll() {
		return _poll;
	}

	/** Live {@code E_ADVERTISING} rating count. */
	public int getAdvertising() {
		return _advertising;
	}

	/** Live {@code F_GAMBLE} rating count. */
	public int getGamble() {
		return _gamble;
	}

	/** Live {@code G_FRAUD} rating count. */
	public int getFraud() {
		return _fraud;
	}

}
