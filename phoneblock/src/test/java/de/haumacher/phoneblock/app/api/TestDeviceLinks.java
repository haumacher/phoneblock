/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.haumacher.phoneblock.app.AuthContext;
import de.haumacher.phoneblock.app.api.DeviceLinks.Decision;
import de.haumacher.phoneblock.app.api.DeviceLinks.LinkInfo;
import de.haumacher.phoneblock.app.api.DeviceLinks.PollResult;
import de.haumacher.phoneblock.app.api.DeviceLinks.Started;
import de.haumacher.phoneblock.app.api.DeviceLinks.State;
import de.haumacher.phoneblock.app.api.DeviceLinks.TokenFactory;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.TestDB;
import de.haumacher.phoneblock.scheduler.SchedulerService;

/**
 * Test case for {@link DeviceLinks}: the device-code link of #621.
 */
public class TestDeviceLinks {

	private static final Pattern USER_CODE = Pattern.compile("^[BCDFGHJKLMNPQRSTVWXZ]{4}-[BCDFGHJKLMNPQRSTVWXZ]{4}$");

	private static final String IP = "192.0.2.10";

	private static final String UA = "PhoneBlockDongle/1.0 (test)";

	private SchedulerService _scheduler;
	private DataSource _dataSource;
	private DB _db;

	private AtomicLong _now;
	private DeviceLinks _links;
	private TokenFactory _tokens;
	private int _baseTokens;

	@BeforeEach
	public void setUp() throws Exception {
		_scheduler = new SchedulerService();
		_scheduler.contextInitialized(null);
		_dataSource = TestDB.createTestDataSource();
		_db = new DB(_dataSource, _scheduler);

		_db.createUser("link-user", "Link User", "de", "+49");
		_db.createUser("other-user", "Other User", "de", "+49");

		_now = new AtomicLong(1_000_000);
		_links = new DeviceLinks(_now::get, new SecureRandom());
		_tokens = (user, now, userAgent, label) -> _db.createAPIToken(user, now, userAgent, label).getToken();
		_baseTokens = tokenCount();
	}

	@AfterEach
	public void tearDown() throws Exception {
		_db.shutdown();
		try (Connection connection = _dataSource.getConnection();
		     Statement statement = connection.createStatement()) {
			statement.execute("SHUTDOWN");
		}
		_scheduler.contextDestroyed(null);
	}

	@Test
	public void testApproveAndCollect() throws Exception {
		Started started = _links.start("PhoneBlockDongle", "Dongle im Flur", IP);
		assertEquals(43, started.deviceCode().length());
		assertEquals(DeviceLinks.INTERVAL, started.interval());
		assertEquals(DeviceLinks.EXPIRES_IN, started.expiresIn());

		PollResult pending = poll(started);
		assertEquals(DeviceLinks.PENDING, pending.status());
		assertNull(pending.token());

		LinkInfo info = _links.find(started.userCode());
		assertEquals("Dongle im Flur", info.label());
		assertEquals(State.PENDING, info.state());

		assertEquals(Decision.OK, _links.approve(started.userCode(), "link-user"));

		advance(DeviceLinks.INTERVAL);
		PollResult approved = poll(started);
		assertEquals(DeviceLinks.APPROVED, approved.status());
		assertNotNull(approved.token());

		AuthContext context = _db.checkAuthToken(approved.token(), _now.get(), UA, false);
		assertNotNull(context);
		assertEquals("link-user", context.getUserName());
		assertEquals("Dongle im Flur", context.getAuthorization().getLabel());
		assertEquals(UA, context.getAuthorization().getUserAgent());
		assertTrue(context.getAuthorization().isAccessQuery());

		// Single use.
		advance(DeviceLinks.INTERVAL);
		assertEquals(DeviceLinks.EXPIRED, poll(started).status());
		assertNull(_links.find(started.userCode()));
		assertEquals(Decision.UNKNOWN, _links.approve(started.userCode(), "link-user"));
		assertEquals(1, newTokens());
	}

	@Test
	public void testDefaultAndLongLabel() throws Exception {
		Started noLabel = _links.start("PhoneBlockDongle", null, IP);
		assertEquals(DeviceLinks.DEFAULT_LABEL, _links.find(noLabel.userCode()).label());

		Started blank = _links.start("PhoneBlockDongle", "  ", IP);
		assertEquals(DeviceLinks.DEFAULT_LABEL, _links.find(blank.userCode()).label());

		Started longLabel = _links.start("PhoneBlockDongle", "x".repeat(200), IP);
		assertEquals(DeviceLinks.MAX_LABEL_LENGTH, _links.find(longLabel.userCode()).label().length());
	}

	@Test
	public void testDeny() throws Exception {
		Started started = _links.start("PhoneBlockDongle", null, IP);
		assertEquals(Decision.OK, _links.deny(started.userCode(), "link-user"));

		// A denied link can no longer be approved.
		assertEquals(Decision.ALREADY_DENIED, _links.approve(started.userCode(), "other-user"));

		AtomicInteger minted = new AtomicInteger();
		PollResult denied = _links.poll(started.deviceCode(), UA, (u, n, ua, l) -> {
			minted.incrementAndGet();
			return _tokens.createToken(u, n, ua, l);
		});
		assertEquals(DeviceLinks.DENIED, denied.status());
		assertNull(denied.token());
		assertEquals(0, minted.get());
		assertEquals(0, newTokens());

		advance(DeviceLinks.INTERVAL);
		assertEquals(DeviceLinks.EXPIRED, poll(started).status());
	}

	@Test
	public void testApproveTwiceRefused() throws Exception {
		Started started = _links.start("PhoneBlockDongle", null, IP);
		assertEquals(Decision.OK, _links.approve(started.userCode(), "link-user"));
		assertEquals(Decision.ALREADY_APPROVED, _links.approve(started.userCode(), "other-user"));
		assertEquals(Decision.ALREADY_APPROVED, _links.deny(started.userCode(), "other-user"));

		PollResult approved = poll(started);
		assertEquals("link-user", _db.checkAuthToken(approved.token(), _now.get(), UA, false).getUserName());
	}

	@Test
	public void testNoTokenBeforePickup() throws Exception {
		Started started = _links.start("PhoneBlockDongle", null, IP);
		assertEquals(Decision.OK, _links.approve(started.userCode(), "link-user"));
		assertEquals(0, newTokens());

		// An approval nobody collects leaves no token behind.
		advance(DeviceLinks.EXPIRES_IN);
		assertEquals(DeviceLinks.EXPIRED, poll(started).status());
		assertEquals(0, newTokens());
	}

	@Test
	public void testUnknownAndExpired() throws Exception {
		assertEquals(DeviceLinks.EXPIRED, poll("unknown-device-code").status());

		Started started = _links.start("PhoneBlockDongle", null, IP);
		advance(DeviceLinks.EXPIRES_IN);

		assertNull(_links.find(started.userCode()));
		assertEquals(Decision.UNKNOWN, _links.approve(started.userCode(), "link-user"));
		assertEquals(DeviceLinks.EXPIRED, poll(started).status());
		assertEquals(0, _links.size());
	}

	@Test
	public void testApproveExpiredRefused() throws Exception {
		Started started = _links.start("PhoneBlockDongle", null, IP);
		advance(DeviceLinks.EXPIRES_IN - 1);
		assertNotNull(_links.find(started.userCode()));
		advance(1);
		assertEquals(Decision.UNKNOWN, _links.approve(started.userCode(), "link-user"));
	}

	@Test
	public void testSlowDown() throws Exception {
		Started started = _links.start("PhoneBlockDongle", null, IP);
		assertEquals(DeviceLinks.PENDING, poll(started).status());
		assertEquals(DeviceLinks.SLOW_DOWN, poll(started).status());

		// The interval counts from the last poll, also a refused one.
		_now.addAndGet(DeviceLinks.INTERVAL * 1000L - DeviceLinks.POLL_GRACE_MS - 1);
		assertEquals(DeviceLinks.SLOW_DOWN, poll(started).status());

		advance(DeviceLinks.INTERVAL);
		assertEquals(DeviceLinks.PENDING, poll(started).status());

		// slow_down takes precedence, the approval is delivered on the next regular poll.
		_links.approve(started.userCode(), "link-user");
		assertEquals(DeviceLinks.SLOW_DOWN, poll(started).status());
		advance(DeviceLinks.INTERVAL);
		assertEquals(DeviceLinks.APPROVED, poll(started).status());
	}

	@Test
	public void testUserCodeFormat() throws Exception {
		DeviceLinks links = new DeviceLinks(_now::get, new SecureRandom());
		Set<String> codes = new HashSet<>();
		for (int n = 0; n < 1000; n++) {
			// Distinct clients, so that the per-client limit does not interfere.
			Started started = links.start("PhoneBlockDongle", null, "198.51.100." + n);
			assertTrue(USER_CODE.matcher(started.userCode()).matches(), started.userCode());
			assertTrue(codes.add(started.userCode()), "Duplicate open user code.");
		}
	}

	@Test
	public void testUserCodeNormalization() throws Exception {
		Started started = _links.start("PhoneBlockDongle", null, IP);
		String code = started.userCode();
		String letters = code.replace("-", "");

		LinkInfo expected = _links.find(code);
		assertNotNull(expected);
		assertEquals(expected, _links.find(letters.substring(0, 4).toLowerCase() + " " + letters.substring(4).toLowerCase()));
		assertEquals(expected, _links.find(letters));
		assertEquals(expected, _links.find(code.toLowerCase()));
		assertEquals(expected, _links.find("  " + code + " "));

		assertEquals("BCDFGHJK", DeviceLinks.normalizeUserCode("bcdf ghjk"));
		assertEquals("BCDFGHJK", DeviceLinks.normalizeUserCode("BCDFGHJK"));
		assertEquals("BCDFGHJK", DeviceLinks.normalizeUserCode("bcdf-ghjk"));
		assertNull(DeviceLinks.normalizeUserCode("BCDF-GHJ"));
		assertNull(DeviceLinks.normalizeUserCode("BCDF-GHJKL"));
		assertNull(DeviceLinks.normalizeUserCode("ABCD-EFGH"));
		assertNull(DeviceLinks.normalizeUserCode(null));
	}

	@Test
	public void testPerClientLimit() throws Exception {
		for (int n = 0; n < DeviceLinks.MAX_PER_CLIENT; n++) {
			_links.start("PhoneBlockDongle", null, IP);
		}
		assertThrows(DeviceLinks.LimitExceeded.class, () -> _links.start("PhoneBlockDongle", null, IP));

		// Another client is not affected.
		assertNotNull(_links.start("PhoneBlockDongle", null, "192.0.2.11"));

		// Expired links no longer count.
		advance(DeviceLinks.EXPIRES_IN);
		assertNotNull(_links.start("PhoneBlockDongle", null, IP));
	}

	@Test
	public void testWrongCodeLimit() {
		WrongCodeLimit limit = new WrongCodeLimit();
		long now = 5_000_000;
		for (int n = 0; n < WrongCodeLimit.MAX_FAILURES; n++) {
			assertTrue(limit.allowed(now));
			limit.failed(now);
		}
		assertTrue(!limit.allowed(now + 1));
		assertTrue(limit.allowed(now + WrongCodeLimit.WINDOW_MS));
	}

	@Test
	public void testInstance() {
		assertSame(DeviceLinks.getInstance(), DeviceLinks.getInstance());
	}

	private PollResult poll(Started started) {
		return poll(started.deviceCode());
	}

	private PollResult poll(String deviceCode) {
		return _links.poll(deviceCode, UA, _tokens);
	}

	private void advance(int seconds) {
		_now.addAndGet(seconds * 1000L);
	}

	private int newTokens() throws SQLException {
		return tokenCount() - _baseTokens;
	}

	private int tokenCount() throws SQLException {
		try (Connection connection = _dataSource.getConnection();
		     Statement statement = connection.createStatement();
		     ResultSet result = statement.executeQuery("select count(1) from TOKENS")) {
			result.next();
			return result.getInt(1);
		}
	}
}
