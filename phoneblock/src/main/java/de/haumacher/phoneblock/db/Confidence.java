/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

/**
 * Confidence estimator on the classification axis of epic #300.
 *
 * <p>Given two {@code SPAM_EVIDENCE} / {@code LEGIT_EVIDENCE} EMA values
 * (decayed to the request time), returns the Wilson lower bound on the
 * probability {@code p = S / (S + L)} that the number is spam, scaled to
 * 0–100.</p>
 *
 * <h2>Why a Wilson lower bound and not a plain ratio</h2>
 *
 * The total evidence mass {@code n = S + L} matters as much as the ratio:
 * <ul>
 * <li>{@code S = 2, L = 0} — looks like 100 % spam but there are only two
 *     reports. Could easily be accidental. The Wilson bound on n = 2 is
 *     ~34 %, properly weak.</li>
 * <li>{@code S = 1000, L = 0} — also 100 % spam ratio, but with huge mass.
 *     The Wilson bound is ~99 %, properly strong.</li>
 * <li>{@code S = 1000, L = 998} — net direction barely positive, lots of
 *     contention. The Wilson bound is ~49 %, properly low.</li>
 * </ul>
 *
 * <p>This is exactly the "accidental vote" / "disputed vote" handling
 * called for in epic #300 — the decay alone does not solve it, the evidence
 * mass does. See {@link #spamConfidence(double, double)}.</p>
 *
 * @see <a href=
 *      "https://en.wikipedia.org/wiki/Binomial_proportion_confidence_interval">Binomial
 *      proportion confidence interval</a> — the underlying statistics; this
 *      class uses the Wilson score interval (lower bound).
 */
public final class Confidence {

	/**
	 * z-score for a 95 % one-sided confidence interval. {@code 1.96^2 ≈ 3.8416}
	 * gives the Wilson term its characteristic shape.
	 */
	private static final double Z = 1.96;

	private static final double Z_SQ = Z * Z;

	private Confidence() {
		// no instances
	}

	/**
	 * Wilson lower bound on {@code p = spam / (spam + legit)}, expressed as
	 * a percentage 0–100 (rounded).
	 *
	 * <p>Inputs are the decoded EMA values (after applying
	 * {@link Ema#decode}) — the confidence model is mass-based on the
	 * decayed evidence at the request moment.</p>
	 *
	 * <p>Returns 0 when both inputs are 0 ("unknown") — there is no evidence
	 * either way, so confidence is bottomed out.</p>
	 */
	public static int spamConfidence(double spamEvidence, double legitEvidence) {
		double n = spamEvidence + legitEvidence;
		if (n <= 0.0) {
			return 0;
		}
		double p = spamEvidence / n;

		// Wilson score interval, lower bound (one-sided 95 %):
		//   LB = (p + z²/(2n) − z · sqrt(p(1−p)/n + z²/(4n²))) / (1 + z²/n)
		double centre = p + Z_SQ / (2.0 * n);
		double margin = Z * Math.sqrt((p * (1.0 - p) + Z_SQ / (4.0 * n)) / n);
		double denom = 1.0 + Z_SQ / n;
		double lb = (centre - margin) / denom;

		if (lb < 0.0) {
			lb = 0.0;
		} else if (lb > 1.0) {
			lb = 1.0;
		}
		return (int) Math.round(lb * 100.0);
	}

}
