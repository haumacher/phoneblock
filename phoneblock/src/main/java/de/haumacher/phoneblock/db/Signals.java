/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

/**
 * Per-signal weights for the projected-EMA columns introduced by epic #300.
 *
 * <p>All values are tuning knobs — keep them in one place so future calibration
 * is a single-file change. The defaults here are deliberate starting points,
 * not measurements:</p>
 *
 * <ul>
 * <li>A direct vote (via {@code RateServlet}) is the reference: weight 1.0
 *     for Heat and 1.0 for the matching evidence column. Every other signal
 *     is calibrated against that baseline.</li>
 * <li>{@code /api/report-call} from a client device is also worth one Heat
 *     unit — a real incoming call observed on a user's line is at least as
 *     much "activity" as someone visiting the web form. Classification is
 *     <em>not</em> touched here; the wildcard-blocked-number case is the
 *     subject of issue #333.</li>
 * <li>Plain searches via {@code SearchServlet} are weak Heat — they reflect
 *     curiosity, not necessarily real call traffic.</li>
 * <li>An answer-bot call is a strong, machine-verified signal: the bot only
 *     picks up when the caller is already classified as spam, and the line
 *     observes the engagement at the SIP level. Both Heat and
 *     {@code SPAM_EVIDENCE} get healthy increments.</li>
 * <li>A submitted comment feeds Heat only — the user's classification stance
 *     is already captured by the rating the comment carries.</li>
 * </ul>
 *
 * <p>Aggregations (block-level Heat / evidence, issue #337) reuse the same
 * weights — every event is added flat at all three levels (number, /10, /100).</p>
 */
public final class Signals {

	/** Reference weight: a direct user vote drives Heat by one unit. */
	public static final double DIRECT_VOTE_HEAT_WEIGHT = 1.0;

	/** Reference weight: a direct user vote drives the matching evidence column by one unit. */
	public static final double DIRECT_VOTE_EVIDENCE_WEIGHT = 1.0;

	/** Client-side {@code /api/report-call}: real activity, weighted like a direct vote on the Heat axis. */
	public static final double REPORT_CALL_HEAT_WEIGHT = 1.0;

	/** Plain text search lookup: a weak Heat signal, no classification impact. */
	public static final double SEARCH_HEAT_WEIGHT = 0.2;

	/** Answer-bot picked up the call: a strong Heat signal. */
	public static final double ANSWERBOT_CALL_HEAT_WEIGHT = 2.0;

	/** Answer-bot picked up the call: machine-verified spam evidence. */
	public static final double ANSWERBOT_CALL_EVIDENCE_WEIGHT = 1.0;

	/** Comment submission: Heat only — the comment's rating already drives classification via the vote path. */
	public static final double COMMENT_HEAT_WEIGHT = 0.5;

	/**
	 * Implicit spam evidence weight for an unknown number that was blocked by
	 * a wildcard rule (#333). The server confirms the wildcard match itself
	 * — the client cannot fake it — so the report counts towards classification,
	 * but at half the weight of a direct user vote. Tunable knob, deliberate
	 * starting point per issue #300 / #333.
	 */
	public static final double IMPLICIT_VOTE_EVIDENCE_WEIGHT = 0.5;

	private Signals() {
		// no instances
	}

}
