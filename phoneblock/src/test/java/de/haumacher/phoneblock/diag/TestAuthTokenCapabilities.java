/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.diag;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBAuthToken;
import de.haumacher.phoneblock.db.TestDB;
import de.haumacher.phoneblock.db.Users;
import de.haumacher.phoneblock.db.settings.AuthToken;
import de.haumacher.phoneblock.scheduler.SchedulerService;

/**
 * Verifies the two new {@code AuthToken} capabilities persist and read back —
 * the plumbing the diagnostics REST API gates on.
 */
public class TestAuthTokenCapabilities {

	private SchedulerService _scheduler;
	private DataSource _dataSource;
	private DB _db;

	@BeforeEach
	public void setUp() throws Exception {
		_scheduler = new SchedulerService();
		_scheduler.contextInitialized(null);
		_dataSource = TestDB.createTestDataSource();
		_db = new DB(_dataSource, _scheduler);
	}

	@AfterEach
	public void tearDown() throws Exception {
		_db.shutdown();
		_scheduler.contextDestroyed(null);
	}

	@Test
	public void testDiagnosticsCapabilitiesRoundTrip() {
		_db.createUser("diagadmin", "Diag Admin", "de", "+49");

		AuthToken template = AuthToken.create()
			.setUserName("diagadmin")
			.setLabel("diag-token")
			.setCreated(1)
			.setImplicit(false)
			.setAccessDiagnostics(true)
			.setAccessAdmin(true)
			.setUserAgent("test");
		_db.createAuthToken(template);

		try (SqlSession s = _db.openSession()) {
			DBAuthToken read = s.getMapper(Users.class).getAuthToken(template.getId());
			assertTrue(read.isAccessDiagnostics(), "accessDiagnostics should persist");
			assertTrue(read.isAccessAdmin(), "accessAdmin should persist");
			assertFalse(read.isAccessRate(), "unset capability stays false");
		}
	}
}
