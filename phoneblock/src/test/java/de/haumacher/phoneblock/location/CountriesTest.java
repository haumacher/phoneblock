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
		assertEquals("0", germany.getTrunkPrefix(), "Germany trunk prefix should be 0");
		assertEquals("Germany", germany.getOfficialNameEn(), "Germany official name");

		// Italy - standard trunk prefix "0"
		Country italy = Countries.get("IT");
		assertNotNull(italy, "Italy should be found");
		assertEquals("0", italy.getTrunkPrefix(), "Italy trunk prefix should be 0");
		assertEquals("Italy", italy.getOfficialNameEn(), "Italy official name");

		// Russia - non-standard trunk prefix "8"
		Country russia = Countries.get("RU");
		assertNotNull(russia, "Russia should be found");
		assertEquals("8", russia.getTrunkPrefix(), "Russia trunk prefix should be 8");
		assertEquals("Russian Federation", russia.getOfficialNameEn(), "Russia official name");

		// Greece - standard trunk prefix "0" (default)
		Country greece = Countries.get("GR");
		assertNotNull(greece, "Greece should be found");
		assertEquals("0", greece.getTrunkPrefix(), "Greece trunk prefix should be 0 (default)");
		assertEquals("Greece", greece.getOfficialNameEn(), "Greece official name");
	}

	/**
	 * Test additional countries with non-standard trunk prefixes.
	 */
	@Test
	public void testNonStandardTrunkPrefixes() {
		// United States - trunk prefix "1"
		Country usa = Countries.get("US");
		assertNotNull(usa, "USA should be found");
		assertEquals("1", usa.getTrunkPrefix(), "USA trunk prefix should be 1");

		// Canada - trunk prefix "1"
		Country canada = Countries.get("CA");
		assertNotNull(canada, "Canada should be found");
		assertEquals("1", canada.getTrunkPrefix(), "Canada trunk prefix should be 1");

		// Hungary - trunk prefix "06"
		Country hungary = Countries.get("HU");
		assertNotNull(hungary, "Hungary should be found");
		assertEquals("06", hungary.getTrunkPrefix(), "Hungary trunk prefix should be 06");

		// Mexico - trunk prefix "01"
		Country mexico = Countries.get("MX");
		assertNotNull(mexico, "Mexico should be found");
		assertEquals("01", mexico.getTrunkPrefix(), "Mexico trunk prefix should be 01");
	}

	/**
	 * Test that the trunk prefix is set for all countries.
	 */
	@Test
	public void testAllCountriesHaveTrunkPrefix() {
		for (Country country : Countries.all()) {
			String iso = country.getISO31661Alpha2();
			assertNotNull(country.getTrunkPrefix(),
				"Country " + iso + " (" + country.getOfficialNameEn() + ") should have a trunk prefix");
		}
	}
}
