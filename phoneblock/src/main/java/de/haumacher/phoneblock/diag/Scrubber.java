/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.diag;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Masks personally identifying data out of a log message before it is either
 * stored as a retained sample or turned into a signature.
 *
 * <p>Run <em>before</em> {@link LogNormalizer}: emails and hostnames carry no
 * digits, so the normalizer's {@code <N>} rule would miss them and they would
 * leak into both the grouping key and the sample. The scrubbed text is what we
 * keep in {@code DIAG_SAMPLE} and what we feed to the normalizer.</p>
 *
 * <p>Phase 1 ships a deliberately <b>conservative</b> built-in rule set — the
 * design (see {@code docs/plans/2026-07-11-diagnostics-framework-design.md})
 * makes scrub rules a growable, DB-backed set that an agent extends via an audit
 * loop; this class is the bootstrap and the seam for that. It masks only
 * high-confidence PII shapes and deliberately does <b>not</b> blanket-mask bare
 * digit runs (that would eat HTTP status codes, SIP reason codes, uptimes and
 * byte counts). Bare non-{@code +} numbers are therefore left in the sample for
 * now (they become {@code <N>} in the signature regardless) — closing that gap is
 * the job of the later scrub-rule/audit phase.</p>
 */
public final class Scrubber {

	private record Rule(Pattern pattern, String replacement) {}

	// Order matters. The sip:/tel: rule runs first: a URI like
	// "sip:+4930123456@fritz.box" otherwise looks like an email to the email rule.
	private static final List<Rule> RULES = List.of(
		// Phone number inside a sip:/sips:/tel: URI (keep the scheme).
		new Rule(Pattern.compile("(?i)(sips?:|tel:)\\+?[0-9][0-9.\\-]*"), "$1<phone>"),
		// Email address.
		new Rule(Pattern.compile("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}"), "<email>"),
		// International subscriber number: '+' followed by 6+ digits, not trailed by
		// a letter/digit. The trailing guard keeps the dongle uptime "+606260s" (and
		// similar "+<n><unit>" tokens) from being read as a number. Short country
		// prefixes ("+49") and prefix wildcards ("+43*") are left alone on purpose.
		new Rule(Pattern.compile("\\+[0-9]{6,}(?![A-Za-z0-9])"), "<phone>")
	);

	private Scrubber() {
		// Static utility (Phase 1). A later phase turns this into an instance
		// backed by DIAG_SCRUB_RULE.
	}

	/**
	 * Returns {@code message} with high-confidence PII masked to placeholders.
	 */
	public static String scrub(String message) {
		String s = message;
		for (Rule rule : RULES) {
			s = rule.pattern().matcher(s).replaceAll(rule.replacement());
		}
		return s;
	}
}
