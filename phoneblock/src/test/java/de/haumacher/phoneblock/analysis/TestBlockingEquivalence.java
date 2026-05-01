/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Test 7 — block-effect equivalence: the set of numbers blocked by the new
 * prefix-bucketing algorithm equals the set blocked by the legacy greedy
 * algorithm, for the same Top-K selection on a real-data fixture.
 *
 * <p>
 * "Blocked" means: the number appears literally in some bucket, or it is covered
 * by a wildcard entry in some bucket. Since both algorithms operate on the same
 * Top-K set produced by the tree walk, this test is the sanity check that the
 * new bucketing does not silently drop or duplicate entries.
 * </p>
 */
class TestBlockingEquivalence {

	private static final String FIXTURE = "blocklist-snapshot.csv";

	private static List<DBRow> loadFixture() throws IOException {
		List<DBRow> rows = new java.util.ArrayList<>();
		try (InputStream in = TestBlockingEquivalence.class.getResourceAsStream(FIXTURE);
		     BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
			r.readLine(); // header
			String line;
			while ((line = r.readLine()) != null) {
				if (line.isBlank()) continue;
				String[] parts = line.split(",");
				rows.add(new DBRow(parts[0], Integer.parseInt(parts[1]), Long.parseLong(parts[2])));
			}
		}
		return rows;
	}

	private static record DBRow(String phone, int votes, long lastActivity) {}

	private static NumberTree buildTree(List<DBRow> rows, long now, boolean wildcards) {
		NumberTree tree = new NumberTree();
		for (DBRow row : rows) {
			int ageDays = (int) ((now - row.lastActivity()) / 86_400_000L);
			tree.insert(row.phone(), row.votes(), ageDays);
		}
		if (wildcards) {
			tree.markWildcards();
		}
		return tree;
	}

	/** Set of literal numbers and wildcard markers across all blocks. */
	private static Set<String> entriesOf(List<NumberBlock> blocks) {
		Set<String> out = new HashSet<>();
		for (NumberBlock b : blocks) {
			out.addAll(b.getNumbers());
		}
		return out;
	}

	private void assertEquivalent(int minVotes, int maxEntries, String dialPrefix, boolean wildcards)
			throws IOException {
		List<DBRow> rows = loadFixture();
		long now = rows.stream().mapToLong(DBRow::lastActivity).max().orElseThrow();

		// The tree state (and thus the Top-K input) must be identical for both runs.
		// We build two trees from the same data so wildcard marking / weight info
		// stays in sync; the tree walk itself is read-only.
		NumberTree tree1 = buildTree(rows, now, wildcards);
		NumberTree tree2 = buildTree(rows, now, wildcards);

		List<NumberBlock> oldBlocks = tree1.createNumberBlocks(minVotes, maxEntries, dialPrefix);
		List<NumberBlock> newBlocks = tree2.createNumberBlocksByPrefix(minVotes, maxEntries, dialPrefix);

		Set<String> oldEntries = entriesOf(oldBlocks);
		Set<String> newEntries = entriesOf(newBlocks);

		assertFalse(oldEntries.isEmpty(), "old algorithm produced no entries — fixture too restrictive?");
		assertEquals(oldEntries, newEntries,
			"Set of blocked entries differs between legacy and prefix-bucketing algorithm "
				+ "(minVotes=" + minVotes + ", maxEntries=" + maxEntries
				+ ", dialPrefix=" + dialPrefix + ", wildcards=" + wildcards + ")");
	}

	@Test
	void defaultListType() throws IOException {
		// Default: minVotes=4, maxEntries=1000, +49, wildcards on
		assertEquivalent(4, 1000, "+49", true);
	}

	@Test
	void wildcardsOff() throws IOException {
		assertEquivalent(4, 1000, "+49", false);
	}

	@Test
	void smallMaxEntries() throws IOException {
		assertEquivalent(4, 200, "+49", true);
	}

	@Test
	void largeMaxEntries() throws IOException {
		assertEquivalent(4, 3000, "+49", true);
	}

	@Test
	void otherDialPrefix() throws IOException {
		// US dial prefix — different weight boost, hence different Top-K membership;
		// but legacy and prefix-bucketing must still agree on the entry set.
		assertEquivalent(4, 1000, "+1", true);
	}
}
