/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

/**
 * Projected exponentially-weighted moving average used by the confidence model
 * (epic #300).
 *
 * <p>Each EMA column is stored as
 * <pre>
 *     A = Σ wᵢ · exp((tᵢ − t0) / τ)
 * </pre>
 * — the sum of all increments to date, each projected forward to a fixed
 * reference epoch {@code t0}. This representation has three properties that
 * matter for storage and indexing:
 * <ul>
 * <li>Writes are pure additions: an event of weight {@code w} at time
 *     {@code now} is added as {@code w · exp((now − t0)/τ)} (computed in Java
 *     and passed as a bind parameter). No read of the existing value, no
 *     {@code EXP()} in SQL on the write path.</li>
 * <li>Decay is applied only at read time: the value at {@code now} is
 *     {@code A · exp(−(now − t0)/τ)}. Every row shares the same decay
 *     factor, so {@code ORDER BY A DESC} matches {@code ORDER BY decoded DESC}
 *     — a plain B-tree index gives correct, index-backed ranking.</li>
 * <li>Overflow horizon: {@code exp((now − t0)/τ)} reaches
 *     {@link Double#MAX_VALUE} around {@code 709 · τ}. For the shortest decay
 *     in use (Heat, τ ≈ 20 d) that is roughly forty years — comfortably above
 *     any time scale this database operates on. Rebasing {@code t0} is a
 *     one-line {@code UPDATE} if it ever becomes relevant.</li>
 * </ul>
 *
 * <h2>Constants must not change after first write</h2>
 *
 * Once a non-zero EMA value has been written to a database, {@link #T0_MILLIS}
 * and the half-lives below define the meaning of every stored value. Changing
 * any of them would silently re-interpret existing data. Decay tuning during
 * operation must therefore be done either by full re-write or by carrying the
 * old constants until the data has decayed away.
 */
public final class Ema {

	/**
	 * Reference epoch t0, in epoch milliseconds. Anchors the projected
	 * representation: each stored EMA cell holds Σ wᵢ · exp((tᵢ − t0)/τ).
	 *
	 * <p>Value: 2026-01-01T00:00:00Z. Chosen close to the introduction of the
	 * confidence model so that {@code (now − t0)} stays small for years to
	 * come and the projection factor {@code exp((now − t0)/τ)} stays in a
	 * comfortable double range across all half-lives in use.</p>
	 */
	public static final long T0_MILLIS = 1767225600000L;

	/**
	 * Heat half-life — "how active is this number right now?". Short memory:
	 * a number that stops being called fades from the top of the ranking
	 * within a couple of weeks.
	 */
	public static final long HEAT_HALF_LIFE_DAYS = 14;

	/**
	 * Classification half-life — "spam or legitimate?". Long memory: keeps a
	 * number reported a handful of times meaningfully classified for well
	 * over a year (50 % at 4 months, ~13 % at a year). Recycled-number
	 * victims are protected by {@code LEGIT_EVIDENCE} reports pulling
	 * confidence down within days, not by decay alone — see #300.
	 */
	public static final long CLASSIFICATION_HALF_LIFE_DAYS = 125;

	private static final double LN2 = 0.6931471805599453;

	private static final long MILLIS_PER_DAY = 86_400_000L;

	/**
	 * Heat time constant τ in milliseconds, derived from
	 * {@link #HEAT_HALF_LIFE_DAYS}. Pre-computed so the per-event hot path
	 * does no division.
	 */
	public static final double HEAT_TAU_MILLIS = tauMillis(HEAT_HALF_LIFE_DAYS);

	/**
	 * Classification time constant τ in milliseconds, derived from
	 * {@link #CLASSIFICATION_HALF_LIFE_DAYS}. Pre-computed so the per-event
	 * hot path does no division.
	 */
	public static final double CLASSIFICATION_TAU_MILLIS = tauMillis(CLASSIFICATION_HALF_LIFE_DAYS);

	private Ema() {
		// no instances
	}

	/**
	 * Increment to add to a projected-EMA column for an event of given weight
	 * at the given time.
	 *
	 * <p>Result: {@code weight · exp((now − t0) / τ)}. Computed in Java; pass
	 * as a bind parameter to the {@code UPDATE} statement.</p>
	 *
	 * @param weight     per-signal weight {@code wᵢ} (e.g. 1.0 for a direct
	 *                   vote, less for weaker signals).
	 * @param now        event time in epoch milliseconds.
	 * @param tauMillis  time constant τ of the EMA being incremented (e.g.
	 *                   {@link #HEAT_TAU_MILLIS}).
	 */
	public static double increment(double weight, long now, double tauMillis) {
		return weight * Math.exp((double) (now - T0_MILLIS) / tauMillis);
	}

	/**
	 * Decode a projected-EMA column to its current decayed value.
	 *
	 * <p>Result: {@code raw · exp(−(now − t0) / τ)}. Use this for any client
	 * surface or threshold comparison. <em>Ranking does not need it</em> —
	 * because every row shares the same decay factor at a given {@code now},
	 * {@code ORDER BY raw DESC} matches {@code ORDER BY decoded DESC}.</p>
	 */
	public static double decode(double raw, long now, double tauMillis) {
		return raw * Math.exp(-(double) (now - T0_MILLIS) / tauMillis);
	}

	/**
	 * Threshold value to compare a raw projected-EMA column against, so that
	 * the comparison matches a decoded-value threshold at {@code now}.
	 *
	 * <p>{@code raw ≥ projectedThreshold(t, now, τ) ⇔ decoded ≥ t} — useful
	 * for SQL predicates that must avoid {@code EXP()} per row (e.g.
	 * Heat-based archiving in #335).</p>
	 */
	public static double projectedThreshold(double decodedThreshold, long now, double tauMillis) {
		return decodedThreshold * Math.exp((double) (now - T0_MILLIS) / tauMillis);
	}

	/**
	 * Convert a half-life in days to a time constant τ in milliseconds.
	 *
	 * <p>Only intended for static-initializer use — every EMA τ in operation
	 * is materialised as a constant ({@link #HEAT_TAU_MILLIS},
	 * {@link #CLASSIFICATION_TAU_MILLIS}); on the hot path callers must use
	 * those constants, never re-compute τ from days.</p>
	 */
	private static double tauMillis(long halfLifeDays) {
		return halfLifeDays * (double) MILLIS_PER_DAY / LN2;
	}

}
