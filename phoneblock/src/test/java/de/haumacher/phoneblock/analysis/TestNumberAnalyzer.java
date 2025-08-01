/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import de.haumacher.phoneblock.app.api.model.PhoneNumer;

/**
 * Test for {@link NumberAnalyzer}.
 */
class TestNumberAnalyzer {
	
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
	
}
