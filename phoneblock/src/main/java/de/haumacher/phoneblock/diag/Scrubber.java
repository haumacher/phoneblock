/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.diag;

import java.util.ArrayList;
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
 * <p>A <b>built-in</b> rule set is the always-on baseline — high-confidence PII
 * shapes (email, phone in a {@code sip:}/{@code tel:} URI, international
 * subscriber number). It deliberately does <b>not</b> blanket-mask bare digit
 * runs (that would eat HTTP status codes, SIP reason codes, uptimes and byte
 * counts). On top of the baseline, LIVE rows of {@code DIAG_SCRUB_RULE} are
 * layered so an agent can grow the anonymizer without a redeploy (see
 * {@code docs/plans/2026-07-11-diagnostics-framework-design.md}); the audit loop
 * (<code>POST /api/admin/diag/scrub/audit</code>) surfaces leaked shapes to add.</p>
 *
 * <p>The {@code applies_to} dimension splits the pass in two: rules tagged
 * {@code SIGNATURE} or {@code BOTH} shape the grouping key; rules tagged
 * {@code SAMPLE} or {@code BOTH} shape the retained text. The built-in rules are
 * all {@code BOTH}, so {@link #scrubForSignature} and {@link #scrubForSample}
 * coincide for a bare {@link #builtin()} scrubber.</p>
 */
public final class Scrubber {

	private record Rule(Pattern pattern, String replacement) {}

	// Order matters. The sip:/tel: rule runs first: a URI like
	// "sip:+4930123456@fritz.box" otherwise looks like an email to the email rule.
	private static final List<DiagScrubRule> BUILTIN = List.of(
		builtin("builtin-sip-tel-phone", "(?i)(sips?:|tel:)\\+?[0-9][0-9.\\-]*", "$1<phone>"),
		builtin("builtin-email", "[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}", "<email>"),
		// International subscriber number: '+' followed by 6+ digits, not trailed by
		// a letter/digit. The trailing guard keeps the dongle uptime "+606260s" (and
		// similar "+<n><unit>" tokens) from being read as a number. Short country
		// prefixes ("+49") and prefix wildcards ("+43*") are left alone on purpose.
		builtin("builtin-intl-phone", "\\+[0-9]{6,}(?![A-Za-z0-9])", "<phone>"));

	private final List<Rule> _signatureRules;
	private final List<Rule> _sampleRules;

	private Scrubber(List<DiagScrubRule> all) {
		_signatureRules = compile(all, DiagScrubRule.SIGNATURE);
		_sampleRules = compile(all, DiagScrubRule.SAMPLE);
	}

	/** The baseline scrubber: only the built-in high-confidence rules. */
	public static Scrubber builtin() {
		return new Scrubber(BUILTIN);
	}

	/**
	 * The baseline plus the given LIVE {@code DIAG_SCRUB_RULE} rows layered on top
	 * (built-ins first, so a DB rule can only add masking). Rows whose pattern does
	 * not compile are skipped.
	 */
	public static Scrubber withLiveRules(List<DiagScrubRule> live) {
		List<DiagScrubRule> all = new ArrayList<>(BUILTIN);
		if (live != null) {
			all.addAll(live);
		}
		return new Scrubber(all);
	}

	/** Masks the text that feeds the grouping key (SIGNATURE + BOTH rules). */
	public String scrubForSignature(String message) {
		return apply(_signatureRules, message);
	}

	/** Masks the text retained as a sample (SAMPLE + BOTH rules). */
	public String scrubForSample(String message) {
		return apply(_sampleRules, message);
	}

	/**
	 * Back-compat convenience: the built-in {@code BOTH} scrub. Equivalent to
	 * {@code builtin().scrubForSample(message)}.
	 */
	public static String scrub(String message) {
		return builtin().scrubForSample(message);
	}

	private static String apply(List<Rule> rules, String message) {
		String s = message;
		for (Rule rule : rules) {
			s = rule.pattern().matcher(s).replaceAll(rule.replacement());
		}
		return s;
	}

	private static List<Rule> compile(List<DiagScrubRule> all, String phase) {
		List<Rule> out = new ArrayList<>(all.size());
		for (DiagScrubRule r : all) {
			// A rule affects this phase when it targets it explicitly or BOTH.
			String appliesTo = r.getAppliesTo() == null ? DiagScrubRule.BOTH : r.getAppliesTo();
			boolean forThisPhase = DiagScrubRule.BOTH.equals(appliesTo) || phase.equals(appliesTo);
			if (!forThisPhase) {
				continue;
			}
			Pattern p = r.pattern();
			if (p == null) {
				continue; // Invalid pattern — skip (never breaks ingest).
			}
			out.add(new Rule(p, r.getReplacement() == null ? "" : r.getReplacement()));
		}
		return out;
	}

	private static DiagScrubRule builtin(String name, String pattern, String replacement) {
		DiagScrubRule r = new DiagScrubRule();
		r.setName(name);
		r.setPattern(pattern);
		r.setReplacement(replacement);
		r.setAppliesTo(DiagScrubRule.BOTH);
		r.setState(DiagScrubRule.LIVE);
		return r;
	}
}
