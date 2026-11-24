/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Base64;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.haumacher.phoneblock.app.AuthContext;
import de.haumacher.phoneblock.db.settings.UserSettings;
import de.haumacher.phoneblock.db.settings.AuthToken;
import de.haumacher.phoneblock.scheduler.SchedulerService;

/**
 * Test of the short-lived basic authentication cache in {@link DB#basicAuth(String, String)}.
 */
class TestAuthCache {

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
		try (Connection connection = _dataSource.getConnection();
				Statement statement = connection.createStatement()) {
			statement.execute("SHUTDOWN");
		}
		_scheduler.contextDestroyed(null);
	}

	@Test
	void tokenAuth_isCachedWithinBurst() throws Exception {
		_db.createUser("fritz", "Fritz", "de", "+49");
		AuthToken token = _db.createCardDavToken("fritz", System.currentTimeMillis(), "FRITZOS_CardDAV_Client/1.0", "box");
		String header = header("fritz", token.getToken());

		AuthContext first = _db.basicAuth(header, "FRITZOS_CardDAV_Client/1.0");
		assertNotNull(first);
		assertEquals("fritz", first.getUserName());
		assertSame(first, _db.basicAuth(header, "FRITZOS_CardDAV_Client/1.0"));
	}

	@Test
	void revokedToken_isRejectedImmediately() throws Exception {
		_db.createUser("fritz", "Fritz", "de", "+49");
		AuthToken token = _db.createCardDavToken("fritz", System.currentTimeMillis(), "ua", "box");
		String header = header("fritz", token.getToken());
		assertNotNull(_db.basicAuth(header, "ua"));

		_db.invalidateAuthToken(token.getId());

		assertNull(_db.basicAuth(header, "ua"));
	}

	@Test
	void tokensDeletedThroughMapper_areRejectedAfterFlush() throws Exception {
		_db.createUser("fritz", "Fritz", "de", "+49");
		AuthToken token = _db.createCardDavToken("fritz", System.currentTimeMillis(), "ua", "box");
		String header = header("fritz", token.getToken());
		assertNotNull(_db.basicAuth(header, "ua"));

		try (var session = _db.openSession()) {
			Users users = session.getMapper(Users.class);
			users.deleteAuthToken(users.getUserId("fritz").longValue(), token.getId());
			session.commit();
		}
		_db.flushAuthCache("fritz");

		assertNull(_db.basicAuth(header, "ua"));
	}

	@Test
	void logoutAll_rejectsCachedTokens() throws Exception {
		_db.createUser("fritz", "Fritz", "de", "+49");
		AuthToken token = _db.createCardDavToken("fritz", System.currentTimeMillis(), "ua", "box");
		String header = header("fritz", token.getToken());
		assertNotNull(_db.basicAuth(header, "ua"));

		_db.logoutAll("fritz");

		assertNull(_db.basicAuth(header, "ua"));
	}

	@Test
	void settingsChange_isVisibleToNextRequest() throws Exception {
		_db.createUser("fritz", "Fritz", "de", "+49");
		AuthToken token = _db.createCardDavToken("fritz", System.currentTimeMillis(), "ua", "box");
		String header = header("fritz", token.getToken());
		AuthContext before = _db.basicAuth(header, "ua");

		UserSettings settings = _db.getSettings("fritz");
		settings.setMinVotes(before.getSettings().getMinVotes() + 7);
		_db.updateSettings(settings);

		AuthContext after = _db.basicAuth(header, "ua");
		assertNotSame(before, after);
		assertEquals(before.getSettings().getMinVotes() + 7, after.getSettings().getMinVotes());
	}

	@Test
	void passwordReset_rejectsOldPassword() throws Exception {
		_db.addUser("user@example.org", "User", "de", "+49", "secret-1");
		String header = header("user@example.org", "secret-1");
		assertNotNull(_db.basicAuth(header, "ua"));

		String newPassword = _db.resetPassword("user@example.org");

		assertNull(_db.basicAuth(header, "ua"));
		assertNotNull(_db.basicAuth(header("user@example.org", newPassword), "ua"));
	}

	@Test
	void failedAuth_isNotCached() throws Exception {
		_db.addUser("user@example.org", "User", "de", "+49", "secret-1");
		String wrong = header("user@example.org", "wrong");
		assertNull(_db.basicAuth(wrong, "ua"));
		assertNull(_db.basicAuth(wrong, "ua"));
		assertNotNull(_db.basicAuth(header("user@example.org", "secret-1"), "ua"));
	}

	@Test
	void entriesExpireAfterTtl() {
		AuthCache cache = new AuthCache();
		AuthContext context = new AuthContext(AuthToken.create().setUserName("u").setUserId(1).setId(2), null);
		cache.put("k", context, 1000, cache.generation());

		assertSame(context, cache.lookup("k", 1000 + AuthCache.TTL_MS - 1));
		assertNull(cache.lookup("k", 1000 + AuthCache.TTL_MS));
		assertEquals(0, cache.size());
	}

	@Test
	void resultCheckedDuringFlush_isNotCached() {
		AuthCache cache = new AuthCache();
		AuthContext context = new AuthContext(AuthToken.create().setUserName("u").setUserId(1).setId(2), null);

		long generation = cache.generation();
		// A concurrent change of another user's data while the credentials were being checked.
		cache.flushUser(99);
		cache.put("k", context, 1000, generation);

		assertNull(cache.lookup("k", 1000));
	}

	@Test
	void flushToken_dropsOnlyThatToken() {
		AuthCache cache = new AuthCache();
		AuthContext a = new AuthContext(AuthToken.create().setUserName("u").setUserId(1).setId(2), null);
		AuthContext b = new AuthContext(AuthToken.create().setUserName("u").setUserId(1).setId(3), null);
		cache.put("a", a, 1000, cache.generation());
		cache.put("b", b, 1000, cache.generation());

		cache.flushToken(2);

		assertNull(cache.lookup("a", 1000));
		assertSame(b, cache.lookup("b", 1000));
	}

	private static String header(String user, String password) {
		return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
	}
}
