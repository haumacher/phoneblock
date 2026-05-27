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
 * <li>A reported call — whether from a user-mediated spam report after a
 *     call came through or from an automatic block (Fritz!Box, dongle, app,
 *     answer-bot) — is one call, one signal. Each report adds one Heat and
 *     one evidence unit. No per-source variants. Materialises a
 *     {@code NUMBERS} row for unknown callers; see {@code DB.recordCall}.</li>
 * <li>Plain searches via {@code SearchServlet} are weak Heat — they reflect
 *     curiosity, not necessarily real call traffic.</li>
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

	/** Reported call (any source): one Heat unit per call. */
	public static final double CALL_HEAT_WEIGHT = 1.0;

	/** Reported call (any source): one evidence unit per call — same axis as a direct vote. */
	public static final double CALL_EVIDENCE_WEIGHT = 1.0;

	/** Plain text search lookup: a weak Heat signal, no classification impact. */
	public static final double SEARCH_HEAT_WEIGHT = 0.2;

	/** Comment submission: Heat only — the comment's rating already drives classification via the vote path. */
	public static final double COMMENT_HEAT_WEIGHT = 0.5;

	private Signals() {
		// no instances
	}

}
