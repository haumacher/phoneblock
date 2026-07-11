/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.diag;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Tests the conservative built-in {@link Scrubber} rule set.
 */
public class TestScrubber {

	@Test
	public void testEmailMasked() {
		assertEquals("status mail to <email> failed",
			Scrubber.scrub("status mail to gerhard@muehlenbeck.bayern failed"));
	}

	@Test
	public void testInternationalNumberMasked() {
		assertEquals("rate: HTTP 400 for <phone>",
			Scrubber.scrub("rate: HTTP 400 for +69874088010"));
	}

	@Test
	public void testSipUriNumberMasked() {
		assertEquals("sip: contact sip:<phone>@fritz.box lost",
			Scrubber.scrub("sip: contact sip:+4930123456@fritz.box lost"));
	}

	@Test
	public void testStatusCodesAndUptimeKept() {
		// Bare numbers that are NOT PII must survive (status codes, uptime, reason).
		assertEquals("E +606260s sip: REGISTER rejected: 400",
			Scrubber.scrub("E +606260s sip: REGISTER rejected: 400"));
		assertEquals("wifi: disconnected (reason 15)",
			Scrubber.scrub("wifi: disconnected (reason 15)"));
	}

	@Test
	public void testShortPrefixAndWildcardKept() {
		// Country prefixes and prefix wildcards are not subscriber numbers.
		assertEquals("sync: rate failed for +43*, keeping in Fritz!Box",
			Scrubber.scrub("sync: rate failed for +43*, keeping in Fritz!Box"));
	}
}
