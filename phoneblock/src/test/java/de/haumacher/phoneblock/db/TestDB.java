/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

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

import junit.framework.TestCase;

/**
 * Test case for {@link DB}.
 */
public class TestDB extends TestCase {
	
	private DB _db;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		
		DataSource dataSource = createTestDataSource();
		_db = new DB(dataSource);
	}
	
	@Override
	protected void tearDown() throws Exception {
		_db.shutdown();
		
		super.tearDown();
	}

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
	
	public void testUserManagement() throws SQLException, IOException {
		_db.addUser("foo@bar.com", "123");
		_db.addUser("baz@bar.com", "123");
		
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
	
	public void testSearchHistory() {
		
		_db.addSearchHit("123");
		_db.addSearchHit("123");
		_db.addSearchHit("456");
		
		_db.cleanupSearchHistory();
		
		_db.addSearchHit("456");
		_db.addSearchHit("789");
		
		_db.cleanupSearchHistory();
		
		_db.addSearchHit("123");
		
		_db.cleanupSearchHistory();
		
		_db.addSearchHit("456");
		_db.addSearchHit("789");
		
		assertEquals(Arrays.asList(2, 0, 1, 0), _db.getSearchHistory("123"));
		assertEquals(Arrays.asList(1, 1, 0, 1), _db.getSearchHistory("456"));
		assertEquals(Arrays.asList(0, 1, 0, 1), _db.getSearchHistory("789"));
	}
	
	public void testSearchHistoryCleanup() {
		for (int n = 0; n < 100; n++) {
			_db.addSearchHit("123");
			_db.cleanupSearchHistory();
		}
		_db.addSearchHit("123");
		
		assertEquals(31, _db.getSearchHistory("123").size());
	}

	private DataSource createTestDataSource() {
		JdbcDataSource result = new JdbcDataSource();
		result.setUrl("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
		result.setUser("foo");
		result.setPassword("bar");
		return result;
	}
}
