/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;

import junit.framework.TestCase;

/**
 * Test case for {@link DB}.
 */
public class TestDB extends TestCase {

	public void testSetup() throws UnsupportedEncodingException, SQLException {
		DataSource dataSource = createTestDataSource();
		DB db = new DB(dataSource);

		assertFalse(db.hasSpamReportFor("123"));

		db.addSpam("123", 2, 1000);
		
		assertTrue(db.hasSpamReportFor("123"));
		
		db.addSpam("456", 1, 1001);
		
		assertEquals(2, db.getSpamVotesFor("123"));
		
		assertFalse(db.hasSpamReportFor("999"));
		assertEquals(0, db.getSpamVotesFor("999"));
		
		db.addSpam("999", -1, 1002);
		assertFalse(db.hasSpamReportFor("999"));
		assertEquals(0, db.getSpamVotesFor("999"));
		
		db.addSpam("999", 0, 1003);
		assertFalse(db.hasSpamReportFor("999"));
		assertEquals(0, db.getSpamVotesFor("999"));
		
		db.addSpam("123", 1, 1004);
		assertEquals(3, db.getSpamVotesFor("123"));
		
		db.addSpam("123", -1, 1005);
		assertEquals(2, db.getSpamVotesFor("123"));
		
		db.addSpam("123", -2, 1006);
		assertEquals(0, db.getSpamVotesFor("123"));
		assertFalse(db.hasSpamReportFor("123"));
		
		assertEquals(1001, db.getLastSpamReport());
		
		List<SpamReport> reports = db.getLatestSpamReports(1001);
		assertEquals(1, reports.size());
		assertEquals("456", reports.get(0).getPhone());
	}

	private DataSource createTestDataSource() {
		JdbcDataSource result = new JdbcDataSource();
		result.setUrl("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
		result.setUser("foo");
		result.setPassword("bar");
		return result;
	}
}
