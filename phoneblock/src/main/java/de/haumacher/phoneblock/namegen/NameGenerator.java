/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.namegen;

import java.io.BufferedReader;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generator for random nick name.
 */
public class NameGenerator {
	
	private static final List<String> ADJECTIVES = load("adjectives.txt");
	private static final List<String> NOUNS = load("nouns.txt"); 
	
	/**
	 * Generates a random nickname using the given source of randomness.
	 * 
	 * <p>
	 * You can expect to generate roughly 100.000.000 different nicknames composed of two different
	 * adjectives and one noun.
	 * </p>
	 */
	public static String generateName(Random rnd) {
		String adjective = lookup(rnd, ADJECTIVES);
		String adjective2 = lookup(rnd, ADJECTIVES);
		String animal = lookup(rnd, NOUNS);
		StringBuilder result = new StringBuilder();
		appendUppercase(adjective, result);
		if (!adjective2.equals(adjective)) {
			appendUppercase(adjective2, result);
		}
		appendUppercase(animal, result);
		return result.toString();
	}

	private static String lookup(Random rnd, List<String> list) {
		return list.get(rnd.nextInt(list.size()));
	}

	private static void appendUppercase(String adjective, StringBuilder result) {
		result.append(Character.toUpperCase(adjective.charAt(0)));
		result.append(adjective, 1, adjective.length());
	}
	
	private static List<String> load(String string) {
		List<String> result = new ArrayList<>();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(NameGenerator.class.getResourceAsStream(string), StandardCharsets.UTF_8))) {
			String line;
			while ((line = r.readLine()) != null) {
				String name = line.trim();
				if (name.isEmpty()) {
					continue;
				}
				result.add(name);
			}
		} catch (IOException ex) {
			throw new IOError(ex);
		}
		return result;
	} 

	/**
	 * Command-line tool for generating nicknames.
	 */
	public static void main(String[] args) {
		Random rnd = new Random();
		for (int n = 0; n < 20 ; n++) {
			System.out.println(generateName(rnd));
		}
	}
}
