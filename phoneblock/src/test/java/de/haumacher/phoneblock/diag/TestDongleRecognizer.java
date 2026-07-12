/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.diag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

/**
 * Tests parsing of real dongle error lines into {@link DiagEvent}s.
 */
public class TestDongleRecognizer {

	private final DongleRecognizer recognizer = new DongleRecognizer();

	private DiagEvent recognize(String logLine) {
		TinylogLine parsed = TinylogLine.parse(logLine);
		assertNotNull(parsed, "log line did not parse: " + logLine);
		return recognizer.recognize(parsed);
	}

	@Test
	public void testParsesDongleError() {
		DiagEvent e = recognize(
			"[2026-07-10 14:27:46] WARN: [de.haumacher.phoneblock.dongle.logreport.LogReportServlet]: "
			+ "Dongle error [user=ee4350e3-3195-4634-9734-35fd01aea986, agent=PhoneBlock-Dongle/1.4.0 "
			+ "(8fd98bad-afa8-4b1d-a891-8a2de09fd6d0)]: E +606260s sip: re-REGISTER failed: binding expired");

		assertNotNull(e);
		assertEquals("DONGLE", e.source());
		assertEquals("8fd98bad-afa8-4b1d-a891-8a2de09fd6d0", e.originId());
		assertEquals("ee4350e3-3195-4634-9734-35fd01aea986", e.userId());
		assertEquals("E", e.severity());
		assertEquals(Long.valueOf(606260), e.uptimeS());
		assertEquals("sip", e.tag());
		assertEquals("sip: re-REGISTER failed: binding expired", e.message());
	}

	@Test
	public void testIgnoresNonDongleLine() {
		TinylogLine parsed = TinylogLine.parse(
			"[2026-07-09 19:23:02] INFO: [de.haumacher.phoneblock.ab.SipService]: Updated registration ab-0542872467422025: 200 OK");
		assertNotNull(parsed);
		assertNull(recognizer.recognize(parsed));
	}

	@Test
	public void testWarningSeverity() {
		DiagEvent e = recognize(
			"[2026-07-10 14:27:46] WARN: [de.haumacher.phoneblock.dongle.logreport.LogReportServlet]: "
			+ "Dongle error [user=u1, agent=PhoneBlock-Dongle/1.4.1 (abc12345-0000-0000-0000-000000000000)]: "
			+ "W +12s wifi: disconnected (reason 15)");
		assertEquals("W", e.severity());
		assertEquals("wifi", e.tag());
		assertEquals("wifi: disconnected (reason 15)", e.message());
	}
}
