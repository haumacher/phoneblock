/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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

	/**
	 * Tests that a request without a {@code User-Agent} header (null user agent) does not
	 * violate the NOT NULL constraint on the USERAGENT column when the token is updated.
	 */
	@Test
	void testAuthTokenWithoutUserAgent() {
		_db.createUser("ua-user", "UA User", "de", "+49");

		long time = 1000;
		AuthToken token = _db.createLoginToken("ua-user", time++, "creating-browser");

		// Skip rate limit so the update path (which writes USERAGENT) is exercised.
		time += DB.RATE_LIMIT_MS;

		// A client omitting the User-Agent header yields a null user agent; this used to
		// crash with "NULL not allowed for column USERAGENT".
		AuthContext context = _db.checkAuthToken(token.getToken(), time++, null, true);
		assertNotNull(context);
		assertEquals("-", context.getAuthorization().getUserAgent());
	}

	@Test
	void testTopSearches() {
		long day = DB.MILLIS_PER_DAY;
		long today = System.currentTimeMillis();
		long yesterday = today - day;
		long twoDaysAgo = today - 2 * day;

		// The list only shows spam-visible numbers, so give the two we assert on
		// some spam votes (does not affect the search counts).
		processVotes("091000000", 2, yesterday);
		processVotes("051000000", 2, yesterday);

		// Searches two days ago count toward the lifetime total but fall outside
		// the "today and yesterday" window.
		addSearchHit("091000000", twoDaysAgo);
		addSearchHit("051000000", twoDaysAgo);

		// Yesterday: 091 x3, 051 x1.
		addSearchHit("091000000", yesterday);
		addSearchHit("091000000", yesterday);
		addSearchHit("091000000", yesterday);
		addSearchHit("051000000", yesterday);

		// Today: 091 x1, 051 x1.
		addSearchHit("091000000", today);
		addSearchHit("051000000", today);

		List<? extends SearchInfo> topSearches = _db.getTopSearches(2, today);

		assertEquals(2, topSearches.size());

		// Ranked by the today+yesterday search count, descending (no longer
		// re-sorted by last-search time). A number quiet for two days has no recent
		// activity rows and cannot appear, so the old SEARCHES_CURRENT freeze is gone.
		assertEquals("091000000", topSearches.get(0).getPhone());
		assertEquals(4, topSearches.get(0).getCount());
		assertEquals(5, topSearches.get(0).getTotal());

		assertEquals("051000000", topSearches.get(1).getPhone());
		assertEquals(2, topSearches.get(1).getCount());
		assertEquals(3, topSearches.get(1).getTotal());
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

		addRating(null, "012300000", Rating.E_ADVERTISING, null, now++);
		
		assertEquals(Rating.E_ADVERTISING, _db.getRating("012300000"));
	}
	
	@Test
	void testSearchHistory() {
		String _123 = "012300000";
		String _456 = "045600000";
		String _789 = "078900000";

		long day = DB.MILLIS_PER_DAY;
		long today = System.currentTimeMillis();
		long d0 = today - 3 * day; // oldest day of the 4-day window
		long d1 = today - 2 * day;
		long d2 = today - 1 * day;
		long d3 = today;

		// d0: _123 x2, _456 x1.
		addSearchHit(_123, d0);
		addSearchHit(_123, d0);
		addSearchHit(_456, d0);

		// d1: _456 x1, _789 x1.
		addSearchHit(_456, d1);
		addSearchHit(_789, d1);

		// d2: _123 x1.
		addSearchHit(_123, d2);

		// d3 (today): _456 x2, _789 x1.
		addSearchHit(_456, d3);
		addSearchHit(_456, d3);
		addSearchHit(_789, d3);

		// Per-day search series, oldest day first, inactive days zero-filled. Each
		// day's value is that day's own searches — no baseline differencing.
		assertEquals(List.of(2, 0, 1, 0), searchSeries(_123, 4, today));
		assertEquals(List.of(1, 1, 0, 2), searchSeries(_456, 4, today));
		assertEquals(List.of(0, 1, 0, 1), searchSeries(_789, 4, today));
	}

	private List<Integer> searchSeries(String phone, int days, long now) {
		return _db.getNumberActivity(phone, days, now).stream()
			.map(DBDayActivity::getSearches).collect(Collectors.toList());
	}

	private void addSearchHit(String phone) {
		_db.addSearchHit(NumberAnalyzer.analyze(phone, "+49"), "+49");
	}

	@Test
	void testActivityRetention() {
		String phone = "012300000";
		long day = DB.MILLIS_PER_DAY;
		long today = System.currentTimeMillis();

		// One search 40 days ago (outside the 30-day retention) and one 5 days ago.
		addSearchHit(phone, today - 40 * day);
		addSearchHit(phone, today - 5 * day);

		// Both present before pruning (45-day window: index = days - 1 - age).
		List<Integer> before = searchSeries(phone, 45, today);
		assertEquals(45, before.size());
		assertEquals(1, before.get(45 - 1 - 40).intValue());
		assertEquals(1, before.get(45 - 1 - 5).intValue());

		int removed = _db.pruneActivity(today);
		assertEquals(1, removed);

		// The 40-day-old row is gone; the 5-day-old row (within retention) survives.
		List<Integer> after = searchSeries(phone, 45, today);
		assertEquals(0, after.get(45 - 1 - 40).intValue());
		assertEquals(1, after.get(45 - 1 - 5).intValue());
	}

	private void addSearchHit(String phone, long now) {
		_db.addSearchHit(NumberAnalyzer.analyze(phone, "+49"), "+49", now);
	}

	@Test
	void testActivityHistory() {
		String a = "012300000";
		String b = "045600000";
		String c = "078900000";

		long day = DB.MILLIS_PER_DAY;
		// Two activity days; query as of the day after, so both are closed days
		// (the global chart shows closed days only, today excluded).
		long t1 = System.currentTimeMillis() - 2 * day;
		long t2 = t1 + day;
		long queryNow = t2 + day;

		// Day t1: 2 searches (a), 3 votes (b), 1 call (a). Every number appears for
		// the first time here — with the ledger there is no baseline to diff, so
		// this first day is counted in full (the old snapshot model dropped it).
		addSearchHit(a, t1);
		addSearchHit(a, t1);
		processVotes(b, 3, t1);
		recordCall(a, t1);

		// Day t2: 1 search (a) + 1 search (c), 2 spam votes plus 1 legit vote (b),
		// 2 calls (a, c). The vote series counts votes cast by magnitude (2 + 1 = 3),
		// not the net spam balance, so a legit vote still registers as activity.
		addSearchHit(a, t2);
		addSearchHit(c, t2);
		processVotes(b, 2, t2);
		processVotes(b, -1, t2);
		recordCall(a, t2);
		recordCall(c, t2);

		Object[] history = _db.getCallsVotesSearchesHistory(30, queryNow);
		@SuppressWarnings("unchecked")
		List<String> labels = (List<String>) history[0];
		@SuppressWarnings("unchecked")
		List<Integer> calls = (List<Integer>) history[1];
		@SuppressWarnings("unchecked")
		List<Integer> votes = (List<Integer>) history[2];
		@SuppressWarnings("unchecked")
		List<Integer> searches = (List<Integer>) history[3];

		// 30 continuous closed days [queryNow-30 .. queryNow-1]. t1 lands at index
		// 28 (two days before queryNow), t2 at index 29 (the day before).
		assertEquals(30, labels.size());
		assertEquals(1, calls.get(28).intValue());
		assertEquals(2, calls.get(29).intValue());
		assertEquals(3, votes.get(28).intValue());
		assertEquals(3, votes.get(29).intValue());
		assertEquals(2, searches.get(28).intValue());
		assertEquals(2, searches.get(29).intValue());

		// Nothing else is reported on any other day.
		assertEquals(3, calls.stream().mapToInt(Integer::intValue).sum());
		assertEquals(6, votes.stream().mapToInt(Integer::intValue).sum());
		assertEquals(4, searches.stream().mapToInt(Integer::intValue).sum());
	}

	private void recordCall(String phone, long now) {
		recordCall("call-reporter", phone, now);
	}

	private void recordCall(String userName, String phone, long now) {
		long userId = ensureUser(userName);
		PhoneNumer number = NumberAnalyzer.analyze(phone, "+49");
		String id = NumberAnalyzer.getPhoneId(number);
		try (SqlSession tx = _db.openSession()) {
			SpamReports reports = tx.getMapper(SpamReports.class);
			BlockList blocklist = tx.getMapper(BlockList.class);
			_db.recordCall(reports, blocklist, userId, number, id, "+49", now);
			tx.commit();
		}
		// Drain the deferred block-aggregation recompute so tests see the effect immediately.
		_db.drainDirtyBlocks();
	}

	/** Looks up the test user by login, creating it on first use. */
	private long ensureUser(String userName) {
		try (SqlSession tx = _db.openSession()) {
			Long id = tx.getMapper(Users.class).getUserId(userName);
			if (id != null) {
				return id.longValue();
			}
		}
		_db.createUser(userName, userName, "de", "+49");
		try (SqlSession tx = _db.openSession()) {
			return tx.getMapper(Users.class).getUserId(userName).longValue();
		}
	}

	@Test
	void testWildcardReportWeightFormula() {
		// Cutting up to two trailing digits counts fully; each further digit halves the weight.
		assertEquals(1.0, DB.wildcardReportWeight(10, 10));
		assertEquals(1.0, DB.wildcardReportWeight(8, 10));
		assertEquals(0.5, DB.wildcardReportWeight(7, 10));
		assertEquals(0.25, DB.wildcardReportWeight(6, 10));
		assertEquals(0.125, DB.wildcardReportWeight(5, 10));
	}

	@Test
	void testWildcardListSeparation() {
		_db.createUser("wc-sep", "WC", "de", "+49");
		assertEquals("030123", _db.addWildcard("wc-sep", "+4930123", true, 100));

		try (SqlSession tx = _db.openSession()) {
			BlockList bl = tx.getMapper(BlockList.class);
			long userId = tx.getMapper(Users.class).getUserId("wc-sep").longValue();

			// Exact-only queries must not surface the wildcard.
			assertTrue(bl.getPersonalizations(userId).isEmpty());

			List<DBPersonalization> blocked = bl.getWildcardsWithCreated(userId, true);
			assertEquals(1, blocked.size());
			assertEquals("030123", blocked.get(0).getPhone());
			assertEquals(100, blocked.get(0).getCreated());

			// It is a blocked wildcard, not an allowed one.
			assertTrue(bl.getWildcardsWithCreated(userId, false).isEmpty());
		}
	}

	@Test
	void testWildcardBlockHidesButKeepsCoveredExactBlocks() {
		_db.createUser("wc-subsume", "WC", "de", "+49");

		try (SqlSession tx = _db.openSession()) {
			BlockList bl = tx.getMapper(BlockList.class);
			long userId = tx.getMapper(Users.class).getUserId("wc-subsume").longValue();

			// Two exact blocks under the prefix, one outside (not covered), and one allowed
			// (white-listed) entry under the prefix.
			bl.addPersonalization(userId, "0301230000", null, 1);
			bl.addPersonalization(userId, "0301239999", null, 1);
			bl.addPersonalization(userId, "0501234567", null, 1);
			bl.addExclude(userId, "0301235555", null, 1);
			tx.commit();
		}

		// Adding the blocking wildcard hides the covered exact blocks from display/export but keeps
		// the rows (and the per-user evidence they carry) intact — it does not delete them.
		assertEquals("030123", _db.addWildcard("wc-subsume", "+4930123", true, 100));

		try (SqlSession tx = _db.openSession()) {
			BlockList bl = tx.getMapper(BlockList.class);
			long userId = tx.getMapper(Users.class).getUserId("wc-subsume").longValue();

			// Covered exact blocks are hidden; only the block outside the prefix is displayed.
			assertEquals(List.of("0501234567"), bl.getPersonalizations(userId));

			// But the covered rows still exist (non-destructive) so their votes are preserved.
			assertNotNull(bl.getPersonalizationActivity(userId, "0301230000"),
				"covered exact block must be kept, only hidden from display");
			assertNotNull(bl.getPersonalizationActivity(userId, "0301239999"),
				"covered exact block must be kept, only hidden from display");

			// The allowed entry under the prefix is unaffected.
			assertEquals(List.of("0301235555"), bl.getWhiteList(userId));
		}
	}

	@Test
	void testWildcardAllowKeepsExactBlocks() {
		_db.createUser("wc-allow", "WC", "de", "+49");

		try (SqlSession tx = _db.openSession()) {
			BlockList bl = tx.getMapper(BlockList.class);
			long userId = tx.getMapper(Users.class).getUserId("wc-allow").longValue();
			bl.addPersonalization(userId, "0301230000", null, 1);
			tx.commit();
		}

		// An allowing wildcard does not touch exact blocks.
		assertEquals("030123", _db.addWildcard("wc-allow", "+4930123", false, 100));

		try (SqlSession tx = _db.openSession()) {
			BlockList bl = tx.getMapper(BlockList.class);
			long userId = tx.getMapper(Users.class).getUserId("wc-allow").longValue();
			assertEquals(List.of("0301230000"), bl.getPersonalizations(userId));
		}
	}

	@Test
	void testWildcardReportWeightLookup() {
		_db.createUser("wc-user", "WC", "de", "+49");
		assertEquals("030123", _db.addWildcard("wc-user", "+4930123", true, 0));

		try (SqlSession tx = _db.openSession()) {
			BlockList blocklist = tx.getMapper(BlockList.class);
			long userId = tx.getMapper(Users.class).getUserId("wc-user").longValue();

			// 0301234567 (len 10) under 030123 (len 6) -> cut 4 -> 0.25.
			assertEquals(0.25, _db.wildcardReportWeight(blocklist, userId, "0301234567"));
			// A number outside the wildcard yields no weight.
			assertEquals(0.0, _db.wildcardReportWeight(blocklist, userId, "0401234567"));
		}
	}

	@Test
	void testQuote() {
		assertEquals("\"\" 0x0 \"33a0a838-7b11-427a-\" 0x9 \"\" 0xD \"\" 0xA \"\" 0xC \"9c84-59b6ab6d3b0e\" 0x20 \"\"", DB.saveChars("\00033a0a838-7b11-427a-\t\r\n\f9c84-59b6ab6d3b0e "));
	}
	
	/** The gated block "wildcard" votes a caller would see for the given number (#300 follow-up). */
	private int wildcardVotes(String phone) {
		try (SqlSession tx = _db.openSession()) {
			SpamReports reports = tx.getMapper(SpamReports.class);
			NumberInfo info = _db.getPhoneInfo(reports, phone);
			AggregationInfo a10 = _db.getAggregation10(reports, phone);
			AggregationInfo a100 = _db.getAggregation100(reports, phone);
			return _db.getPhoneInfo(info, a10, a100).getVotesWildcard();
		}
	}

	/** A single heavily-voted number is one member — it must not turn its /10 into a spam block (#300). */
	@Test
	void testSingleNumberDoesNotPoisonBlock() {
		long now = System.currentTimeMillis();
		processVotes("0305200529031", 100, now);

		assertEquals(100, _db.getVotesFor("0305200529031"));
		assertEquals(0, wildcardVotes("0305200529031"), "one member -> /10 not a block");
		assertEquals(0, wildcardVotes("0305200529030"), "neighbour not poisoned");
	}

	/** Four distinct members (>= 2 votes) in a /10 make it a concentration spam block (#300). */
	@Test
	void testFourMembersMakeTenBlock() {
		long now = System.currentTimeMillis();
		processVotes("0305200529030", 2, now);
		processVotes("0305200529031", 2, now);
		processVotes("0305200529032", 2, now);
		assertEquals(0, wildcardVotes("0305200529039"), "three members: below the /10 gate");

		processVotes("0305200529033", 2, now);
		// Fourth member: /10 qualifies, an unvoted neighbour sees the block's net votes (4 x 2).
		assertEquals(8, wildcardVotes("0305200529039"));
	}

	/** A number must reach {@link DB#MIN_MEMBER_VOTES} displayed votes to count toward a block (#300). */
	@Test
	void testMemberThresholdIsTwo() {
		long now = System.currentTimeMillis();
		// Four distinct numbers, one vote each — none is a member, so no block forms.
		processVotes("0305200529030", 1, now);
		processVotes("0305200529031", 1, now);
		processVotes("0305200529032", 1, now);
		processVotes("0305200529033", 1, now);
		assertEquals(0, wildcardVotes("0305200529039"));
	}

	/**
	 * A spammer spreading 2 numbers across each of 4 /10 sub-blocks (none reaching the /10 gate of
	 * 4) still makes the /100 a spam block via spread×mass (#300 follow-up).
	 */
	@Test
	void testSpreadMassMakesHundredBlock() {
		long now = System.currentTimeMillis();
		// /100 "03052005290": numbers are "03052005290" + <ten-digit> + <unit>. Two members in each
		// of three /10 sub-blocks (6 numbers over 3 tens, none reaching the /10 gate): below /100 gate.
		for (int ten = 0; ten <= 2; ten++) {
			processVotes("03052005290" + ten + "0", 2, now);
			processVotes("03052005290" + ten + "1", 2, now);
		}
		assertEquals(0, wildcardVotes("0305200529099"), "3 tens / 6 members: below spread gate");

		// Fourth /10 sub-block with 2 members -> 8 members over 4 tens -> /100 qualifies.
		processVotes("0305200529030", 2, now);
		processVotes("0305200529031", 2, now);
		assertTrue(wildcardVotes("0305200529099") > 0, "8 members over 4 tens -> /100 spam block");
	}

	/**
	 * The hash-prefix mappers used by {@code /check-prefix} return only the aggregation rows of
	 * qualifying spam blocks (#300 follow-up): a /10 with at least {@link DB#MIN_AGGREGATE_10}
	 * current members, a /100 by spread×mass. Under-populated blocks have no row.
	 */
	@Test
	void testAggregationByHashPrefixOnlyQualifyingBlocks() {
		long now = System.currentTimeMillis();

		// /10 "04029996290": 4 members (>= 2 votes each) -> qualifies as a concentration block.
		processVotes("040299962900", 2, now);
		processVotes("040299962901", 2, now);
		processVotes("040299962902", 2, now);
		processVotes("040299962903", 2, now);

		// /10 "04029996280": one member -> below the /10 gate, no aggregation row.
		processVotes("040299962800", 2, now);

		byte[] low = new byte[20];
		byte[] high = new byte[20];
		Arrays.fill(high, (byte) 0xff);

		try (SqlSession tx = _db.openSession()) {
			SpamReports reports = tx.getMapper(SpamReports.class);
			Set<String> tens = reports.getAggregation10ByHashPrefix(low, high, DB.MIN_AGGREGATE_10)
				.stream().map(AggregationInfo::getPrefix).collect(Collectors.toSet());
			assertTrue(tens.contains("04029996290"), "dense /10 must qualify");
			assertFalse(tens.contains("04029996280"), "single-member /10 must not have a row");
		}

		// /100 "0402999629" by spread×mass: 2 members in each of three further /10 sub-blocks
		// (with …90 that is four contributing /10 and 10 members in total).
		processVotes("040299962910", 2, now);
		processVotes("040299962911", 2, now);
		processVotes("040299962930", 2, now);
		processVotes("040299962931", 2, now);
		processVotes("040299962940", 2, now);
		processVotes("040299962941", 2, now);

		try (SqlSession tx = _db.openSession()) {
			SpamReports reports = tx.getMapper(SpamReports.class);
			Set<String> hundreds = reports.getAggregation100ByHashPrefix(low, high, DB.MIN_AGGREGATE_100)
				.stream().map(AggregationInfo::getPrefix).collect(Collectors.toSet());
			assertTrue(hundreds.contains("0402999629"), "spread×mass /100 must qualify");
		}
	}

	/**
	 * Regression for the /10 aggregation clear boundary (incremental rebuild). A member one digit
	 * shorter than the number being rated produces a /10 block whose {@code PREFIX} equals the
	 * incremental rebuild's own /100 scope. {@link SpamReports#clearAggregation10ForHundred} must
	 * clear that boundary row with an inclusive lower bound; a strict {@code >} leaves it behind and
	 * {@link DB#writeBlocksFromTens} then collides with it on re-insert (unique-key violation on
	 * {@code NUMBERS_AGGREGATION_10(PREFIX)}), which surfaced as an HTTP 500 on {@code /api/rate}.
	 *
	 * <p>Numbers are synthetic (030-1234567…).</p>
	 */
	@Test
	void testIncrementalRebuildClearsBoundaryTenBlock() {
		long now = System.currentTimeMillis();

		// Four 11-digit members sharing the 10-digit prefix "0301234567" form a /10 concentration
		// block with PREFIX "0301234567" (written under the 9-digit /100 scope "030123456").
		processVotes("03012345670", 2, now);
		processVotes("03012345671", 2, now);
		processVotes("03012345672", 2, now);
		processVotes("03012345673", 2, now);
		assertTrue(wildcardVotes("03012345679") > 0, "four members -> /10 '0301234567' is a block");

		// Rating a 12-digit number in the same range runs the incremental rebuild for /100 scope
		// prefix100("030123456700") == "0301234567" — exactly the existing /10 block's prefix. The
		// clear must remove that row so the re-insert does not collide. Before the fix this threw a
		// unique-key violation (JdbcSQLIntegrityConstraintViolationException).
		assertDoesNotThrow(() -> processVotes("030123456700", 2, now),
			"incremental rebuild must clear the boundary /10 row before re-inserting it");

		// The /10 block survives the rebuild intact.
		assertTrue(wildcardVotes("03012345679") > 0, "/10 block intact after incremental rebuild");
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
		// Block aggregation is now recomputed off the request path; drain synchronously so tests
		// observe the block state deterministically instead of waiting for the timer.
		_db.drainDirtyBlocks();
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

		// Run the NUMBERS EMA backfill the same way migration 29 would (the block aggregation is
		// no longer backfilled here — it is rebuilt by recomputeBlockAggregation, #300 follow-up).
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
	void testRecordCallPerUserCap() {
		// Per-user spam-evidence cap: each user contributes at most one decoded unit of
		// SPAM_EVIDENCE to a number, however many calls they intercept from it; distinct users add
		// independently. HEAT and the CALLS counter stay uncapped (genuine activity that drives
		// space-constrained blocklist selection). The reporting user gets the number auto-added to
		// their personal blacklist so the contribution is tracked.

		long t = Ema.T0_MILLIS;
		double tau = Ema.CLASSIFICATION_TAU_MILLIS;
		String raw = "030555111222";
		PhoneNumer fresh = NumberAnalyzer.analyze(raw, "+49");
		String freshId = NumberAnalyzer.getPhoneId(fresh);

		// First report by user A — materialises the row with one decoded spam unit.
		recordCall("caller-A", raw, t);
		double[] after = rawEmas(freshId);
		assertTrue(after[0] > 0, "HEAT must be set on the freshly materialised row");
		assertEquals(1.0, Ema.decode(after[1], t, tau), 1e-9, "first call contributes one decoded spam unit");
		assertEquals(0.0, after[2], 0.0, "LEGIT_EVIDENCE must stay 0 (calls are not legit votes)");
		assertEquals(0, _db.getVotesFor(freshId), "Direct VOTES counter stays 0 — calls are not direct votes");

		// The intercepted number is auto-added to the reporter's personal blacklist.
		try (SqlSession tx = _db.openSession()) {
			BlockList bl = tx.getMapper(BlockList.class);
			long a = tx.getMapper(Users.class).getUserId("caller-A").longValue();
			assertEquals(Boolean.TRUE, bl.getPersonalizationState(a, freshId),
				"an intercepted call auto-adds the number to the reporter's blacklist");
		}

		// Second report by the SAME user — HEAT grows, decoded spam stays capped at 1.
		long later = t + 60_000L;
		recordCall("caller-A", raw, later);
		double[] capped = rawEmas(freshId);
		assertTrue(capped[0] > after[0], "HEAT must grow on the second report (uncapped)");
		assertEquals(1.0, Ema.decode(capped[1], later, tau), 1e-9,
			"a second call from the same user must not push decoded spam beyond 1");

		// A different user reporting the same number adds an independent unit (~2 total decoded).
		recordCall("caller-B", raw, later);
		double[] twoUsers = rawEmas(freshId);
		assertEquals(2.0, Ema.decode(twoUsers[1], later, tau), 1e-9,
			"a second distinct user adds another decoded spam unit");

		// Cold range — same path, same effect: row materialises with heat and one capped unit.
		String coldRaw = "020999888777";
		String coldId = NumberAnalyzer.getPhoneId(NumberAnalyzer.analyze(coldRaw, "+49"));
		recordCall("caller-A", coldRaw, t);
		double[] coldRow = rawEmas(coldId);
		assertTrue(coldRow[0] > 0, "cold-range report still materialises the row with HEAT");
		assertEquals(1.0, Ema.decode(coldRow[1], t, tau), 1e-9, "cold-range report gives one decoded spam unit");
	}

	@Test
	void testBlacklistRemovalSubtractsResidual() {
		// A user's spam contribution (rating + topped-up calls, capped at one decoded unit) is fully
		// reversed when they remove the number from their blacklist.
		_db.createUser("rm-user", "RM", "de", "+49");
		long t = Ema.T0_MILLIS;
		double tau = Ema.CLASSIFICATION_TAU_MILLIS;
		String raw = "030777000111";
		PhoneNumer number = NumberAnalyzer.analyze(raw, "+49");
		String id = NumberAnalyzer.getPhoneId(number);

		// Rating the number as spam adds one decoded unit.
		addRating("rm-user", raw, Rating.B_MISSED, null, t);
		assertEquals(1.0, Ema.decode(rawEmas(id)[1], t, tau), 1e-9, "rating adds one decoded spam unit");

		// Intercepted calls top up but stay capped at one unit.
		recordCall("rm-user", raw, t + 1000);
		recordCall("rm-user", raw, t + 2000);
		assertEquals(1.0, Ema.decode(rawEmas(id)[1], t + 2000, tau), 1e-9, "calls keep the contribution capped at 1");

		// Removing the number reverses the residual back to (near) zero.
		long now = t + 3000;
		removePersonalization("rm-user", id, number, now);
		assertEquals(0.0, Ema.decode(rawEmas(id)[1], now, tau), 1e-9, "removal subtracts the residual spam contribution");
	}

	@Test
	void testWhitelistRemovalSubtractsLegitResidual() {
		// Symmetric to the blacklist: whitelisting adds one decoded legit unit and removal reverses it.
		_db.createUser("wl-user", "WL", "de", "+49");
		long t = Ema.T0_MILLIS;
		double tau = Ema.CLASSIFICATION_TAU_MILLIS;
		String raw = "030888000222";
		PhoneNumer number = NumberAnalyzer.analyze(raw, "+49");
		String id = NumberAnalyzer.getPhoneId(number);

		addRating("wl-user", raw, Rating.A_LEGITIMATE, null, t);
		double[] e = rawEmas(id);
		assertEquals(1.0, Ema.decode(e[2], t, tau), 1e-9, "whitelisting adds one decoded legit unit");
		assertEquals(0.0, e[1], 0.0, "whitelisting adds no spam evidence");

		long now = t + 1000;
		removePersonalization("wl-user", id, number, now);
		assertEquals(0.0, Ema.decode(rawEmas(id)[2], now, tau), 1e-9, "removal subtracts the residual legit contribution");
	}

	/** Mirrors the servlet delete path: drop the entry and reverse the user's residual contribution. */
	private void removePersonalization(String userName, String phoneId, PhoneNumer number, long now) {
		try (SqlSession tx = _db.openSession()) {
			BlockList bl = tx.getMapper(BlockList.class);
			SpamReports reports = tx.getMapper(SpamReports.class);
			long userId = tx.getMapper(Users.class).getUserId(userName).longValue();
			DBPersonalization entry = bl.getPersonalizationActivity(userId, phoneId);
			bl.removePersonalization(userId, phoneId);
			if (entry != null) {
				_db.revertPersonalContribution(reports, phoneId, NumberAnalyzer.getPhoneHash(number),
					"+49", entry.isBlocked(), entry.getLastActivity(), 0, now);
			}
			tx.commit();
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
		// Drain the deferred block-aggregation recompute so tests see the effect immediately.
		_db.drainDirtyBlocks();
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
