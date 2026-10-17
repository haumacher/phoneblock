/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

/**
 * Test case for {@link LogThrottle}.
 */
class TestLogThrottle {

	private static final long WINDOW = 10 * 60 * 1000L;

	private final AtomicLong _now = new AtomicLong(1_000_000L);

	private final LogThrottle _throttle = new LogThrottle(WINDOW, _now::get);

	@Test
	void firstOccurrenceIsLogged() {
		assertEquals(0, _throttle.acquire("a"));
	}

	@Test
	void repeatsInsideWindowAreSuppressed() {
		assertEquals(0, _throttle.acquire("a"));
		_now.addAndGet(1000);
		assertEquals(LogThrottle.SUPPRESS, _throttle.acquire("a"));
		_now.addAndGet(WINDOW - 1001);
		assertEquals(LogThrottle.SUPPRESS, _throttle.acquire("a"));
	}

	@Test
	void summaryAfterWindowReportsSuppressedCount() {
		assertEquals(0, _throttle.acquire("a"));
		for (int n = 0; n < 24; n++) {
			_now.addAndGet(1000);
			assertEquals(LogThrottle.SUPPRESS, _throttle.acquire("a"));
		}

		_now.set(1_000_000L + WINDOW);
		assertEquals(24, _throttle.acquire("a"));

		// A new window has started, the count is reset.
		_now.addAndGet(1);
		assertEquals(LogThrottle.SUPPRESS, _throttle.acquire("a"));
		_now.addAndGet(WINDOW);
		assertEquals(1, _throttle.acquire("a"));
	}

	@Test
	void quietWindowReportsZero() {
		assertEquals(0, _throttle.acquire("a"));
		_now.addAndGet(5 * WINDOW);
		assertEquals(0, _throttle.acquire("a"));
	}

	@Test
	void independentKeysDoNotSuppressEachOther() {
		assertEquals(0, _throttle.acquire("a"));
		assertEquals(0, _throttle.acquire("b"));
		assertEquals(LogThrottle.SUPPRESS, _throttle.acquire("a"));
		assertEquals(LogThrottle.SUPPRESS, _throttle.acquire("a"));
		assertEquals(LogThrottle.SUPPRESS, _throttle.acquire("b"));

		_now.addAndGet(WINDOW);
		assertEquals(2, _throttle.acquire("a"));
		assertEquals(1, _throttle.acquire("b"));
	}

}
