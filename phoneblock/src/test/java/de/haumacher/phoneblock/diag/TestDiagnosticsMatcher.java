/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.diag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.TestDB;
import de.haumacher.phoneblock.scheduler.SchedulerService;

/**
 * Tests the rule matcher: classification, thresholds, SHADOW vs LIVE routing, the
 * one-shot latch and rearm.
 */
public class TestDiagnosticsMatcher {

	private static final long DAY_MS = 86_400_000L;
	private static final long T0 = 1_700_000_000_000L;

	private SchedulerService _scheduler;
	private DataSource _dataSource;
	private DB _db;

	/** Records notifier calls; notifyUser returns a configurable result. */
	private static class CapturingNotifier implements Notifier {
		final List<String> userCalls = new ArrayList<>();
		final List<String> devCalls = new ArrayList<>();
		boolean userResult = false;

		@Override public boolean notifyUser(DiagRule rule, String source, String originId, String userId) {
			userCalls.add(originId);
			return userResult;
		}
		@Override public void notifyDev(DiagRule rule, String source, String originId, String userId) {
			devCalls.add(originId);
		}
	}

	@BeforeEach
	public void setUp() throws Exception {
		_scheduler = new SchedulerService();
		_scheduler.contextInitialized(null);
		_dataSource = TestDB.createTestDataSource();
		_db = new DB(_dataSource, _scheduler);
		// Start from a clean rule set (drop the seeded SHADOW rules).
		try (Connection c = _dataSource.getConnection(); Statement s = c.createStatement()) {
			s.execute("DELETE FROM DIAG_RULE");
		}
	}

	@AfterEach
	public void tearDown() throws Exception {
		_db.shutdown();
		_db = null;
		try (Connection c = _dataSource.getConnection(); Statement s = c.createStatement()) {
			s.execute("SHUTDOWN");
		}
		_scheduler.contextDestroyed(null);
		_scheduler = null;
	}

	private DiagRule rule(String tag, String regex, String actor, String state, int minEvents) {
		DiagRule r = new DiagRule();
		r.setName(tag + "-rule");
		r.setSource("DONGLE");
		r.setMatchTag(tag);
		r.setMatchRegex(regex);
		r.setCategory("cat-" + tag);
		r.setActor(actor);
		r.setState(state);
		r.setMinEvents(minEvents);
		r.setMinDistinctDays(1);
		return r;
	}

	private void insertRule(DiagRule r) {
		try (SqlSession s = _db.openSession()) {
			s.getMapper(DiagnosticsMapper.class).insertRule(r);
			s.commit();
		}
	}

	private void ingest(String tag, String message, String origin, int count, long ts) {
		DiagnosticsAggregator agg = new DiagnosticsAggregator(20);
		try (SqlSession s = _db.openSession()) {
			DiagnosticsMapper m = s.getMapper(DiagnosticsMapper.class);
			for (int i = 0; i < count; i++) {
				agg.apply(m, new DiagEvent("DONGLE", origin, "user-1", "E", 1L, tag, message, ts + i));
			}
			s.commit();
		}
	}

	private DiagnosticsMatcher.MatchStats runMatcher(Notifier notifier, long now) {
		DiagnosticsMatcher matcher = new DiagnosticsMatcher(3);
		try (SqlSession s = _db.openSession()) {
			DiagnosticsMatcher.MatchStats stats = matcher.run(s.getMapper(DiagnosticsMapper.class), notifier, now);
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
	public void testShadowProjectsWithoutClassifying() throws Exception {
		insertRule(rule("sip", "REGISTER rejected", DiagRule.ACTOR_USER, DiagRule.SHADOW, 2));
		ingest("sip", "sip: REGISTER rejected: 400", "dev-a", 3, T0);

		CapturingNotifier notifier = new CapturingNotifier();
		runMatcher(notifier, T0 + 1000);

		assertEquals(1, scalar("SELECT COUNT(*) FROM DIAG_NOTIFICATION"));
		assertEquals("PENDING", text("SELECT STATE FROM DIAG_NOTIFICATION"));
		assertEquals(1, scalar("SELECT COUNT(*) FROM DIAG_NOTIFICATION WHERE DRY_RUN = TRUE"));
		assertEquals(0, notifier.userCalls.size()); // SHADOW never calls the notifier
		assertNull(text("SELECT CATEGORY FROM DIAG_SIGNATURE")); // SHADOW does not classify
	}

	@Test
	public void testLiveDevClassifiesAndAlerts() throws Exception {
		insertRule(rule("api", "rate: HTTP", DiagRule.ACTOR_DEV, DiagRule.LIVE, 2));
		ingest("api", "api: rate: HTTP 500 for x", "dev-a", 3, T0);

		CapturingNotifier notifier = new CapturingNotifier();
		runMatcher(notifier, T0 + 1000);

		assertEquals("cat-api", text("SELECT CATEGORY FROM DIAG_SIGNATURE")); // LIVE classifies
		assertEquals(1, notifier.devCalls.size());
		assertEquals("SENT", text("SELECT STATE FROM DIAG_NOTIFICATION"));
	}

	@Test
	public void testLiveUserSuppressedLeavesNoLatch() throws Exception {
		insertRule(rule("sip", "REGISTER rejected", DiagRule.ACTOR_USER, DiagRule.LIVE, 2));
		ingest("sip", "sip: REGISTER rejected: 400", "dev-a", 3, T0);

		CapturingNotifier notifier = new CapturingNotifier();
		notifier.userResult = false; // simulates kill switch / cap / no address
		runMatcher(notifier, T0 + 1000);

		assertEquals(1, notifier.userCalls.size());
		assertEquals(0, scalar("SELECT COUNT(*) FROM DIAG_NOTIFICATION")); // unlatched -> retry later
		assertEquals("cat-sip", text("SELECT CATEGORY FROM DIAG_SIGNATURE")); // still classified
	}

	@Test
	public void testThresholdNotMet() throws Exception {
		insertRule(rule("sip", "REGISTER rejected", DiagRule.ACTOR_USER, DiagRule.SHADOW, 5));
		ingest("sip", "sip: REGISTER rejected: 400", "dev-a", 2, T0); // only 2 < 5
		runMatcher(new CapturingNotifier(), T0 + 1000);
		assertEquals(0, scalar("SELECT COUNT(*) FROM DIAG_NOTIFICATION"));
	}

	@Test
	public void testLatchNoDuplicate() throws Exception {
		insertRule(rule("sip", "REGISTER rejected", DiagRule.ACTOR_USER, DiagRule.SHADOW, 2));
		ingest("sip", "sip: REGISTER rejected: 400", "dev-a", 3, T0);
		runMatcher(new CapturingNotifier(), T0 + 1000);
		runMatcher(new CapturingNotifier(), T0 + 2000);
		assertEquals(1, scalar("SELECT COUNT(*) FROM DIAG_NOTIFICATION"));
	}

	@Test
	public void testDryRunProjectsWithoutWriting() throws Exception {
		ingest("sip", "sip: REGISTER rejected: 400", "dev-a", 3, T0);
		ingest("sip", "sip: REGISTER rejected: 401", "dev-b", 3, T0);
		try (SqlSession s = _db.openSession()) {
			DiagnosticsMatcher.DryRun dr = DiagnosticsMatcher.dryRun(
				s.getMapper(DiagnosticsMapper.class), "DONGLE", "sip", "REGISTER rejected", 1, 2);
			assertEquals(1, dr.matchingSignatures()); // 400/401 -> <N> -> one signature
			assertEquals(2, dr.matchingOrigins());
			assertEquals(1, dr.matchingUsers());       // both use user-1
		}
		assertEquals(0, scalar("SELECT COUNT(*) FROM DIAG_NOTIFICATION")); // no side effects
	}

	@Test
	public void testRearmWhenQuiet() throws Exception {
		insertRule(rule("sip", "REGISTER rejected", DiagRule.ACTOR_USER, DiagRule.SHADOW, 2));
		ingest("sip", "sip: REGISTER rejected: 400", "dev-a", 3, T0);
		runMatcher(new CapturingNotifier(), T0 + 1000);                 // recent -> PENDING
		runMatcher(new CapturingNotifier(), T0 + 5 * DAY_MS);           // quiet -> cleared
		assertEquals("CLEARED", text("SELECT STATE FROM DIAG_NOTIFICATION"));
	}
}
