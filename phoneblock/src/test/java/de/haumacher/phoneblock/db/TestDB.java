/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.util.Arrays;
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

import de.haumacher.phoneblock.db.model.Rating;
import de.haumacher.phoneblock.db.model.SearchInfo;

/**
 * Test case for {@link DB}.
 */
public class TestDB {
	
	private DB _db;

	@BeforeEach
	public void setUp() throws Exception {
		DataSource dataSource = createTestDataSource();
		_db = new DB(dataSource);
	}
	
	@AfterEach
	public void tearDown() throws Exception {
		_db.shutdown();
	}

	@Test
	public void testTopSearches() throws UnsupportedEncodingException, SQLException {
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
	public void testSpamReports() throws UnsupportedEncodingException, SQLException {
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
		
		List<SpamReport> reports = _db.getLatestSpamReports(1001);
		assertEquals(1, reports.size());
		assertEquals("456", reports.get(0).getPhone());
	}
	
	@Test
	public void testBlocklist() throws UnsupportedEncodingException, SQLException {
		try (SqlSession session = _db.openSession()) {
			BlockList blockList = session.getMapper(BlockList.class);
			
			blockList.addExclude(1, "123");
			blockList.addExclude(2, "123");
			blockList.addExclude(1, "345");
			blockList.addExclude(1, "678");
			blockList.addExclude(2, "999");
			
			assertEquals(new HashSet<>(Arrays.asList("123", "345", "678")), blockList.getExcluded(1));
			
			blockList.removeExclude(1, "345");
			
			assertEquals(new HashSet<>(Arrays.asList("123", "678")), blockList.getExcluded(1));
			
			blockList.removeExclude(2, "123");
			
			assertEquals(new HashSet<>(Arrays.asList("123", "678")), blockList.getExcluded(1));
			
			blockList.addPersonalization(1, "654");
			blockList.addPersonalization(1, "321");
			blockList.addPersonalization(2, "321");
			blockList.addPersonalization(2, "987");
			
			assertEquals(Arrays.asList("321", "654"), blockList.getPersonalizations(1));
			
			blockList.removePersonalization(1, "654");
			
			assertEquals(Arrays.asList("321"), blockList.getPersonalizations(1));
			
			blockList.removePersonalization(2, "321");
			
			assertEquals(Arrays.asList("321"), blockList.getPersonalizations(1));
		}
	}

	@Test
	public void testDuplicateAdd() throws UnsupportedEncodingException, SQLException {
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
	public void testUserManagement() throws SQLException, IOException {
		_db.addUser("none", "1", "foo@bar.com", "Mr. X", "123");
		_db.addUser("none", "2", "baz@bar.com", "Mr. Y", "123");
		
		assertEquals("foo@bar.com", _db.basicAuth(header("foo@bar.com", "123")));
		assertEquals(null, _db.basicAuth(header("foo@bar.com", "321")));
		assertEquals(null, _db.basicAuth(header("xxx@bar.com", "123")));
		
		try (SqlSession session = _db.openSession()) {
			long userA = session.getMapper(Users.class).getUserId("foo@bar.com");
			long userB = session.getMapper(Users.class).getUserId("baz@bar.com");
			
			assertTrue(userA != 0);
			assertTrue(userB != 0);
			assertTrue(userA != userB);
		}
	}
	
	private String header(String user, String pw) throws UnsupportedEncodingException {
		return "Basic " + Base64.getEncoder().encodeToString((user + ':' + pw).getBytes("utf-8"));
	}
	
	@Test
	public void testRatings() {
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
	public void testSearchHistory() {
		
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
		
		assertEquals(Arrays.asList(2, 0, 1, 0), _db.getSearchHistory("123"));
		assertEquals(Arrays.asList(1, 1, 0, 1), _db.getSearchHistory("456"));
		assertEquals(Arrays.asList(0, 1, 0, 1), _db.getSearchHistory("789"));
	}
	
	@Test
	public void testSearchHistoryCleanup() {
		for (int n = 0; n < 100; n++) {
			_db.addSearchHit("123");
			_db.cleanupSearchHistory(30);
		}
		_db.addSearchHit("123");
		
		assertEquals(31, _db.getSearchHistory("123").size());
	}
	
	@Test
	public void testQuote() {
		assertEquals("\"\" 0x0 \"33a0a838-7b11-427a-\" 0x9 \"\" 0xD \"\" 0xA \"\" 0xC \"9c84-59b6ab6d3b0e\" 0x20 \"\"", DB.saveChars("\00033a0a838-7b11-427a-\t\r\n\f9c84-59b6ab6d3b0e "));
	}

	private DataSource createTestDataSource() {
		JdbcDataSource result = new JdbcDataSource();
		result.setUrl("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
		result.setUser("foo");
		result.setPassword("bar");
		return result;
	}
}
