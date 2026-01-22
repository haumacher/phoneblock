/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import de.haumacher.phoneblock.app.api.model.PhoneNumer;

/**
 * Test for {@link NumberAnalyzer}.
 */
class TestNumberAnalyzer {
	
	void testExtract() {
		PhoneNumer numberDe = NumberAnalyzer.extractNumber("+49123456789", "+49");
		assertEquals("0123456789", numberDe.getId());

		PhoneNumer numberDeIt = NumberAnalyzer.extractNumber("+49123456789", "+39");
		assertEquals("0123456789", numberDeIt.getId());
		
		PhoneNumer numberUs = NumberAnalyzer.extractNumber("+49123456789", "+1");
		assertEquals("0123456789", numberUs.getId());

		PhoneNumer numberIt = NumberAnalyzer.extractNumber("+390123456789", "+39");
		assertEquals("00390123456789", numberIt.getId());
		
		PhoneNumer numberItDe = NumberAnalyzer.extractNumber("+390123456789", "+49");
		assertEquals("00390123456789", numberItDe.getId());
	}
	
	@ParameterizedTest
	@CsvSource({"+1684" + "123456789,American Samoa",
			"+49" + "308154" + ",Germany",
			"+49" + "704187650,Germany",
			"+1" + "241" + "123456789,'Canada, United States of America'",
	})
	void testCountry(String phone, String label) {
		PhoneNumer info = NumberAnalyzer.analyze(phone, "+49");
		assertNotNull(info);
		assertEquals(label, info.getCountry());
	}

	@ParameterizedTest
	@CsvSource({"+49" + "704128,Mühlacker",
			"+49" + "704187650,Mühlacker",
			"+49-7041 87650,Mühlacker",
			"+43720072491,location independent numbers",
			"+390123456789,Lanzo Torinese",
			"+49 9131 9235017072,Erlangen"
	})
	void testCity(String phone, String label) {
		PhoneNumer info = NumberAnalyzer.analyze(phone, "+49");
		assertNotNull(info);
		assertEquals(label, info.getCity());
	}

	@ParameterizedTest
	@CsvSource({
		"017650642602, 017650642602",
		"+4917650642602+, +4917650642602",
		"0049176506426+02, 004917650642602",
		"*017650642+*602*, 017650642602*",
		"+*4917650642+*602*, +4917650642602*",
		"*004917650642+*602*, 004917650642602*",
		"00+491722144286, 00491722144286",
	})
	void testNormalize(String input, String normalized) {
		String normalizedNumber = NumberAnalyzer.normalizeNumber(input);
		assertEquals(normalized, normalizedNumber);
	}

	@ParameterizedTest
	@CsvSource({
		// Hungary - trunk prefix "06"
		"06123456789, +36, +36123456789, Hungary",
		"061234567, +36, +361234567, Hungary",

		// Russia - trunk prefix "8"
		"84951234567, +7, +74951234567, 'Kazakhstan, Russian Federation'",
		"89161234567, +7, +79161234567, 'Kazakhstan, Russian Federation'",

		// Belarus - trunk prefix "80"
		"80291234567, +375, +375291234567, Belarus",

		// USA/Canada - trunk prefix "1"
		"12125551234, +1, +12125551234, 'Canada, United States of America'",

		// Italy - no trunk prefix, "0" is part of area code
		"0612345678, +39, +390612345678, Italy",
		"03123456789, +39, +3903123456789, Italy",

		// Germany - standard trunk prefix "0" (regression test)
		"089123456, +49, +4989123456, Germany",
		"030123456, +49, +4930123456, Germany",

		// Austria - trunk prefix "0"
		"01234567890, +43, +431234567890, Austria",
	})
	void testTrunkPrefixParsing(String input, String dialPrefix, String expectedPlus, String expectedCountry) {
		PhoneNumer result = NumberAnalyzer.analyze(input, dialPrefix);
		assertNotNull(result);
		assertEquals(expectedPlus, result.getPlus());
		assertEquals(expectedCountry, result.getCountry());
	}

	@ParameterizedTest
	@CsvSource({
		// Hungary: number starting with "0" but not "06" should fail
		"0123456789, +36",

		// Russia: number starting with "0" should fail (trunk prefix is "8")
		"04951234567, +7",

		// Too short numbers
		"012345, +49",
		"812345, +7",
	})
	void testInvalidTrunkPrefixNumbers(String input, String dialPrefix) {
		PhoneNumer result = NumberAnalyzer.analyze(input, dialPrefix);
		assertNull(result);
	}

	@ParameterizedTest
	@CsvSource({
		// Mexico has multiple trunk prefixes: "01", "044", "045"
		// Note: Mexico has dial prefix "+52"
		"015551234567, +52, +525551234567",
		"0445551234567, +52, +525551234567",
		"0455551234567, +52, +525551234567",
	})
	void testMexicoMultipleTrunkPrefixes(String input, String dialPrefix, String expectedPlus) {
		PhoneNumer result = NumberAnalyzer.analyze(input, dialPrefix);
		assertNotNull(result);
		assertEquals(expectedPlus, result.getPlus());
	}

	@ParameterizedTest
	@CsvSource({
		// Test that international and 00 formats still work with non-standard trunk prefixes
		"+36123456789, +36, +36123456789",
		"0036123456789, +36, +36123456789",
		"+74951234567, +7, +74951234567",
		"0074951234567, +7, +74951234567",
		"+375291234567, +375, +375291234567",
		"00375291234567, +375, +375291234567",
	})
	void testInternationalFormatWithNonStandardTrunkPrefixes(String input, String dialPrefix, String expectedPlus) {
		PhoneNumer result = NumberAnalyzer.analyze(input, dialPrefix);
		assertNotNull(result);
		assertEquals(expectedPlus, result.getPlus());
	}

	@ParameterizedTest
	@CsvSource({
		// Italian numbers with "0" after country code should be ACCEPTED (0 is part of area code)
		// Italy has empty trunk prefix, so 0 in international format is valid
		"+390123456789, +39, Italy",  // Genoa area
		"+39010123456, +39, Italy",   // Genoa
		"+39011123456, +39, Italy",   // Turin
		"+3902123456, +39, Italy",    // Milan
		"00390123456789, +39, Italy",
		"+390612345678, +39, Italy", // Rome (Vatican)

		// Same numbers entered from Italian dial prefix
		"0123456789, +39, Italy",
		"010123456, +39, Italy",
		"011123456, +39, Italy",
		"02123456, +39, Italy",
	})
	void testItalianNumbersWithLeadingZero(String input, String dialPrefix, String expectedCountry) {
		PhoneNumer result = NumberAnalyzer.analyze(input, dialPrefix);
		assertNotNull(result, "Italian number " + input + " should be valid");
		assertEquals(expectedCountry, result.getCountry());
	}

	@ParameterizedTest
	@CsvSource({
		// German numbers with "0" after country code should be REJECTED (trunk prefix was not stripped)
		// Germany has "0" as trunk prefix, so +49 0... is invalid
		"+490123456789, +49",
		"00490123456789, +49",

		// Hungarian numbers with "06" after country code should be REJECTED
		"+36061234567, +36",
		"003606123456, +36",

		// Russian numbers with "8" after country code should be REJECTED
		"+784951234567, +7",
		"00784951234567, +7",
	})
	void testInternationalNumbersWithTrunkPrefixShouldBeRejected(String input, String dialPrefix) {
		PhoneNumer result = NumberAnalyzer.analyze(input, dialPrefix);
		assertNull(result, "Number " + input + " should be invalid (trunk prefix in international format)");
	}

	@ParameterizedTest
	@CsvSource({
		// Italian numbers should display with single leading 0 (not double 0)
		// Italy has empty trunk prefix, so national format = local part (no prefix added)
		"+390123456789, +39, '(IT) 0123456789'",
		"+39010123456, +39, '(IT) 010123456'",
		"+39011123456, +39, '(IT) 011123456'",
		"+3902123456, +39, '(IT) 02123456'",
		"0123456789, +39, '(IT) 0123456789'",

		// German numbers should have trunk prefix "0" added
		"+49891234567, +49, '(DE) 0891234567'",
		"+4930123456, +49, '(DE) 030123456'",
		"0891234567, +49, '(DE) 0891234567'",

		// Hungarian numbers should have trunk prefix "06" added
		"+36123456789, +36, '(HU) 06123456789'",
		"06123456789, +36, '(HU) 06123456789'",

		// Russia should have trunk prefix "8" added
		"+74951234567, +7, '(KZ, RU) 84951234567'",
		"84951234567, +7, '(KZ, RU) 84951234567'",
	})
	void testNationalFormatDisplay(String input, String dialPrefix, String expectedShortcut) {
		PhoneNumer result = NumberAnalyzer.analyze(input, dialPrefix);
		assertNotNull(result, "Number " + input + " should be valid");
		assertEquals(expectedShortcut, result.getShortcut());
	}

	@ParameterizedTest
	@CsvSource({
		// Cross-country searches: User's dial prefix different from number's country
		// German user searching for Italian number should work
		"+390123456789, +49, Italy, '(IT) 0123456789'",
		"+39010123456, +49, Italy, '(IT) 010123456'",

		// Italian user searching for German number should work
		"+49891234567, +39, Germany, '(DE) 0891234567'",
		"+4930123456, +39, Germany, '(DE) 030123456'",

		// German user searching for Hungarian number should work
		"+36123456789, +49, Hungary, '(HU) 06123456789'",

		// Hungarian user searching for Russian number should work
		"+74951234567, +36, 'Kazakhstan, Russian Federation', '(KZ, RU) 84951234567'",

		// Any user can search for numbers in international format
		"00390123456789, +49, Italy, '(IT) 0123456789'",
		"0049891234567, +39, Germany, '(DE) 0891234567'",
	})
	void testCrossCountrySearch(String input, String userDialPrefix, String expectedCountry, String expectedShortcut) {
		PhoneNumer result = NumberAnalyzer.analyze(input, userDialPrefix);
		assertNotNull(result, "Cross-country search for " + input + " with user dial prefix " + userDialPrefix + " should work");
		assertEquals(expectedCountry, result.getCountry());
		assertEquals(expectedShortcut, result.getShortcut());
	}

	@ParameterizedTest
	@CsvSource({
		// CRITICAL: Italian user searching for German number starting with +49 should get GERMAN number
		// NOT Italian number by prepending +39 to "49123456789"
		"+49123456789, +39, +49123456789",
		"+49891234567, +39, +49891234567",
		"+4930123456, +39, +4930123456",

		// German user searching for Italian number starting with +39
		"+390123456789, +49, +390123456789",
		"+39010123456, +49, +39010123456",

		// Numbers already in + format should NEVER be modified
		"+1234567890, +49, +1234567890",
		"+1234567890, +39, +1234567890",
		"+36123456789, +39, +36123456789",
	})
	void testInternationalFormatNotModified(String input, String userDialPrefix, String expectedPlus) {
		PhoneNumer result = NumberAnalyzer.analyze(input, userDialPrefix);
		assertNotNull(result, "Number " + input + " should be valid regardless of user dial prefix");
		assertEquals(expectedPlus, result.getPlus(), "International format should not be modified by user's dial prefix");
	}

	@ParameterizedTest
	@CsvSource({
		// German user (uses "00" international prefix) looking at various numbers
		"+49891234567, +49, 0049891234567",      // German number
		"+390123456789, +49, 00390123456789",    // Italian number
		"+12125551234, +49, 0012125551234",      // US number
		"+36123456789, +49, 0036123456789",      // Hungarian number
		"+74951234567, +49, 0074951234567",      // Russian number

		// US user (uses "011" international prefix) looking at various numbers
		"+49891234567, +1, 01149891234567",      // German number
		"+390123456789, +1, 011390123456789",    // Italian number
		"+12125551234, +1, 01112125551234",      // US number (same country)
		"+36123456789, +1, 01136123456789",      // Hungarian number

		// Russian user (uses "810" international prefix) looking at various numbers
		"+49891234567, +7, 81049891234567",      // German number
		"+390123456789, +7, 810390123456789",    // Italian number
		"+74951234567, +7, 81074951234567",      // Russian number (same country)

		// Japanese user (uses "010" international prefix)
		"+49891234567, +81, 01049891234567",     // German number
		"+12125551234, +81, 01012125551234",     // US number
	})
	void testDialFormatWithDifferentInternationalPrefixes(String input, String userDialPrefix, String expectedDial) {
		PhoneNumer result = NumberAnalyzer.analyze(input, userDialPrefix);
		assertNotNull(result, "Number " + input + " should be valid");
		assertEquals(expectedDial, result.getDial(), "Dial format should use user's international prefix");
	}

	@ParameterizedTest
	@CsvSource({
		// Test that dial format always has a value (never null)
		"+49891234567, +49",
		"+390123456789, +49",
		"+12125551234, +1",
		"089123456, +49",      // national format
		"0123456789, +39",     // Italian national
	})
	void testDialFormatAlwaysSet(String input, String userDialPrefix) {
		PhoneNumer result = NumberAnalyzer.analyze(input, userDialPrefix);
		assertNotNull(result, "Number " + input + " should be valid");
		assertNotNull(result.getDial(), "Dial format should never be null");
		assertNotNull(result.getZeroZero(), "ZeroZero format should never be null");
		assertNotNull(result.getPlus(), "Plus format should never be null");
	}

	@ParameterizedTest
	@CsvSource({
		// Verify dial format defaults to "00" when country data is missing
		// This tests the fallback behavior
		"+49891234567, +999, 0049891234567",  // Unknown dial prefix should default to "00"
	})
	void testDialFormatFallbackToDefaultPrefix(String input, String userDialPrefix, String expectedDial) {
		PhoneNumer result = NumberAnalyzer.analyze(input, userDialPrefix);
		assertNotNull(result, "Number " + input + " should be valid even with unknown dial prefix");
		assertEquals(expectedDial, result.getDial(), "Dial format should fall back to '00' prefix");
	}

}
