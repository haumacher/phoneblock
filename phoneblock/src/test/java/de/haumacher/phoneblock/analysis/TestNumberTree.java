/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Test of {@link NumberTree}.
 */
class TestNumberTree {
	
	@Test
	void testLeve1Wildcard() {
		NumberTree tree = new NumberTree();
		tree.insert("0201234567");
		tree.insert("0305200156399");
		tree.insert("0305200156332");
		tree.insert("0305200156327");
		tree.insert("0305200156329");
		tree.insert("0305200156328");
		tree.insert("0305200156330");
		tree.insert("0305200156331");
		tree.insert("0305200156325");
		tree.insert("0305200156324");
		tree.insert("0305200156333");
		tree.insert("0305200156322");
		tree.markWildcards();
		List<String> entries = tree.createBlockEntries();
		
		assertEquals(List.of("0201234567", "030520015632*", "030520015633*", "0305200156399"), entries);
	}

	@Test
	void testWhitelistBreaksWildcard() {
		NumberTree tree = new NumberTree();
		tree.insert("0100001");
		tree.insert("0100002");
		tree.insert("0100003");
		tree.insert("0100004", -10, 0);
		tree.insert("0100005");
		
		tree.insert("0200001");
		tree.insert("0200002");
		tree.insert("0200003");
		tree.insert("0200005");
		tree.markWildcards();
		List<String> entries = tree.createBlockEntries();
		
		assertEquals(List.of("0100001", "0100002", "0100003", "0100005", "020000*"), entries);
	}
	
	@Test
	void testWhitelistBreaksWildcardLevel2() {
		NumberTree tree = new NumberTree();
		tree.insert("01000015");
		tree.insert("01000016");
		tree.insert("01000017");
		tree.insert("01000027");
		tree.insert("01000028");
		tree.insert("01000029");
		tree.insert("0100003");
		tree.insert("01000041", -10, 0);
		tree.insert("01000057");
		tree.insert("01000058");
		tree.insert("01000059");
		tree.markWildcards();
		List<String> entries = tree.createBlockEntries();
		
		assertEquals(List.of("0100001*", "0100002*", "0100003", "0100005*"), entries);
	}
	
	@Test
	void testLeve2Wildcard() {
		NumberTree tree = new NumberTree();
		tree.insert("030123010");
		tree.insert("030123011");
		tree.insert("030123012");
		tree.insert("030123020");
		tree.insert("030123021");
		tree.insert("030123022");
		tree.insert("030123030");
		tree.insert("030123031");
		tree.insert("030123032");
		tree.markWildcards();
		List<String> entries = tree.createBlockEntries();
		
		assertEquals(List.of("0301230*"), entries);
	}
	
	@Test
	void testInternational() {
		NumberTree tree = new NumberTree();
		tree.insert("+4930123010");
		tree.insert("+4930123011");
		tree.insert("+4930123012");
		tree.insert("+4930123020");
		tree.insert("+4930123021");
		tree.insert("+4930123022");
		tree.insert("+4930123030");
		tree.insert("+4930123031");
		tree.insert("+4930123032");
		tree.insert("+4330123010");
		tree.insert("+4330123011");
		tree.insert("+4330123012");
		tree.insert("+4330123020");
		tree.insert("+4330123021");
		tree.insert("+4330123022");
		tree.insert("+4330123030");
		tree.insert("+4330123031");
		tree.insert("+4330123032");
		tree.markWildcards();
		List<String> entries = tree.createBlockEntries();
		
		assertEquals(List.of("+43301230*", "+49301230*"), entries);
	}
	
	@Test
	void testRealData() throws IOException {
		NumberTree tree = new NumberTree();

		int cnt = 0;
		try (BufferedReader in = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("SPAMREPORTS_202309301715.csv"), StandardCharsets.UTF_8))) {
			in.readLine();
			
			String line;
			while ((line = in.readLine()) != null) {
				if (line.isBlank()) {
					continue;
				}
				
				if (line.startsWith("\"") && line.endsWith("\"")) {
					String phone = line.substring(1, line.length() - 1);
					
					cnt ++;
					tree.insert(phone);
				}
			}
		}
		
		tree.markWildcards();
		
		List<NumberBlock> blocks = tree.createNumberBlocks(1, 300, "+49");
		int numbers = 0;
		int wildcard = 0;
		
		for (NumberBlock block : blocks) {
			System.out.println("## " + block.getBlockTitle());
			for (String number : block.getNumbers()) {
				System.out.println(number);
				
				numbers++;
				if (number.endsWith("*")) {
					wildcard++;
				}
			}
			
			System.out.println();
		}
		
		System.out.println("Input numbers: " + cnt);
		System.out.println("Output numbers: " + numbers);
		System.out.println("Wildcards: " + wildcard);
		System.out.println("Blocks: " + blocks.size());
		assertEquals(10049, cnt);
		assertEquals(266, wildcard);
		assertEquals(300, numbers);
		assertEquals(99, blocks.size());
	}

	// === Prefix-bucketing tests for createNumberBlocksByPrefix ===

	private static NumberTree treeWith(String... numbers) {
		NumberTree tree = new NumberTree();
		for (String n : numbers) {
			tree.insert(n);
		}
		return tree;
	}

	private static List<String> blockIds(List<NumberBlock> blocks) {
		List<String> ids = new ArrayList<>(blocks.size());
		for (NumberBlock b : blocks) {
			ids.add(b.getBlockId());
		}
		return ids;
	}

	/** Test 1: Determinismus — gleicher Input zweimal liefert byte-identische Bucket-Liste. */
	@Test
	void prefixBucketing_isDeterministic() {
		String[] sample = {
			"+491521010", "+491521011", "+491521012", "+491521013", "+491521014",
			"+491521015", "+491521016", "+491521017", "+491521018", "+491521019",
			"+491632000", "+491632001", "+493012345"
		};

		List<NumberBlock> first = treeWith(sample).createNumberBlocksByPrefix(1, 1000, "+49");
		List<NumberBlock> second = treeWith(sample).createNumberBlocksByPrefix(1, 1000, "+49");

		assertEquals(blockIds(first), blockIds(second));
		for (int i = 0; i < first.size(); i++) {
			assertEquals(first.get(i).getNumbers(), second.get(i).getNumbers());
		}
	}

	/** Test 2: Reihenfolge-Robustheit — permutierter Input liefert identische Buckets. */
	@Test
	void prefixBucketing_orderIndependent() {
		List<String> base = List.of(
			"+491521010", "+491521011", "+491521012",
			"+491632000", "+491632001",
			"+493012345"
		);

		List<NumberBlock> reference = treeWith(base.toArray(new String[0]))
			.createNumberBlocksByPrefix(1, 1000, "+49");

		Random rnd = new Random(42);
		for (int trial = 0; trial < 5; trial++) {
			List<String> permuted = new ArrayList<>(base);
			Collections.shuffle(permuted, rnd);
			List<NumberBlock> blocks = treeWith(permuted.toArray(new String[0]))
				.createNumberBlocksByPrefix(1, 1000, "+49");

			assertEquals(blockIds(reference), blockIds(blocks));
			for (int i = 0; i < reference.size(); i++) {
				assertEquals(reference.get(i).getNumbers(), blocks.get(i).getNumbers());
			}
		}
	}

	/** Test 3: 9er-Limit — kein Bucket > 9 Mitglieder (außer pathologisch identische Präfixe). */
	@Test
	void prefixBucketing_respectsNineLimit() {
		// 25 Nummern mit gemeinsamem 5er-Präfix, dann unterscheidende Stellen
		String[] sample = new String[25];
		for (int i = 0; i < 25; i++) {
			sample[i] = String.format("+49152%07d", i);
		}

		List<NumberBlock> blocks = treeWith(sample).createNumberBlocksByPrefix(1, 1000, "+49");
		for (NumberBlock b : blocks) {
			assertTrue(b.size() <= 9, "Bucket " + b.getBlockId() + " hat " + b.size() + " Mitglieder");
		}
	}

	/** Test 4: Vollständigkeit + Disjunktheit — jedes Eingabe-Element landet in genau einem Bucket. */
	@Test
	void prefixBucketing_completeAndDisjoint() {
		String[] sample = {
			"+491521010", "+491521011", "+491521012", "+491521013",
			"+491632000", "+491632001",
			"+493012345", "+493012346",
			"+18334567890"
		};

		List<NumberBlock> blocks = treeWith(sample).createNumberBlocksByPrefix(1, 1000, "+49");

		Set<String> collected = new HashSet<>();
		int total = 0;
		for (NumberBlock b : blocks) {
			for (String n : b.getNumbers()) {
				assertTrue(collected.add(n), "Nummer " + n + " ist in mehr als einem Bucket");
				total++;
			}
		}
		assertEquals(sample.length, total);
		for (String n : sample) {
			assertTrue(collected.contains(n), "Nummer " + n + " fehlt in den Buckets");
		}
	}

	/** Test 5: Bucket-ID = Präfix — jede Nummer im Bucket startet mit dem Bucket-ID-String. */
	@Test
	void prefixBucketing_blockIdIsPrefix() {
		String[] sample = {
			"+491521010", "+491521011", "+491521012",
			"+491632000", "+491632001",
			"+493012345"
		};

		List<NumberBlock> blocks = treeWith(sample).createNumberBlocksByPrefix(1, 1000, "+49");
		for (NumberBlock b : blocks) {
			String id = b.getBlockId();
			for (String n : b.getNumbers()) {
				assertTrue(n.startsWith(id) || n.equals(id),
					"Nummer " + n + " startet nicht mit Bucket-ID " + id);
			}
		}
	}

	/** Test 6: Lokale Stabilität — eine Nummer hinzufügen ändert ≤ 2 Bucket-IDs. */
	@Test
	void prefixBucketing_localStabilityOnAdd() {
		// Genug Nummern, dass der +491-Bucket auf depth=5 aufsplittet:
		// +4915xxx (5 numbers) und +4916xxx (5 numbers) bei depth=4 wären 10 Mitglieder
		// unter +491 → muss auf depth=5 splitten in +4915 (5 nums) und +4916 (5 nums).
		String[] base = {
			"+491521010", "+491521011", "+491521012", "+491521013", "+491521014",
			"+491632000", "+491632001", "+491632002", "+491632003", "+491632004",
			"+493012345"
		};
		List<NumberBlock> baseBlocks = treeWith(base).createNumberBlocksByPrefix(1, 1000, "+49");
		Set<String> baseIds = new HashSet<>(blockIds(baseBlocks));

		// In den +4915-Bereich eine sechste Nummer einfügen.
		String[] perturbed = new String[base.length + 1];
		System.arraycopy(base, 0, perturbed, 0, base.length);
		perturbed[base.length] = "+491521099";

		List<NumberBlock> perturbedBlocks = treeWith(perturbed).createNumberBlocksByPrefix(1, 1000, "+49");
		Set<String> perturbedIds = new HashSet<>(blockIds(perturbedBlocks));

		Set<String> removed = new HashSet<>(baseIds);
		removed.removeAll(perturbedIds);
		Set<String> added = new HashSet<>(perturbedIds);
		added.removeAll(baseIds);

		// Stabilität: höchstens 2 ID-Änderungen erwartet
		// (bei reinem Hinzufügen in den +4915-Bucket eigentlich 0; bei Splitting maximal 2).
		int idChurn = removed.size() + added.size();
		assertTrue(idChurn <= 2,
			"Mehr als 2 Bucket-ID-Änderungen: removed=" + removed + ", added=" + added);

		// Buckets außerhalb des +4915-Subtrees müssen byte-identisch bleiben.
		for (NumberBlock b : baseBlocks) {
			String id = b.getBlockId();
			if (id.startsWith("+4915") || "+4915".startsWith(id) || "+491".equals(id)) {
				// im oder über dem betroffenen Subtree — Änderung erlaubt
				continue;
			}
			NumberBlock match = perturbedBlocks.stream()
				.filter(p -> p.getBlockId().equals(id))
				.findFirst()
				.orElse(null);
			assertTrue(match != null, "Bucket " + id + " im ruhigen Bereich verschwunden");
			assertEquals(b.getNumbers(), match.getNumbers(),
				"Bucket " + id + " im ruhigen Bereich hat geänderten Inhalt");
		}
	}

}
