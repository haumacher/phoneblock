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

		PhoneNumer numberUs = NumberAnalyzer.extractNumber("+49123456789", "+1");
		assertEquals("0123456789", numberUs.getId());
	}
	
	@ParameterizedTest
	@CsvSource({"+1684" + "123456789,American Samoa",
			"+49" + "308154" + ",Germany",
			"+49" + "704187650,Germany",
			"+1" + "241" + "123456789,'Canada, United States of America'",
	})
	void testCountry(String phone, String label) {
		PhoneNumer info = NumberAnalyzer.analyze(phone);
		assertNotNull(info);
		assertEquals(label, info.getCountry());
	}

	@ParameterizedTest
	@CsvSource({"+49" + "704128,Mühlacker",
			"+49" + "704187650,Mühlacker",
			"+49-7041 87650,Mühlacker",
			"+43720072491,location independent numbers",
			"+39123456789,Lanzo Torinese",
			"+49 9131 9235017072,Erlangen"
	})
	void testCity(String phone, String label) {
		PhoneNumer info = NumberAnalyzer.analyze(phone);
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
		"0612345678, +39, +390612345678, Holy See",
		"3123456789, +39, +393123456789, Italy",

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
		"+390612345678, +39, Holy See", // Rome (Vatican)

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

}
