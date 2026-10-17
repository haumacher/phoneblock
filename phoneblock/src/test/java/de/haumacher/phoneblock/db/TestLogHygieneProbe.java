/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import de.haumacher.phoneblock.diag.LogNormalizer;
import de.haumacher.phoneblock.util.LogThrottle;

/**
 * Probe for #525/#528: composes the token parse-failure message with the
 * diagnostics normalizer and the throttle with concurrent callers.
 */
class TestLogHygieneProbe {

	/**
	 * Different broken tokens must neither leak into the message nor fragment
	 * the diagnostics signature.
	 */
	@Test
	void testParseFailureYieldsOneSignature() {
		String[] tokens = {
			"pbt_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
			"pbt_zzzz-not-base64-zzzz",
			"pbt_Q",
			"pbt_0123456789abcdef0123456789abcdef0123456789",
		};
		String signature = null;
		for (int n = 0; n < tokens.length; n++) {
			String token = tokens[n];
			String message = TokenInfo.describeParseFailure(token, new IOException("Illegal base64 character 2d in " + token), n * 7);
			assertFalse(message.contains(token), message);
			String normalized = LogNormalizer.normalize(message);
			if (signature == null) {
				signature = normalized;
			} else {
				assertEquals(signature, normalized);
			}
		}
	}

	/**
	 * Under contention exactly one occurrence per window is reported and no
	 * suppressed occurrence is lost from the count reported after the window.
	 */
	@Test
	void testThrottleConcurrent() throws Exception {
		AtomicLong now = new AtomicLong(1_000);
		LogThrottle throttle = new LogThrottle(10_000, now::get);

		int threads = 8;
		int perThread = 5_000;
		AtomicLong reported = new AtomicLong();
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		for (int t = 0; t < threads; t++) {
			pool.execute(() -> {
				try {
					start.await();
				} catch (InterruptedException ex) {
					return;
				}
				for (int i = 0; i < perThread; i++) {
					if (throttle.acquire("k") != LogThrottle.SUPPRESS) {
						reported.incrementAndGet();
					}
				}
			});
		}
		start.countDown();
		pool.shutdown();
		assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

		assertEquals(1, reported.get());

		now.addAndGet(10_000);
		assertEquals(threads * perThread - 1, throttle.acquire("k"));
	}
}
