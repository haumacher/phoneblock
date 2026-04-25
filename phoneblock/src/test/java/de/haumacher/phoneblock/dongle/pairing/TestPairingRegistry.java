/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.dongle.pairing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import de.haumacher.phoneblock.dongle.pairing.PairingRegistry.RegisterResult;

/**
 * Pin down the registry's correctness AND its anti-abuse behaviour. The
 * tests drive a fake clock so TTL/rate-limit logic can be exercised in
 * milliseconds without sleeping.
 */
class TestPairingRegistry {

	private static final String S1 = "00112233445566778899aabbccddeeff";
	private static final String S2 = "ffeeddccbbaa99887766554433221100";
	private static final String IP1 = "192.168.178.42";
	private static final String IP2 = "10.0.0.7";

	private final AtomicLong _now = new AtomicLong(1_000_000_000L);

	private PairingRegistry newRegistry() {
		// scheduler=null disables the periodic sweep; tests call sweep()
		// manually where they care about it.
		return new PairingRegistry(null, _now::get);
	}

	@Test
	void registerThenLookup() {
		PairingRegistry r = newRegistry();
		assertEquals(RegisterResult.OK, r.register(S1, IP1, "1.2.3.4"));
		assertEquals(IP1, r.lookup(S1));
		// Lookup is idempotent.
		assertEquals(IP1, r.lookup(S1));
	}

	@Test
	void unknownSecretIs404() {
		PairingRegistry r = newRegistry();
		assertNull(r.lookup(S1));
	}

	@Test
	void rejectsMalformedSecret() {
		PairingRegistry r = newRegistry();
		assertEquals(RegisterResult.BAD_REQUEST, r.register("abc", IP1, "1.2.3.4"));
		assertEquals(RegisterResult.BAD_REQUEST, r.register("ZZ" + S1.substring(2), IP1, "1.2.3.4"));
		assertEquals(RegisterResult.BAD_REQUEST, r.register(S1.toUpperCase(), IP1, "1.2.3.4"));
		assertNull(r.lookup("abc"));
		assertNull(r.lookup(null));
	}

	@Test
	void rejectsMalformedIp() {
		PairingRegistry r = newRegistry();
		assertEquals(RegisterResult.BAD_REQUEST, r.register(S1, "fritz.box",        "1.2.3.4"));
		assertEquals(RegisterResult.BAD_REQUEST, r.register(S1, "300.1.1.1",        "1.2.3.4"));
		assertEquals(RegisterResult.BAD_REQUEST, r.register(S1, "1.2.3",            "1.2.3.4"));
		assertEquals(RegisterResult.BAD_REQUEST, r.register(S1, "::1",              "1.2.3.4"));
		assertEquals(RegisterResult.BAD_REQUEST, r.register(S1, "192.168.178.42 ",  "1.2.3.4"));
	}

	@Test
	void entryExpiresAfterTtl() {
		PairingRegistry r = newRegistry();
		assertEquals(RegisterResult.OK, r.register(S1, IP1, "1.2.3.4"));
		assertEquals(IP1, r.lookup(S1));

		_now.addAndGet(PairingRegistry.TTL_MS - 1);
		assertEquals(IP1, r.lookup(S1), "still alive 1 ms before TTL");

		_now.addAndGet(2);
		assertNull(r.lookup(S1), "expired after TTL elapsed");
		// Lazy-cleanup must drop the entry from the underlying map too.
		assertEquals(0, r.size());
	}

	@Test
	void reregisterRefreshesTtl() {
		PairingRegistry r = newRegistry();
		assertEquals(RegisterResult.OK, r.register(S1, IP1, "1.2.3.4"));

		_now.addAndGet(PairingRegistry.TTL_MS - 1_000);
		// Same secret, dongle re-posted (e.g. retry path).
		assertEquals(RegisterResult.OK, r.register(S1, IP2, "1.2.3.4"));

		// Walking past the original TTL must NOT expire the refreshed
		// entry.
		_now.addAndGet(2_000);
		assertEquals(IP2, r.lookup(S1));
	}

	@Test
	void perPublicIpRateLimit() {
		PairingRegistry r = newRegistry();
		String pubIp = "1.2.3.4";

		// Bucket starts full; we should be able to consume CAPACITY tokens.
		for (int i = 0; i < PairingRegistry.RATE_BUCKET_CAPACITY; i++) {
			String secret = String.format("%032x", i);
			assertEquals(RegisterResult.OK, r.register(secret, IP1, pubIp),
				"attempt " + i + " inside bucket capacity");
		}

		// Next one from the same IP is throttled.
		assertEquals(RegisterResult.RATE_LIMITED,
			r.register(String.format("%032x", 99), IP1, pubIp));

		// A different public IP has its own bucket.
		assertEquals(RegisterResult.OK,
			r.register(String.format("%032x", 99), IP1, "5.6.7.8"));

		// After enough time has passed the bucket has refilled.
		_now.addAndGet(PairingRegistry.RATE_REFILL_PERIOD_MS + 1);
		assertEquals(RegisterResult.OK,
			r.register(String.format("%032x", 100), IP1, pubIp));
	}

	@Test
	void mapFullRejectsNewKeysButAllowsRefresh() {
		// We can't fill 10 000 entries cheaply, so simulate by checking
		// the policy at a smaller threshold via reflection-free behaviour:
		// fill the map up to MAX_ENTRIES, then verify policy.
		PairingRegistry r = newRegistry();

		// Use a "big enough" but representative count — the assertion is
		// behavioural (refresh allowed, new key rejected at boundary).
		int n = PairingRegistry.MAX_ENTRIES;
		for (int i = 0; i < n; i++) {
			String s = String.format("%032x", i);
			// No rate-limiting for this test — pass null as publicIp.
			assertEquals(RegisterResult.OK, r.register(s, IP1, null),
				"prefilling at " + i);
		}
		assertEquals(n, r.size());

		// One more *new* key must be rejected.
		String overflow = "deadbeefdeadbeefdeadbeefdeadbeef";
		assertEquals(RegisterResult.MAP_FULL, r.register(overflow, IP1, null));
		assertNull(r.lookup(overflow));

		// But refreshing an EXISTING key still works (we don't want a full
		// map to lock out a dongle's retry path).
		String existing = String.format("%032x", 0);
		assertEquals(RegisterResult.OK, r.register(existing, IP2, null));
		assertEquals(IP2, r.lookup(existing));
	}

	@Test
	void sweepDropsExpiredEntries() {
		PairingRegistry r = newRegistry();
		assertEquals(RegisterResult.OK, r.register(S1, IP1, "1.2.3.4"));
		assertEquals(RegisterResult.OK, r.register(S2, IP2, "1.2.3.4"));
		assertEquals(2, r.size());

		_now.addAndGet(PairingRegistry.TTL_MS + 1);
		// Add a fresh entry AFTER the clock moved.
		String fresh = "11111111111111111111111111111111";
		assertEquals(RegisterResult.OK, r.register(fresh, IP1, "5.5.5.5"));

		r.sweep();
		assertEquals(1, r.size(), "only the post-jump entry survives");
		assertNotNull(r.lookup(fresh));
		assertNull(r.lookup(S1));
		assertNull(r.lookup(S2));
	}

	@Test
	void publicIpNullSkipsRateLimit() {
		// PairingRegistry tolerates null publicIp (test/internal usage)
		// and does no rate accounting in that case. Real servlet always
		// supplies a string.
		PairingRegistry r = newRegistry();
		for (int i = 0; i < PairingRegistry.RATE_BUCKET_CAPACITY * 3; i++) {
			String s = String.format("%032x", i);
			assertEquals(RegisterResult.OK, r.register(s, IP1, null));
		}
	}
}
