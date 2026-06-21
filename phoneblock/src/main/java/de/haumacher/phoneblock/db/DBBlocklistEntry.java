/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import de.haumacher.phoneblock.app.api.model.Rating;

/**
 * One row of the published BLOCKLIST table (#342).
 *
 * <p>
 * {@link #getVotes()} is the bucket floor (2, 4, 10, 20, 50, 100) and
 * {@link #getHeat()} the log4 class of the activity EMA, both frozen at
 * publication time — independent of the read moment, unlike the decay-exposed
 * EMA columns on NUMBERS. {@code votes = 0} marks a tombstone: the removal
 * signal served to incremental-sync clients. {@link #getCategory()} (the
 * dominant rating) and {@link #getLastPing()} (last activity) are likewise
 * frozen at publication and denormalized onto BLOCKLIST (migration 43), so the
 * sync reads need no live NUMBERS join; they are informational only — the
 * rating colors the entry, lastActivity is for display.
 * </p>
 */
public class DBBlocklistEntry {

	private final String _phone;

	private final int _votes;

	private final int _heat;

	private final long _lastPing;

	private final Rating _category;

	private DBBlocklistEntry(String phone, int votes, int heat, long lastPing, Rating category) {
		_phone = phone;
		_votes = votes;
		_heat = heat;
		_lastPing = lastPing;
		_category = category;
	}

	/**
	 * Creates a {@link DBBlocklistEntry} for the API download: published last
	 * activity and dominant category, no Heat class.
	 */
	public DBBlocklistEntry(String phone, int votes, long lastPing, Rating category) {
		this(phone, votes, 0, lastPing, category);
	}

	/**
	 * Creates a {@link DBBlocklistEntry} for the CardDAV pipeline: phone,
	 * votes bucket and Heat class — nothing else is rendered.
	 */
	public DBBlocklistEntry(String phone, int votes, int heat) {
		this(phone, votes, heat, 0, null);
	}

	/** The number in PhoneBlock ID format (see NumberAnalyzer). */
	public String getPhone() {
		return _phone;
	}

	/** The published vote bucket floor, {@code 0} for a tombstone. */
	public int getVotes() {
		return _votes;
	}

	/** The published Heat class (log4 of the decoded activity EMA). */
	public int getHeat() {
		return _heat;
	}

	/** Published last activity timestamp of the number. */
	public long getLastPing() {
		return _lastPing;
	}

	/**
	 * The published dominant rating category, or {@code null} on the CardDAV
	 * path (which does not render a category).
	 */
	public Rating getCategory() {
		return _category;
	}

}
