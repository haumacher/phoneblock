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
import de.haumacher.phoneblock.app.api.model.BlockListEntry;
import de.haumacher.phoneblock.app.api.model.Blocklist;
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
		// Timestamps must be close to "now": getLatestSpamReports filters on the decay-aware
		// visibility threshold (#300), so a vote placed at e.g. the epoch reference t0 would
		// have decayed below one displayed vote by the time the test runs and would be filtered.
		long base = System.currentTimeMillis();

		assertFalse(_db.hasSpamReportFor("012300000"));

		processVotes("012300000", 2, base);

		assertTrue(_db.hasSpamReportFor("012300000"));

		processVotes("045600000", 1, base + 1);

		assertEquals(2, _db.getVotesFor("012300000"));

		assertFalse(_db.hasSpamReportFor("099900000"));
		assertEquals(0, _db.getVotesFor("099900000"));

		processVotes("099900000", -1, base + 2);
		assertEquals(-1, _db.getVotesFor("099900000"));

		processVotes("099900000", 0, base + 3);
		assertEquals(-1, _db.getVotesFor("099900000"));

		processVotes("012300000", 1, base + 4);
		assertEquals(3, _db.getVotesFor("012300000"));

		{
			List<? extends NumberInfo> reports = _db.getLatestSpamReports(base + 1);
			assertEquals(2, reports.size());
			assertEquals("012300000", reports.get(0).getPhone());
			assertEquals("045600000", reports.get(1).getPhone());
		}

		processVotes("012300000", -1, base + 5);
		assertEquals(2, _db.getVotesFor("012300000"));

		processVotes("012300000", -2, base + 6);
		assertEquals(0, _db.getVotesFor("012300000"));

		assertEquals(base + 6, _db.getLastSpamReport().longValue());

		List<? extends NumberInfo> reports = _db.getLatestSpamReports(base + 1);
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
	void testActivityHistory() {
		String a = "012300000";
		String b = "045600000";
		String c = "078900000";
		// Two distinct days: the snapshot only captures numbers whose LASTPING is
		// newer than the previous revision, so each day needs its own timestamp.
		long t1 = System.currentTimeMillis();
		long t2 = t1 + 24 * 60 * 60 * 1000;

		// Day 1: 2 searches (a), 3 votes (b), 1 call (a). All three numbers
		// appear for the first time here, so this revision has no predecessor
		// snapshot to diff against.
		addSearchHit(a, t1);
		addSearchHit(a, t1);
		processVotes(b, 3, t1);
		recordCall(a, t1);
		_db.updateHistory(30, t1);

		// Day 2: 1 search (a) and 1 search (c), 2 spam votes plus 1 legit vote
		// (b), 2 calls (a, c). a and b already have a day-1 snapshot, so their
		// increments are real deltas; c is a first appearance. b's net VOTES rise
		// by only 1 (2 down - 1 up), but three votes were cast, so the activity
		// series must report 3 - differencing the net counter would lose the
		// legit vote (and elsewhere even go negative).
		addSearchHit(a, t2);
		addSearchHit(c, t2);
		processVotes(b, 2, t2);
		processVotes(b, -1, t2);
		recordCall(a, t2);
		recordCall(c, t2);
		_db.updateHistory(30, t2);

		Object[] history = _db.getCallsVotesSearchesHistory(30);
		@SuppressWarnings("unchecked")
		List<String> labels = (List<String>) history[0];
		@SuppressWarnings("unchecked")
		List<Integer> calls = (List<Integer>) history[1];
		@SuppressWarnings("unchecked")
		List<Integer> votes = (List<Integer>) history[2];
		@SuppressWarnings("unchecked")
		List<Integer> searches = (List<Integer>) history[3];

		// One data point per snapshotted day; the deltas are summed across all
		// numbers. A row without a predecessor snapshot contributes 0 rather than
		// its full lifetime counter, so day 1 (all first appearances) and the
		// first-appearance number c on day 2 add nothing. Day 2 therefore only
		// reflects the real increments of the already-known numbers: a's extra
		// call and search, and b's three cast votes (2 spam + 1 legit) - the vote
		// series counts votes cast, not the net spam balance, so it stays
		// non-negative.
		assertEquals(2, labels.size());
		assertEquals(List.of(0, 1), calls);
		assertEquals(List.of(0, 3), votes);
		assertEquals(List.of(0, 1), searches);
	}

	private void recordCall(String phone, long now) {
		PhoneNumer number = NumberAnalyzer.analyze(phone, "+49");
		String id = NumberAnalyzer.getPhoneId(number);
		try (SqlSession tx = _db.openSession()) {
			SpamReports reports = tx.getMapper(SpamReports.class);
			_db.recordCall(reports, number, id, "+49", now);
			tx.commit();
		}
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

	/**
	 * The prev/next navigation on the number page must skip numbers whose
	 * classification has decayed so far that the displayed vote count rounds to
	 * 0 — landing on such a faded number would show an empty page (#300).
	 */
	@Test
	void testNavigationSkipsDecayedNumbers() {
		long now = Ema.T0_MILLIS + 365L * 86_400_000L;
		// A single vote older than the classification half-life (125 d) decays to a
		// decoded value below 0.5, i.e. a displayed vote count of 0.
		long longAgo = now - 200L * 86_400_000L;

		processVotes("030100000", 4, now);      // active — fresh votes
		processVotes("030200000", 1, longAgo);  // decayed — displays 0 votes
		processVotes("030300000", 4, now);      // active — fresh votes

		double minRawSpam = DB.maxRawSpamAt(now, 1);
		try (SqlSession tx = _db.openSession()) {
			SpamReports reports = tx.getMapper(SpamReports.class);

			// Sanity: without the visibility filter the decayed number is the
			// immediate neighbour in both directions.
			assertEquals("030200000", reports.getNextPhone("030150000", 0.0));
			assertEquals("030200000", reports.getPrevPhone("030250000", 0.0));

			// With the filter the decayed neighbour is skipped.
			assertEquals("030300000", reports.getNextPhone("030150000", minRawSpam));
			assertEquals("030100000", reports.getPrevPhone("030250000", minRawSpam));
		}
	}

	/**
	 * The SHA1 reverse-lookup hash must exist exactly for spam-visible numbers (#300
	 * privacy guard): a pure search must not store it, a spam signal populates it, and a
	 * number voted/decayed back to legitimate must not keep it.
	 */
	@Test
	void testHashTracksSpamVisibility() {
		long now = Ema.T0_MILLIS + 10L * 86_400_000L;
		String phone = "035000000";
		PhoneNumer number = NumberAnalyzer.analyze(phone, "+49");
		byte[] expectedHash = NumberAnalyzer.getPhoneHash(number);

		// A pure search adds Heat only — no spam evidence, so no rainbow-table entry.
		_db.addSearchHit(number, "+49", now);
		assertNull(rawSha1(phone), "Search must not store the SHA1 hash");

		// A spam vote makes the number spam-visible — the hash is populated.
		processVotes(phone, 2, now);
		Assertions.assertArrayEquals(expectedHash, rawSha1(phone), "Spam vote must populate the SHA1 hash");

		// Enough 'legitimate' votes push LEGIT_EVIDENCE above SPAM_EVIDENCE — hash cleared again.
		processVotes(phone, -5, now);
		assertNull(rawSha1(phone), "A number voted to legitimate must not keep the SHA1 hash");
	}

	/**
	 * Migration 37 must clear the SHA1 hash of numbers that are not spam-visible
	 * (the rainbow-table garbage left by the old search/meta insert paths), while
	 * keeping it for genuine spam. Runs the real {@code db-migration-37.sql}.
	 */
	@Test
	void testMigration37ClearsLegitHashes() throws Exception {
		long now = Ema.T0_MILLIS + 10L * 86_400_000L;

		// Genuine spam: SPAM_EVIDENCE > LEGIT_EVIDENCE → hash must survive.
		String spam = "036000000";
		processVotes(spam, 2, now);
		assertNotNull(rawSha1(spam), "precondition: spam number has a hash");

		// Legitimate, never-voted number with a stale hash (as the old buggy
		// search/meta path would have left behind): evidence 0/0, hash forced.
		String legit = "036100000";
		_db.addSearchHit(NumberAnalyzer.analyze(legit, "+49"), "+49", now);
		try (Connection conn = _dataSource.getConnection();
				PreparedStatement stmt = conn.prepareStatement("update NUMBERS set SHA1 = ? where PHONE = ?")) {
			stmt.setBytes(1, new byte[] {1, 2, 3, 4, 5});
			stmt.setString(2, legit);
			assertEquals(1, stmt.executeUpdate());
		}
		assertNotNull(rawSha1(legit), "precondition: stale hash is present");

		// Run the actual migration script.
		try (Connection conn = _dataSource.getConnection();
				java.io.InputStream in = SpamReports.class.getResourceAsStream("db-migration-37.sql")) {
			assertNotNull(in, "db-migration-37.sql must be on the classpath");
			org.apache.ibatis.jdbc.ScriptRunner sr = new org.apache.ibatis.jdbc.ScriptRunner(conn);
			sr.setAutoCommit(true);
			sr.setStopOnError(true);
			sr.setLogWriter(null);
			sr.runScript(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
		}

		assertNull(rawSha1(legit), "Migration must clear the hash of a non-spam number");
		assertNotNull(rawSha1(spam), "Migration must keep the hash of a spam number");
	}

	/**
	 * The /check-prefix hash lookup must not return numbers whose decoded net evidence
	 * rounds to 0 displayed votes, even though they still carry a SHA1 hash (#300).
	 */
	@Test
	void testHashPrefixExcludesRoundedZeroVotes() {
		long now = Ema.T0_MILLIS + 365L * 86_400_000L;
		long longAgo = now - 200L * 86_400_000L; // > classification half-life ago → decodes below 0.5

		processVotes("037000000", 4, now);      // active spam
		processVotes("037100000", 1, longAgo);  // decayed → rounds to 0 votes

		// Both carry a hash (both had net-positive spam evidence when voted).
		assertNotNull(rawSha1("037000000"));
		assertNotNull(rawSha1("037100000"));

		byte[] low = new byte[20];
		byte[] high = new byte[20];
		Arrays.fill(high, (byte) 0xFF);
		try (SqlSession tx = _db.openSession()) {
			SpamReports reports = tx.getMapper(SpamReports.class);

			// Bare net-positive filter (threshold 0) still returns the decayed number...
			Set<String> loose = reports.getPhoneInfosByHashPrefix(low, high, 0.0)
				.stream().map(DBNumberInfo::getPhone).collect(Collectors.toSet());
			assertTrue(loose.contains("037100000"));

			// ...the displayed-votes>=1 threshold drops it, keeping only the active number.
			Set<String> strict = reports.getPhoneInfosByHashPrefix(low, high, DB.maxRawSpamAt(now, 1))
				.stream().map(DBNumberInfo::getPhone).collect(Collectors.toSet());
			assertTrue(strict.contains("037000000"));
			assertFalse(strict.contains("037100000"), "decayed 0-vote number must not be returned");
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

	/** Raw SHA1 column straight from NUMBERS — {@code null} if the number is absent or its hash was cleared (#300 privacy guard). */
	private byte[] rawSha1(String phone) {
		try (Connection conn = _dataSource.getConnection();
				PreparedStatement stmt = conn.prepareStatement(
					"select SHA1 from NUMBERS where PHONE = ?")) {
			stmt.setString(1, phone);
			try (ResultSet rs = stmt.executeQuery()) {
				if (!rs.next()) {
					return null; // row absent
				}
				return rs.getBytes(1);
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
	void testConfidenceEmaBackfillAppliesTimeProjection() {
		// Regression for the integer-division bug in backfillNumbersEmas: H2
		// inferred the EXP() bind parameters as BIGINT, so (eventTime - t0) / tau
		// truncated to 0 and the projection collapsed to EXP(0) = 1, leaving the
		// raw EMA columns equal to the bare vote counts regardless of time.
		//
		// Seed all activity 90 days AFTER t0 — far enough that the correct
		// projection factor differs sharply from 1, so the bug is unmissable.
		long ninetyDaysMillis = 90L * 86_400_000L;
		long t = Ema.T0_MILLIS + ninetyDaysMillis;

		processVotes("030555000020", -1, t); // one LEGIT vote
		processVotes("030555000020", 1, t);  // one SPAM vote on the same number

		int[] counters = rawVoteCounters("030555000020");
		assertEquals(1, counters[0], "DOWN_VOTES");
		assertEquals(1, counters[1], "UP_VOTES");

		// Mimic the migration-29 starting state: counters set, EMAs still zero.
		zeroOutEmas();

		try (SqlSession session = _db.openSession()) {
			MigrationStatements migrations = session.getMapper(MigrationStatements.class);
			migrations.backfillNumbersEmas((double) Ema.T0_MILLIS,
				Ema.HEAT_TAU_MILLIS, Ema.CLASSIFICATION_TAU_MILLIS,
				Signals.DIRECT_VOTE_HEAT_WEIGHT,
				Signals.DIRECT_VOTE_EVIDENCE_WEIGHT,
				Signals.CALL_HEAT_WEIGHT,
				Signals.CALL_EVIDENCE_WEIGHT,
				Signals.SEARCH_HEAT_WEIGHT);
			session.commit();
		}

		double classFactor = Math.exp((double) ninetyDaysMillis / Ema.CLASSIFICATION_TAU_MILLIS);
		double heatFactor = Math.exp((double) ninetyDaysMillis / Ema.HEAT_TAU_MILLIS);

		double[] after = rawEmas("030555000020");
		// SPAM_EVIDENCE = DOWN_VOTES × weight × classFactor; LEGIT likewise from
		// UP_VOTES; HEAT = (DOWN_VOTES + UP_VOTES) × weight × heatFactor.
		assertEquals(Signals.DIRECT_VOTE_EVIDENCE_WEIGHT * classFactor, after[1], 1e-6,
			"raw SPAM_EVIDENCE must carry the time projection, not the bare vote count");
		assertEquals(Signals.DIRECT_VOTE_EVIDENCE_WEIGHT * classFactor, after[2], 1e-6,
			"raw LEGIT_EVIDENCE must carry the time projection, not the bare vote count");
		assertEquals(2 * Signals.DIRECT_VOTE_HEAT_WEIGHT * heatFactor, after[0], 1e-6,
			"raw HEAT must carry the time projection");

		// Explicit guard against the regression: the projected value is clearly
		// above the bare count of 1 (classFactor ≈ 1.65), so a collapsed
		// projection (== 1.0) would fail here.
		assertTrue(after[2] > 1.5,
			"LEGIT_EVIDENCE must be the projected ~1.65, not the integer-division 1.0, was " + after[2]);
	}

	@Test
	void testConfidenceEmaBackfillFromExistingCounters() {
		// Simulate the migration-29 starting state: pre-existing rows whose
		// cumulative counters are filled but whose EMA columns are still zero.
		// processVotes via the post-#332 path would already write the EMAs, so
		// we have to zero them out after seeding to mimic the upgrade.

		// Use t = T0_MILLIS so exp((t − t0)/τ) = 1 and the raw EMA value equals
		// the unprojected weight — keeps the arithmetic trivially auditable.
		long t = Ema.T0_MILLIS;

		// Three SPAM votes and one LEGIT vote, all on the same number. Net
		// VOTES = 2, DOWN_VOTES = 3, UP_VOTES = 1.
		processVotes("030555000010", 1, t);
		processVotes("030555000010", 1, t);
		processVotes("030555000010", 1, t);
		processVotes("030555000010", -1, t);
		// Sibling so the /10 aggregation row has cnt > 0:
		processVotes("030555000011", 1, t);
		processVotes("030555000012", 1, t);
		processVotes("030555000013", 1, t);

		// Sanity: confirm DOWN_VOTES/UP_VOTES match expectation before zero-out.
		int[] counters = rawVoteCounters("030555000010");
		assertEquals(3, counters[0], "DOWN_VOTES");
		assertEquals(1, counters[1], "UP_VOTES");

		zeroOutEmas();

		// Sanity: EMAs are now zero.
		double[] before = rawEmas("030555000010");
		assertEquals(0.0, before[0]);
		assertEquals(0.0, before[1]);
		assertEquals(0.0, before[2]);
		double[] aggBefore = rawAggEmas("03055500001", 10);
		assertNotNull(aggBefore);
		assertEquals(0.0, aggBefore[0]);
		assertEquals(0.0, aggBefore[1]);

		// Run the backfill the same way migration 29 would.
		try (SqlSession session = _db.openSession()) {
			MigrationStatements migrations = session.getMapper(MigrationStatements.class);

			int n = migrations.backfillNumbersEmas((double) Ema.T0_MILLIS,
				Ema.HEAT_TAU_MILLIS, Ema.CLASSIFICATION_TAU_MILLIS,
				Signals.DIRECT_VOTE_HEAT_WEIGHT,
				Signals.DIRECT_VOTE_EVIDENCE_WEIGHT,
				Signals.CALL_HEAT_WEIGHT,
				Signals.CALL_EVIDENCE_WEIGHT,
				Signals.SEARCH_HEAT_WEIGHT);
			assertTrue(n >= 4, "Backfill must touch all four seeded numbers, was " + n);

			int agg10n = migrations.backfillAggregation10Emas();
			assertTrue(agg10n >= 1, "Backfill must touch the /10 aggregation row, was " + agg10n);

			int agg100n = migrations.backfillAggregation100Emas();
			assertTrue(agg100n >= 1, "Backfill must touch the /100 aggregation row, was " + agg100n);

			session.commit();
		}

		// After backfill: number EMAs reflect the pre-existing counters.
		double[] after = rawEmas("030555000010");
		assertTrue(after[0] > 0, "HEAT must be > 0 after backfill, was " + after[0]);
		assertTrue(after[1] > 0, "SPAM_EVIDENCE must be > 0 after backfill, was " + after[1]);
		assertTrue(after[2] > 0, "LEGIT_EVIDENCE must be > 0 after backfill, was " + after[2]);

		// With t = t0 the projection factor is exactly 1, so raw HEAT equals
		// the unprojected weight: (DOWN_VOTES + UP_VOTES) × DIRECT_VOTE_HEAT_WEIGHT = 4 × 1.0.
		assertEquals(4.0, after[0], 1e-9, "raw HEAT must equal lumped weight at t = t0");
		assertEquals(3.0, after[1], 1e-9, "raw SPAM_EVIDENCE must equal DOWN_VOTES × weight");
		assertEquals(1.0, after[2], 1e-9, "raw LEGIT_EVIDENCE must equal UP_VOTES × weight");

		// At now (well after t0) the decoded value reflects natural decay — a
		// number with all its activity at t0 has decayed strongly already.
		double decodedNow = Ema.decode(after[0], System.currentTimeMillis(), Ema.HEAT_TAU_MILLIS);
		assertTrue(decodedNow > 0 && decodedNow < 4.0,
			"Decoded HEAT at now must be > 0 and below the raw value (decay applied), was " + decodedNow);

		// Aggregation rows: must be the sum of the per-number EMAs in the block.
		double[] agg10After = rawAggEmas("03055500001", 10);
		assertNotNull(agg10After);
		assertTrue(agg10After[0] > 0, "/10 HEAT must be > 0 after backfill, was " + agg10After[0]);
		// /10 block carries 030555000010..030555000013 — 4 numbers — so its
		// EMA should be at least as large as the single-number value.
		assertTrue(agg10After[0] >= after[0],
			"/10 HEAT must be >= number's HEAT (sum over block), was " + agg10After[0] + " vs " + after[0]);

		// /100 block (prefix '0305550000') sums the same four /10 numbers.
		double[] agg100After = rawAggEmas("0305550000", 100);
		assertNotNull(agg100After);
		assertTrue(agg100After[0] >= after[0]);
	}

	/** Raw DOWN_VOTES, UP_VOTES, CALLS, SEARCHES from NUMBERS — for backfill assertions. */
	private int[] rawVoteCounters(String phone) {
		try (Connection conn = _dataSource.getConnection();
				PreparedStatement stmt = conn.prepareStatement(
					"select DOWN_VOTES, UP_VOTES, CALLS, SEARCHES from NUMBERS where PHONE = ?")) {
			stmt.setString(1, phone);
			try (ResultSet rs = stmt.executeQuery()) {
				assertTrue(rs.next(), "No row for " + phone);
				return new int[] { rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4) };
			}
		} catch (SQLException ex) {
			throw new RuntimeException(ex);
		}
	}

	/** Zero out every EMA column in NUMBERS and the aggregation tables. */
	private void zeroOutEmas() {
		try (Connection conn = _dataSource.getConnection();
				Statement stmt = conn.createStatement()) {
			stmt.execute("update NUMBERS set HEAT = 0, SPAM_EVIDENCE = 0, LEGIT_EVIDENCE = 0");
			stmt.execute("update NUMBERS_AGGREGATION_10 set HEAT = 0, SPAM_EVIDENCE = 0, LEGIT_EVIDENCE = 0");
			stmt.execute("update NUMBERS_AGGREGATION_100 set HEAT = 0, SPAM_EVIDENCE = 0, LEGIT_EVIDENCE = 0");
		} catch (SQLException ex) {
			throw new RuntimeException(ex);
		}
	}

	@Test
	void testHeatRankedBlocklist() {
		// Issue #336: ?limit=N returns the top-N currently-loudest spam numbers,
		// ordered by Heat — quiet old numbers drop out so currently-active ones
		// take their slot.
		//
		// All test numbers carry enough votes to clear DEFAULT_MIN_VISIBLE_VOTES.

		long now = System.currentTimeMillis();
		long oldT = now - 180L * 86_400_000L;  // half a year ago — ≈ 13 Heat half-lives

		int freshVotes = DB.DEFAULT_MIN_VISIBLE_VOTES + 2;
		// #342: old numbers must carry enough cumulative votes that their
		// decoded SPAM_EVIDENCE 180 d later still clears the visibility floor.
		// 180 d is ~1.44 classification half-lives ⇒ decay factor ≈ 0.37;
		// freshVotes × 3 keeps them comfortably above the cut so we can still
		// test the Heat-ranking behaviour (fresh wins) without the visibility
		// filter masking the test.
		int oldVotes = freshVotes * 3;

		// Three loud "old" numbers — votes long ago. Heat has decayed strongly
		// but SPAM_EVIDENCE is still above the visibility threshold.
		for (int i = 0; i < oldVotes; i++) {
			processVotes("030992200010", 1, oldT - i * 60_000L);
			processVotes("030992200020", 1, oldT - i * 60_000L);
			processVotes("030992200030", 1, oldT - i * 60_000L);
		}

		// Two "fresh" numbers — fewer votes, but they happened just now.
		for (int i = 0; i < freshVotes; i++) {
			processVotes("030992200040", 1, now - i * 60_000L);
			processVotes("030992200041", 1, now - i * 60_000L);
		}

		Blocklist top2 = _db.getBlockListByHeatAPI(null, 2);
		assertNotNull(top2);
		assertEquals(2, top2.getNumbers().size(), "limit must clip to exactly 2");

		Set<String> topPhones = top2.getNumbers().stream()
			.map(BlockListEntry::getPhone)
			.collect(Collectors.toSet());

		// Both fresh numbers must beat the long-dormant ones on Heat ranking.
		assertTrue(topPhones.contains("+4930992200040"), "fresh number 1 must be in top-2");
		assertTrue(topPhones.contains("+4930992200041"), "fresh number 2 must be in top-2");

		// Asking for more entries pulls the old ones in too.
		Blocklist all = _db.getBlockListByHeatAPI(null, 100);
		assertTrue(all.getNumbers().size() >= 5,
			"larger limit must include the old numbers as well, was " + all.getNumbers().size());
	}

	@Test
	void testHeatRankedBlocklistPartitionsByDial() {
		// Issue #340: the space-limited blocklist must be composed from the
		// region the reports originate from. A number reported only by US
		// users must not push numbers off a German client's top-N (and vice
		// versa). The dial argument selects the per-region Heat from
		// NUMBERS_LOCALE; null falls back to the global ranking.

		long now = System.currentTimeMillis();
		int votes = DB.DEFAULT_MIN_VISIBLE_VOTES + 2;

		// German users vote down a German number.
		PhoneNumer dePhone = NumberAnalyzer.analyze("030330000001", "+49");
		for (int i = 0; i < votes; i++) {
			_db.processVotes(dePhone, "+49", 1, now - i * 60_000L);
		}

		// US users vote down a different German number — same number-space, but
		// the reports come from a different region. Picking a German number
		// keeps the test independent of the international-number id format and
		// proves the partitioning is by *reporter* dial, not by phone-country.
		PhoneNumer otherPhone = NumberAnalyzer.analyze("030330000002", "+49");
		for (int i = 0; i < votes; i++) {
			_db.processVotes(otherPhone, "+1", 1, now - i * 60_000L);
		}

		Blocklist de = _db.getBlockListByHeatAPI("+49", 10);
		Set<String> dePhones = de.getNumbers().stream()
			.map(BlockListEntry::getPhone).collect(Collectors.toSet());
		assertTrue(dePhones.contains("+4930330000001"),
			"German top-N must contain the German-reported number");
		assertFalse(dePhones.contains("+4930330000002"),
			"German top-N must not contain the US-reported number");

		Blocklist us = _db.getBlockListByHeatAPI("+1", 10);
		Set<String> usPhones = us.getNumbers().stream()
			.map(BlockListEntry::getPhone).collect(Collectors.toSet());
		assertTrue(usPhones.contains("+4930330000002"),
			"US top-N must contain the US-reported number");
		assertFalse(usPhones.contains("+4930330000001"),
			"US top-N must not contain the German-reported number");

		// The global view (dial=null) carries both — same as before #340.
		Blocklist global = _db.getBlockListByHeatAPI(null, 10);
		Set<String> globalPhones = global.getNumbers().stream()
			.map(BlockListEntry::getPhone).collect(Collectors.toSet());
		assertTrue(globalPhones.contains("+4930330000001"));
		assertTrue(globalPhones.contains("+4930330000002"));
	}

	@Test
	void testApiVotesAreDecayAware() {
		// Issue #338: PhoneInfo.votes is no longer the raw cumulative counter
		// but `round(decoded SPAM_EVIDENCE - decoded LEGIT_EVIDENCE)`. Clients
		// that filter `votes >= minVotes AND !archived` therefore see a
		// smooth decay through the threshold instead of a binary archive
		// flip. The raw NUMBERS.VOTES column is unchanged and still drives
		// the rating; only the API-output `votes` is decay-aware now.

		long fresh = System.currentTimeMillis() - 60_000L;
		// Two years before t0 — easily six classification half-lives by "now",
		// so a 10-vote burst decays to ≈ 10 · 2^-6 = 0.16, well below rounding.
		long ancient = Ema.T0_MILLIS - 2L * 365L * 86_400_000L;

		// Number A: ten fresh SPAM votes — decoded SPAM_EVIDENCE ≈ 10.
		for (int i = 0; i < 10; i++) {
			processVotes("030993300010", 1, fresh - i * 1000L);
		}
		// Number B: ten ancient SPAM votes — decoded is decayed almost to zero.
		for (int i = 0; i < 10; i++) {
			processVotes("030993300020", 1, ancient - i * 1000L);
		}

		// Cumulative counter assertions remain valid via the DB-level helper.
		assertEquals(10, _db.getVotesFor("030993300010"));
		assertEquals(10, _db.getVotesFor("030993300020"));

		PhoneInfo freshApi = _db.getPhoneApiInfo("030993300010");
		PhoneInfo ancientApi = _db.getPhoneApiInfo("030993300020");

		// API-side votes: fresh number reads ~10, ancient reads near 0.
		assertTrue(freshApi.getVotes() >= 9 && freshApi.getVotes() <= 11,
			"Fresh 10-vote number must read votes ≈ 10 (was " + freshApi.getVotes() + ")");
		assertEquals(0, ancientApi.getVotes(),
			"Heavily decayed number must read votes = 0 (was " + ancientApi.getVotes() + ")");

		// Number C: ten SPAM votes plus three LEGIT — net spam evidence ≈ 7.
		for (int i = 0; i < 10; i++) {
			processVotes("030993300030", 1, fresh - i * 1000L);
		}
		for (int i = 0; i < 3; i++) {
			processVotes("030993300030", -1, fresh - i * 500L);
		}
		PhoneInfo netApi = _db.getPhoneApiInfo("030993300030");
		assertTrue(netApi.getVotes() >= 6 && netApi.getVotes() <= 8,
			"Mixed SPAM/LEGIT must net out (~10 − 3), was " + netApi.getVotes());
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
	void testRecordCallUnifiedPath() {
		// #342 consolidation: every reported call — Fritz!Box, dongle, app,
		// answer-bot, all the same path — adds +1 Heat and +1 evidence to
		// NUMBERS (materialising the row if it doesn't exist yet), to the
		// /10 and /100 aggregations, and to the per-region NUMBERS_LOCALE
		// row. No wildcard-match gate, no firstFromUser dedup — one call,
		// one signal.

		long t = Ema.T0_MILLIS;

		// First report for an unknown number — materialises the row.
		PhoneNumer fresh = NumberAnalyzer.analyze("030555111222", "+49");
		String freshId = NumberAnalyzer.getPhoneId(fresh);
		try (SqlSession tx = _db.openSession()) {
			SpamReports reports = tx.getMapper(SpamReports.class);
			assertNull(reports.getVotes(freshId), "Number must not exist before the report");
			_db.recordCall(reports, fresh, freshId, "+49", t);
			tx.commit();
		}

		double[] after = rawEmas(freshId);
		assertTrue(after[0] > 0, "HEAT must be set on the freshly materialised row");
		assertTrue(after[1] > 0, "SPAM_EVIDENCE must be set on the freshly materialised row");
		assertEquals(0.0, after[2], 0.0, "LEGIT_EVIDENCE must stay 0 (calls are not legit votes)");
		assertEquals(0, _db.getVotesFor(freshId), "Direct VOTES counter stays 0 — calls are not direct votes");

		double[] block10After = rawAggEmas("03055511122", 10);
		assertNotNull(block10After, "/10 aggregation row must exist after the report");
		assertTrue(block10After[0] > 0, "block HEAT must rise on the report");
		assertTrue(block10After[1] > 0, "block SPAM_EVIDENCE must rise on the report");

		// Second report on the same number — accumulates, no firstFromUser cap.
		long later = t + 60_000L;
		try (SqlSession tx = _db.openSession()) {
			SpamReports reports = tx.getMapper(SpamReports.class);
			_db.recordCall(reports, fresh, freshId, "+49", later);
			tx.commit();
		}
		double[] doubled = rawEmas(freshId);
		assertTrue(doubled[0] > after[0], "HEAT must grow on the second report");
		assertTrue(doubled[1] > after[1], "SPAM_EVIDENCE must also grow — no per-user cap");

		// Report into a cold range — same path, same effect: row materialises,
		// gets heat and evidence. No "range-must-be-hot" gate any more.
		PhoneNumer cold = NumberAnalyzer.analyze("020999888777", "+49");
		String coldId = NumberAnalyzer.getPhoneId(cold);
		try (SqlSession tx = _db.openSession()) {
			SpamReports reports = tx.getMapper(SpamReports.class);
			_db.recordCall(reports, cold, coldId, "+49", t);
			tx.commit();
		}
		double[] coldRow = rawEmas(coldId);
		assertTrue(coldRow[0] > 0, "cold-range report still materialises the row with HEAT");
		assertTrue(coldRow[1] > 0, "cold-range report still gives the new row SPAM_EVIDENCE");
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
			assertEquals(votes, info.getRawVotes());
			
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
	 * @see SpamReports#mergeLastMetaSearch(String, long)
	 */
	@Test
	public void testMergeLastMetaSearch() {
		String phone = "012345678";

		try (SqlSession session = _db.openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);

			// Initially, no record exists
			assertNull(reports.getLastMetaSearch(phone));

			// First merge should insert
			long insertTime = 1000;
			reports.mergeLastMetaSearch(phone, insertTime);
			session.commit();

			// Verify the record was created with correct LASTMETA
			Long lastMeta1 = reports.getLastMetaSearch(phone);
			assertNotNull(lastMeta1);
			assertEquals(insertTime, lastMeta1.longValue());

			// Privacy guard (#300): a meta-search placeholder carries no spam evidence,
			// so it must not enter the SHA1 reverse-lookup table.
			assertNull(rawSha1(phone));

			// Verify ADDED was set
			DBNumberInfo info1 = reports.getPhoneInfo(phone);
			assertNotNull(info1);
			assertEquals(insertTime, info1.getAdded());

			// Second merge should update LASTMETA (and ADDED due to H2 MERGE KEY semantics)
			long updateTime = 2000;
			reports.mergeLastMetaSearch(phone, updateTime);
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
						reports.mergeLastMetaSearch(phone, time);
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
