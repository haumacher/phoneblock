/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.ConnectException;
import java.net.Socket;

import org.junit.jupiter.api.Test;

import de.haumacher.phoneblock.scheduler.SchedulerService;

/**
 * Test case for {@link DBService}.
 *
 * @author <a href="mailto:haui@haumacher.de">Bernhard Haumacher</a>
 */
class TestDbService {

	@Test
	void testStart() {
		SchedulerService scheduler = new SchedulerService();
		DBService service = new DBService(scheduler) {
			@Override
			protected String defaultDbUrl(String appName) {
				return "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1";
			}
			
			@Override
			protected int defaultDbPort() {
				return 12345;
			}
		};
		
		scheduler.contextInitialized(null);
		service.contextInitialized(null);
		
		service.contextDestroyed(null);
		scheduler.contextDestroyed(null);

		assertThrows(ConnectException.class, () -> new Socket("localhost", 12345),
				"Must have been shut down.");
	}
	
}
