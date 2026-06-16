/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

import de.haumacher.phoneblock.db.settings.AuthToken;
import de.haumacher.phoneblock.scheduler.SchedulerService;

/**
 * Test case for the per-subject API rate limit ({@link DB#tryConsumeQuota} and
 * the {@link Quota} mapper).
 */
public class TestApiQuota {

	private static final int BUCKET = DB.QUOTA_BUCKET_NUMBER_QUERY;
	private static final long INTERVAL = 10_000;
	private static final int LIMIT = 3;

	private DB _db;
	private SchedulerService _scheduler;
	private DataSource _dataSource;

	@BeforeEach
	public void setUp() throws Exception {
		_scheduler = new SchedulerService();
		_scheduler.contextInitialized(null);

		_dataSource = TestDB.createTestDataSource();
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

	/** A synthetic token subject (persistent API key). */
	private static AuthToken token(long id) {
		return AuthToken.create().setId(id).setUserId(id);
	}

	/** A synthetic account subject (account-password auth — no persistent token). */
	private static AuthToken account(long userId) {
		return AuthToken.create().setId(0).setUserId(userId);
	}

	@Test
	void testFixedWindow() {
		AuthToken token = token(42);

		// First three requests inside the window are admitted.
		assertEquals(-1, _db.tryConsumeQuota(token, BUCKET, 1_000, INTERVAL, LIMIT));
		assertEquals(-1, _db.tryConsumeQuota(token, BUCKET, 1_001, INTERVAL, LIMIT));
		assertEquals(-1, _db.tryConsumeQuota(token, BUCKET, 1_002, INTERVAL, LIMIT));

		// The fourth within the window is rejected with a positive Retry-After.
		long retry = _db.tryConsumeQuota(token, BUCKET, 1_003, INTERVAL, LIMIT);
		assertTrue(retry > 0, "Expected a Retry-After delay, got " + retry);
		assertTrue(retry <= INTERVAL / 1000, "Retry-After must not exceed the window length.");

		// Still rejected just before the window elapses.
		assertTrue(_db.tryConsumeQuota(token, BUCKET, 1_000 + INTERVAL, INTERVAL, LIMIT) > 0);

		// Once the window has fully elapsed the counter resets and admits again.
		assertEquals(-1, _db.tryConsumeQuota(token, BUCKET, 1_001 + INTERVAL, INTERVAL, LIMIT));
		assertEquals(-1, _db.tryConsumeQuota(token, BUCKET, 1_002 + INTERVAL, INTERVAL, LIMIT));
	}

	@Test
	void testTokenAndAccountAreIndependent() {
		// Same numeric id, different subject kind — token key vs. account.
		AuthToken token = token(7);
		AuthToken account = account(7);

		// Exhaust the token budget.
		assertEquals(-1, _db.tryConsumeQuota(token, BUCKET, 1_000, INTERVAL, LIMIT));
		assertEquals(-1, _db.tryConsumeQuota(token, BUCKET, 1_000, INTERVAL, LIMIT));
		assertEquals(-1, _db.tryConsumeQuota(token, BUCKET, 1_000, INTERVAL, LIMIT));
		assertTrue(_db.tryConsumeQuota(token, BUCKET, 1_000, INTERVAL, LIMIT) > 0);

		// The account budget is untouched.
		assertEquals(-1, _db.tryConsumeQuota(account, BUCKET, 1_000, INTERVAL, LIMIT));
	}

	@Test
	void testBucketsAreIndependent() {
		AuthToken token = token(11);

		// Exhaust the number-query bucket.
		for (int i = 0; i < LIMIT; i++) {
			assertEquals(-1, _db.tryConsumeQuota(token, DB.QUOTA_BUCKET_NUMBER_QUERY, 1_000, INTERVAL, LIMIT));
		}
		assertTrue(_db.tryConsumeQuota(token, DB.QUOTA_BUCKET_NUMBER_QUERY, 1_000, INTERVAL, LIMIT) > 0);

		// A different bucket of the same token still has its full budget.
		assertEquals(-1, _db.tryConsumeQuota(token, DB.QUOTA_BUCKET_BLOCKLIST_FULL, 1_000, INTERVAL, LIMIT));
	}

	@Test
	void testTokenDeletionClearsCounter() {
		_db.createUser("quota-user", "Quota User", "de", "+49");
		AuthToken loginToken = _db.createLoginToken("quota-user", 1_000, "agent");
		long tokenId = loginToken.getId();
		assertTrue(tokenId > 0);

		assertEquals(-1, _db.tryConsumeQuota(loginToken, BUCKET, 1_000, INTERVAL, LIMIT));
		assertNotNull(quotaTime(DB.QUOTA_SUBJECT_TOKEN, tokenId), "Counter must exist after use.");

		_db.invalidateAuthToken(tokenId);
		assertNull(quotaTime(DB.QUOTA_SUBJECT_TOKEN, tokenId), "Counter must be removed with the token.");
	}

	@Test
	void testAccountDeletionClearsCounters() {
		_db.createUser("del-user", "Del User", "de", "+49");
		long userId;
		try (SqlSession session = _db.openSession()) {
			userId = session.getMapper(Users.class).getUserId("del-user").longValue();
		}
		AuthToken loginToken = _db.createLoginToken("del-user", 1_000, "agent");

		// One token counter and one account counter for the same user.
		assertEquals(-1, _db.tryConsumeQuota(loginToken, BUCKET, 1_000, INTERVAL, LIMIT));
		assertEquals(-1, _db.tryConsumeQuota(account(userId), BUCKET, 1_000, INTERVAL, LIMIT));
		assertNotNull(quotaTime(DB.QUOTA_SUBJECT_TOKEN, loginToken.getId()));
		assertNotNull(quotaTime(DB.QUOTA_SUBJECT_ACCOUNT, userId));

		_db.deleteUser("del-user");

		assertNull(quotaTime(DB.QUOTA_SUBJECT_TOKEN, loginToken.getId()), "Token counter must be removed.");
		assertNull(quotaTime(DB.QUOTA_SUBJECT_ACCOUNT, userId), "Account counter must be removed.");
	}

	private Long quotaTime(int kind, long subjectId) {
		try (SqlSession session = _db.openSession()) {
			return session.getMapper(Quota.class).getQuotaTime(kind, subjectId, BUCKET);
		}
	}

}
