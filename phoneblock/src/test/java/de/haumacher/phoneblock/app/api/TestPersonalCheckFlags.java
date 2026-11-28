/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.Statement;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.haumacher.phoneblock.analysis.NumberAnalyzer;
import de.haumacher.phoneblock.app.api.model.PhoneInfo;
import de.haumacher.phoneblock.app.api.model.PhoneNumer;
import de.haumacher.phoneblock.app.api.model.Rating;
import de.haumacher.phoneblock.db.BlockList;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBPersonalization;
import de.haumacher.phoneblock.db.SpamReports;
import de.haumacher.phoneblock.db.TestDB;
import de.haumacher.phoneblock.db.Users;
import de.haumacher.phoneblock.scheduler.SchedulerService;

/**
 * The personal list flags in the answer of {@code /api/check} (and {@code /api/num}): a personal
 * whitelist hit must carry {@code whiteListed=true}, so clients can tell "allowed by the user"
 * from "unknown number".
 */
class TestPersonalCheckFlags {

	private static final String NUMBER = "+4930123456789";

	private SchedulerService _scheduler;
	private DataSource _dataSource;
	private DB _db;

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
		_db = null;
		try (Connection connection = _dataSource.getConnection();
				Statement statement = connection.createStatement()) {
			statement.execute("SHUTDOWN");
		}
		_scheduler.contextDestroyed(null);
		_scheduler = null;
	}

	@Test
	void personalWhitelistSetsWhiteListed() {
		PhoneInfo info = check("whitelister", Boolean.FALSE);
		assertTrue(info.isWhiteListed());
		assertFalse(info.isBlackListed());
		assertEquals(Rating.A_LEGITIMATE, info.getRating());
	}

	@Test
	void personalBlockSetsBlackListedOnly() {
		PhoneInfo info = check("blocker", Boolean.TRUE);
		assertTrue(info.isBlackListed());
		assertFalse(info.isWhiteListed());
	}

	@Test
	void personalBlockOverridesCommunityWhitelist() throws Exception {
		PhoneNumer number = NumberAnalyzer.parsePhoneNumber(NUMBER, "+49");
		try (Connection connection = _dataSource.getConnection();
				Statement statement = connection.createStatement()) {
			statement.execute("INSERT INTO WHITELIST (PHONE) VALUES ('" + NumberAnalyzer.getPhoneId(number) + "')");
		}

		PhoneInfo info = check("blocker", Boolean.TRUE);
		assertTrue(info.isBlackListed());
		assertFalse(info.isWhiteListed(), "a personal block must not be reported as whitelisted");
	}

	@Test
	void communityWhitelistWithoutPersonalizationIsWhiteListed() throws Exception {
		PhoneNumer number = NumberAnalyzer.parsePhoneNumber(NUMBER, "+49");
		try (Connection connection = _dataSource.getConnection();
				Statement statement = connection.createStatement()) {
			statement.execute("INSERT INTO WHITELIST (PHONE) VALUES ('" + NumberAnalyzer.getPhoneId(number) + "')");
		}

		PhoneInfo info = check("plain", null);
		assertTrue(info.isWhiteListed());
		assertFalse(info.isBlackListed());
	}

	@Test
	void noPersonalizationSetsNeither() {
		PhoneInfo info = check("plain", null);
		assertFalse(info.isBlackListed());
		assertFalse(info.isWhiteListed());
	}

	/**
	 * Resolves the number the way {@link SpamCheckServlet} does for an authenticated user.
	 *
	 * @param blocked {@code true} for a personal block, {@code false} for a personal whitelist
	 *        entry, {@code null} for no personalization.
	 */
	private PhoneInfo check(String userName, Boolean blocked) {
		_db.createUser(userName, userName, "de", "+49");

		PhoneNumer number = NumberAnalyzer.parsePhoneNumber(NUMBER, "+49");
		assertNotNull(number);
		String phoneId = NumberAnalyzer.getPhoneId(number);
		byte[] sha1 = NumberAnalyzer.getPhoneHash(number);

		try (SqlSession session = _db.openSession()) {
			long userId = session.getMapper(Users.class).getUserId(userName);
			BlockList blocklist = session.getMapper(BlockList.class);
			SpamReports reports = session.getMapper(SpamReports.class);

			if (blocked != null) {
				long now = System.currentTimeMillis();
				if (blocked.booleanValue()) {
					blocklist.addPersonalization(userId, phoneId, sha1, now);
				} else {
					blocklist.addExclude(userId, phoneId, sha1, now);
				}
				session.commit();
			}

			DBPersonalization personalized = blocklist.resolvePersonalizationByHash(userId, sha1);
			if (blocked == null) {
				assertNull(personalized);
				// Falls through to the community lookup.
				return _db.getPhoneApiInfo(reports, phoneId);
			}
			assertNotNull(personalized);
			assertEquals(blocked.booleanValue(), personalized.isBlocked());
			return SpamCheckServlet.personalInfo(_db, reports, personalized.getPhone(), personalized.isBlocked());
		}
	}
}
