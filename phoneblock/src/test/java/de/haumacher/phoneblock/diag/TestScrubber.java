/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.diag;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests the conservative built-in {@link Scrubber} rule set and the LIVE
 * {@code DIAG_SCRUB_RULE} rows layered on top.
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

	@Test
	public void testLiveRuleLayeredOnBuiltins() {
		// A LIVE DB rule masking a MAC address, plus the built-in email rule.
		Scrubber scrubber = Scrubber.withLiveRules(List.of(
			rule("mac", "\\b([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}\\b", "<mac>", DiagScrubRule.BOTH)));
		assertEquals("wifi: <mac> mailed <email>",
			scrubber.scrubForSample("wifi: 00:11:22:33:44:55 mailed a@b.de"));
		assertEquals("wifi: <mac> mailed <email>",
			scrubber.scrubForSignature("wifi: 00:11:22:33:44:55 mailed a@b.de"));
	}

	@Test
	public void testSampleOnlyRuleDoesNotAffectSignature() {
		// A SAMPLE-scoped rule masks the retained text but leaves the grouping key.
		Scrubber scrubber = Scrubber.withLiveRules(List.of(
			rule("acct", "account \\d+", "account <n>", DiagScrubRule.SAMPLE)));
		assertEquals("api: account <n> blocked", scrubber.scrubForSample("api: account 987654 blocked"));
		assertEquals("api: account 987654 blocked", scrubber.scrubForSignature("api: account 987654 blocked"));
	}

	@Test
	public void testSignatureOnlyRuleDoesNotAffectSample() {
		Scrubber scrubber = Scrubber.withLiveRules(List.of(
			rule("host", "host=[a-z.]+", "host=<h>", DiagScrubRule.SIGNATURE)));
		assertEquals("dns: host=<h> failed", scrubber.scrubForSignature("dns: host=fritz.box failed"));
		assertEquals("dns: host=fritz.box failed", scrubber.scrubForSample("dns: host=fritz.box failed"));
	}

	@Test
	public void testInvalidPatternSkippedNotThrown() {
		// A malformed DB pattern must never break ingest — it is simply ignored.
		Scrubber scrubber = Scrubber.withLiveRules(List.of(
			rule("bad", "([unterminated", "<x>", DiagScrubRule.BOTH)));
		assertEquals("still masks <email>", scrubber.scrubForSample("still masks a@b.de"));
	}

	private static DiagScrubRule rule(String name, String pattern, String replacement, String appliesTo) {
		DiagScrubRule r = new DiagScrubRule();
		r.setName(name);
		r.setPattern(pattern);
		r.setReplacement(replacement);
		r.setAppliesTo(appliesTo);
		r.setState(DiagScrubRule.LIVE);
		return r;
	}
}
