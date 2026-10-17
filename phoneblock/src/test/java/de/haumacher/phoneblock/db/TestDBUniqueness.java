/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.haumacher.phoneblock.analysis.NumberAnalyzer;
import de.haumacher.phoneblock.app.api.model.PhoneNumer;
import de.haumacher.phoneblock.app.api.model.Rating;
import de.haumacher.phoneblock.db.DB.EmailUser;
import de.haumacher.phoneblock.db.DB.PersonalRating;
import de.haumacher.phoneblock.scheduler.SchedulerService;
import jakarta.mail.internet.AddressException;

/**
 * Tests for the DB layer under duplicate data and concurrent submissions: duplicate
 * {@code PERSONALIZATION} inserts (#520) and duplicate {@code USERS.EMAIL} values (#523).
 */
public class TestDBUniqueness {

	private static final String PHONE = "0301234567";

	private static final String EMAIL = "user@example.com";

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

	// #520: duplicate PERSONALIZATION inserts.

	@Test
	void testConditionalPersonalizationInsert() {
		long userId = createUser("rater");
		String phone = phoneId(PHONE);
		byte[] sha1 = NumberAnalyzer.getPhoneHash(number(PHONE));

		try (SqlSession session = _db.openSession()) {
			BlockList blocklist = session.getMapper(BlockList.class);
			assertEquals(1, blocklist.addPersonalization(userId, phone, sha1, 1000));
			assertEquals(0, blocklist.addPersonalization(userId, phone, sha1, 2000));
			assertEquals(0, blocklist.addExclude(userId, phone, sha1, 3000));
			assertEquals(0, blocklist.addWildcard(userId, phone, false, 4000));
			session.commit();
		}

		assertEquals(1, personalizationCount(userId, phone));
		DBPersonalization entry = activity(userId, phone);
		assertTrue(entry.isBlocked());
		assertEquals(1000, entry.getLastActivity());
	}

	@Test
	void testRatingOnExistingPersonalization() {
		long userId = createUser("rater");
		String phone = phoneId(PHONE);
		long now = 1_000_000;

		addRating("rater", Rating.B_MISSED, now++);
		assertEquals(1, personalizationCount(userId, phone));
		assertTrue(activity(userId, phone).isBlocked());

		// Same vote again: ignored repeat.
		addRating("rater", Rating.C_PING, now++);
		assertEquals(1, personalizationCount(userId, phone));
		assertTrue(activity(userId, phone).isBlocked());

		// Opposite vote: flips the entry.
		addRating("rater", Rating.A_LEGITIMATE, now++);
		assertEquals(1, personalizationCount(userId, phone));
		assertFalse(activity(userId, phone).isBlocked());

		// Same (legitimate) vote again: ignored repeat.
		addRating("rater", Rating.A_LEGITIMATE, now++);
		assertEquals(1, personalizationCount(userId, phone));
		assertFalse(activity(userId, phone).isBlocked());

		// Flip back.
		addRating("rater", Rating.G_FRAUD, now++);
		assertEquals(1, personalizationCount(userId, phone));
		assertTrue(activity(userId, phone).isBlocked());
	}

	/**
	 * A concurrent submission with the same vote commits its entry between the read and the insert of
	 * this one.
	 */
	@Test
	void testRatingRaceSameVote() {
		long userId = createUser("rater");
		String phone = phoneId(PHONE);
		byte[] sha1 = NumberAnalyzer.getPhoneHash(number(PHONE));

		PersonalRating result;
		try (SqlSession session = _db.openSession()) {
			BlockList blocklist = racingBlockList(session, () -> commitPersonalization(userId, phone, true, 1000));
			result = _db.applyPersonalRating(blocklist, userId, phone, sha1, true, 2000);
			session.commit();
		}

		assertSame(PersonalRating.IGNORED, result);
		assertEquals(1, personalizationCount(userId, phone));
		DBPersonalization entry = activity(userId, phone);
		assertTrue(entry.isBlocked());
		assertEquals(1000, entry.getLastActivity(), "The concurrent submission's entry is kept.");
	}

	/**
	 * A concurrent submission with the opposite vote commits its entry between the read and the
	 * insert of this one: the first committed submission wins, this one is ignored.
	 */
	@Test
	void testRatingRaceOppositeVote() {
		long userId = createUser("rater");
		String phone = phoneId(PHONE);
		byte[] sha1 = NumberAnalyzer.getPhoneHash(number(PHONE));

		PersonalRating result;
		try (SqlSession session = _db.openSession()) {
			BlockList blocklist = racingBlockList(session, () -> commitPersonalization(userId, phone, false, 1000));
			result = _db.applyPersonalRating(blocklist, userId, phone, sha1, true, 2000);
			session.commit();
		}

		assertFalse(result.applied());
		assertNull(result.flipFrom());
		assertEquals(1, personalizationCount(userId, phone));
		assertFalse(activity(userId, phone).isBlocked());
	}

	@Test
	void testCallReportOnExistingPersonalization() {
		long userId = createUser("reporter");
		String phone = phoneId(PHONE);
		byte[] sha1 = NumberAnalyzer.getPhoneHash(number(PHONE));

		commitPersonalization(userId, phone, true, 1000);
		recordCall(userId, 2000);
		assertEquals(1, personalizationCount(userId, phone));
		DBPersonalization entry = activity(userId, phone);
		assertTrue(entry.isBlocked());
		assertEquals(2000, entry.getLastActivity());

		// An allow-listed number gets no evidence and stays allowed.
		try (SqlSession session = _db.openSession()) {
			BlockList blocklist = session.getMapper(BlockList.class);
			blocklist.removePersonalization(userId, phone);
			assertEquals(1, blocklist.addExclude(userId, phone, sha1, 3000));
			assertEquals(0.0, _db.cappedCallSpamContribution(blocklist, userId, phone, sha1, 4000));
			session.commit();
		}
		assertEquals(1, personalizationCount(userId, phone));
		assertFalse(activity(userId, phone).isBlocked());
	}

	/**
	 * A concurrent call report commits the entry between the read and the insert of this one.
	 */
	@Test
	void testCallReportRace() {
		long userId = createUser("reporter");
		String phone = phoneId(PHONE);
		byte[] sha1 = NumberAnalyzer.getPhoneHash(number(PHONE));
		long now = 2000;

		double delta;
		try (SqlSession session = _db.openSession()) {
			BlockList blocklist = racingBlockList(session, () -> commitPersonalization(userId, phone, true, now));
			delta = _db.cappedCallSpamContribution(blocklist, userId, phone, sha1, now);
			session.commit();
		}

		// The concurrent report already contributed the full unit at the same time.
		assertEquals(0.0, delta, 1e-9);
		assertEquals(1, personalizationCount(userId, phone));
		DBPersonalization entry = activity(userId, phone);
		assertTrue(entry.isBlocked());
		assertEquals(now, entry.getLastActivity());
	}

	/**
	 * A concurrent rating allows the number between the read and the insert of a call report.
	 */
	@Test
	void testCallReportRaceWithAllow() {
		long userId = createUser("reporter");
		String phone = phoneId(PHONE);
		byte[] sha1 = NumberAnalyzer.getPhoneHash(number(PHONE));

		double delta;
		try (SqlSession session = _db.openSession()) {
			BlockList blocklist = racingBlockList(session, () -> commitPersonalization(userId, phone, false, 1000));
			delta = _db.cappedCallSpamContribution(blocklist, userId, phone, sha1, 2000);
			session.commit();
		}

		assertEquals(0.0, delta);
		assertEquals(1, personalizationCount(userId, phone));
		assertFalse(activity(userId, phone).isBlocked());
	}

	// #523: duplicate USERS.EMAIL values.

	@Test
	void testEmailLoginWithDuplicates() throws Exception {
		long older = createUser("older");
		long newer = createUser("newer");
		assertTrue(older < newer);

		// Duplicates as they exist in production, bypassing the guard.
		forceEmail("older", EMAIL);
		forceEmail("newer", EMAIL);

		setLastAccess("older", 5000);
		setLastAccess("newer", 3000);
		assertEquals("older", _db.getEmailLogin(EMAIL));
		assertEquals("older", _db.getEmailLogin("User@Example.COM"));

		setLastAccess("newer", 6000);
		assertEquals("newer", _db.getEmailLogin(EMAIL));

		// Tie: the oldest account wins.
		setLastAccess("older", 6000);
		assertEquals("older", _db.getEmailLogin(EMAIL));

		assertTrue(_db.isEmailInUse(EMAIL));
		assertTrue(_db.isEmailInUse(" USER@example.com "));
		assertFalse(_db.isEmailInUse("other@example.com"));
		assertNull(_db.getEmailLogin("other@example.com"));
	}

	@Test
	void testRegistrationDoesNotCreateDuplicate() throws Exception {
		EmailUser created = _db.getOrCreateEmailUser(EMAIL, "User", "de", "+49");
		assertTrue(created.created());
		assertNotNull(created.password());
		assertEquals(created.login(), _db.getEmailLogin(EMAIL));

		// Registering the same address again (other spelling) logs into the existing account.
		EmailUser again = _db.getOrCreateEmailUser("User@Example.com", "User", "de", "+49");
		assertFalse(again.created());
		assertNull(again.password());
		assertEquals(created.login(), again.login());

		assertEquals(1, emailCount(EMAIL));
	}

	@Test
	void testRegistrationWithExistingDuplicates() throws Exception {
		createUser("older");
		createUser("newer");
		forceEmail("older", EMAIL);
		forceEmail("newer", EMAIL);
		setLastAccess("older", 1000);
		setLastAccess("newer", 2000);

		EmailUser account = _db.getOrCreateEmailUser(EMAIL, "User", "de", "+49");
		assertFalse(account.created());
		assertEquals("newer", account.login());
		assertEquals(2, emailCount(EMAIL));
	}

	/**
	 * A concurrent registration created the account after the lookup: the conditional insert adds
	 * nothing.
	 */
	@Test
	void testConditionalUserInsert() {
		try (SqlSession session = _db.openSession()) {
			Users users = session.getMapper(Users.class);
			assertEquals(1, users.addEmailUser("first", "First", "de", "+49", new byte[] { 1 }, 1000, EMAIL));
			assertEquals(0, users.addEmailUser("second", "Second", "de", "+49", new byte[] { 1 }, 2000, EMAIL));
			session.commit();
		}
		assertEquals(1, emailCount(EMAIL));
		try (SqlSession session = _db.openSession()) {
			assertNull(session.getMapper(Users.class).getUserId("second"));
		}
	}

	@Test
	void testEmailChangeToUsedAddressRefused() throws Exception {
		createUser("owner");
		createUser("other");
		assertTrue(_db.setEmail("owner", EMAIL));

		// Another account must not take the address, in no spelling.
		assertFalse(_db.setEmail("other", EMAIL));
		assertFalse(_db.setEmail("other", "USER@example.com"));
		assertEquals(1, emailCount(EMAIL));
		assertNull(emailOf("other"));

		// Setting the own address again is fine.
		assertTrue(_db.setEmail("owner", EMAIL));

		// A free address can be set.
		assertTrue(_db.setEmail("other", "Other@Example.com"));
		assertEquals("other@example.com", emailOf("other"));
		assertNotEquals(_db.getEmailLogin(EMAIL), _db.getEmailLogin("other@example.com"));
	}

	@Test
	void testCanonicalEmail() throws AddressException {
		assertEquals(EMAIL, DB.canonicalEMail(" User <USER@Example.com> "));
	}

	// Helpers

	/**
	 * Creates a {@link BlockList} that runs the given concurrent action right after the first read of
	 * an entry, before returning the (now stale) result.
	 */
	private BlockList racingBlockList(SqlSession session, Runnable concurrent) {
		BlockList real = session.getMapper(BlockList.class);
		boolean[] done = { false };
		return (BlockList) Proxy.newProxyInstance(BlockList.class.getClassLoader(), new Class<?>[] { BlockList.class },
			(proxy, method, args) -> {
				Object result;
				try {
					result = method.invoke(real, args);
				} catch (InvocationTargetException ex) {
					throw ex.getCause();
				}
				if (!done[0] && method.getName().equals("getPersonalizationActivity")) {
					done[0] = true;
					concurrent.run();
				}
				return result;
			});
	}

	/**
	 * Adds a personalization in a separate, committed transaction (the concurrent submission).
	 */
	private void commitPersonalization(long userId, String phone, boolean blocked, long time) {
		byte[] sha1 = NumberAnalyzer.getPhoneHash(number(PHONE));
		try (SqlSession other = _db.openSession()) {
			BlockList blocklist = other.getMapper(BlockList.class);
			int inserted = blocked
				? blocklist.addPersonalization(userId, phone, sha1, time)
				: blocklist.addExclude(userId, phone, sha1, time);
			assertEquals(1, inserted);
			other.commit();
		}
	}

	private void addRating(String userName, Rating rating, long now) {
		_db.addRating(userName, number(PHONE), "+49", rating, null, "de", now);
		_db.drainDirtyBlocks();
	}

	private void recordCall(long userId, long now) {
		PhoneNumer number = number(PHONE);
		try (SqlSession tx = _db.openSession()) {
			_db.recordCall(tx.getMapper(SpamReports.class), tx.getMapper(BlockList.class), userId, number,
				NumberAnalyzer.getPhoneId(number), "+49", now);
			tx.commit();
		}
		_db.drainDirtyBlocks();
	}

	private static PhoneNumer number(String phone) {
		return NumberAnalyzer.analyze(phone, "+49");
	}

	private static String phoneId(String phone) {
		return NumberAnalyzer.getPhoneId(number(phone));
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

	private int personalizationCount(long userId, String phone) {
		return queryInt("select count(*) from PERSONALIZATION where USERID = ? and PHONE = ?", userId, phone);
	}

	private int emailCount(String email) {
		return queryInt("select count(*) from USERS where EMAIL = ?", email);
	}

	private String emailOf(String login) throws SQLException {
		try (Connection connection = _dataSource.getConnection();
				PreparedStatement stmt = connection.prepareStatement("select EMAIL from USERS where LOGIN = ?")) {
			stmt.setString(1, login);
			try (ResultSet result = stmt.executeQuery()) {
				assertTrue(result.next());
				return result.getString(1);
			}
		}
	}

	private void forceEmail(String login, String email) throws SQLException {
		update("update USERS set EMAIL = ? where LOGIN = ?", email, login);
	}

	private void setLastAccess(String login, long lastAccess) throws SQLException {
		update("update USERS set LASTACCESS = ? where LOGIN = ?", lastAccess, login);
	}

	private void update(String sql, Object... args) throws SQLException {
		try (Connection connection = _dataSource.getConnection();
				PreparedStatement stmt = connection.prepareStatement(sql)) {
			for (int n = 0; n < args.length; n++) {
				stmt.setObject(n + 1, args[n]);
			}
			assertEquals(1, stmt.executeUpdate());
		}
	}

	private int queryInt(String sql, Object... args) {
		try (Connection connection = _dataSource.getConnection();
				PreparedStatement stmt = connection.prepareStatement(sql)) {
			for (int n = 0; n < args.length; n++) {
				stmt.setObject(n + 1, args[n]);
			}
			try (ResultSet result = stmt.executeQuery()) {
				assertTrue(result.next());
				return result.getInt(1);
			}
		} catch (SQLException ex) {
			throw new RuntimeException(ex);
		}
	}
}
