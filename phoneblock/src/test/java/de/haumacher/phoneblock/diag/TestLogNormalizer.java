/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.diag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

/**
 * Pins {@link LogNormalizer} against {@code phoneblock-tools/bin/pb-log-summary.sh}.
 *
 * <p>The expected values were produced by running the script itself in {@code -x}
 * (exact, non-collapsing) mode over the input lines:</p>
 * <pre>
 *   pb-log-summary.sh -x -n 0 -l 'ERROR|WARN|INFO' -f &lt;these lines&gt;
 * </pre>
 * so this test fails if the Java port ever diverges from the canonical spec.
 */
public class TestLogNormalizer {

	/** input → the script's normalized grouping key for that line. */
	private static final String[][] ORACLE = {
		{
			"[2026-07-10 14:27:46] WARN: [de.haumacher.phoneblock.dongle.logreport.LogReportServlet]: Dongle error [user=ee4350e3-3195-4634-9734-35fd01aea986, agent=PhoneBlock-Dongle/1.4.0 (8fd98bad-afa8-4b1d-a891-8a2de09fd6d0)]: E +606260s sip: re-REGISTER failed: binding expired: REGISTER: no response from registrar (timeout/transport) — retry in 30 s",
			"WARN: [de.haumacher.phoneblock.dongle.logreport.LogReportServlet]: Dongle error [user=<UUID>, agent=PhoneBlock-Dongle/<N>.<N>.<N> (<UUID>)]: E +<N>s sip: re-REGISTER failed: binding expired: REGISTER: no response from registrar (timeout/transport) — retry in <N> s",
		},
		{
			"[2026-07-09 19:23:02] INFO: [de.haumacher.phoneblock.ab.SipService]: Updated registration ab-0542872467422025: 200 OK",
			"INFO: [de.haumacher.phoneblock.ab.SipService]: Updated registration ab-<N>: <N> OK",
		},
		{
			"[2026-07-09 19:23:05] INFO: [de.haumacher.phoneblock.app.LoginFilter]: Accepted bearer token pbt_CKpOFoIBFRjt...(pbt_CKpOFoIBFRjtzCv5QXnxBqHydbFT2gU) for user 10026.",
			"INFO: [de.haumacher.phoneblock.app.LoginFilter]: Accepted bearer token pbt_CKpOFoIBFRjt...(pbt_CKpOFoIBFRjtzCv<N>QXnxBqHydbFT<N>gU) for user <N>.",
		},
		{
			"[2026-07-10 13:33:14] INFO: [de.haumacher.phoneblock.app.LoginFilter]: Accepted login token for user ea299d87-fffd-499b-b0e2-e41941427922 accessing '/dongle'.",
			"INFO: [de.haumacher.phoneblock.app.LoginFilter]: Accepted login token for user <UUID> accessing '<ARG>'.",
		},
		{
			"[2026-07-09 19:23:04] INFO: [de.haumacher.phoneblock.app.api.BlocklistServlet]: Sending blocklist update (since 56, minVotes 10) to user '8fab9f64-95a9-40d9-ba9a-3673de49c97b' (agent 'PhoneBlockMobile/1.3.1')",
			"INFO: [de.haumacher.phoneblock.app.api.BlocklistServlet]: Sending blocklist update (since <N>, minVotes <N>) to user '<ARG>' (agent '<ARG>')",
		},
		{
			"[2026-07-10 14:01:12] INFO: [de.haumacher.phoneblock.mail.MailServiceImpl]: Firmware updated Mon Jul 06 20:09:22 CEST 2026 from 192.168.1.42 hash a1b2c3d4e5f6",
			"INFO: [de.haumacher.phoneblock.mail.MailServiceImpl]: Firmware updated <DATE> from <IP> hash <HEX>",
		},
	};

	@Test
	public void testParityWithScript() {
		for (String[] pair : ORACLE) {
			assertEquals(pair[1], LogNormalizer.normalize(pair[0]),
				"normalization diverged from pb-log-summary.sh for: " + pair[0]);
		}
	}

	@Test
	public void testNumbersAndUptime() {
		assertEquals("sip: REGISTER rejected: <N>",
			LogNormalizer.normalize("sip: REGISTER rejected: 400"));
		assertEquals("wifi: disconnected (reason <N>)",
			LogNormalizer.normalize("wifi: disconnected (reason 15)"));
	}

	@Test
	public void testSigIdStableAndDistinct() {
		String sig = "sip: REGISTER rejected: <N>";
		assertEquals(LogNormalizer.sigId("DONGLE", sig), LogNormalizer.sigId("DONGLE", sig));
		assertEquals(40, LogNormalizer.sigId("DONGLE", sig).length());
		// Same signature under a different source is a different row.
		assertNotEquals(LogNormalizer.sigId("DONGLE", sig), LogNormalizer.sigId("SERVER", sig));
	}
}
