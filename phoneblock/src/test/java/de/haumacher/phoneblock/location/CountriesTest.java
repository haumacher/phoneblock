/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.location;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;

import org.junit.jupiter.api.Test;

import de.haumacher.phoneblock.location.model.Country;

/**
 * Test case for {@link Countries}.
 */
public class CountriesTest {

	/**
	 * Test that trunk prefixes are correctly loaded for different countries.
	 */
	@Test
	public void testTrunkPrefixes() {
		// Germany - standard trunk prefix "0"
		Country germany = Countries.get("DE");
		assertNotNull(germany, "Germany should be found");
		assertEquals(1, germany.getTrunkPrefixes().size(), "Germany should have 1 trunk prefix");
		assertEquals("0", germany.getTrunkPrefixes().get(0), "Germany trunk prefix should be 0");
		assertEquals("Germany", germany.getOfficialNameEn(), "Germany official name");

		// Italy - no trunk prefix in CSV (empty)
		Country italy = Countries.get("IT");
		assertNotNull(italy, "Italy should be found");
		assertTrue(italy.getTrunkPrefixes().isEmpty(), "Italy should have no trunk prefix");
		assertEquals("Italy", italy.getOfficialNameEn(), "Italy official name");

		// Russia - non-standard trunk prefix "8"
		Country russia = Countries.get("RU");
		assertNotNull(russia, "Russia should be found");
		assertEquals(1, russia.getTrunkPrefixes().size(), "Russia should have 1 trunk prefix");
		assertEquals("8", russia.getTrunkPrefixes().get(0), "Russia trunk prefix should be 8");
		assertEquals("Russian Federation", russia.getOfficialNameEn(), "Russia official name");

		// Greece - no trunk prefix in CSV (empty)
		Country greece = Countries.get("GR");
		assertNotNull(greece, "Greece should be found");
		assertTrue(greece.getTrunkPrefixes().isEmpty(), "Greece should have no trunk prefix");
		assertEquals("Greece", greece.getOfficialNameEn(), "Greece official name");
	}

	/**
	 * Test additional countries with non-standard trunk prefixes and international prefixes.
	 */
	@Test
	public void testNonStandardTrunkPrefixes() {
		// United States - trunk prefix "1", international prefix "011"
		Country usa = Countries.get("US");
		assertNotNull(usa, "USA should be found");
		assertEquals(Set.of("1"), Set.copyOf(usa.getTrunkPrefixes()), "USA trunk prefixes");
		assertEquals(Set.of("011"), Set.copyOf(usa.getInternationalPrefixes()), "USA international prefixes");

		// Canada - trunk prefix "1", international prefix "011"
		Country canada = Countries.get("CA");
		assertNotNull(canada, "Canada should be found");
		assertEquals(Set.of("1"), Set.copyOf(canada.getTrunkPrefixes()), "Canada trunk prefixes");
		assertEquals(Set.of("011"), Set.copyOf(canada.getInternationalPrefixes()), "Canada international prefixes");

		// Hungary - trunk prefix "06", international prefix "00"
		Country hungary = Countries.get("HU");
		assertNotNull(hungary, "Hungary should be found");
		assertEquals(Set.of("06"), Set.copyOf(hungary.getTrunkPrefixes()), "Hungary trunk prefixes");
		assertEquals(Set.of("00"), Set.copyOf(hungary.getInternationalPrefixes()), "Hungary international prefixes");

		// Mexico - has multiple trunk prefixes: 01, 044, 045
		Country mexico = Countries.get("MX");
		assertNotNull(mexico, "Mexico should be found");
		assertEquals(Set.of("01", "044", "045"), Set.copyOf(mexico.getTrunkPrefixes()), "Mexico trunk prefixes");
		assertEquals(Set.of("00"), Set.copyOf(mexico.getInternationalPrefixes()), "Mexico international prefixes");
	}

	/**
	 * Test countries with multiple international prefixes.
	 */
	@Test
	public void testMultiplePrefixes() {
		// Colombia has multiple international prefixes based on trunk-prefixes.csv
		Country colombia = Countries.get("CO");
		assertNotNull(colombia, "Colombia should be found");
		assertEquals(Set.of("0"), Set.copyOf(colombia.getTrunkPrefixes()), "Colombia trunk prefixes");
		assertEquals(Set.of("005", "007", "009", "00414", "00468", "00456", "00444"),
				Set.copyOf(colombia.getInternationalPrefixes()), "Colombia international prefixes");

		// Israel has multiple international prefixes
		Country israel = Countries.get("IL");
		assertNotNull(israel, "Israel should be found");
		assertTrue(israel.getInternationalPrefixes().size() >= 2, "Israel should have multiple international prefixes");
		assertTrue(israel.getInternationalPrefixes().contains("00"), "Israel should have international prefix 00");
	}

	/**
	 * Test that data from trunk-prefixes.csv is loaded correctly with semicolon delimiter.
	 */
	@Test
	public void testTrunkPrefixCsvLoading() {
		// Verify Germany data is loaded
		Country germany = Countries.get("DE");
		assertNotNull(germany, "Germany should be found");
		assertEquals(Set.of("+49"), Set.copyOf(germany.getDialPrefixes()), "Germany dial prefixes");
		assertEquals(Set.of("0"), Set.copyOf(germany.getTrunkPrefixes()), "Germany trunk prefixes");
		assertEquals(Set.of("00"), Set.copyOf(germany.getInternationalPrefixes()), "Germany international prefixes");

		// Verify USA data is loaded
		Country usa = Countries.get("US");
		assertNotNull(usa, "USA should be found");
		assertEquals(Set.of("+1"), Set.copyOf(usa.getDialPrefixes()), "USA dial prefixes");
		assertEquals(Set.of("1"), Set.copyOf(usa.getTrunkPrefixes()), "USA trunk prefixes");
		assertEquals(Set.of("011"), Set.copyOf(usa.getInternationalPrefixes()), "USA international prefixes");

		// Verify Colombia data is loaded (has multiple international prefixes)
		Country colombia = Countries.get("CO");
		assertNotNull(colombia, "Colombia should be found");
		assertEquals(Set.of("+57"), Set.copyOf(colombia.getDialPrefixes()), "Colombia dial prefixes");
		assertEquals(Set.of("0"), Set.copyOf(colombia.getTrunkPrefixes()), "Colombia trunk prefixes");
		assertEquals(Set.of("005", "007", "009", "00414", "00468", "00456", "00444"),
				Set.copyOf(colombia.getInternationalPrefixes()), "Colombia international prefixes");
	}
}
