/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.analysis;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Test of {@link NumberTree}.
 */
@SuppressWarnings("javadoc")
public class TestNumberTree {
	
	@Test
	public void testLeve1Wildcard() {
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
		
		Assertions.assertEquals(Arrays.asList("0201234567", "030520015632*", "030520015633*", "0305200156399"), entries);
	}

	@Test
	public void testLeve2Wildcard() {
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
		
		Assertions.assertEquals(Arrays.asList("0301230*"), entries);
	}
	
}
