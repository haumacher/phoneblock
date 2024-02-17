/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.analysis;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import de.haumacher.phoneblock.db.model.PhoneNumer;

/**
 * Test for {@link NumberAnalyzer}.
 */
class TestNumberAnalyzer {
	
	@ParameterizedTest
	@CsvSource({"+1684" + "123456789,Amerikanisch-Samoa",
			"+49,Deutschland",
			"+49" + "704187650,Deutschland",
			"+1" + "241" + "123456789,Vereinigte Staaten oder Kanada",
			"+999999999,Unbekannt",
			"+9,Unbekannt"
	})
	void testCountry(String phone, String label) {
		PhoneNumer info = NumberAnalyzer.analyze(phone);
		assertNotNull(info);
		assertEquals(label, info.getCountry());
	}

	@ParameterizedTest
	@CsvSource({"+49" + "7041,Mühlacker",
			"+49" + "704187650,Mühlacker",
			"+49-7041 87650,Mühlacker"
	})
	void testCity(String phone, String label) {
		PhoneNumer info = NumberAnalyzer.analyze(phone);
		assertNotNull(info);
		assertEquals(label, info.getCity());
	}
	
}
