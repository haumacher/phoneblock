/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.diag;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recognizes the dongle error lines that
 * {@code de.haumacher.phoneblock.dongle.logreport.LogReportServlet} writes to the
 * server log:
 *
 * <pre>
 *   Dongle error [user=&lt;U&gt;, agent=&lt;A&gt;]: &lt;E|W&gt; +&lt;uptime&gt;s &lt;tag&gt;: &lt;msg&gt;
 * </pre>
 *
 * <p>{@code originId} is the stable per-device id carried in the {@code agent}
 * User-Agent ({@code PhoneBlock-Dongle/<ver> (<uuid>)}); {@code userId} is the
 * account the token authenticated as.</p>
 */
public class DongleRecognizer implements LineRecognizer {

	/** The {@code source} value for dongle events. */
	public static final String SOURCE = "DONGLE";

	// user has no comma; agent has no ']'; the rest is the payload.
	private static final Pattern ENVELOPE = Pattern.compile(
			"^Dongle error \\[user=([^,]*), agent=([^\\]]*)\\]: (.*)$");

	// "<E|W> +<uptime>s <rest>", rest = "<tag>: <msg>".
	private static final Pattern PAYLOAD = Pattern.compile("^([EW])\\s+\\+(\\d+)s\\s+(.*)$");

	// The stable device id in "... (<uuid>)".
	private static final Pattern DEVICE_ID = Pattern.compile("\\(([0-9a-fA-F-]{8,})\\)\\s*$");

	@Override
	public DiagEvent recognize(TinylogLine line) {
		if (!line.className().endsWith("LogReportServlet")) {
			return null;
		}
		Matcher env = ENVELOPE.matcher(line.message());
		if (!env.matches()) {
			return null;
		}

		String userId = blankToNull(env.group(1).strip());
		String agent = env.group(2).strip();
		String payload = env.group(3).strip();

		String originId = deviceId(agent);

		String severity;
		Long uptimeS;
		String message;
		Matcher pl = PAYLOAD.matcher(payload);
		if (pl.matches()) {
			severity = pl.group(1);
			uptimeS = Long.valueOf(pl.group(2));
			message = pl.group(3).strip();
		} else {
			// Not the usual envelope — fall back to the tinylog level, keep the
			// whole payload as the message so it still groups.
			severity = "WARN".equals(line.level()) ? "W" : "E";
			uptimeS = null;
			message = payload;
		}

		String tag = tagOf(message);
		return new DiagEvent(SOURCE, originId, userId, severity, uptimeS, tag, message, line.timestampMs());
	}

	private static String deviceId(String agent) {
		Matcher m = DEVICE_ID.matcher(agent);
		if (m.find()) {
			return m.group(1);
		}
		return agent.isEmpty() ? "unknown" : agent;
	}

	private static String tagOf(String message) {
		int colon = message.indexOf(':');
		if (colon <= 0) {
			return "";
		}
		String tag = message.substring(0, colon).strip();
		// A tag is a short component name; anything longer is really free text
		// without a tag prefix.
		return tag.length() <= 32 ? tag : "";
	}

	private static String blankToNull(String s) {
		return s.isEmpty() ? null : s;
	}
}
