/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.api;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.IntFunction;
import java.util.function.LongSupplier;

/**
 * Process-wide cache for the binary community blocklist, keyed by
 * {@code minVotes}. The dongle's daily sync rhythm and the encoding cost of
 * the ~20k-entry community list make per-request regeneration measurable at
 * scale; the cache shares one pre-encoded byte array across all users with
 * the same {@code minVotes}.
 *
 * <h2>What lives outside the cache key</h2>
 *
 * The community binary file is shared across:
 * <ul>
 *   <li>users with different {@code dialPrefix} settings &mdash; phone IDs
 *       are normalised to bare E.164 digits server-side, so the bytes are
 *       dial-prefix-independent;</li>
 *   <li>users with {@code wildcards} on or off &mdash; the file always
 *       carries the prefix section; the dongle decides locally whether to
 *       consult it.</li>
 * </ul>
 *
 * <h2>Staleness</h2>
 *
 * Entries expire {@link #TTL_NANOS 15 minutes} after creation; no
 * event-driven invalidation. The community blocklist updates continuously,
 * and the dongle's once-a-day full sync makes anything finer than minutes
 * pointless. Tests and admin tooling can drop everything via
 * {@link #flushAll()}.
 */
public final class BinaryBlocklistCache {

	private static final long TTL_NANOS = 1_000_000_000L * 60 * 15;

	private static final BinaryBlocklistCache INSTANCE = new BinaryBlocklistCache();

	/** The process-wide cache instance. */
	public static BinaryBlocklistCache getInstance() {
		return INSTANCE;
	}

	private final ConcurrentMap<Integer, Entry> _entries = new ConcurrentHashMap<>();

	private final LongSupplier _clock;

	BinaryBlocklistCache() {
		this(System::nanoTime);
	}

	/** Test-only constructor that lets the suite drive the clock. */
	BinaryBlocklistCache(LongSupplier clock) {
		_clock = clock;
	}

	/**
	 * Returns the cached bytes for {@code minVotes}, computing and caching
	 * them on a miss or after the entry has expired.
	 *
	 * @param minVotes Cache key.
	 * @param compute  Producer called on a cache miss. Must be deterministic
	 *                 modulo data freshness: the same {@code minVotes} value
	 *                 must always yield bytes that are interchangeable
	 *                 between users.
	 */
	public byte[] getOrCompute(int minVotes, IntFunction<byte[]> compute) {
		long now = _clock.getAsLong();
		Entry existing = _entries.get(minVotes);
		if (existing != null && !existing.isStale(now)) {
			return existing.bytes();
		}
		byte[] fresh = compute.apply(minVotes);
		_entries.put(minVotes, new Entry(now, fresh));
		return fresh;
	}

	/** Drops every cached entry. */
	public void flushAll() {
		_entries.clear();
	}

	/** Current size for diagnostics / tests. */
	int size() {
		return _entries.size();
	}

	private static final class Entry {

		private final long _created;

		private final byte[] _bytes;

		Entry(long created, byte[] bytes) {
			_created = created;
			_bytes = bytes;
		}

		byte[] bytes() {
			return _bytes;
		}

		boolean isStale(long now) {
			return now - _created > TTL_NANOS;
		}

	}

}
