/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.diag;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.TestDB;
import de.haumacher.phoneblock.db.Users;
import de.haumacher.phoneblock.db.settings.AuthToken;
import de.haumacher.phoneblock.scheduler.SchedulerService;

/**
 * Tests the dongle liveness (silence) detector over the TOKENS table.
 */
public class TestDongleSilence {

	private static final long DAY = 86_400_000L;
	private static final long NOW = 1_800_000_000_000L;

	private SchedulerService _scheduler;
	private DataSource _dataSource;
	private DB _db;

	private static class NoopNotifier implements Notifier {
		@Override public boolean notifyUser(DiagRule r, String s, String o, String u) { return false; }
		@Override public void notifyDev(DiagRule r, String s, String o, String u) {}
	}

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

	private void dongleToken(String user, String deviceUuid, long lastAccess) {
		_db.createUser(user, user, "de", "+49");
		AuthToken t = AuthToken.create()
			.setUserName(user)
			.setLabel("dongle")
			.setCreated(NOW - 30 * DAY)
			.setImplicit(false)
			.setUserAgent("PhoneBlock-Dongle/1.4.1 (" + deviceUuid + ")")
			.setLastAccess(lastAccess);
		_db.createAuthToken(t);
	}

	private DongleSilenceDetector.Stats runSilence(long now) {
		DongleSilenceDetector detector = new DongleSilenceDetector(5); // 5-day silence window
		try (SqlSession s = _db.openSession()) {
			DongleSilenceDetector.Stats stats = detector.run(
				s.getMapper(DiagnosticsMapper.class), s.getMapper(Users.class), new NoopNotifier(), now);
			s.commit();
			return stats;
		}
	}

	private long scalar(String sql) throws Exception {
		try (Connection c = _dataSource.getConnection(); Statement s = c.createStatement();
				ResultSet rs = s.executeQuery(sql)) {
			rs.next();
			return rs.getLong(1);
		}
	}

	private String text(String sql) throws Exception {
		try (Connection c = _dataSource.getConnection(); Statement s = c.createStatement();
				ResultSet rs = s.executeQuery(sql)) {
			return rs.next() ? rs.getString(1) : null;
		}
	}

	@Test
	public void testSilentDeviceProjectedActiveIgnored() throws Exception {
		dongleToken("silent@x.de", "11111111-1111-1111-1111-111111111111", NOW - 10 * DAY); // silent
		dongleToken("active@x.de", "22222222-2222-2222-2222-222222222222", NOW - 1 * DAY);  // active

		DongleSilenceDetector.Stats stats = runSilence(NOW);

		assertEquals(1, stats.notified());
		assertEquals(1, scalar("SELECT COUNT(*) FROM DIAG_NOTIFICATION"));
		assertEquals("11111111-1111-1111-1111-111111111111", text("SELECT ORIGIN_ID FROM DIAG_NOTIFICATION"));
		assertEquals("PENDING", text("SELECT STATE FROM DIAG_NOTIFICATION")); // SHADOW -> dry-run
		assertEquals(1, scalar("SELECT COUNT(*) FROM DIAG_NOTIFICATION WHERE DRY_RUN = TRUE"));
	}

	@Test
	public void testLatchNoDuplicate() throws Exception {
		dongleToken("silent@x.de", "11111111-1111-1111-1111-111111111111", NOW - 10 * DAY);
		runSilence(NOW);
		runSilence(NOW + DAY);
		assertEquals(1, scalar("SELECT COUNT(*) FROM DIAG_NOTIFICATION")); // one nudge per silence period
	}

	@Test
	public void testRearmWhenDeviceReturns() throws Exception {
		String uuid = "11111111-1111-1111-1111-111111111111";
		dongleToken("silent@x.de", uuid, NOW - 10 * DAY);
		runSilence(NOW);

		// Device checks in again — bump its token's LASTACCESS to "now".
		try (Connection c = _dataSource.getConnection(); Statement s = c.createStatement()) {
			s.executeUpdate("UPDATE TOKENS SET LASTACCESS = " + NOW + " WHERE USERAGENT LIKE '%" + uuid + "%'");
		}
		runSilence(NOW);
		assertEquals("CLEARED", text("SELECT STATE FROM DIAG_NOTIFICATION"));
	}
}
