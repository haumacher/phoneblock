/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import static org.junit.jupiter.api.Assertions.*;

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

import de.haumacher.phoneblock.db.model.PhoneInfo;
import de.haumacher.phoneblock.db.model.Rating;
import de.haumacher.phoneblock.db.model.SearchInfo;
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
	void testTopSearches() {
		_db.addRating("0", Rating.C_PING, null, 0);
		_db.addRating("1", Rating.C_PING, null, 0);
		_db.addRating("2", Rating.C_PING, null, 0);
		_db.addRating("3", Rating.C_PING, null, 0);
		_db.addRating("4", Rating.C_PING, null, 0);
		_db.addRating("5", Rating.C_PING, null, 0);
		
		_db.addSearchHit("5", 0);
		_db.addSearchHit("0", 0);
		_db.addSearchHit("0", 0);
		_db.addSearchHit("0", 0);
		
		_db.addSearchHit("1", 1);
		_db.addSearchHit("2", 2);
		_db.addSearchHit("3", 3);
		_db.cleanupSearchHistory(30);
		
		_db.addSearchHit("0", 4);
		_db.addSearchHit("4", 5);
		_db.addSearchHit("5", 6);
		
		List<? extends SearchInfo> topSearches = _db.getTopSearches(1, 1);

		assertEquals(2, topSearches.size());
		
		assertEquals("5", topSearches.get(0).getPhone());
		assertEquals(1, topSearches.get(0).getCount());
		assertEquals(1, topSearches.get(0).getTotal());
		
		assertEquals("0", topSearches.get(1).getPhone());
		assertEquals(1, topSearches.get(1).getCount());
		assertEquals(3, topSearches.get(1).getTotal());
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
		assertFalse(_db.hasSpamReportFor("999"));
		assertEquals(0, _db.getVotesFor("999"));
		
		_db.processVotes("999", 0, 1003);
		assertFalse(_db.hasSpamReportFor("999"));
		assertEquals(0, _db.getVotesFor("999"));
		
		_db.processVotes("123", 1, 1004);
		assertEquals(3, _db.getVotesFor("123"));
		
		_db.processVotes("123", -1, 1005);
		assertEquals(2, _db.getVotesFor("123"));
		
		_db.processVotes("123", -2, 1006);
		assertEquals(0, _db.getVotesFor("123"));
		assertFalse(_db.hasSpamReportFor("123"));
		
		assertEquals(1001, _db.getLastSpamReport().longValue());
		
		List<DBSpamReport> reports = _db.getLatestSpamReports(1001);
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
			
			blockList.removeExclude(1, "345");
			
			assertEquals(new HashSet<>(List.of("123", "678")), blockList.getExcluded(1));
			
			blockList.removeExclude(2, "123");
			
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
		
		_db.addRating("123", Rating.G_FRAUD, null, now++);
		_db.addRating("123", Rating.B_MISSED, null, now++);
		_db.addRating("123", Rating.B_MISSED, null, now++);
		_db.addRating("123", Rating.C_PING, null, now++);
		_db.addRating("123", Rating.D_POLL, null, now++);
		_db.addRating("123", Rating.E_ADVERTISING, null, now++);
		_db.addRating("123", Rating.F_GAMBLE, null, now++);

		assertEquals(Rating.G_FRAUD, _db.getRating("123"));
		
		_db.cleanupSearchHistory(10);
		
		assertEquals(Rating.G_FRAUD, _db.getRating("123"));
		
		_db.addRating("123", Rating.C_PING, null, now++);
		
		assertEquals(Rating.C_PING, _db.getRating("123"));
	}
	
	@Test
	void testSearchHistory() {
		
		_db.addSearchHit("123");
		_db.addSearchHit("123");
		_db.addSearchHit("456");
		
		_db.cleanupSearchHistory(30);
		
		_db.addSearchHit("456");
		_db.addSearchHit("789");
		
		_db.cleanupSearchHistory(30);
		
		_db.addSearchHit("123");
		
		_db.cleanupSearchHistory(30);
		
		_db.addSearchHit("456");
		_db.addSearchHit("789");
		
		assertEquals(List.of(2, 0, 1, 0), _db.getSearchHistory("123"));
		assertEquals(List.of(1, 1, 0, 1), _db.getSearchHistory("456"));
		assertEquals(List.of(0, 1, 0, 1), _db.getSearchHistory("789"));
	}
	
	@Test
	void testSearchHistoryCleanup() {
		for (int n = 0; n < 100; n++) {
			_db.addSearchHit("123");
			_db.cleanupSearchHistory(30);
		}
		_db.addSearchHit("123");
		
		assertEquals(31, _db.getSearchHistory("123").size());
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
		PhoneInfo info = _db.getPhoneApiInfo(phone);
		assertEquals(votes, info.getVotes());
		assertEquals(cnt10, info.getCnt10());
		assertEquals(votes10, info.getVotes10());
		assertEquals(cnt100, info.getCnt100());
		assertEquals(votes100, info.getVotes100());
	}

	private DataSource createTestDataSource() {
		JdbcDataSource result = new JdbcDataSource();
		result.setUrl("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
		result.setUser("foo");
		result.setPassword("bar");
		return result;
	}
}
