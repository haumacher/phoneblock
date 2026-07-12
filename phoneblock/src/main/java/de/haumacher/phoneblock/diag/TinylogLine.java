/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.diag;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One parsed head line of the server log in tinylog's configured format
 * {@code [{date}] {level}: [{class}]: {message}} (see
 * {@code phoneblock/src/main/java/tinylog.properties}).
 *
 * @param timestampMs event time in Unix millis, parsed from the {@code [date]}
 *                    field (interpreted in the JVM default zone, as tinylog wrote
 *                    it).
 * @param level       the log level, e.g. {@code "WARN"}.
 * @param className   the fully-qualified logger/class name.
 * @param message     the message body (a single line; continuation lines such as
 *                    stack traces are not parsed as head lines).
 */
public record TinylogLine(long timestampMs, String level, String className, String message) {

	private static final Pattern HEAD = Pattern.compile(
			"^\\[(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2})\\] (\\w+): \\[([^\\]]*)\\]: (.*)$");

	private static final DateTimeFormatter DATE =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private static final ZoneId ZONE = ZoneId.systemDefault();

	/**
	 * Parses one physical log line, or returns {@code null} when it is not a head
	 * line (blank line, a stack-trace continuation, or any other non-matching
	 * text).
	 */
	public static TinylogLine parse(String line) {
		Matcher m = HEAD.matcher(line);
		if (!m.matches()) {
			return null;
		}
		long ts;
		try {
			ts = LocalDateTime.parse(m.group(1), DATE).atZone(ZONE).toInstant().toEpochMilli();
		} catch (RuntimeException ex) {
			return null;
		}
		return new TinylogLine(ts, m.group(2), m.group(3), m.group(4));
	}
}
