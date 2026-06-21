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
 * {@code (minDirect, minRange)} pair, TTL expiry, manual flush.
 */
class TestBinaryBlocklistCache {

	/** 16 minutes — past the 15-minute TTL. */
	private static final long PAST_TTL_NANOS = 1_000_000_000L * 60 * 16;

	@Test
	void firstLookupComputesAndStores() {
		AtomicInteger calls = new AtomicInteger();
		BinaryBlocklistCache cache = new BinaryBlocklistCache();

		byte[] first = cache.getOrCompute(4, 4, (d, r) -> {
			calls.incrementAndGet();
			return new byte[] { (byte) d, (byte) r };
		});
		byte[] second = cache.getOrCompute(4, 4, (d, r) -> {
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

		byte[] a = cache.getOrCompute(4, 4, (d, r) -> {
			calls.incrementAndGet();
			return new byte[] { (byte) r };
		});
		byte[] b = cache.getOrCompute(4, 8, (d, r) -> {
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

		byte[] a = cache.getOrCompute(4, 10, (d, r) -> {
			calls.incrementAndGet();
			return new byte[] { (byte) d };
		});
		byte[] b = cache.getOrCompute(8, 10, (d, r) -> {
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
	void expiredEntryIsRecomputed() {
		AtomicInteger calls = new AtomicInteger();
		long[] clock = { 0L };
		BinaryBlocklistCache cache = new BinaryBlocklistCache(() -> clock[0]);

		byte[] first = cache.getOrCompute(4, 4, (d, r) -> {
			calls.incrementAndGet();
			return new byte[] { 1 };
		});
		clock[0] += PAST_TTL_NANOS;
		byte[] refreshed = cache.getOrCompute(4, 4, (d, r) -> {
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
		cache.getOrCompute(4, 4, (d, r) -> new byte[] { 1 });
		cache.getOrCompute(8, 8, (d, r) -> new byte[] { 2 });
		assertEquals(2, cache.size());

		cache.flushAll();
		assertEquals(0, cache.size());

		AtomicInteger calls = new AtomicInteger();
		cache.getOrCompute(4, 4, (d, r) -> {
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
