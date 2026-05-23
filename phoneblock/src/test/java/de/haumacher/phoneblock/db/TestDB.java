/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.session.SqlSession;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.haumacher.phoneblock.analysis.NumberAnalyzer;
import de.haumacher.phoneblock.app.api.model.NumberInfo;
import de.haumacher.phoneblock.app.api.model.PhoneInfo;
import de.haumacher.phoneblock.app.api.model.PhoneNumer;
import de.haumacher.phoneblock.app.api.model.Rating;
import de.haumacher.phoneblock.app.api.model.SearchInfo;
import de.haumacher.phoneblock.app.api.model.UserComment;
import de.haumacher.phoneblock.app.AuthContext;
import de.haumacher.phoneblock.credits.MessageDetails;
import de.haumacher.phoneblock.db.settings.AuthToken;
import de.haumacher.phoneblock.scheduler.SchedulerService;

/**
 * Test case for {@link DB}.
 */
public class TestDB {
	
	private DB _db;
	private SchedulerService _scheduler;
	private DataSource _dataSource;

	@BeforeEach
	public void setUp() throws Exception {
		_scheduler = new SchedulerService();
		_scheduler.contextInitialized(null);
		
		_dataSource = createTestDataSource();
		_db = new DB(_dataSource, _scheduler);
	}
	
	@AfterEach
	public void tearDown() throws Exception {
		_db.shutdown();
		_db = null;
		
		try (Connection connection = _dataSource.getConnection()) {
			try (Statement statement = connection.createStatement()) {
				statement.execute("SHUTDOWN");
			}
		}
		
		_scheduler.contextDestroyed(null);
		_scheduler = null;
	}
	
	@Test
	void testCallReportQuota() {
		_db.createUser("flagger", "Flagger", "de", "+49");

		try (SqlSession session = _db.openSession()) {
			Users users = session.getMapper(Users.class);
			long userId = users.getUserId("flagger").longValue();

			// First flag of day 100 — counter resets to 1.
			assertEquals(1, users.tryConsumeCallReportQuota(userId, 100, 3));
			// Two further flags still fit within the quota (quota = 3).
			assertEquals(1, users.tryConsumeCallReportQuota(userId, 100, 3));
			assertEquals(1, users.tryConsumeCallReportQuota(userId, 100, 3));
			// Fourth flag on the same day is refused.
			assertEquals(0, users.tryConsumeCallReportQuota(userId, 100, 3));
			// Day change triggers a lazy reset — the new day starts fresh.
			assertEquals(1, users.tryConsumeCallReportQuota(userId, 101, 3));
			assertEquals(1, users.tryConsumeCallReportQuota(userId, 101, 3));

			session.commit();
		}
	}

	@Test
	void testAuthToken() {
		_db.createUser("user1", "User 1", "de", "+49");

		long time = 1000;
		final long createTime = time;

		AuthToken token1 = _db.createLoginToken("user1", time++, "creating-browser");

		// Skip rate limit.
		time += DB.RATE_LIMIT_MS;

		final String origToken1 = token1.getToken();
		final long checkTime = time;
		AuthContext context1 = _db.checkAuthToken(token1.getToken(), time++, "other-browser", true);
		assertNotNull(context1);
		token1 = context1.getAuthorization();

		assertEquals(token1.getUserName(), "user1");
		assertEquals(createTime, token1.getCreated());
		assertEquals(checkTime, token1.getLastAccess());
		// User agent is updated when it changes (even within rate limit)
		assertEquals("other-browser", token1.getUserAgent());
		assertTrue(token1.isImplicit());
		assertTrue(token1.isAccessLogin());

		AuthToken token2 = _db.createLoginToken("user1", time++, "creating-browser");
		AuthToken token3 = _db.createLoginToken("user1", time++, "creating-browser");
		AuthToken token4 = _db.createLoginToken("user1", time++, "creating-browser");
		AuthToken token5 = _db.createLoginToken("user1", time++, "creating-browser");

		// Skip rate limit.
		time += DB.RATE_LIMIT_MS;

		String oldToken1 = token1.getToken();

		token1 = checkAuthToken(token1.getToken(), time++, "login-browser", true);
		token2 = checkAuthToken(token2.getToken(), time++, "login-browser", true);
		token3 = checkAuthToken(token3.getToken(), time++, "login-browser", true);
		token4 = checkAuthToken(token4.getToken(), time++, "login-browser", true);
		token5 = checkAuthToken(token5.getToken(), time++, "login-browser", true);

		assertNotEquals(origToken1, token1.getToken());
		assertNull(_db.checkAuthToken(oldToken1, time, "bad-browser", false));

		AuthToken token6 = _db.createLoginToken("user1", time++, "creating-browser");

		assertNull(   _db.checkAuthToken(token1.getToken(), time++, "login-browser", false));
		assertNotNull(_db.checkAuthToken(token2.getToken(), time++, "login-browser", false));
		assertNotNull(_db.checkAuthToken(token3.getToken(), time++, "login-browser", false));
		assertNotNull(_db.checkAuthToken(token4.getToken(), time++, "login-browser", false));
		assertNotNull(_db.checkAuthToken(token5.getToken(), time++, "login-browser", false));
		assertNotNull(_db.checkAuthToken(token6.getToken(), time++, "login-browser", false));
	}

	private AuthToken checkAuthToken(String token, long time, String userAgent, boolean renew) {
		AuthContext context = _db.checkAuthToken(token, time, userAgent, renew);
		assertNotNull(context);
		return context.getAuthorization();
	}

	@Test
	void testTopSearches() {
		long now = 1000000000000000000L;

		addSearchHit("091000000", now++);
		addSearchHit("051000000", now++);

		_db.updateHistory(30, now++);
		
		// Yesterday
		addRating(null, "091000000", Rating.C_PING, null, now++);
		addRating(null, "011000000", Rating.C_PING, null, now++);
		addRating(null, "021000000", Rating.C_PING, null, now++);
		addRating(null, "033000000", Rating.C_PING, null, now++);
		addRating(null, "041000000", Rating.C_PING, null, now++);
		addRating(null, "051000000", Rating.C_PING, null, now++);
		
		addSearchHit("051000000", now++);
		addSearchHit("091000000", now++);
		addSearchHit("091000000", now++);
		addSearchHit("091000000", now++);
		
		addSearchHit("011000000", now++);
		addSearchHit("021000000", now++);
		addSearchHit("033000000", now++);
		_db.updateHistory(30, now++);
		
		// Today
		addSearchHit("091000000", now++);
		addSearchHit("041000000", now++);
		addSearchHit("051000000", now++);
		
		List<? extends SearchInfo> topSearches = _db.getTopSearches(2);

		assertEquals(2, topSearches.size());
		
		assertEquals("051000000", topSearches.get(0).getPhone());
		assertEquals(2, topSearches.get(0).getCount());
		assertEquals(3, topSearches.get(0).getTotal());
		
		assertEquals("091000000", topSearches.get(1).getPhone());
		assertEquals(4, topSearches.get(1).getCount());
		assertEquals(5, topSearches.get(1).getTotal());
	}
	
	@Test
	void testSpamReports() {
		assertFalse(_db.hasSpamReportFor("012300000"));

		processVotes("012300000", 2, 1000);
		
		assertTrue(_db.hasSpamReportFor("012300000"));
		
		processVotes("045600000", 1, 1001);
		
		assertEquals(2, _db.getVotesFor("012300000"));
		
		assertFalse(_db.hasSpamReportFor("099900000"));
		assertEquals(0, _db.getVotesFor("099900000"));
		
		processVotes("099900000", -1, 1002);
		assertEquals(-1, _db.getVotesFor("099900000"));
		
		processVotes("099900000", 0, 1003);
		assertEquals(-1, _db.getVotesFor("099900000"));
		
		processVotes("012300000", 1, 1004);
		assertEquals(3, _db.getVotesFor("012300000"));
		
		{
			List<? extends NumberInfo> reports = _db.getLatestSpamReports(1001);
			assertEquals(2, reports.size());
			assertEquals("012300000", reports.get(0).getPhone());
			assertEquals("045600000", reports.get(1).getPhone());
		}
		
		processVotes("012300000", -1, 1005);
		assertEquals(2, _db.getVotesFor("012300000"));
		
		processVotes("012300000", -2, 1006);
		assertEquals(0, _db.getVotesFor("012300000"));
		
		assertEquals(1006, _db.getLastSpamReport().longValue());
		
		List<? extends NumberInfo> reports = _db.getLatestSpamReports(1001);
		assertEquals(1, reports.size());
		assertEquals("045600000", reports.get(0).getPhone());
	}
	
	@Test
	void testBlocklist() {
		try (SqlSession session = _db.openSession()) {
			BlockList blockList = session.getMapper(BlockList.class);
			
			blockList.addExclude(1, "012300000", null, System.currentTimeMillis());
			blockList.addExclude(2, "012300000", null, System.currentTimeMillis());
			blockList.addExclude(1, "034500000", null, System.currentTimeMillis());
			blockList.addExclude(1, "067800000", null, System.currentTimeMillis());
			blockList.addExclude(2, "099900000", null, System.currentTimeMillis());

			assertEquals(new HashSet<>(List.of("012300000", "034500000", "067800000")), blockList.getExcluded(1));

			blockList.removePersonalization(1, "034500000");

			assertEquals(new HashSet<>(List.of("012300000", "067800000")), blockList.getExcluded(1));

			blockList.removePersonalization(2, "012300000");

			assertEquals(new HashSet<>(List.of("012300000", "067800000")), blockList.getExcluded(1));

			blockList.addPersonalization(1, "065400000", null, System.currentTimeMillis());
			blockList.addPersonalization(1, "032100000", null, System.currentTimeMillis());
			blockList.addPersonalization(2, "032100000", null, System.currentTimeMillis());
			blockList.addPersonalization(2, "098700000", null, System.currentTimeMillis());
			
			assertEquals(List.of("032100000", "065400000"), blockList.getPersonalizations(1));

			blockList.removePersonalization(1, "065400000");

			assertEquals(List.of("032100000"), blockList.getPersonalizations(1));
			
			blockList.removePersonalization(2, "032100000");
			
			assertEquals(List.of("032100000"), blockList.getPersonalizations(1));
		}
	}

	@Test
	void testDuplicateAdd() {
		try (SqlSession session = _db.openSession()) {
			BlockList blockList = session.getMapper(BlockList.class);

			blockList.addExclude(1, "012300000", null, System.currentTimeMillis());
			try {
				blockList.addExclude(1, "012300000", null, System.currentTimeMillis());
				fail("Expecting duplicate key constraint violation.");
			} catch (PersistenceException ex) {
				// Expected.
			}
		}
	}
	
	@Test
	void testUserManagement() throws IOException {
		_db.addUser("foo@bar.com", "Mr. X", "de", "+49", "012300000");
		_db.addUser("baz@bar.com", "Mr. Y", "de", "+49", "012300000");
		
		assertEquals("foo@bar.com", _db.basicAuth(header("foo@bar.com", "012300000"), "none").getUserName());
        assertNull(_db.basicAuth(header("foo@bar.com", "0321"), "none"));
        assertNull(_db.basicAuth(header("xxx@bar.com", "012300000"), "none"));
		
		try (SqlSession session = _db.openSession()) {
			long userA = session.getMapper(Users.class).getUserId("foo@bar.com");
			long userB = session.getMapper(Users.class).getUserId("baz@bar.com");

			assertNotEquals(0, userA);
			assertNotEquals(0, userB);
			assertNotEquals(userA, userB);
		}
	}
	
	private String header(String user, String pw) {
		return "Basic " + Base64.getEncoder().encodeToString((user + ':' + pw).getBytes(StandardCharsets.UTF_8));
	}
	
	@Test
	void testRatings() {
		long now = 1;
		
		addRating(null, "012300000", Rating.G_FRAUD, null, now++);
		addRating(null, "012300000", Rating.B_MISSED, null, now++);
		addRating(null, "012300000", Rating.B_MISSED, null, now++);
		addRating(null, "012300000", Rating.C_PING, null, now++);
		addRating(null, "012300000", Rating.D_POLL, null, now++);
		addRating(null, "012300000", Rating.E_ADVERTISING, null, now++);
		addRating(null, "012300000", Rating.F_GAMBLE, null, now++);

		assertEquals(Rating.G_FRAUD, _db.getRating("012300000"));
		
		_db.updateHistory(10);
		
		assertEquals(Rating.G_FRAUD, _db.getRating("012300000"));
		
		addRating(null, "012300000", Rating.E_ADVERTISING, null, now++);
		
		assertEquals(Rating.E_ADVERTISING, _db.getRating("012300000"));
	}
	
	@Test
	void testSearchHistory() {
		String _123 = "012300000";
		String _456 = "045600000";
		String _789 = "078900000";
		
		// A search far in the history.
		addSearchHit(_123);
		
		// No more searches for three periods.
		_db.updateHistory(30);
		_db.updateHistory(30);
		_db.updateHistory(30);
		
		// The first day of the four day history.
		addSearchHit(_123);
		addSearchHit(_123);
		addSearchHit(_456);
		
		_db.updateHistory(30);
		
		addSearchHit(_456);
		addSearchHit(_789);
		
		_db.updateHistory(30);
		
		addSearchHit(_123);
		
		_db.updateHistory(30);
		
		addSearchHit(_456);
		addSearchHit(_456);
		addSearchHit(_789);
		
		assertEquals(List.of(2, 0, 1, 0), _db.getSearchHistory(_123, 4));
		assertEquals(List.of(1, 1, 0, 2), _db.getSearchHistory(_456, 4));
		assertEquals(List.of(0, 1, 0, 1), _db.getSearchHistory(_789, 4));
	}
	
	private void addSearchHit(String phone) {
		_db.addSearchHit(NumberAnalyzer.analyze(phone, "+49"), "+49");
	}

	@Test
	void testSearchHistoryCleanup() {
		long time = 1000;
		for (int n = 0; n < 49; n++) {
			addSearchHit("012300000", time);
			_db.updateHistory(30, time);
			
			time++;
		}
		addSearchHit("012300000", time);
		
		List<Integer> all = _db.getSearchHistory("012300000", 31);
		assertEquals(31, all.size());
		assertEquals(1, all.get(31 - 1));
		assertEquals(1, all.get(1));
		assertEquals(50 - 30, all.get(0));
		
		assertEquals(7, _db.getSearchHistory("012300000", 7).size());
	}
	
	private void addSearchHit(String phone, long now) {
		_db.addSearchHit(NumberAnalyzer.analyze(phone, "+49"), "+49", now);
	}

	@Test
	void testQuote() {
		assertEquals("\"\" 0x0 \"33a0a838-7b11-427a-\" 0x9 \"\" 0xD \"\" 0xA \"\" 0xC \"9c84-59b6ab6d3b0e\" 0x20 \"\"", DB.saveChars("\00033a0a838-7b11-427a-\t\r\n\f9c84-59b6ab6d3b0e "));
	}
	
	@Test
	void testAggregation() {
		long now = 0;
		
		processVotes("040299962900", 1, now);
		processVotes("040299962900", 1, now);
		processVotes("040299962901", 1, now);
		
		checkPhone("040299962900", 2, 2, 3, 0, 0);
		checkPhone("040299962909", 0, 2, 3, 0, 0);
		checkPhone("040299962999", 0, 0, 0, 0, 0);
		
		processVotes("040299962902", 1, now);
		processVotes("040299962903", 1, now);
		
		checkPhone("040299962900", 2, 4, 5, 1, 5);
		checkPhone("040299962909", 0, 4, 5, 1, 5);
		checkPhone("040299962999", 0, 0, 0, 1, 5);
		
		processVotes("040299962903", -1, now);
		
		checkPhone("040299962900", 2, 3, 4, 0, 0);
		checkPhone("040299962909", 0, 3, 4, 0, 0);
		checkPhone("040299962999", 0, 0, 0, 0, 0);
	}

	@Test
	void testAggregation100() {
		long now = 0;
		
		processVotes("040299962900", 1, now);
		processVotes("040299962901", 1, now);
		processVotes("040299962902", 1, now);
		processVotes("040299962903", 1, now);
		
		processVotes("040299962910", 1, now);
		processVotes("040299962911", 1, now);
		processVotes("040299962912", 1, now);
		processVotes("040299962913", 1, now);
		
		processVotes("040299962920", 1, now);
		processVotes("040299962921", 1, now);
		processVotes("040299962922", 1, now);
		processVotes("040299962923", 1, now);
		
		checkPhone("040299962999", 0, 0, 0, 3, 12);
		
		processVotes("040299962903", -1, now);
		processVotes("040299962913", -1, now);
		processVotes("040299962923", -1, now);

		checkPhone("040299962999", 0, 0, 0, 0, 0);
	}

	/**
	 * The hash-prefix mappers used by {@code /check-prefix} must skip aggregation rows whose
	 * {@code CNT} is below the wildcard-vote thresholds. Otherwise the API would leak ranges
	 * that don't contribute to a wildcard vote — inconsistent with {@code /check} and with
	 * {@link DB#computeWildcardVotes(AggregationInfo, AggregationInfo)}.
	 */
	@Test
	void testAggregationByHashPrefixFiltersThreshold() {
		long now = 0;

		// Block-of-10 "0402999629_0": 4 distinct numbers → qualifies (cnt10 = MIN_AGGREGATE_10).
		processVotes("040299962900", 1, now);
		processVotes("040299962901", 1, now);
		processVotes("040299962902", 1, now);
		processVotes("040299962903", 1, now);

		// Block-of-10 "0402999628_0": 1 number only → has a hash row but cnt10 = 1 < 4.
		processVotes("040299962800", 1, now);

		byte[] low = new byte[20];
		byte[] high = new byte[20];
		Arrays.fill(high, (byte) 0xff);

		try (SqlSession tx = _db.openSession()) {
			SpamReports reports = tx.getMapper(SpamReports.class);

			// Aggregation rows are keyed on the block prefix (last digit stripped).
			// Unfiltered (minCnt = 0) sees both 10-blocks.
			List<AggregationInfo> all10 = reports.getAggregation10ByHashPrefix(low, high, 0);
			Set<String> all10Prefixes = all10.stream().map(AggregationInfo::getPrefix).collect(Collectors.toSet());
			assertTrue(all10Prefixes.contains("04029996290"));
			assertTrue(all10Prefixes.contains("04029996280"));

			// Threshold filter drops the under-populated 10-block.
			List<AggregationInfo> filtered10 = reports.getAggregation10ByHashPrefix(low, high, DB.MIN_AGGREGATE_10);
			Set<String> filtered10Prefixes = filtered10.stream().map(AggregationInfo::getPrefix).collect(Collectors.toSet());
			assertTrue(filtered10Prefixes.contains("04029996290"));
			assertFalse(filtered10Prefixes.contains("04029996280"));
		}

		// Promote two more 10-sub-blocks so the 100-block "040299962" qualifies (cnt100 = 3).
		processVotes("040299962910", 1, now);
		processVotes("040299962911", 1, now);
		processVotes("040299962912", 1, now);
		processVotes("040299962913", 1, now);

		processVotes("040299962920", 1, now);
		processVotes("040299962921", 1, now);
		processVotes("040299962922", 1, now);
		processVotes("040299962923", 1, now);

		try (SqlSession tx = _db.openSession()) {
			SpamReports reports = tx.getMapper(SpamReports.class);

			List<AggregationInfo> filtered100 = reports.getAggregation100ByHashPrefix(low, high, DB.MIN_AGGREGATE_100);
			Set<String> filtered100Prefixes = filtered100.stream().map(AggregationInfo::getPrefix).collect(Collectors.toSet());
			assertTrue(filtered100Prefixes.contains("0402999629"));

			// Raise the threshold one above what this 100-block delivers → it must drop out.
			List<AggregationInfo> tooStrict = reports.getAggregation100ByHashPrefix(low, high, DB.MIN_AGGREGATE_100 + 1);
			Set<String> tooStrictPrefixes = tooStrict.stream().map(AggregationInfo::getPrefix).collect(Collectors.toSet());
			assertFalse(tooStrictPrefixes.contains("0402999629"));
		}
	}

	private void processVotes(String phone, int votes, long time) {
		_db.processVotes(NumberAnalyzer.analyze(phone, "+49"), "+49", votes, time);
	}

	/** Raw EMA columns straight from NUMBERS — for confidence-model assertions (#332). */
	private double[] rawEmas(String phone) {
		try (Connection conn = _dataSource.getConnection();
				PreparedStatement stmt = conn.prepareStatement(
					"select HEAT, SPAM_EVIDENCE, LEGIT_EVIDENCE from NUMBERS where PHONE = ?")) {
			stmt.setString(1, phone);
			try (ResultSet rs = stmt.executeQuery()) {
				assertTrue(rs.next(), "No row for " + phone);
				return new double[] { rs.getDouble(1), rs.getDouble(2), rs.getDouble(3) };
			}
		} catch (SQLException ex) {
			throw new RuntimeException(ex);
		}
	}

	/** Raw EMA columns from NUMBERS_AGGREGATION_10 / _100 — for #337 assertions. */
	private double[] rawAggEmas(String prefix, int blockSize) {
		String table = blockSize == 10 ? "NUMBERS_AGGREGATION_10" : "NUMBERS_AGGREGATION_100";
		try (Connection conn = _dataSource.getConnection();
				PreparedStatement stmt = conn.prepareStatement(
					"select HEAT, SPAM_EVIDENCE, LEGIT_EVIDENCE from " + table + " where PREFIX = ?")) {
			stmt.setString(1, prefix);
			try (ResultSet rs = stmt.executeQuery()) {
				if (!rs.next()) {
					return null; // row absent
				}
				return new double[] { rs.getDouble(1), rs.getDouble(2), rs.getDouble(3) };
			}
		} catch (SQLException ex) {
			throw new RuntimeException(ex);
		}
	}

	@Test
	void testAggregationEmasPopulatedFlat() {
		// Issue #337: every vote feeds the EMAs at all three levels (number,
		// /10, /100) flat — independent of the cnt/votes-promotion path.

		long t = Ema.T0_MILLIS;

		// Positive vote on a brand-new number.
		processVotes("030555000010", 1, t);

		// Number-level EMAs are populated (covered by #332 — sanity assertion).
		double[] numberEmas = rawEmas("030555000010");
		assertTrue(numberEmas[0] > 0, "number HEAT > 0");
		assertTrue(numberEmas[1] > 0, "number SPAM_EVIDENCE > 0");

		// /10 block (prefix '03055500001'): the row must exist and carry the same
		// EMA increment (same weight, same time → same projected value).
		double[] ema10 = rawAggEmas("03055500001", 10);
		assertNotNull(ema10, "/10 aggregation row must exist after a positive vote");
		assertEquals(numberEmas[0], ema10[0], 1e-12, "/10 HEAT must equal number HEAT");
		assertEquals(numberEmas[1], ema10[1], 1e-12, "/10 SPAM_EVIDENCE must equal number SPAM_EVIDENCE");
		assertEquals(0.0, ema10[2], 0.0);

		// /100 block (prefix '0305550000'): same projection.
		double[] ema100 = rawAggEmas("0305550000", 100);
		assertNotNull(ema100, "/100 aggregation row must exist after a positive vote");
		assertEquals(numberEmas[0], ema100[0], 1e-12, "/100 HEAT must equal number HEAT");
		assertEquals(numberEmas[1], ema100[1], 1e-12);

		// LEGITIMATE vote on a brand-new number — the existing cnt/votes-promotion
		// path would not have created an aggregation row at all (votes < 0, rows == 0).
		// With #337, the EMA path creates rows with cnt=0/votes=0 and carries
		// LEGIT_EVIDENCE so the block-level decay can later balance it.
		processVotes("030666000010", -1, t);
		double[] legitNum = rawEmas("030666000010");
		assertTrue(legitNum[2] > 0, "LEGIT_EVIDENCE on number");

		double[] legit10 = rawAggEmas("03066600001", 10);
		assertNotNull(legit10, "/10 row must exist even for a legitimate-only vote (#337)");
		assertEquals(legitNum[2], legit10[2], 1e-12, "/10 LEGIT_EVIDENCE matches number");
		assertEquals(0.0, legit10[1], 0.0, "/10 SPAM_EVIDENCE stays 0");

		// Second positive vote in the SAME /10 block — block EMAs grow, cnt-promotion
		// also moves, but the EMA path is independent and additive.
		processVotes("030555000011", 1, t + 86_400_000L);
		double[] ema10After = rawAggEmas("03055500001", 10);
		assertTrue(ema10After[0] > ema10[0], "/10 HEAT must monotonically grow on a second vote in the block");
		assertTrue(ema10After[1] > ema10[1], "/10 SPAM_EVIDENCE must monotonically grow");
	}

	@Test
	void testPhoneApiInfoExposesHeatAndSpamConfidence() {
		// Issue #334: the /api/check response (PhoneInfo) carries the
		// decoded heat and a Wilson-bound spamConfidence.

		long now = System.currentTimeMillis();
		// Make the number look like steady spam activity in the recent past.
		for (int i = 0; i < 10; i++) {
			processVotes("030888000010", 1, now - i * 60_000L);
		}

		PhoneInfo info = _db.getPhoneApiInfo("030888000010");
		assertTrue(info.getHeat() > 0, "decoded heat must be > 0 after recent activity");
		assertTrue(info.getSpamConfidence() > 50,
			"10 SPAM votes with no LEGIT must read high confidence, was " + info.getSpamConfidence());

		// Adding plenty of legit evidence drives confidence down.
		for (int i = 0; i < 10; i++) {
			processVotes("030888000011", 1, now - i * 60_000L);
		}
		for (int i = 0; i < 10; i++) {
			processVotes("030888000011", -1, now - i * 60_000L);
		}
		PhoneInfo disputed = _db.getPhoneApiInfo("030888000011");
		assertTrue(disputed.getSpamConfidence() < info.getSpamConfidence(),
			"Disputed number must read lower confidence than pure spam");
	}

	@Test
	void testBlockSpamEvidenceDecaysOutOfBlocking() {
		// Issue #337: a block stays wildcard-blocked while its decayed
		// SPAM_EVIDENCE is above MIN_BLOCK_SPAM_EVIDENCE, and decays out of
		// blocking once the spammer moves on.

		long t0 = Ema.T0_MILLIS;

		// Build a hot /10 block at t0 — four direct votes hit the threshold exactly.
		processVotes("030777000010", 1, t0);
		processVotes("030777000011", 1, t0);
		processVotes("030777000012", 1, t0);
		processVotes("030777000013", 1, t0);

		PhoneNumer fresh = NumberAnalyzer.analyze("030777000014", "+49");
		String freshId = NumberAnalyzer.getPhoneId(fresh);

		// Just after the burst the block is hot → implicit tracking fires.
		try (SqlSession tx = _db.openSession()) {
			SpamReports reports = tx.getMapper(SpamReports.class);
			assertTrue(_db.recordCallOrTrackWildcard(reports, fresh, freshId, t0, true),
				"Hot block at t0 must materialise the row");
			tx.commit();
		}

		// A different unknown number in the same /10 — but report it far in the
		// future. By that point the block's SPAM_EVIDENCE has decayed below the
		// threshold (4 direct-vote units, classification half-life 125 days).
		// Three classification half-lives → decoded evidence drops to 4 × 2^-3 = 0.5 < threshold.
		PhoneNumer later = NumberAnalyzer.analyze("030777000015", "+49");
		String laterId = NumberAnalyzer.getPhoneId(later);
		long farFuture = t0 + 3L * Ema.CLASSIFICATION_HALF_LIFE_DAYS * 86_400_000L;
		try (SqlSession tx = _db.openSession()) {
			SpamReports reports = tx.getMapper(SpamReports.class);
			assertFalse(_db.recordCallOrTrackWildcard(reports, later, laterId, farFuture, true),
				"Block must have decayed out of wildcard-blocking after several half-lives");
			tx.commit();
		}
		try (SqlSession tx = _db.openSession()) {
			SpamReports reports = tx.getMapper(SpamReports.class);
			assertNull(reports.getVotes(laterId),
				"Decayed-out block must not materialise a NUMBERS row for the new number");
		}
	}

	@Test
	void testWildcardImplicitVoteFlow() {
		// Issue #333: a report-call on an unknown number must materialise a NUMBERS
		// row only when the number falls into a hot wildcard-blocked /10 (or /100)
		// block. Idempotency uses the "first report from this user" flag.

		long now = Ema.T0_MILLIS;

		// Build a hot /10 block "0301234567_": four neighbours cross MIN_AGGREGATE_10.
		processVotes("030123456700", 1, now);
		processVotes("030123456701", 1, now);
		processVotes("030123456702", 1, now);
		processVotes("030123456703", 1, now);

		PhoneNumer hotUnknown = NumberAnalyzer.analyze("030123456704", "+49");
		String hotUnknownId = NumberAnalyzer.getPhoneId(hotUnknown);

		// Sanity: the number is not yet in NUMBERS.
		try (SqlSession tx = _db.openSession()) {
			SpamReports reports = tx.getMapper(SpamReports.class);
			assertNull(reports.getVotes(hotUnknownId));
		}

		// First report from this user for the hot-unknown number — must create the row.
		try (SqlSession tx = _db.openSession()) {
			SpamReports reports = tx.getMapper(SpamReports.class);
			boolean materialized = _db.recordCallOrTrackWildcard(reports, hotUnknown, hotUnknownId, now, true);
			assertTrue(materialized, "Hot wildcard block must materialise the row");
			tx.commit();
		}
		double[] firstReport = rawEmas(hotUnknownId);
		assertEquals(0, _db.getVotesFor(hotUnknownId), "Direct VOTES must stay 0 — implicit only");
		assertTrue(firstReport[0] > 0, "HEAT must be set by the implicit report");
		assertTrue(firstReport[1] > 0, "SPAM_EVIDENCE must be set by the implicit report");
		assertEquals(0.0, firstReport[2], 0.0, "LEGIT_EVIDENCE must stay 0");

		// Second report from the SAME user — row exists now, only Heat grows;
		// SPAM_EVIDENCE must not be inflated a second time.
		long later = now + 60_000L;
		try (SqlSession tx = _db.openSession()) {
			SpamReports reports = tx.getMapper(SpamReports.class);
			boolean materialized = _db.recordCallOrTrackWildcard(reports, hotUnknown, hotUnknownId, later, false);
			assertFalse(materialized, "Second report must not create a new row");
			tx.commit();
		}
		double[] secondReport = rawEmas(hotUnknownId);
		assertTrue(secondReport[0] > firstReport[0], "HEAT must grow on the second report");
		assertEquals(firstReport[1], secondReport[1], 1e-12,
			"SPAM_EVIDENCE must NOT grow on a second report from the same user");

		// Unknown number in a *cold* range (no wildcard block) — must stay unknown.
		PhoneNumer coldUnknown = NumberAnalyzer.analyze("020987654321", "+49");
		String coldUnknownId = NumberAnalyzer.getPhoneId(coldUnknown);
		try (SqlSession tx = _db.openSession()) {
			SpamReports reports = tx.getMapper(SpamReports.class);
			boolean materialized = _db.recordCallOrTrackWildcard(reports, coldUnknown, coldUnknownId, now, true);
			assertFalse(materialized, "Cold range must not materialise the row");
			tx.commit();
		}
		try (SqlSession tx = _db.openSession()) {
			SpamReports reports = tx.getMapper(SpamReports.class);
			assertNull(reports.getVotes(coldUnknownId),
				"Cold-range report must not create a NUMBERS row");
		}
	}

	@Test
	void testConfidenceModelEmaPopulation() {
		// A positive vote populates HEAT and SPAM_EVIDENCE; LEGIT_EVIDENCE stays at 0.
		// Use a time near t0 to keep the projected values within close range of the weight.
		long t = Ema.T0_MILLIS;
		processVotes("030111111", 1, t);

		double[] after1 = rawEmas("030111111");
		assertTrue(after1[0] > 0, "HEAT must be > 0 after a SPAM vote");
		assertTrue(after1[1] > 0, "SPAM_EVIDENCE must be > 0 after a SPAM vote");
		assertEquals(0.0, after1[2], 0.0, "LEGIT_EVIDENCE must stay 0 after a SPAM vote");

		// A negative vote populates LEGIT_EVIDENCE on a different number.
		processVotes("030222222", -1, t);
		double[] legit = rawEmas("030222222");
		assertTrue(legit[0] > 0, "HEAT must be > 0 after a LEGITIMATE vote too");
		assertEquals(0.0, legit[1], 0.0, "SPAM_EVIDENCE must stay 0 after a LEGITIMATE vote");
		assertTrue(legit[2] > 0, "LEGIT_EVIDENCE must be > 0 after a LEGITIMATE vote");

		// A second positive vote on the first number must monotonically grow HEAT and SPAM_EVIDENCE.
		processVotes("030111111", 1, t + 86_400_000L);
		double[] after2 = rawEmas("030111111");
		assertTrue(after2[0] > after1[0], "HEAT must monotonically grow on a second positive vote");
		assertTrue(after2[1] > after1[1], "SPAM_EVIDENCE must monotonically grow on a second positive vote");

		// Weight scales with |votes|: a 2-vote increment must be ≈ 2× a single-vote increment
		// at the same moment in time.
		processVotes("030333333", 2, t);
		double[] doubled = rawEmas("030333333");
		processVotes("030444444", 1, t);
		double[] single = rawEmas("030444444");
		assertEquals(2.0 * single[0], doubled[0], 1e-9);
		assertEquals(2.0 * single[1], doubled[1], 1e-9);
	}

	protected void checkPhone(String phone, int votes, int cnt10, int votes10, int cnt100, int votes100) {
		try (SqlSession tx = _db.openSession()) {
			SpamReports reports = tx.getMapper(SpamReports.class);
			
			NumberInfo info = _db.getPhoneInfo(reports, phone);
			assertEquals(votes, info.getVotes());
			
			AggregationInfo aggregation10 = _db.getAggregation10(reports, phone);
			assertEquals(cnt10, aggregation10.getCnt());
			assertEquals(votes10, aggregation10.getVotes());
			
			AggregationInfo aggregation100 = _db.getAggregation100(reports, phone);
			assertEquals(cnt100, aggregation100.getCnt());
			assertEquals(votes100, aggregation100.getVotes());
			
		}
	}
	
	@Test
	void testRating() {
		_db.createUser("user-1", "User 1", "de", "+49");
		
		long time = 1;
		
		addRating("user-1", "0123456789", Rating.B_MISSED, "Don't know.", time++);
		addRating("user-1", "0123456789", Rating.C_PING, "Did not answer.", time++);
		
		// Only one rating recorded.
		assertEquals(1, _db.getVotesFor("0123456789"));
		
		// Only one comment per user per number - second comment replaced the first.
		assertEquals(1, getComments("0123456789").size());

		addRating("user-1", "0123456789", Rating.A_LEGITIMATE, "Was my uncle.", time++);

		assertEquals(0, _db.getVotesFor("0123456789"));

		// Still only one comment - third comment replaced the second.
		assertEquals(1, getComments("0123456789").size());
	}

	private void addRating(String userName, String phoneId, Rating rating, String comment, long now) {
		_db.addRating(userName, NumberAnalyzer.analyze(phoneId, "+49"), "+49", rating, comment, "de", now);
	}

	public List<? extends UserComment> getComments(String phone) {
		try (SqlSession session = _db.openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			return reports.getComments(phone, Collections.singleton("de"));
		}
	} 

	@Test
	public void testContribution() {
		_db.createUser("aaaaaaaa-bbbb", "Noname", "de", "+49");
		_db.createUser("cccccccc-dddd", "Egon Maier", "de", "+49");
		_db.createUser("eeeeeeee-ffff", "Erna Busch", "de", "+49");
		
		try (SqlSession tx = _db.openSession()) {
			Users users = tx.getMapper(Users.class);
			
			DB.processContribution(users, new MessageDetails("Danke, PhoneBlock-aaaaaaaa-bbbb!", "00001", 150, new GregorianCalendar(2025, 1, 2).getTime(), "Top Secret", "aaaaaaaa-bbbb"));
			DB.processContribution(users, new MessageDetails("Vielen Dank!", "00002", 100, new GregorianCalendar(2025, 1, 1).getTime(), "Egon Maier", "cccccccc-dddd"));
			DB.processContribution(users, new MessageDetails("Danke (xx@y.com)", "00003", 200, new GregorianCalendar(2025, 1, 3).getTime(), "Thanks!", null));
			DB.processContribution(users, new MessageDetails("", "00004", 500, new GregorianCalendar(2025, 1, 1).getTime(), "Erna Busch", null));
			
			// Process twice.
			DB.processContribution(users, new MessageDetails("Danke, PhoneBlock-aaaaaaaa-bbbb!", "00001", 150, new GregorianCalendar(2025, 1, 2).getTime(), "Top Secret", "aaaaaaaa-bbbb"));

			tx.commit();
			
			DBContribution contribution1 = users.getContribution("00001");
			DBContribution contribution2 = users.getContribution("00002");
			DBContribution contribution3 = users.getContribution("00003");
			DBContribution contribution4 = users.getContribution("00004");
			
			assertEquals(users.getUserId("aaaaaaaa-bbbb"), contribution1.getUserId());
			assertEquals(users.getUserId("cccccccc-dddd"), contribution2.getUserId());
			assertEquals(null, contribution3.getUserId());
			assertEquals(users.getUserId("eeeeeeee-ffff"), contribution4.getUserId());
			
			assertEquals(150, users.getSettingsRaw("aaaaaaaa-bbbb").getCredit());
			assertEquals(100, users.getSettingsRaw("cccccccc-dddd").getCredit());
			assertEquals(500, users.getSettingsRaw("eeeeeeee-ffff").getCredit());
			
			assertFalse(contribution1.isAcknowledged());
			users.ackContribution(contribution1.getId());
			DBContribution contribution1a = users.getContribution("00001");
			assertTrue(contribution1a.isAcknowledged());
		}
	}
	
	@Test
	public void testLastSearch() {
		try (SqlSession tx = _db.openSession()) {
			Users users = tx.getMapper(Users.class);

			long search0 = DB.getLastSearch(users);
			assertEquals(0, search0);
			
			DB.setLastSearch(users, 1000);

			long search1 = DB.getLastSearch(users);
			assertEquals(1000, search1);
		}
	}
	
	@Test 
	public void testInactiveUsers() {
		byte[] passwd = new byte[] {1, 2, 3};
		
		try (SqlSession tx = _db.openSession()) {
			Users users = tx.getMapper(Users.class);
			
			users.addUser("user-1", "U1", "de", "+49", passwd, 1000);
			users.addUser("user-2a", "U2a", "de", "+49", passwd, 2000);
			users.addUser("user-2b", "U2b", "de", "+49", passwd, 2000);
			users.addUser("user-3", "U3", "de", "+49", passwd, 3000);
			tx.commit();
		}
		
		long lastAccessBefore = 4000;
		long accessAfter = 1000;
		long registeredBefore = 3000;
		
		try (SqlSession tx = _db.openSession()) {
			Users users = tx.getMapper(Users.class);

			{
				List<DBUserSettings> inactiveUsers = users.getNewInactiveUsers(lastAccessBefore, accessAfter, registeredBefore);
				Set<String> ids = inactiveUsers.stream().map(u -> u.getLogin()).collect(Collectors.toSet());
				Assertions.assertEquals(new HashSet<>(Arrays.asList("user-2a", "user-2b")), ids);
			}
			
			users.setLastAccess("user-2a", 5000, "FRITZOS_CardDAV_Client/1.0");
			tx.commit();

			{
				List<DBUserSettings> inactiveUsers = users.getNewInactiveUsers(lastAccessBefore, accessAfter, registeredBefore);
				Set<String> ids = inactiveUsers.stream().map(u -> u.getLogin()).collect(Collectors.toSet());
				Assertions.assertEquals(new HashSet<>(Arrays.asList("user-2b")), ids);
			}
		}
			
		_db.createAPIToken("user-2b", (long) 5000, "SpamBlocker", null);

		try (SqlSession tx = _db.openSession()) {
			Users users = tx.getMapper(Users.class);
		
			{
				List<DBUserSettings> inactiveUsers = users.getNewInactiveUsers(lastAccessBefore, accessAfter, registeredBefore);
				Set<String> ids = inactiveUsers.stream().map(u -> u.getLogin()).collect(Collectors.toSet());
				Assertions.assertEquals(new HashSet<>(Arrays.asList()), ids);
			}
		}
	}

	/**
	 * Test that mergeLastMetaSearch correctly inserts new rows and updates existing ones.
	 *
	 * @see SpamReports#mergeLastMetaSearch(String, byte[], long)
	 */
	@Test
	public void testMergeLastMetaSearch() {
		String phone = "012345678";
		byte[] hash = new byte[] {1, 2, 3, 4, 5};

		try (SqlSession session = _db.openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);

			// Initially, no record exists
			assertNull(reports.getLastMetaSearch(phone));

			// First merge should insert
			long insertTime = 1000;
			reports.mergeLastMetaSearch(phone, hash, insertTime);
			session.commit();

			// Verify the record was created with correct LASTMETA
			Long lastMeta1 = reports.getLastMetaSearch(phone);
			assertNotNull(lastMeta1);
			assertEquals(insertTime, lastMeta1.longValue());

			// Verify ADDED was set
			DBNumberInfo info1 = reports.getPhoneInfo(phone);
			assertNotNull(info1);
			assertEquals(insertTime, info1.getAdded());

			// Second merge should update LASTMETA (and ADDED due to H2 MERGE KEY semantics)
			long updateTime = 2000;
			reports.mergeLastMetaSearch(phone, hash, updateTime);
			session.commit();

			// Verify LASTMETA was updated
			Long lastMeta2 = reports.getLastMetaSearch(phone);
			assertNotNull(lastMeta2);
			assertEquals(updateTime, lastMeta2.longValue());

			// Note: H2's MERGE KEY syntax overwrites all columns, so ADDED is also updated.
			// This is acceptable since in practice this only happens during race conditions
			// where both requests happen at nearly the same time.
			DBNumberInfo info2 = reports.getPhoneInfo(phone);
			assertNotNull(info2);
			assertEquals(updateTime, info2.getAdded());
		}
	}

	/**
	 * Test that concurrent mergeLastMetaSearch calls for the same phone number
	 * don't cause exceptions (race condition fix for issue #216).
	 */
	@Test
	public void testMergeLastMetaSearchConcurrent() throws Exception {
		String phone = "098765432";
		byte[] hash = new byte[] {9, 8, 7, 6, 5};
		int threadCount = 10;

		ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		CountDownLatch startLatch = new CountDownLatch(1);
		CountDownLatch doneLatch = new CountDownLatch(threadCount);
		List<Future<Exception>> futures = new ArrayList<>();

		// Submit tasks that all try to merge the same phone number simultaneously
		for (int i = 0; i < threadCount; i++) {
			final long time = 1000 + i;
			futures.add(executor.submit(() -> {
				try {
					// Wait for all threads to be ready
					startLatch.await();

					try (SqlSession session = _db.openSession()) {
						SpamReports reports = session.getMapper(SpamReports.class);
						reports.mergeLastMetaSearch(phone, hash, time);
						session.commit();
					}
					return null;
				} catch (Exception e) {
					return e;
				} finally {
					doneLatch.countDown();
				}
			}));
		}

		// Start all threads at once
		startLatch.countDown();

		// Wait for all threads to complete
		assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Threads did not complete in time");

		executor.shutdown();

		// Check that no exceptions occurred
		for (Future<Exception> future : futures) {
			Exception ex = future.get();
			if (ex != null) {
				fail("Concurrent merge failed with exception: " + ex.getMessage());
			}
		}

		// Verify the record exists
		try (SqlSession session = _db.openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			Long lastMeta = reports.getLastMetaSearch(phone);
			assertNotNull(lastMeta, "Record should exist after concurrent merges");
		}
	}

	@Test
	void testPersonalizationByHash() {
		_db.createUser("user1", "User 1", "de", "+49");

		try (SqlSession session = _db.openSession()) {
			Users users = session.getMapper(Users.class);
			long userId = users.getUserId("user1");

			BlockList blocklist = session.getMapper(BlockList.class);

			PhoneNumer number = NumberAnalyzer.analyzePhoneID("004912345678");
			String phone = NumberAnalyzer.getPhoneId(number);
			byte[] sha1 = NumberAnalyzer.getPhoneHash(number);

			// No personalization initially.
			assertNull(blocklist.resolvePersonalizationByHash(userId, sha1));

			// Add personal block.
			blocklist.addPersonalization(userId, phone, sha1, System.currentTimeMillis());
			session.commit();

			// Should find phone by hash, marked as blocked.
			DBPersonalization blocked = blocklist.resolvePersonalizationByHash(userId, sha1);
			assertNotNull(blocked);
			assertEquals(phone, blocked.getPhone());
			assertTrue(blocked.isBlocked());

			// Remove and add as whitelist.
			blocklist.removePersonalization(userId, phone);
			blocklist.addExclude(userId, phone, sha1, System.currentTimeMillis());
			session.commit();

			// Should still resolve by hash, now marked as not blocked.
			DBPersonalization whitelisted = blocklist.resolvePersonalizationByHash(userId, sha1);
			assertNotNull(whitelisted);
			assertEquals(phone, whitelisted.getPhone());
			assertFalse(whitelisted.isBlocked());
		}
	}

	@Test
	void testRatingWhitelistedNumber() throws Exception {
		_db.createUser("user1", "User 1", "de", "+49");

		PhoneNumer number = NumberAnalyzer.analyzePhoneID("004912345678");
		String phone = NumberAnalyzer.getPhoneId(number);

		// Add number to global whitelist using phone ID format.
		try (SqlSession session = _db.openSession()) {
			Connection conn = session.getConnection();
			try (Statement stmt = conn.createStatement()) {
				stmt.execute("INSERT INTO WHITELIST (PHONE) VALUES ('" + phone + "')");
			}
			session.commit();
		}

		// Rate the whitelisted number as spam.
		_db.addRating("user1", number, "+49", Rating.G_FRAUD, "test", "de", System.currentTimeMillis());

		// Personalization should exist.
		try (SqlSession session = _db.openSession()) {
			Users users = session.getMapper(Users.class);
			long userId = users.getUserId("user1");
			BlockList blocklist = session.getMapper(BlockList.class);

			Boolean state = blocklist.getPersonalizationState(userId, phone);
			assertTrue(state, "Number should be personally blocked");

			// NUMBERS table should NOT have this entry resolvable by hash.
			SpamReports reports = session.getMapper(SpamReports.class);
			assertNull(reports.resolvePhoneHash(NumberAnalyzer.getPhoneHash(number)),
				"Whitelisted number must not be resolvable by hash");
		}
	}

	public static DataSource createTestDataSource() {
		JdbcDataSource result = new JdbcDataSource();
		result.setUrl("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
		result.setUser("foo");
		result.setPassword("bar");
		return result;
	}
}
