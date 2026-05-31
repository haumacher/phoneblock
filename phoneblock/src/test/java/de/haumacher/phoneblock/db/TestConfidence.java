/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests for the Wilson-bound spam-confidence estimator used by the API
 * (epic #300 / issue #334).
 */
public class TestConfidence {

	@Test
	public void unknownNumberReadsZero() {
		assertEquals(0, Confidence.spamConfidence(0.0, 0.0));
	}

	@Test
	public void twoSpamVotesAreNotEnoughForHighConfidence() {
		// The "maybe accidental" case from #300 — pure spam ratio but tiny mass.
		// Wilson 95 % LB on n = 2, p = 1 is ≈ 34 %.
		int c = Confidence.spamConfidence(2.0, 0.0);
		assertTrue(c < 50, "Two-vote pure-spam case must read < 50 %, was " + c);
		assertTrue(c > 20, "But not implausibly low, was " + c);
	}

	@Test
	public void manySpamVotesReadHigh() {
		// 1000 spam, 0 legit — the LB approaches the ratio (1.0).
		int c = Confidence.spamConfidence(1000.0, 0.0);
		assertTrue(c > 99, "Massive pure-spam case must read > 99 %, was " + c);
	}

	@Test
	public void disputedNumberReadsLow() {
		// 1000 spam, 998 legit — net direction barely positive, lots of mass.
		// p ≈ 0.5005, LB sits just below 0.5 — properly low.
		int c = Confidence.spamConfidence(1000.0, 998.0);
		assertTrue(c < 53, "Disputed case must read low, was " + c);
		assertTrue(c > 47, "But around 50 %, was " + c);
	}

	@Test
	public void moreEvidenceTightensTheBound() {
		// Same pure-spam ratio, more mass → confidence monotonically grows.
		int c2 = Confidence.spamConfidence(2.0, 0.0);
		int c10 = Confidence.spamConfidence(10.0, 0.0);
		int c100 = Confidence.spamConfidence(100.0, 0.0);
		assertTrue(c2 < c10, c2 + " < " + c10);
		assertTrue(c10 < c100, c10 + " < " + c100);
	}

	@Test
	public void legitEvidenceLowersConfidence() {
		// Same spam mass, additional legit evidence drives confidence down.
		int pureSpam = Confidence.spamConfidence(10.0, 0.0);
		int withLegit = Confidence.spamConfidence(10.0, 5.0);
		assertTrue(withLegit < pureSpam, withLegit + " < " + pureSpam);
	}

	@Test
	public void outputBoundedTo0_100() {
		// Defensive: stress with degenerate inputs.
		assertEquals(0, Confidence.spamConfidence(0.0, 100.0));
		int allSpam = Confidence.spamConfidence(1_000_000.0, 0.0);
		assertTrue(allSpam <= 100);
		assertTrue(allSpam >= 99);
	}

}
