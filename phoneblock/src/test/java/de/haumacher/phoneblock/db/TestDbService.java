/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.net.UnknownHostException;

import junit.framework.TestCase;

/**
 * Test case for {@link DBService}.
 *
 * @author <a href="mailto:haui@haumacher.de">Bernhard Haumacher</a>
 */
public class TestDbService extends TestCase {

	public void testStart() throws UnknownHostException, IOException {
		DBService service = new DBService() {
			@Override
			protected String defaultDbUrl(String appName) {
				return "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1";
			}
			
			@Override
			protected int defaultDbPort() {
				return 12345;
			}
		};
		
		service.contextInitialized(null);
		
		service.contextDestroyed(null);
		
		try {
			Socket socket = new Socket("localhost", 12345);
			fail("Must have been shut down.");
		} catch (ConnectException ex) {
			// expected.
		}
	}
	
}
