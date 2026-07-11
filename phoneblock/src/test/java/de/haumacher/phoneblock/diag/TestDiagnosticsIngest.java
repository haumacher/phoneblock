/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.diag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import de.haumacher.phoneblock.scheduler.SchedulerService;

/**
 * End-to-end test of the aggregation layer against a real H2 schema: events →
 * {@link DiagnosticsAggregator} → {@code DIAG_*} tables, exercising the
 * update-then-insert upserts, distinct-day counting, the sample cap and PII
 * scrubbing on the stored sample.
 */
public class TestDiagnosticsIngest {

	private static final long DAY_MS = 86_400_000L;
	private static final long T0 = 1_700_000_000_000L;

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
		_db = null;
		try (Connection c = _dataSource.getConnection(); Statement s = c.createStatement()) {
			s.execute("SHUTDOWN");
		}
		_scheduler.contextDestroyed(null);
		_scheduler = null;
	}

	private DiagEvent dongle(String originId, String message, long ts) {
		return new DiagEvent("DONGLE", originId, "user-1", "E", 100L, tagOf(message), message, ts);
	}

	private static String tagOf(String message) {
		int colon = message.indexOf(':');
		return colon > 0 ? message.substring(0, colon) : "";
	}

	private void ingest(int sampleCap, DiagEvent... events) {
		DiagnosticsAggregator aggregator = new DiagnosticsAggregator(sampleCap);
		try (SqlSession session = _db.openSession()) {
			DiagnosticsMapper mapper = session.getMapper(DiagnosticsMapper.class);
			for (DiagEvent e : events) {
				aggregator.apply(mapper, e);
			}
			session.commit();
		}
	}

	private long scalar(String sql) throws Exception {
		try (Connection c = _dataSource.getConnection();
				Statement s = c.createStatement();
				ResultSet rs = s.executeQuery(sql)) {
			rs.next();
			return rs.getLong(1);
		}
	}

	private String text(String sql) throws Exception {
		try (Connection c = _dataSource.getConnection();
				Statement s = c.createStatement();
				ResultSet rs = s.executeQuery(sql)) {
			rs.next();
			return rs.getString(1);
		}
	}

	@Test
	public void testRepeatedEventAccumulates() throws Exception {
		ingest(20,
			dongle("dev-a", "sip: REGISTER rejected: 400", T0),
			dongle("dev-a", "sip: REGISTER rejected: 400", T0 + 1000));

		assertEquals(1, scalar("SELECT COUNT(*) FROM DIAG_SIGNATURE"));
		assertEquals(2, scalar("SELECT TOTAL_EVENTS FROM DIAG_SIGNATURE"));
		assertEquals(1, scalar("SELECT COUNT(*) FROM DIAG_ORIGIN_SIGNATURE"));
		assertEquals(2, scalar("SELECT EVENT_COUNT FROM DIAG_ORIGIN_SIGNATURE"));
		assertEquals(1, scalar("SELECT DISTINCT_DAYS FROM DIAG_ORIGIN_SIGNATURE"));
		// Differ only in the status code -> same signature.
		assertEquals("sip: REGISTER rejected: <N>", text("SELECT SIGNATURE FROM DIAG_SIGNATURE"));
	}

	@Test
	public void testDistinctDaysAcrossDays() throws Exception {
		ingest(20,
			dongle("dev-a", "wifi: disconnected (reason 15)", T0),
			dongle("dev-a", "wifi: disconnected (reason 3)", T0 + DAY_MS),
			dongle("dev-a", "wifi: disconnected (reason 7)", T0 + 2 * DAY_MS));

		assertEquals(3, scalar("SELECT EVENT_COUNT FROM DIAG_ORIGIN_SIGNATURE"));
		assertEquals(3, scalar("SELECT DISTINCT_DAYS FROM DIAG_ORIGIN_SIGNATURE"));
	}

	@Test
	public void testSeparateOrigins() throws Exception {
		ingest(20,
			dongle("dev-a", "sip: REGISTER rejected: 400", T0),
			dongle("dev-b", "sip: REGISTER rejected: 400", T0));

		assertEquals(1, scalar("SELECT COUNT(*) FROM DIAG_SIGNATURE"));
		assertEquals(2, scalar("SELECT COUNT(*) FROM DIAG_ORIGIN_SIGNATURE"));
	}

	@Test
	public void testSampleCap() throws Exception {
		DiagEvent[] many = new DiagEvent[5];
		for (int i = 0; i < 5; i++) {
			many[i] = dongle("dev-a", "api: rate: HTTP 500 for x", T0 + i);
		}
		ingest(2, many);

		assertEquals(5, scalar("SELECT TOTAL_EVENTS FROM DIAG_SIGNATURE"));
		assertEquals(2, scalar("SELECT COUNT(*) FROM DIAG_SAMPLE")); // capped
	}

	@Test
	public void testSampleScrubsPii() throws Exception {
		ingest(20, dongle("dev-a", "mail: status mail to gerhard@muehlenbeck.bayern failed", T0));

		assertEquals("mail: status mail to <email> failed", text("SELECT MESSAGE_SCRUBBED FROM DIAG_SAMPLE"));
		assertEquals("mail: status mail to <email> failed", text("SELECT SIGNATURE FROM DIAG_SIGNATURE"));
	}

	@Test
	public void testSampleDetailExposesUptime() throws Exception {
		ingest(20, dongle("dev-a", "sip: REGISTER rejected: 400", T0)); // uptime 100

		try (SqlSession session = _db.openSession()) {
			DiagnosticsMapper mapper = session.getMapper(DiagnosticsMapper.class);
			String sigId = text("SELECT SIG_ID FROM DIAG_SIGNATURE");
			java.util.Map<String, Object> sample = mapper.listSamples(sigId, 20).get(0);
			assertEquals(100L, ((Number) sample.get("UPTIMES")).longValue());
		}
	}

	@Test
	public void testNotificationRowsUseCamelCaseKeys() throws Exception {
		try (SqlSession session = _db.openSession()) {
			DiagnosticsMapper mapper = session.getMapper(DiagnosticsMapper.class);
			mapper.insertNotification("DONGLE", "dev-a", "user-1", 1L, "SENT", false, T0, T0);
			session.commit();

			java.util.Map<String, Object> row = mapper.listNotifications(null, -1, null, 0, 10).get(0);
			// Quoted SQL aliases must survive as camelCase map keys (not H2-uppercased).
			assertTrue(row.containsKey("originId"), "keys=" + row.keySet());
			assertTrue(row.containsKey("ruleId"), "keys=" + row.keySet());
			assertEquals("dev-a", row.get("originId"));
		}
	}

	@Test
	public void testScrubRuleCrudAndState() throws Exception {
		try (SqlSession session = _db.openSession()) {
			DiagnosticsMapper mapper = session.getMapper(DiagnosticsMapper.class);

			DiagScrubRule rule = new DiagScrubRule();
			rule.setName("mac");
			rule.setPattern("\\b([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}\\b");
			rule.setReplacement("<mac>");
			rule.setAppliesTo(DiagScrubRule.BOTH);
			rule.setState(DiagScrubRule.DRAFT);
			mapper.insertScrubRule(rule);
			session.commit();

			// DRAFT is not yet live.
			assertEquals(0, mapper.listLiveScrubRules().size());
			assertEquals(1, mapper.listScrubRules(DiagScrubRule.DRAFT).size());

			mapper.setScrubRuleState(rule.getId(), DiagScrubRule.LIVE, T0);
			session.commit();
			assertEquals(1, mapper.listLiveScrubRules().size());
			// setRuleState bumps the version (1 -> 2).
			assertEquals(2, mapper.getScrubRule(rule.getId()).getVersion());
		}
	}

	@Test
	public void testIngestAppliesLiveDbScrubRule() throws Exception {
		try (SqlSession session = _db.openSession()) {
			DiagnosticsMapper mapper = session.getMapper(DiagnosticsMapper.class);
			DiagScrubRule rule = new DiagScrubRule();
			rule.setName("mac");
			rule.setPattern("\\b([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}\\b");
			rule.setReplacement("<mac>");
			rule.setAppliesTo(DiagScrubRule.BOTH);
			rule.setState(DiagScrubRule.LIVE);
			mapper.insertScrubRule(rule);
			session.commit();
		}

		DiagnosticsAggregator aggregator = new DiagnosticsAggregator(20);
		try (SqlSession session = _db.openSession()) {
			DiagnosticsMapper mapper = session.getMapper(DiagnosticsMapper.class);
			Scrubber scrubber = Scrubber.withLiveRules(mapper.listLiveScrubRules());
			aggregator.apply(mapper, dongle("dev-a", "wifi: assoc 00:11:22:33:44:55 lost", T0), scrubber);
			session.commit();
		}

		assertEquals("wifi: assoc <mac> lost", text("SELECT MESSAGE_SCRUBBED FROM DIAG_SAMPLE"));
	}
}
