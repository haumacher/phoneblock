/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import de.haumacher.phoneblock.app.api.DeviceLinks.Decision;
import de.haumacher.phoneblock.app.api.DeviceLinks.PollResult;
import de.haumacher.phoneblock.app.api.DeviceLinks.Started;
import de.haumacher.phoneblock.app.api.DeviceLinks.TokenFactory;

/**
 * Probe for {@link DeviceLinks} (#621): composes the link store with concurrency and with several
 * users linking at the same time.
 */
public class TestDeviceLinksProbe {

	private final AtomicLong _now = new AtomicLong(5_000_000);

	private final DeviceLinks _links = new DeviceLinks(_now::get, new SecureRandom());

	/**
	 * A device retrying its poll in parallel (e.g. a timed-out request still in flight) must get
	 * the token exactly once, and exactly one token must be created.
	 */
	@Test
	public void testConcurrentPickupMintsOnce() throws Exception {
		Started started = _links.start("PhoneBlockDongle", null, "192.0.2.1");
		assertEquals(Decision.OK, _links.approve(started.userCode(), "user-a"));

		AtomicInteger minted = new AtomicInteger();
		TokenFactory tokens = (user, now, ua, label) -> "token-" + minted.incrementAndGet();

		int threads = 32;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch go = new CountDownLatch(1);
		List<Future<PollResult>> results = new ArrayList<>();
		for (int n = 0; n < threads; n++) {
			results.add(pool.submit(() -> {
				go.await();
				return _links.poll(started.deviceCode(), "ua", tokens);
			}));
		}
		go.countDown();

		int approved = 0;
		for (Future<PollResult> result : results) {
			PollResult poll = result.get(10, TimeUnit.SECONDS);
			if (DeviceLinks.APPROVED.equals(poll.status())) {
				approved++;
			} else {
				// Every other concurrent poll sees the link already gone or too early.
				assertNull(poll.token());
			}
		}
		pool.shutdown();

		assertEquals(1, approved);
		assertEquals(1, minted.get());
		assertEquals(0, _links.size());
	}

	/**
	 * Two users link two devices at the same time: each token belongs to the user who confirmed
	 * that device's code, with that device's label — confirming one code never completes the
	 * other link.
	 */
	@Test
	public void testTwoUsersTwoDevices() throws Exception {
		Started a = _links.start("PhoneBlockDongle", "Dongle A", "192.0.2.1");
		Started b = _links.start("PhoneBlockDongle", "Dongle B", "192.0.2.1");
		assertNotEquals(a.userCode(), b.userCode());

		List<String> minted = new ArrayList<>();
		TokenFactory tokens = (user, now, ua, label) -> {
			String token = user + "/" + label;
			minted.add(token);
			return token;
		};

		// User B confirms first, typing the code in lower case without dash.
		assertEquals(Decision.OK, _links.approve(b.userCode().replace("-", "").toLowerCase(), "user-b"));
		assertEquals(DeviceLinks.PENDING, _links.poll(a.deviceCode(), "ua", tokens).status());

		_now.addAndGet(DeviceLinks.INTERVAL * 1000L);
		assertEquals("user-b/Dongle B", _links.poll(b.deviceCode(), "ua", tokens).token());

		assertEquals(Decision.OK, _links.approve(a.userCode(), "user-a"));
		_now.addAndGet(DeviceLinks.INTERVAL * 1000L);
		assertEquals("user-a/Dongle A", _links.poll(a.deviceCode(), "ua", tokens).token());

		assertEquals(List.of("user-b/Dongle B", "user-a/Dongle A"), minted);
	}

	/**
	 * A collected code is gone for the confirmation page too: it cannot be confirmed a second time
	 * to mint another token.
	 */
	@Test
	public void testCollectedCodeCannotBeReused() throws Exception {
		Started started = _links.start("PhoneBlockDongle", null, "192.0.2.1");
		assertEquals(Decision.OK, _links.approve(started.userCode(), "user-a"));
		assertEquals(DeviceLinks.APPROVED,
			_links.poll(started.deviceCode(), "ua", (u, n, ua, l) -> "t").status());

		assertNull(_links.find(started.userCode()));
		assertEquals(Decision.UNKNOWN, _links.approve(started.userCode(), "user-b"));
	}

}
