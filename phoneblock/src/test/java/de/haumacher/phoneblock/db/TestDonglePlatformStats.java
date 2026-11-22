/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.haumacher.phoneblock.db.DB.DongleAgent;
import de.haumacher.phoneblock.db.DB.DonglePlatform;
import de.haumacher.phoneblock.db.DB.DonglePlatformCounts;
import de.haumacher.phoneblock.db.settings.AuthToken;
import de.haumacher.phoneblock.scheduler.SchedulerService;

/**
 * Tests parsing and aggregation of dongle platform statistics.
 */
public class TestDonglePlatformStats {

	private static final long NOW = 1_800_000_000_000L;
	private static final long DAY = DB.MILLIS_PER_DAY;

	private SchedulerService _scheduler;
	private DataSource _dataSource;
	private DB _db;
	private long _userId;

	@BeforeEach
	public void setUp() throws Exception {
		_scheduler = new SchedulerService();
		_scheduler.contextInitialized(null);
		_dataSource = TestDB.createTestDataSource();
		_db = new DB(_dataSource, _scheduler);
		_db.createUser("dongle-stats", "Dongle Stats", "de", "+49");
		try (SqlSession session = _db.openSession()) {
			_userId = session.getMapper(Users.class).getUserId("dongle-stats");
		}
	}

	@AfterEach
	public void tearDown() throws Exception {
		_db.shutdown();
		try (Connection connection = _dataSource.getConnection(); Statement statement = connection.createStatement()) {
			statement.execute("SHUTDOWN");
		}
		_scheduler.contextDestroyed(null);
	}

	@Test
	public void testUserAgentParsing() {
		for (DonglePlatform platform : List.of(DonglePlatform.ESP32, DonglePlatform.ESP32_C3,
				DonglePlatform.ESP32_S3, DonglePlatform.LINUX, DonglePlatform.LINUX_HA,
				DonglePlatform.LINUX_DOCKER)) {
			assertEquals(new DongleAgent(platform, "device-a"),
				DB.parseDongleAgent("PhoneBlock-Dongle/2.0 (" + platform.tag() + ") (device-a)"));
		}
		assertEquals(new DongleAgent(DonglePlatform.OTHER, "device-a"),
			DB.parseDongleAgent("PhoneBlock-Dongle/2.0 (ESP32-P4) (device-a)"));
		assertEquals(new DongleAgent(DonglePlatform.OTHER, "device-a"),
			DB.parseDongleAgent("PhoneBlock-Dongle/2.0 (<b>x</b>) (device-a)"));
		assertEquals(new DongleAgent(DonglePlatform.OTHER, "device-a"),
			DB.parseDongleAgent("PhoneBlock-Dongle/2.0 (Other) (extra) (device-a)"));
		assertEquals(new DongleAgent(DonglePlatform.UNKNOWN, "device-a"),
			DB.parseDongleAgent("PhoneBlock-Dongle/1.0 (device-a)"));
		assertEquals(new DongleAgent(DonglePlatform.UNKNOWN, null), DB.parseDongleAgent("PhoneBlock-Dongle/1.0"));
		assertEquals(new DongleAgent(DonglePlatform.UNKNOWN, null), DB.parseDongleAgent(null));
		assertEquals(new DongleAgent(DonglePlatform.UNKNOWN, null), DB.parseDongleAgent("garbage (ESP32) (device-a)"));
		assertEquals(new DongleAgent(DonglePlatform.UNKNOWN, null), DB.parseDongleAgent("PhoneBlock-Dongle/1.0 (ESP32) ("));
		assertEquals(new DongleAgent(DonglePlatform.UNKNOWN, null), DB.parseDongleAgent("PhoneBlock-Dongle/1.0 (ESP32) ()"));
		assertEquals(new DongleAgent(DonglePlatform.UNKNOWN, null), DB.parseDongleAgent("PhoneBlock-Dongle/1.0 ((device-a))"));
		assertEquals(new DongleAgent(DonglePlatform.UNKNOWN, null), DB.parseDongleAgent("PhoneBlock-Dongle/1.0 (device-a) garbage"));
		assertEquals(new DongleAgent(DonglePlatform.UNKNOWN, "device-a"),
			DB.parseDongleAgent("PhoneBlock-Dongle/1.0 () (device-a)"));
	}

	@Test
	public void testNoDongles() {
		assertEquals(List.of(), _db.getActiveDonglesByPlatform(NOW));
	}

	@Test
	public void testActiveDonglesByPlatform() {
		addToken("PhoneBlock-Dongle/2.0 (ESP32) (device-a)", NOW - 45 * DAY, false);
		addToken("PhoneBlock-Dongle/2.1 (Linux; HA) (device-a)", NOW - 10 * DAY, false);
		addToken("PhoneBlock-Dongle/2.1 (Linux; Docker) (device-l)", NOW - 10 * DAY, false);
		addToken("PhoneBlock-Dongle/2.1 (Linux; Ingress-Test) (device-m)", NOW - 10 * DAY, false);
		addToken("PhoneBlock-Dongle/2.0 (ESP32-C3) (device-b)", NOW - 45 * DAY, false);
		addToken("PhoneBlock-Dongle/2.0 (ESP32-S3) (device-c)", NOW - 80 * DAY, false);
		addToken("PhoneBlock-Dongle/2.0 (Linux) (device-d)", NOW - 120 * DAY, false);
		addToken("PhoneBlock-Dongle/2.0 (ESP32) (device-e)", NOW - 30 * DAY, false);
		addToken("PhoneBlock-Dongle/2.0 (Linux) (device-f)", NOW - 60 * DAY, false);
		addToken("PhoneBlock-Dongle/2.0 (Zeta) (device-g)", NOW - 90 * DAY, false);
		addToken("PhoneBlock-Dongle/2.0 (Alpha) (device-h)", NOW - 90 * DAY - 1, false);
		addToken("PhoneBlock-Dongle/2.0 (Alpha) (device-i)", NOW - DAY, false);
		addToken("PhoneBlock-Dongle/2.0 (ESP32-P4) (device-j)", NOW - 20 * DAY, false);
		addToken("PhoneBlock-Dongle/2.0 (<b>x</b>) (device-k)", NOW - 70 * DAY, false);
		addToken("PhoneBlock-Dongle/1.0 (legacy)", NOW - 80 * DAY, false);
		addToken("PhoneBlock-Dongle/1.0 (legacy)", NOW - 45 * DAY, false);
		addToken("PhoneBlock-Dongle/1.0", NOW - DAY, false);
		addToken("PhoneBlock-Dongle/1.0", NOW - DAY, false);
		addToken("PhoneBlock-Dongle/2.0 (ESP32) (implicit)", NOW - DAY, true);
		addToken("Another-Agent/1.0 (ESP32) (other)", NOW - DAY, false);

		assertEquals(List.of(
			new DonglePlatformCounts(DonglePlatform.ESP32, 1, 1, 1),
			new DonglePlatformCounts(DonglePlatform.ESP32_C3, 0, 1, 1),
			new DonglePlatformCounts(DonglePlatform.ESP32_S3, 0, 0, 1),
			new DonglePlatformCounts(DonglePlatform.LINUX, 0, 1, 1),
			new DonglePlatformCounts(DonglePlatform.LINUX_HA, 1, 1, 1),
			new DonglePlatformCounts(DonglePlatform.LINUX_DOCKER, 1, 1, 1),
			new DonglePlatformCounts(DonglePlatform.OTHER, 3, 3, 5),
			new DonglePlatformCounts(DonglePlatform.UNKNOWN, 2, 3, 3)
		), _db.getActiveDonglesByPlatform(NOW));
	}

	private void addToken(String agent, long lastAccess, boolean implicit) {
		AuthToken token = AuthToken.create().setUserId(_userId).setLabel("test")
			.setPwHash(new byte[] { 1 }).setUserAgent(agent).setLastAccess(lastAccess).setImplicit(implicit);
		try (SqlSession session = _db.openSession()) {
			session.getMapper(Users.class).createAuthToken(token);
			session.commit();
		}
	}
}
