/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.analysis;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
	
	@Test
	public void testRealData() throws IOException {
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
		int[] numbers = {0};
		int[] wildcard = {0};
		tree.createBlockEntries((number, weight) -> {
			numbers[0]++;
			wildcard[0]+=number.endsWith("*") ? 1 : 0;
			System.out.println(number + " (" + weight + ")");
		});
		
		System.out.println("Input numbers: " + cnt);
		System.out.println("Output numbers: " + numbers[0]);
		System.out.println("Wildcards: " + wildcard[0]);
	}
	
}
