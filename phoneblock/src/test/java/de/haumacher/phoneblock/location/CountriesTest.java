/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.location;

import static org.junit.jupiter.api.Assertions.*;

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

		// Greece - standard trunk prefix "0"
		Country greece = Countries.get("GR");
		assertNotNull(greece, "Greece should be found");
		assertEquals(1, greece.getTrunkPrefixes().size(), "Greece should have 1 trunk prefix");
		assertEquals("0", greece.getTrunkPrefixes().get(0), "Greece trunk prefix should be 0");
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
		assertEquals(1, usa.getTrunkPrefixes().size(), "USA should have 1 trunk prefix");
		assertEquals("1", usa.getTrunkPrefixes().get(0), "USA trunk prefix should be 1");
		assertEquals(1, usa.getInternationalPrefixes().size(), "USA should have 1 international prefix");
		assertEquals("011", usa.getInternationalPrefixes().get(0), "USA international prefix should be 011");

		// Canada - trunk prefix "1", international prefix "011"
		Country canada = Countries.get("CA");
		assertNotNull(canada, "Canada should be found");
		assertEquals(1, canada.getTrunkPrefixes().size(), "Canada should have 1 trunk prefix");
		assertEquals("1", canada.getTrunkPrefixes().get(0), "Canada trunk prefix should be 1");
		assertEquals(1, canada.getInternationalPrefixes().size(), "Canada should have 1 international prefix");
		assertEquals("011", canada.getInternationalPrefixes().get(0), "Canada international prefix should be 011");

		// Hungary - trunk prefix "06", international prefix "00"
		Country hungary = Countries.get("HU");
		assertNotNull(hungary, "Hungary should be found");
		assertEquals(1, hungary.getTrunkPrefixes().size(), "Hungary should have 1 trunk prefix");
		assertEquals("06", hungary.getTrunkPrefixes().get(0), "Hungary trunk prefix should be 06");
		assertEquals(1, hungary.getInternationalPrefixes().size(), "Hungary should have 1 international prefix");
		assertEquals("00", hungary.getInternationalPrefixes().get(0), "Hungary international prefix should be 00");

		// Mexico - trunk prefix "01", international prefix "00"
		Country mexico = Countries.get("MX");
		assertNotNull(mexico, "Mexico should be found");
		assertEquals(1, mexico.getTrunkPrefixes().size(), "Mexico should have 1 trunk prefix");
		assertEquals("01", mexico.getTrunkPrefixes().get(0), "Mexico trunk prefix should be 01");
		assertEquals(1, mexico.getInternationalPrefixes().size(), "Mexico should have 1 international prefix");
		assertEquals("00", mexico.getInternationalPrefixes().get(0), "Mexico international prefix should be 00");
	}

	/**
	 * Test countries with multiple international prefixes.
	 */
	@Test
	public void testMultiplePrefixes() {
		// Colombia has multiple trunk prefixes: 09, 07, 05
		Country colombia = Countries.get("CO");
		assertNotNull(colombia, "Colombia should be found");
		assertEquals(3, colombia.getTrunkPrefixes().size(), "Colombia should have 3 trunk prefixes");
		assertTrue(colombia.getTrunkPrefixes().contains("09"), "Colombia should have trunk prefix 09");
		assertTrue(colombia.getTrunkPrefixes().contains("07"), "Colombia should have trunk prefix 07");
		assertTrue(colombia.getTrunkPrefixes().contains("05"), "Colombia should have trunk prefix 05");

		// Colombia has multiple international prefixes: 009, 007, 005
		assertEquals(3, colombia.getInternationalPrefixes().size(), "Colombia should have 3 international prefixes");
		assertTrue(colombia.getInternationalPrefixes().contains("009"), "Colombia should have international prefix 009");
		assertTrue(colombia.getInternationalPrefixes().contains("007"), "Colombia should have international prefix 007");
		assertTrue(colombia.getInternationalPrefixes().contains("005"), "Colombia should have international prefix 005");

		// Israel has multiple international prefixes
		Country israel = Countries.get("IL");
		assertNotNull(israel, "Israel should be found");
		assertTrue(israel.getInternationalPrefixes().size() >= 2, "Israel should have multiple international prefixes");
		assertTrue(israel.getInternationalPrefixes().contains("00"), "Israel should have international prefix 00");
	}
}
