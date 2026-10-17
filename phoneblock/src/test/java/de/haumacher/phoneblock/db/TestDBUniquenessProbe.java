/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.Statement;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.haumacher.phoneblock.analysis.NumberAnalyzer;
import de.haumacher.phoneblock.app.api.model.PhoneNumer;
import de.haumacher.phoneblock.scheduler.SchedulerService;

/**
 * Probe for #520: the conditional personalization insert composed with the
 * wildcard feature (#377), whose callers delete and re-insert under the same
 * key within one transaction.
 */
class TestDBUniquenessProbe {

	private DB _db;
	private SchedulerService _scheduler;
	private DataSource _dataSource;

	@BeforeEach
	void setUp() throws Exception {
		_scheduler = new SchedulerService();
		_scheduler.contextInitialized(null);
		_dataSource = TestDB.createTestDataSource();
		_db = new DB(_dataSource, _scheduler);
	}

	@AfterEach
	void tearDown() throws Exception {
		_db.shutdown();
		try (Connection connection = _dataSource.getConnection();
				Statement statement = connection.createStatement()) {
			statement.execute("SHUTDOWN");
		}
		_scheduler.contextDestroyed(null);
	}

	/**
	 * Re-adding a wildcard with the opposite mode replaces the entry: the delete and
	 * the conditional insert run in the same transaction, so the insert must see the
	 * delete and must not be silently skipped.
	 */
	@Test
	void testWildcardToggleInOneTransaction() {
		long userId = createUser("wild");

		String prefix = _db.addWildcard("wild", "030123", true, 1000);
		assertNotNull(prefix);
		assertTrue(activity(userId, prefix).isBlocked());

		assertEquals(prefix, _db.addWildcard("wild", "030123", false, 2000));
		DBPersonalization entry = activity(userId, prefix);
		assertNotNull(entry);
		assertFalse(entry.isBlocked());

		assertEquals(prefix, _db.addWildcard("wild", "030123", true, 3000));
		assertTrue(activity(userId, prefix).isBlocked());
	}

	/**
	 * An exact block under a prefix and the wildcard for that prefix are distinct
	 * keys: the conditional insert of one must not be suppressed by the other.
	 */
	@Test
	void testExactNumberAndCoveringWildcardCoexist() {
		long userId = createUser("both");
		PhoneNumer number = NumberAnalyzer.analyze("0301234567", "+49");
		String phone = NumberAnalyzer.getPhoneId(number);

		try (SqlSession session = _db.openSession()) {
			BlockList blocklist = session.getMapper(BlockList.class);
			assertEquals(1, blocklist.addPersonalization(userId, phone, NumberAnalyzer.getPhoneHash(number), 1000));
			session.commit();
		}
		String prefix = _db.addWildcard("both", "030123", true, 2000);
		assertNotNull(prefix);
		assertNotEquals(phone, prefix);

		assertNotNull(activity(userId, phone));
		assertNotNull(activity(userId, prefix));
	}

	private long createUser(String login) {
		_db.createUser(login, login, "de", "+49");
		try (SqlSession session = _db.openSession()) {
			return session.getMapper(Users.class).getUserId(login).longValue();
		}
	}

	private DBPersonalization activity(long userId, String phone) {
		try (SqlSession session = _db.openSession()) {
			return session.getMapper(BlockList.class).getPersonalizationActivity(userId, phone);
		}
	}
}
