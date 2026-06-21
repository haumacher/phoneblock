/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link BinaryBlocklistCache}: hit/miss behaviour keyed by the
 * {@code (dialPrefix, minDirect, minRange, maxBytes)} tuple, TTL expiry,
 * manual flush.
 */
class TestBinaryBlocklistCache {

	/** 16 minutes — past the 15-minute TTL. */
	private static final long PAST_TTL_NANOS = 1_000_000_000L * 60 * 16;

	/** Default region / budget for tests not exercising those dimensions. */
	private static final String DIAL = "+49";
	private static final int BYTES = 262144;

	@Test
	void firstLookupComputesAndStores() {
		AtomicInteger calls = new AtomicInteger();
		BinaryBlocklistCache cache = new BinaryBlocklistCache();

		byte[] first = cache.getOrCompute(DIAL, 4, 4, BYTES, (dp, d, r, mb) -> {
			calls.incrementAndGet();
			return new byte[] { (byte) d, (byte) r };
		});
		byte[] second = cache.getOrCompute(DIAL, 4, 4, BYTES, (dp, d, r, mb) -> {
			calls.incrementAndGet();
			return new byte[] { 99 };
		});

		assertEquals(1, calls.get(), "second call hits the cache, supplier not invoked");
		assertSame(first, second, "same byte array instance returned");
		assertEquals(1, cache.size());
	}

	@Test
	void minRangeIsPartOfTheKey() {
		// Same minDirect, different minRange must cache separately — otherwise a
		// user's wildcard threshold would leak across cache entries.
		AtomicInteger calls = new AtomicInteger();
		BinaryBlocklistCache cache = new BinaryBlocklistCache();

		byte[] a = cache.getOrCompute(DIAL, 4, 4, BYTES, (dp, d, r, mb) -> {
			calls.incrementAndGet();
			return new byte[] { (byte) r };
		});
		byte[] b = cache.getOrCompute(DIAL, 4, 8, BYTES, (dp, d, r, mb) -> {
			calls.incrementAndGet();
			return new byte[] { (byte) r };
		});

		assertEquals(2, calls.get());
		assertNotSame(a, b);
		assertEquals(4, a[0]);
		assertEquals(8, b[0]);
		assertEquals(2, cache.size());
	}

	@Test
	void minDirectIsPartOfTheKey() {
		AtomicInteger calls = new AtomicInteger();
		BinaryBlocklistCache cache = new BinaryBlocklistCache();

		byte[] a = cache.getOrCompute(DIAL, 4, 10, BYTES, (dp, d, r, mb) -> {
			calls.incrementAndGet();
			return new byte[] { (byte) d };
		});
		byte[] b = cache.getOrCompute(DIAL, 8, 10, BYTES, (dp, d, r, mb) -> {
			calls.incrementAndGet();
			return new byte[] { (byte) d };
		});

		assertEquals(2, calls.get());
		assertNotSame(a, b);
		assertEquals(4, a[0]);
		assertEquals(8, b[0]);
		assertEquals(2, cache.size());
	}

	@Test
	void dialPrefixIsPartOfTheKey() {
		// Region-scoped lists differ (#340), so two dial prefixes must not share
		// a cache entry — otherwise a German dongle could get the French list.
		AtomicInteger calls = new AtomicInteger();
		BinaryBlocklistCache cache = new BinaryBlocklistCache();

		byte[] de = cache.getOrCompute("+49", 4, 10, BYTES, (dp, d, r, mb) -> {
			calls.incrementAndGet();
			return new byte[] { (byte) dp.charAt(dp.length() - 1) };
		});
		byte[] fr = cache.getOrCompute("+33", 4, 10, BYTES, (dp, d, r, mb) -> {
			calls.incrementAndGet();
			return new byte[] { (byte) dp.charAt(dp.length() - 1) };
		});

		assertEquals(2, calls.get());
		assertNotSame(de, fr);
		assertEquals(2, cache.size());
	}

	@Test
	void nullDialPrefixIsItsOwnKey() {
		// The global (unscoped) list must not collide with any region's list.
		AtomicInteger calls = new AtomicInteger();
		BinaryBlocklistCache cache = new BinaryBlocklistCache();

		cache.getOrCompute(null, 4, 10, BYTES, (dp, d, r, mb) -> {
			calls.incrementAndGet();
			return new byte[] { 0 };
		});
		cache.getOrCompute("+49", 4, 10, BYTES, (dp, d, r, mb) -> {
			calls.incrementAndGet();
			return new byte[] { 1 };
		});
		// Repeat the global lookup: must hit the cache, not recompute.
		cache.getOrCompute(null, 4, 10, BYTES, (dp, d, r, mb) -> {
			calls.incrementAndGet();
			return new byte[] { 2 };
		});

		assertEquals(2, calls.get());
		assertEquals(2, cache.size());
	}

	@Test
	void maxBytesIsPartOfTheKey() {
		// A larger budget yields a longer direct section, so two budgets must
		// cache separately — a small dongle must not be served the big file.
		AtomicInteger calls = new AtomicInteger();
		BinaryBlocklistCache cache = new BinaryBlocklistCache();

		byte[] small = cache.getOrCompute(DIAL, 4, 10, 65536, (dp, d, r, mb) -> {
			calls.incrementAndGet();
			return new byte[] { (byte) (mb >> 16) };
		});
		byte[] big = cache.getOrCompute(DIAL, 4, 10, 524288, (dp, d, r, mb) -> {
			calls.incrementAndGet();
			return new byte[] { (byte) (mb >> 16) };
		});

		assertEquals(2, calls.get());
		assertNotSame(small, big);
		assertEquals(2, cache.size());
	}

	@Test
	void expiredEntryIsRecomputed() {
		AtomicInteger calls = new AtomicInteger();
		long[] clock = { 0L };
		BinaryBlocklistCache cache = new BinaryBlocklistCache(() -> clock[0]);

		byte[] first = cache.getOrCompute(DIAL, 4, 4, BYTES, (dp, d, r, mb) -> {
			calls.incrementAndGet();
			return new byte[] { 1 };
		});
		clock[0] += PAST_TTL_NANOS;
		byte[] refreshed = cache.getOrCompute(DIAL, 4, 4, BYTES, (dp, d, r, mb) -> {
			calls.incrementAndGet();
			return new byte[] { 2 };
		});

		assertEquals(2, calls.get(), "stale entry → supplier invoked again");
		assertNotSame(first, refreshed);
		assertEquals(2, refreshed[0]);
	}

	@Test
	void flushAllDropsEverything() {
		BinaryBlocklistCache cache = new BinaryBlocklistCache();
		cache.getOrCompute(DIAL, 4, 4, BYTES, (dp, d, r, mb) -> new byte[] { 1 });
		cache.getOrCompute(DIAL, 8, 8, BYTES, (dp, d, r, mb) -> new byte[] { 2 });
		assertEquals(2, cache.size());

		cache.flushAll();
		assertEquals(0, cache.size());

		AtomicInteger calls = new AtomicInteger();
		cache.getOrCompute(DIAL, 4, 4, BYTES, (dp, d, r, mb) -> {
			calls.incrementAndGet();
			return new byte[] { 9 };
		});
		assertEquals(1, calls.get(), "after flush, the next lookup misses");
	}

	@Test
	void singletonIsReused() {
		// Two requests to getInstance must return the same object — the cache
		// is process-wide and must persist across servlet invocations.
		assertSame(BinaryBlocklistCache.getInstance(), BinaryBlocklistCache.getInstance());
	}

}
