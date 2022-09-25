/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.analysis;

import junit.framework.TestCase;

/**
 * Test for {@link NumberAnalyzer}.
 */
public class TestNumberAnalyzer extends TestCase {
	
	public void testFindInfo() {
		assertCountry("+1684" + "123456789", "Amerikanisch-Samoa");
		assertCountry("+49", "Deutschland");
		assertCity("+49" + "7041", "Mühlacker");
		assertCountry("+49" + "704187650", "Deutschland");
		assertCity("+49" + "704187650", "Mühlacker");
		assertCity("+49-7041 87650", "Mühlacker");
		assertCountry("+1" + "241" + "123456789", "Vereinigte Staaten oder Kanada");
		assertCountry("+999999999", "Unbekannt");
		assertCountry("+9", "Unbekannt");
	}

	private void assertCountry(String phone, String label) {
		PhoneNumer info = NumberAnalyzer.analyze(phone);
		assertEquals(label, info.getCountry());
	}

	private void assertCity(String phone, String label) {
		PhoneNumer info = NumberAnalyzer.analyze(phone);
		assertEquals(label, info.getCity());
	}
	
}
