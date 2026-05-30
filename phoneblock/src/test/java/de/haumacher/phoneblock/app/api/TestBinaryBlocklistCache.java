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
 * Tests {@link BinaryBlocklistCache}: hit/miss behaviour, TTL expiry, manual
 * flush.
 */
class TestBinaryBlocklistCache {

	/** 16 minutes — past the 15-minute TTL. */
	private static final long PAST_TTL_NANOS = 1_000_000_000L * 60 * 16;

	@Test
	void firstLookupComputesAndStores() {
		AtomicInteger calls = new AtomicInteger();
		BinaryBlocklistCache cache = new BinaryBlocklistCache();

		byte[] first = cache.getOrCompute(4, mv -> {
			calls.incrementAndGet();
			return new byte[] { (byte) mv };
		});
		byte[] second = cache.getOrCompute(4, mv -> {
			calls.incrementAndGet();
			return new byte[] { 99 };
		});

		assertEquals(1, calls.get(), "second call hits the cache, supplier not invoked");
		assertSame(first, second, "same byte array instance returned");
		assertEquals(1, cache.size());
	}

	@Test
	void differentKeysCacheSeparately() {
		AtomicInteger calls = new AtomicInteger();
		BinaryBlocklistCache cache = new BinaryBlocklistCache();

		byte[] a = cache.getOrCompute(4, mv -> {
			calls.incrementAndGet();
			return new byte[] { (byte) mv };
		});
		byte[] b = cache.getOrCompute(8, mv -> {
			calls.incrementAndGet();
			return new byte[] { (byte) mv };
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

		byte[] first = cache.getOrCompute(4, mv -> {
			calls.incrementAndGet();
			return new byte[] { 1 };
		});
		clock[0] += PAST_TTL_NANOS;
		byte[] refreshed = cache.getOrCompute(4, mv -> {
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
		cache.getOrCompute(4, mv -> new byte[] { 1 });
		cache.getOrCompute(8, mv -> new byte[] { 2 });
		assertEquals(2, cache.size());

		cache.flushAll();
		assertEquals(0, cache.size());

		AtomicInteger calls = new AtomicInteger();
		cache.getOrCompute(4, mv -> {
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
