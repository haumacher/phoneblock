package de.haumacher.phoneblock.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import de.haumacher.phoneblock.app.api.TestConnectServlet;
import de.haumacher.phoneblock.carddav.CardDavServlet;

public class TestBasicLoginFilter {
	@Test
	public void testMatches() {
		assertTrue(BasicLoginFilter.matches(TestConnectServlet.PATH));
		assertTrue(BasicLoginFilter.matches(CardDavServlet.DIR_NAME));
		assertFalse(BasicLoginFilter.matches("/index.jsp"));
	}
}
