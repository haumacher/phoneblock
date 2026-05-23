/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests for the projected-EMA primitive used by the confidence model
 * (epic #300).
 */
public class TestEma {

	private static final long MS_PER_DAY = 86_400_000L;

	@Test
	public void roundTripAtEpochIsIdentity() {
		// At t = t0 the projection factor exp(0) = 1: the stored value equals
		// the decoded value.
		double raw = Ema.increment(2.5, Ema.T0_MILLIS, Ema.HEAT_HALF_LIFE_DAYS);
		assertEquals(2.5, raw, 1e-12);
		assertEquals(2.5, Ema.decode(raw, Ema.T0_MILLIS, Ema.HEAT_HALF_LIFE_DAYS), 1e-12);
	}

	@Test
	public void incrementThenDecodeAtSameTimeReturnsWeight() {
		// Writing at now and decoding at the same now must give back the weight
		// unchanged — the exp() factors cancel.
		long now = Ema.T0_MILLIS + 30 * MS_PER_DAY;
		double raw = Ema.increment(1.0, now, Ema.HEAT_HALF_LIFE_DAYS);
		assertEquals(1.0, Ema.decode(raw, now, Ema.HEAT_HALF_LIFE_DAYS), 1e-12);
	}

	@Test
	public void decayHalvesAtOneHalfLife() {
		// A single increment at t0, decoded one half-life later, must be 0.5.
		double raw = Ema.increment(1.0, Ema.T0_MILLIS, Ema.HEAT_HALF_LIFE_DAYS);
		long later = Ema.T0_MILLIS + Ema.HEAT_HALF_LIFE_DAYS * MS_PER_DAY;
		assertEquals(0.5, Ema.decode(raw, later, Ema.HEAT_HALF_LIFE_DAYS), 1e-12);
	}

	@Test
	public void decayQuartersAtTwoHalfLives() {
		double raw = Ema.increment(1.0, Ema.T0_MILLIS, Ema.HEAT_HALF_LIFE_DAYS);
		long later = Ema.T0_MILLIS + 2 * Ema.HEAT_HALF_LIFE_DAYS * MS_PER_DAY;
		assertEquals(0.25, Ema.decode(raw, later, Ema.HEAT_HALF_LIFE_DAYS), 1e-12);
	}

	@Test
	public void additivityMatchesContinuousRefresh() {
		// Adding two events in projected space matches the analytic sum.
		long t1 = Ema.T0_MILLIS + 5 * MS_PER_DAY;
		long t2 = Ema.T0_MILLIS + 20 * MS_PER_DAY;
		double raw = Ema.increment(1.0, t1, Ema.HEAT_HALF_LIFE_DAYS)
			+ Ema.increment(2.0, t2, Ema.HEAT_HALF_LIFE_DAYS);

		// Decoded at t2: first event has decayed for (t2 − t1) = 15 days, second
		// is fresh. Heat half-life = 14 d ⇒ first event factor = 0.5^(15/14).
		long readAt = t2;
		double expected = Math.pow(0.5, 15.0 / 14.0) + 2.0;
		assertEquals(expected, Ema.decode(raw, readAt, Ema.HEAT_HALF_LIFE_DAYS), 1e-12);
	}

	@Test
	public void rankingPreservedWithoutDecode() {
		// Two events at the same time with different weights: the heavier one
		// must remain larger after arbitrary further decay. This is the
		// "ORDER BY raw DESC matches ORDER BY decoded DESC" invariant the
		// Heat index relies on.
		long now = Ema.T0_MILLIS + 7 * MS_PER_DAY;
		double rawSmall = Ema.increment(1.0, now, Ema.HEAT_HALF_LIFE_DAYS);
		double rawBig = Ema.increment(3.0, now, Ema.HEAT_HALF_LIFE_DAYS);
		assertTrue(rawBig > rawSmall);

		long later = now + 100 * MS_PER_DAY;
		assertTrue(Ema.decode(rawBig, later, Ema.HEAT_HALF_LIFE_DAYS)
			> Ema.decode(rawSmall, later, Ema.HEAT_HALF_LIFE_DAYS));
	}

	@Test
	public void projectedThresholdMatchesDecodedComparison() {
		// projectedThreshold(t) must split raw values the same way decoded
		// values are split by t.
		long now = Ema.T0_MILLIS + 50 * MS_PER_DAY;
		double raw = Ema.increment(1.0, Ema.T0_MILLIS, Ema.HEAT_HALF_LIFE_DAYS);
		double decoded = Ema.decode(raw, now, Ema.HEAT_HALF_LIFE_DAYS);

		// raw vs projectedThreshold must mirror decoded vs threshold for any t.
		assertTrue(raw >= Ema.projectedThreshold(decoded, now, Ema.HEAT_HALF_LIFE_DAYS) - 1e-9);
		assertTrue(raw <= Ema.projectedThreshold(decoded, now, Ema.HEAT_HALF_LIFE_DAYS) + 1e-9);

		double tighter = decoded * 1.0001;
		assertTrue(raw < Ema.projectedThreshold(tighter, now, Ema.HEAT_HALF_LIFE_DAYS));

		double looser = decoded * 0.9999;
		assertTrue(raw > Ema.projectedThreshold(looser, now, Ema.HEAT_HALF_LIFE_DAYS));
	}

	@Test
	public void heatAndClassificationDecaysAreIndependent() {
		// Same event at t0, decoded one Heat half-life later: Heat reads 0.5,
		// Classification (much longer half-life) reads barely below 1.
		double rawHeat = Ema.increment(1.0, Ema.T0_MILLIS, Ema.HEAT_HALF_LIFE_DAYS);
		double rawClass = Ema.increment(1.0, Ema.T0_MILLIS, Ema.CLASSIFICATION_HALF_LIFE_DAYS);
		long later = Ema.T0_MILLIS + Ema.HEAT_HALF_LIFE_DAYS * MS_PER_DAY;
		assertEquals(0.5, Ema.decode(rawHeat, later, Ema.HEAT_HALF_LIFE_DAYS), 1e-12);
		// Classification value 14 d in: 0.5^(14/125) ≈ 0.9258.
		assertEquals(Math.pow(0.5, 14.0 / 125.0),
			Ema.decode(rawClass, later, Ema.CLASSIFICATION_HALF_LIFE_DAYS), 1e-12);
	}

}
