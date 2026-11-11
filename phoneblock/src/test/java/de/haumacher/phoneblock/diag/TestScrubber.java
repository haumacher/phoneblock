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
		// A LIVE DB rule masking a Wi-Fi SSID, plus the built-in email rule.
		Scrubber scrubber = Scrubber.withLiveRules(List.of(
			rule("ssid", "(ssid=)\\S+", "$1<ssid>", DiagScrubRule.BOTH)));
		assertEquals("wifi: ssid=<ssid> mailed <email>",
			scrubber.scrubForSample("wifi: ssid=HomeNet mailed a@b.de"));
		assertEquals("wifi: ssid=<ssid> mailed <email>",
			scrubber.scrubForSignature("wifi: ssid=HomeNet mailed a@b.de"));
	}

	@Test
	public void testMacMasked() {
		assertEquals("wifi: link watchdog: Wi-Fi signal below -80 dBm for 10 min (BSSID <mac>, RSSI -87 dBm)",
			Scrubber.scrub("wifi: link watchdog: Wi-Fi signal below -80 dBm for 10 min (BSSID 02:4e:6d:b4:e6:a9, RSSI -87 dBm)"));
	}

	@Test
	public void testMacRuleSparesLongerColonChains() {
		// An IPv6 address or a longer hex chain is not a MAC.
		assertEquals("ip fe80:0:0:0:12:34:56:78 up",
			Scrubber.scrub("ip fe80:0:0:0:12:34:56:78 up"));
		assertEquals("time 12:34:56 ok", Scrubber.scrub("time 12:34:56 ok"));
	}

	@Test
	public void testHostKeepsRegistrableDomain() {
		assertEquals("sip_transport: TLS connect to <host>.t-ipnet.de:5061 failed (rc=-1)",
			Scrubber.scrub("sip_transport: TLS connect to kln9-l1-mav-pc-ra-3.edns.t-ipnet.de:5061 failed (rc=-1)"));
		assertEquals("esp-tls: couldn't get hostname for :<host>.t-ipnet.de: getaddrinfo() returns 202",
			Scrubber.scrub("esp-tls: couldn't get hostname for :ber1-l1-mav-pc-rb-2.edns.t-ipnet.de: getaddrinfo() returns 202"));
		assertEquals("mail: connect to <host>.example.org:587 failed",
			Scrubber.scrub("mail: connect to smtp.example.org:587 failed"));
		assertEquals("mail: connect to <host>.example.de failed.",
			Scrubber.scrub("mail: connect to imap.mail.example.de failed."));
	}

	@Test
	public void testDifferentCustomerHostsShareOneSignature() {
		assertEquals(
			LogNormalizer.normalize(Scrubber.scrub("sip: transport open failed (tls stg1-l1-mav-pc-ra-4.edns.t-ipnet.de:5061)")),
			LogNormalizer.normalize(Scrubber.scrub("sip: transport open failed (tls dtm2-l1-mav-pc-rb-9.edns.t-ipnet.de:5061)")));
	}

	@Test
	public void testOwnHostsKept() {
		assertEquals("esp-tls: couldn't get hostname for :cdn1.phoneblock.net: getaddrinfo() returns 202",
			Scrubber.scrub("esp-tls: couldn't get hostname for :cdn1.phoneblock.net: getaddrinfo() returns 202"));
		assertEquals("fwup: open(https://cdn.phoneblock.net/dongle/firmware/stable/ota-esp32.json): ESP_ERR_HTTP_CONNECT",
			Scrubber.scrub("fwup: open(https://cdn.phoneblock.net/dongle/firmware/stable/ota-esp32.json): ESP_ERR_HTTP_CONNECT"));
	}

	@Test
	public void testUrlHostMaskedButPathKept() {
		assertEquals("api: GET https://<host>.example.com/a/b.c.json failed",
			Scrubber.scrub("api: GET https://www.foo.example.com/a/b.c.json failed"));
	}

	@Test
	public void testFileNamesVersionsAndIpsKept() {
		String[] kept = {
			"i18nsync: ui download https://cdn.phoneblock.net/dongle/firmware/1.6.0/i18n/ui/lang-de.json: HTTP -1",
			"i18nsync: announcement sync: wrote announcement-de.alaw",
			"sip_transport: DNS lookup of fritz.box failed: 202",
			"sip_transport: DNS lookup of t-online.de failed: 202",
			"sip: registrar 192.168.178.1:5060 unreachable",
			"ota: running 2.0.1 from ota_0",
			"java.lang.IllegalStateException: boom",
			"httpd_uri: Method 'GET' not allowed for URI '/app.config.json'",
			"scan: probe config.php.bak",
			"at de.haumacher.phoneblock.diag.Scrubber.apply(Scrubber.java:96)",
			"Logger de.haumacher.phoneblock.diag started",
		};
		for (String line : kept) {
			assertEquals(line, Scrubber.scrub(line));
		}
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
