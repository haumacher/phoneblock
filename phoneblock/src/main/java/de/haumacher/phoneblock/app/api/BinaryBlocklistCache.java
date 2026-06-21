/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.api;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;

/**
 * Process-wide cache for the binary community blocklist, keyed by the
 * {@code (minDirect, minRange)} threshold pair. The dongle's daily sync
 * rhythm and the encoding cost of the ~20k-entry community list make
 * per-request regeneration measurable at scale; the cache shares one
 * pre-encoded byte array across all users requesting the same threshold
 * pair.
 *
 * <p>
 * The pair is the dongle's own {@code min_direct_votes} /
 * {@code min_range_votes} settings: {@code minDirect} gates exact entries,
 * {@code minRange} gates the net-evidence wildcard blocks. Encoding the same
 * pair the dongle's live API path applies (see
 * {@code DB#computeWildcardVotes}) is what keeps the downloaded list and the
 * API-fallback verdict identical.
 * </p>
 *
 * <h2>What the cache key captures</h2>
 *
 * Beyond the threshold pair, the key carries the {@code dialPrefix} (the list
 * is region-scoped &mdash; #340 Heat ranking restricted to the caller's
 * region) and the {@code maxBytes} budget bucket (the direct-number section is
 * truncated to fit the dongle's storage). Both change the produced bytes, so
 * both must distinguish cache entries; the servlet snaps each to a small
 * shared grid (dial set, {@code MAX_BYTES_OPTIONS}) so the fleet still shares
 * entries.
 *
 * <h2>What lives outside the cache key</h2>
 *
 * The community binary file is still shared across users with {@code wildcards}
 * on or off &mdash; the file always carries the prefix section; the dongle
 * decides locally whether to consult it.
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

	/**
	 * Cache key: region ({@code dialPrefix}, {@code null} for the global list),
	 * the dongle's two SPAM-vote thresholds, and the storage budget bucket that
	 * caps the direct-number section.
	 */
	public record Key(String dialPrefix, int minDirect, int minRange, int maxBytes) {
		// Value-based key; record gives equals/hashCode over all fields.
	}

	/** Producer of community bytes for a given region / threshold / budget. */
	@FunctionalInterface
	public interface Encoder {
		byte[] encode(String dialPrefix, int minDirect, int minRange, int maxBytes);
	}

	private final ConcurrentMap<Key, Entry> _entries = new ConcurrentHashMap<>();

	private final LongSupplier _clock;

	BinaryBlocklistCache() {
		this(System::nanoTime);
	}

	/** Test-only constructor that lets the suite drive the clock. */
	BinaryBlocklistCache(LongSupplier clock) {
		_clock = clock;
	}

	/**
	 * Returns the cached bytes for the {@code (minDirect, minRange)} pair,
	 * computing and caching them on a miss or after the entry has expired.
	 *
	 * @param dialPrefix Region scope (part of the cache key); {@code null} for
	 *                   the global list.
	 * @param minDirect Exact-entry vote threshold (part of the cache key).
	 * @param minRange  Wildcard net-vote threshold (part of the cache key).
	 * @param maxBytes  Storage budget bucket capping the direct section (part of
	 *                  the cache key).
	 * @param compute   Producer called on a cache miss. Must be deterministic
	 *                  modulo data freshness: the same key must always yield
	 *                  bytes that are interchangeable between users.
	 */
	public byte[] getOrCompute(String dialPrefix, int minDirect, int minRange, int maxBytes, Encoder compute) {
		Key key = new Key(dialPrefix, minDirect, minRange, maxBytes);
		long now = _clock.getAsLong();
		Entry existing = _entries.get(key);
		if (existing != null && !existing.isStale(now)) {
			return existing.bytes();
		}
		byte[] fresh = compute.encode(dialPrefix, minDirect, minRange, maxBytes);
		_entries.put(key, new Entry(now, fresh));
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
