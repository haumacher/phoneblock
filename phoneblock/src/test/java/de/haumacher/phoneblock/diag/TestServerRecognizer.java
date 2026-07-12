/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.diag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Tests the second source: native server WARN/ERROR recognition.
 */
public class TestServerRecognizer {

	private final ServerRecognizer recognizer = new ServerRecognizer("node-1");

	private DiagEvent recognize(String logLine) {
		TinylogLine parsed = TinylogLine.parse(logLine);
		assertNotNull(parsed, "did not parse: " + logLine);
		return recognizer.recognize(parsed);
	}

	@Test
	public void testRecognizesServerWarn() {
		DiagEvent e = recognize(
			"[2026-07-10 14:01:12] WARN: [de.haumacher.phoneblock.mail.MailServiceImpl]: Sending failed for user 42");
		assertNotNull(e);
		assertEquals("SERVER", e.source());
		assertEquals("node-1", e.originId());
		assertNull(e.userId()); // server events never mail a user
		assertEquals("W", e.severity());
		assertEquals("MailServiceImpl", e.tag());
		assertEquals("MailServiceImpl: Sending failed for user 42", e.message());
	}

	@Test
	public void testIgnoresInfo() {
		assertNull(recognize(
			"[2026-07-09 19:23:02] INFO: [de.haumacher.phoneblock.ab.SipService]: Updated registration ab-1: 200 OK"));
	}

	@Test
	public void testIgnoresOwnDiagnosticsLines() {
		assertNull(recognize(
			"[2026-07-10 14:01:12] WARN: [de.haumacher.phoneblock.diag.DiagnosticsMatcher]: something"));
	}
}
