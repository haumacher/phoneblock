/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.diag;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

/**
 * Collapses the variable parts of a log message into stable placeholders so that
 * lines differing only in ids / numbers / dates share one <em>signature</em>.
 *
 * <p>This is a faithful Java port of the ordered normalization in
 * {@code phoneblock-tools/bin/pb-log-summary.sh} (the {@code -x} / non-collapsing
 * variant): that script is the canonical spec, and {@code TestLogNormalizer}
 * pins this port against script-derived oracle values. Keeping the two identical
 * means the offline overview and the persisted aggregates group the same way.</p>
 *
 * <p>The rules are applied in order — earlier ones consume the raw digits later
 * ones would otherwise mangle. The placeholder vocabulary is
 * {@code <ARG> <DATE> <IP> <UUID> <TOKEN> <HEX> <N>}. Note it deliberately does
 * <b>not</b> mask emails/hostnames; that is the {@link Scrubber}'s job (run
 * before this) so PII is gone from both the signature and the retained sample.</p>
 */
public final class LogNormalizer {

	private LogNormalizer() {
		// Static utility.
	}

	// A leading "[date] " tinylog prefix (when a whole record line is passed).
	private static final Pattern DATE_PREFIX = Pattern.compile("^\\[[^\\]]*\\] ");

	private static final Pattern SINGLE_QUOTED = Pattern.compile("'[^']*'");
	private static final Pattern DOUBLE_QUOTED = Pattern.compile("\"[^\"]*\"");

	// java.util.Date.toString(), e.g. "Mon Jul 06 20:09:22 CEST 2026".
	private static final Pattern JAVA_DATE = Pattern.compile(
			"\\b(?:Mon|Tue|Wed|Thu|Fri|Sat|Sun) "
			+ "(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec) +\\d+ "
			+ "\\d+:\\d+:\\d+ \\w+ \\d{4}\\b");

	// Bracketed IPv6 — matched before the numeric rules (its digit-free marker
	// [<IP>] must survive the later \d+ rule).
	private static final Pattern IPV6_BRACKET = Pattern.compile(
			"\\[(?:[0-9a-fA-F]{0,4}:){2,}[0-9a-fA-F]{0,4}\\]");

	private static final Pattern IPV4 = Pattern.compile(
			"\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b");

	private static final Pattern UUID = Pattern.compile(
			"\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b");

	// A random credential token: 12+ alphanumerics containing lower AND upper AND
	// a digit — the only tell of an opaque secret with no other structure.
	private static final Pattern TOKEN = Pattern.compile(
			"\\b(?=[A-Za-z0-9]{12,}\\b)(?=[A-Za-z0-9]*[a-z])(?=[A-Za-z0-9]*[A-Z])(?=[A-Za-z0-9]*\\d)[A-Za-z0-9]+\\b");

	// sha1 hash / device token: 8+ hex digits that include at least one letter
	// (pure-digit runs stay numbers, handled by the final rule).
	private static final Pattern HEX = Pattern.compile(
			"\\b(?=[0-9a-fA-F]{8,}\\b)[0-9a-fA-F]*[a-fA-F][0-9a-fA-F]*\\b");

	private static final Pattern NUMBER = Pattern.compile("\\d+");

	/**
	 * Normalizes one log line (with or without a leading {@code [date]} tinylog
	 * prefix) into its signature.
	 */
	public static String normalize(String line) {
		String s = DATE_PREFIX.matcher(line).replaceFirst("");
		s = SINGLE_QUOTED.matcher(s).replaceAll("'<ARG>'");
		s = DOUBLE_QUOTED.matcher(s).replaceAll("\"<ARG>\"");
		s = JAVA_DATE.matcher(s).replaceAll("<DATE>");
		s = IPV6_BRACKET.matcher(s).replaceAll("[<IP>]");
		s = IPV4.matcher(s).replaceAll("<IP>");
		s = UUID.matcher(s).replaceAll("<UUID>");
		s = TOKEN.matcher(s).replaceAll("<TOKEN>");
		s = HEX.matcher(s).replaceAll("<HEX>");
		s = NUMBER.matcher(s).replaceAll("<N>");
		return s;
	}

	/**
	 * A stable, URL-safe id for a {@code (source, signature)} pair: the SHA-1 hex
	 * of {@code source + '\n' + signature}. Computed by the reader so aggregate
	 * upserts key on it without a generated-key round trip.
	 */
	public static String sigId(String source, String signature) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-1");
			digest.update(source.getBytes(StandardCharsets.UTF_8));
			digest.update((byte) '\n');
			digest.update(signature.getBytes(StandardCharsets.UTF_8));
			byte[] hash = digest.digest();
			StringBuilder out = new StringBuilder(40);
			for (byte b : hash) {
				out.append(Character.forDigit((b >> 4) & 0xF, 16));
				out.append(Character.forDigit(b & 0xF, 16));
			}
			return out.toString();
		} catch (NoSuchAlgorithmException ex) {
			// SHA-1 is always available on a JRE.
			throw new IllegalStateException(ex);
		}
	}
}
