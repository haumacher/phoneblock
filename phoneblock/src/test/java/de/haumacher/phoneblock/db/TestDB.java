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
import java.sql.Statement;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;

import javax.sql.DataSource;

import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.session.SqlSession;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.haumacher.phoneblock.app.api.model.NumberInfo;
import de.haumacher.phoneblock.app.api.model.Rating;
import de.haumacher.phoneblock.app.api.model.SearchInfo;
import de.haumacher.phoneblock.db.settings.AuthToken;
import de.haumacher.phoneblock.db.settings.UserSettings;
import de.haumacher.phoneblock.scheduler.SchedulerService;

/**
 * Test case for {@link DB}.
 */
class TestDB {
	
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
	void testAuthToken() {
		_db.createUser("test", "test1", "user1", "User 1");

		long time = 1000;
		final long createTime = time;
		
		AuthToken token1 = _db.createLoginToken("user1", time++, "creating-browser");
		
		// Skip rate limit.
		time += DB.RATE_LIMIT_MS;
		
		final String origToken1 = token1.getToken();
		final long checkTime = time;
		assertNotNull(token1 = _db.checkAuthToken(token1.getToken(), time++, "other-browser", true));
		
		assertEquals(token1.getUserName(), "user1");
		assertEquals(createTime, token1.getCreated());
		assertEquals(checkTime, token1.getLastAccess());
		assertEquals("creating-browser", token1.getUserAgent());
		assertTrue(token1.isImplicit());
		assertTrue(token1.isAccessLogin());
		
		AuthToken token2 = _db.createLoginToken("user1", time++, "creating-browser");
		AuthToken token3 = _db.createLoginToken("user1", time++, "creating-browser");
		AuthToken token4 = _db.createLoginToken("user1", time++, "creating-browser");
		AuthToken token5 = _db.createLoginToken("user1", time++, "creating-browser");
		
		// Skip rate limit.
		time += DB.RATE_LIMIT_MS;
		
		String oldToken1 = token1.getToken();
		
		assertNotNull(token1 = _db.checkAuthToken(token1.getToken(), time++, "login-browser", true));
		assertNotNull(token2 = _db.checkAuthToken(token2.getToken(), time++, "login-browser", true));
		assertNotNull(token3 = _db.checkAuthToken(token3.getToken(), time++, "login-browser", true));
		assertNotNull(token4 = _db.checkAuthToken(token4.getToken(), time++, "login-browser", true));
		assertNotNull(token5 = _db.checkAuthToken(token5.getToken(), time++, "login-browser", true));
		
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

	@Test
	void testTopSearches() {
		long now = 1000000000000000000L;

		_db.addSearchHit("0", now++);
		_db.addSearchHit("5", now++);

		_db.updateHistory(30, now++);
		
		// Yesterday
		_db.addRating(null, "0", Rating.C_PING, null, now++);
		_db.addRating(null, "1", Rating.C_PING, null, now++);
		_db.addRating(null, "2", Rating.C_PING, null, now++);
		_db.addRating(null, "3", Rating.C_PING, null, now++);
		_db.addRating(null, "4", Rating.C_PING, null, now++);
		_db.addRating(null, "5", Rating.C_PING, null, now++);
		
		_db.addSearchHit("5", now++);
		_db.addSearchHit("0", now++);
		_db.addSearchHit("0", now++);
		_db.addSearchHit("0", now++);
		
		_db.addSearchHit("1", now++);
		_db.addSearchHit("2", now++);
		_db.addSearchHit("3", now++);
		_db.updateHistory(30, now++);
		
		// Today
		_db.addSearchHit("0", now++);
		_db.addSearchHit("4", now++);
		_db.addSearchHit("5", now++);
		
		List<? extends SearchInfo> topSearches = _db.getTopSearches(2);

		assertEquals(2, topSearches.size());
		
		assertEquals("5", topSearches.get(0).getPhone());
		assertEquals(2, topSearches.get(0).getCount());
		assertEquals(3, topSearches.get(0).getTotal());
		
		assertEquals("0", topSearches.get(1).getPhone());
		assertEquals(4, topSearches.get(1).getCount());
		assertEquals(5, topSearches.get(1).getTotal());
	}
	
	@Test
	void testSpamReports() {
		assertFalse(_db.hasSpamReportFor("123"));

		_db.processVotes("123", 2, 1000);
		
		assertTrue(_db.hasSpamReportFor("123"));
		
		_db.processVotes("456", 1, 1001);
		
		assertEquals(2, _db.getVotesFor("123"));
		
		assertFalse(_db.hasSpamReportFor("999"));
		assertEquals(0, _db.getVotesFor("999"));
		
		_db.processVotes("999", -1, 1002);
		assertEquals(-1, _db.getVotesFor("999"));
		
		_db.processVotes("999", 0, 1003);
		assertEquals(-1, _db.getVotesFor("999"));
		
		_db.processVotes("123", 1, 1004);
		assertEquals(3, _db.getVotesFor("123"));
		
		{
			List<? extends NumberInfo> reports = _db.getLatestSpamReports(1001);
			assertEquals(2, reports.size());
			assertEquals("123", reports.get(0).getPhone());
			assertEquals("456", reports.get(1).getPhone());
		}
		
		_db.processVotes("123", -1, 1005);
		assertEquals(2, _db.getVotesFor("123"));
		
		_db.processVotes("123", -2, 1006);
		assertEquals(0, _db.getVotesFor("123"));
		
		assertEquals(1006, _db.getLastSpamReport().longValue());
		
		List<? extends NumberInfo> reports = _db.getLatestSpamReports(1001);
		assertEquals(1, reports.size());
		assertEquals("456", reports.get(0).getPhone());
	}
	
	@Test
	void testBlocklist() {
		try (SqlSession session = _db.openSession()) {
			BlockList blockList = session.getMapper(BlockList.class);
			
			blockList.addExclude(1, "123");
			blockList.addExclude(2, "123");
			blockList.addExclude(1, "345");
			blockList.addExclude(1, "678");
			blockList.addExclude(2, "999");
			
			assertEquals(new HashSet<>(List.of("123", "345", "678")), blockList.getExcluded(1));
			
			blockList.removePersonalization(1, "345");
			
			assertEquals(new HashSet<>(List.of("123", "678")), blockList.getExcluded(1));
			
			blockList.removePersonalization(2, "123");
			
			assertEquals(new HashSet<>(List.of("123", "678")), blockList.getExcluded(1));
			
			blockList.addPersonalization(1, "654");
			blockList.addPersonalization(1, "321");
			blockList.addPersonalization(2, "321");
			blockList.addPersonalization(2, "987");
			
			assertEquals(List.of("321", "654"), blockList.getPersonalizations(1));
			
			blockList.removePersonalization(1, "654");
			
			assertEquals(List.of("321"), blockList.getPersonalizations(1));
			
			blockList.removePersonalization(2, "321");
			
			assertEquals(List.of("321"), blockList.getPersonalizations(1));
		}
	}

	@Test
	void testDuplicateAdd() {
		try (SqlSession session = _db.openSession()) {
			BlockList blockList = session.getMapper(BlockList.class);

			blockList.addExclude(1, "123");
			try {
				blockList.addExclude(1, "123");
				fail("Expecting duplicate key constraint violation.");
			} catch (PersistenceException ex) {
				// Expected.
			}
		}
	}
	
	@Test
	void testUserManagement() throws IOException {
		_db.addUser("none", "1", "foo@bar.com", "Mr. X", "123");
		_db.addUser("none", "2", "baz@bar.com", "Mr. Y", "123");
		
		assertEquals("foo@bar.com", _db.basicAuth(header("foo@bar.com", "123")));
        assertNull(_db.basicAuth(header("foo@bar.com", "321")));
        assertNull(_db.basicAuth(header("xxx@bar.com", "123")));
		
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
		
		_db.addRating(null, "123", Rating.G_FRAUD, null, now++);
		_db.addRating(null, "123", Rating.B_MISSED, null, now++);
		_db.addRating(null, "123", Rating.B_MISSED, null, now++);
		_db.addRating(null, "123", Rating.C_PING, null, now++);
		_db.addRating(null, "123", Rating.D_POLL, null, now++);
		_db.addRating(null, "123", Rating.E_ADVERTISING, null, now++);
		_db.addRating(null, "123", Rating.F_GAMBLE, null, now++);

		assertEquals(Rating.G_FRAUD, _db.getRating("123"));
		
		_db.updateHistory(10);
		
		assertEquals(Rating.G_FRAUD, _db.getRating("123"));
		
		_db.addRating(null, "123", Rating.E_ADVERTISING, null, now++);
		
		assertEquals(Rating.E_ADVERTISING, _db.getRating("123"));
	}
	
	@Test
	void testSearchHistory() {
		String _123 = "123";
		String _456 = "456";
		String _789 = "789";
		
		// A search far in the history.
		_db.addSearchHit(_123);
		
		// No more searches for three periods.
		_db.updateHistory(30);
		_db.updateHistory(30);
		_db.updateHistory(30);
		
		// The first day of the four day history.
		_db.addSearchHit(_123);
		_db.addSearchHit(_123);
		_db.addSearchHit(_456);
		
		_db.updateHistory(30);
		
		_db.addSearchHit(_456);
		_db.addSearchHit(_789);
		
		_db.updateHistory(30);
		
		_db.addSearchHit(_123);
		
		_db.updateHistory(30);
		
		_db.addSearchHit(_456);
		_db.addSearchHit(_456);
		_db.addSearchHit(_789);
		
		assertEquals(List.of(2, 0, 1, 0), _db.getSearchHistory(_123, 4));
		assertEquals(List.of(1, 1, 0, 2), _db.getSearchHistory(_456, 4));
		assertEquals(List.of(0, 1, 0, 1), _db.getSearchHistory(_789, 4));
	}
	
	@Test
	void testSearchHistoryCleanup() {
		long time = 1000;
		for (int n = 0; n < 49; n++) {
			_db.addSearchHit("123", time);
			_db.updateHistory(30, time);
			
			time++;
		}
		_db.addSearchHit("123", time);
		
		List<Integer> all = _db.getSearchHistory("123", 31);
		assertEquals(31, all.size());
		assertEquals(1, all.get(31 - 1));
		assertEquals(1, all.get(1));
		assertEquals(50 - 30, all.get(0));
		
		assertEquals(7, _db.getSearchHistory("123", 7).size());
	}
	
	@Test
	void testQuote() {
		assertEquals("\"\" 0x0 \"33a0a838-7b11-427a-\" 0x9 \"\" 0xD \"\" 0xA \"\" 0xC \"9c84-59b6ab6d3b0e\" 0x20 \"\"", DB.saveChars("\00033a0a838-7b11-427a-\t\r\n\f9c84-59b6ab6d3b0e "));
	}
	
	@Test
	void testAggregation() {
		long now = 0;
		
		_db.processVotes("040299962900", 1, now);
		_db.processVotes("040299962900", 1, now);
		_db.processVotes("040299962901", 1, now);
		
		checkPhone("040299962900", 2, 2, 3, 0, 0);
		checkPhone("040299962909", 0, 2, 3, 0, 0);
		checkPhone("040299962999", 0, 0, 0, 0, 0);
		
		_db.processVotes("040299962902", 1, now);
		_db.processVotes("040299962903", 1, now);
		
		checkPhone("040299962900", 2, 4, 5, 1, 5);
		checkPhone("040299962909", 0, 4, 5, 1, 5);
		checkPhone("040299962999", 0, 0, 0, 1, 5);
		
		_db.processVotes("040299962903", -1, now);
		
		checkPhone("040299962900", 2, 3, 4, 0, 0);
		checkPhone("040299962909", 0, 3, 4, 0, 0);
		checkPhone("040299962999", 0, 0, 0, 0, 0);
	}

	@Test
	void testAggregation100() {
		long now = 0;
		
		_db.processVotes("040299962900", 1, now);
		_db.processVotes("040299962901", 1, now);
		_db.processVotes("040299962902", 1, now);
		_db.processVotes("040299962903", 1, now);
		
		_db.processVotes("040299962910", 1, now);
		_db.processVotes("040299962911", 1, now);
		_db.processVotes("040299962912", 1, now);
		_db.processVotes("040299962913", 1, now);
		
		_db.processVotes("040299962920", 1, now);
		_db.processVotes("040299962921", 1, now);
		_db.processVotes("040299962922", 1, now);
		_db.processVotes("040299962923", 1, now);
		
		checkPhone("040299962999", 0, 0, 0, 3, 12);
		
		_db.processVotes("040299962903", -1, now);
		_db.processVotes("040299962913", -1, now);
		_db.processVotes("040299962923", -1, now);

		checkPhone("040299962999", 0, 0, 0, 0, 0);
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
		_db.createUser("domain", "ext-1", "user-1", "User 1");
		
		long time = 1;
		
		_db.addRating("user-1", "0123456789", Rating.B_MISSED, "Don't know.", time++);
		_db.addRating("user-1", "0123456789", Rating.C_PING, "Did not answer.", time++);
		
		// Only one rating recorded.
		assertEquals(1, _db.getVotesFor("0123456789"));
		
		// Both comments have been recorded.
		assertEquals(2, _db.getComments("0123456789").size());
		
		_db.addRating("user-1", "0123456789", Rating.A_LEGITIMATE, "Was my uncle.", time++);

		assertEquals(0, _db.getVotesFor("0123456789"));

		assertEquals(3, _db.getComments("0123456789").size());
	}

	private DataSource createTestDataSource() {
		JdbcDataSource result = new JdbcDataSource();
		result.setUrl("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
		result.setUser("foo");
		result.setPassword("bar");
		return result;
	}
}
