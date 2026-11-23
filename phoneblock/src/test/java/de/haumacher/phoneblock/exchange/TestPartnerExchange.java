/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.exchange;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.haumacher.msgbuf.json.JsonWriter;
import de.haumacher.msgbuf.server.io.WriterAdapter;
import de.haumacher.phoneblock.analysis.NumberAnalyzer;
import de.haumacher.phoneblock.app.api.model.PhoneNumer;
import de.haumacher.phoneblock.app.api.model.Rating;
import de.haumacher.phoneblock.app.api.model.UserComment;
import de.haumacher.phoneblock.db.BlockList;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.ExternalReports;
import de.haumacher.phoneblock.db.SpamReports;
import de.haumacher.phoneblock.db.TestDB;
import de.haumacher.phoneblock.db.Users;
import de.haumacher.phoneblock.index.IndexUpdateService;
import de.haumacher.phoneblock.scheduler.SchedulerService;

/**
 * Test case for the partner report exchange: {@link PartnerExport} and {@link NizeImportService}.
 */
class TestPartnerExchange {

	private static final long DAY = PartnerExport.DAY_MILLIS;

	/** Start of "today" (UTC) in all tests. */
	private static final long TODAY = LocalDate.of(2026, 11, 20).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();

	/** The current time in all tests. */
	private static final long NOW = TODAY + 12 * 60 * 60 * 1000L;

	/** Start of the previous day, the default window. */
	private static final long YESTERDAY = TODAY - DAY;

	private static final byte[] SECRET = "test-secret".getBytes(StandardCharsets.UTF_8);

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
		_db = null;

		try (Connection connection = _dataSource.getConnection()) {
			try (Statement statement = connection.createStatement()) {
				statement.execute("SHUTDOWN");
			}
		}

		_scheduler.contextDestroyed(null);
		_scheduler = null;
	}

	// ---- export ----

	@Test
	void testExportUserReport() {
		_db.createUser("alice", "Alice", "de", "+49");
		_db.addRating("alice", number("+493012345671"), "+49", Rating.G_FRAUD, "Fake bank hotline", "de", YESTERDAY + 1000);
		corroborate("+493012345671", 1);

		List<PartnerReport> feed = export(null, null);
		assertEquals(1, feed.size());

		PartnerReport report = feed.get(0);
		assertEquals("+493012345671", report.phone());
		assertEquals(1, report.score());
		assertEquals(8, report.typeId());
		assertEquals("Fake bank hotline", report.text());
		assertEquals(YESTERDAY + 1000, report.created());
		assertEquals(32, report.id().length());
		assertEquals(32, report.senderId().length());
		assertNotEquals(report.id(), report.senderId());

		// Stable on a second call.
		List<PartnerReport> again = export(null, null);
		assertEquals(report, again.get(0));

		// Other secret, other ids.
		List<PartnerReport> otherSecret;
		try (SqlSession session = _db.openSession()) {
			otherSecret = PartnerExport.export(session.getMapper(ExternalReports.class),
				"other".getBytes(StandardCharsets.UTF_8), PartnerExport.window(null, null, NOW));
		}
		assertNotEquals(report.senderId(), otherSecret.get(0).senderId());
		assertNotEquals(report.id(), otherSecret.get(0).id());
	}

	@Test
	void testExportExcludesLegitimateAndImports() {
		_db.createUser("alice", "Alice", "de", "+49");
		_db.addRating("alice", number("+493012345671"), "+49", Rating.C_PING, null, "de", YESTERDAY + 1000);

		// Whitelist entry of the same user.
		_db.addRating("alice", number("+493012345672"), "+49", Rating.A_LEGITIMATE, "My dentist", "de", YESTERDAY + 2000);

		// Meta-search comment: SERVICE set, no user.
		try (SqlSession session = _db.openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			UserComment comment = UserComment.create()
				.setId("meta-1")
				.setPhone(NumberAnalyzer.getPhoneId(number("+493012345673")))
				.setRating(Rating.E_ADVERTISING)
				.setComment("Werbung")
				.setLang("de")
				.setService("tellows")
				.setCreated(YESTERDAY + 3000);
			_db.addRating(reports, number("+493012345673"), "+49", comment, true);

			// FTC-style bare vote on another number.
			_db.processVotesAndPublish(reports, number("+493012345674"), "+49", 1, YESTERDAY + 4000);
			session.commit();
		}

		// All numbers corroborated, so that only the source decides.
		corroborate("+493012345671", 1);
		corroborate("+493012345672", 3);
		corroborate("+493012345673", 1);
		corroborate("+493012345674", 1);

		List<PartnerReport> feed = export(null, null);
		assertEquals(List.of("+493012345671"), phones(feed));
		assertEquals(10, feed.get(0).typeId());
		assertNull(feed.get(0).text(), "No comment text, no text field.");
	}

	@Test
	void testExportWithoutCommentRow() {
		_db.createUser("alice", "Alice", "de", "+49");
		addBlocklistEntry("alice", "+493012345675", YESTERDAY + 1000);
		corroborate("+493012345675", 2);

		List<PartnerReport> feed = export(null, null);
		assertEquals(1, feed.size());
		assertEquals(1, feed.get(0).typeId());
		assertNull(feed.get(0).text());

		String json = toJson(feed);
		assertFalse(json.contains("\"text\""), json);
		assertFalse(json.contains("\"name\""), json);
		assertTrue(json.startsWith("{\"comments\":[{"), json);
	}

	@Test
	void testExportSkipsWildcards() {
		_db.createUser("alice", "Alice", "de", "+49");
		_db.addWildcard("alice", "+4930123", true, YESTERDAY + 1000);

		assertEquals(List.of(), export(null, null));
	}

	@Test
	void testExportDuplicateComments() {
		_db.createUser("alice", "Alice", "de", "+49");
		addBlocklistEntry("alice", "+493012345676", YESTERDAY + 1000);
		corroborate("+493012345676", 2);

		String phoneId = NumberAnalyzer.getPhoneId(number("+493012345676"));
		try (SqlSession session = _db.openSession()) {
			long userId = session.getMapper(Users.class).getUserId("alice");
			SpamReports reports = session.getMapper(SpamReports.class);
			reports.addComment("dup-1", phoneId, Rating.D_POLL, "Umfrage", "de", null, YESTERDAY + 1000, userId);
			reports.addComment("dup-2", phoneId, Rating.F_GAMBLE, "Gewinnspiel", "de", null, YESTERDAY + 2000, userId);
			session.commit();
		}

		List<PartnerReport> feed = export(null, null);
		assertEquals(1, feed.size(), "One entry per block-list entry.");
		assertEquals(3, feed.get(0).typeId(), "The newest comment row wins.");
		assertEquals("Gewinnspiel", feed.get(0).text());
	}

	@Test
	void testExportWindow() {
		_db.createUser("alice", "Alice", "de", "+49");
		addBlocklistEntry("alice", "+493012345671", TODAY - 11 * DAY);
		addBlocklistEntry("alice", "+493012345672", TODAY - 5 * DAY);
		addBlocklistEntry("alice", "+493012345673", YESTERDAY - 1);
		addBlocklistEntry("alice", "+493012345674", YESTERDAY);
		addBlocklistEntry("alice", "+493012345675", TODAY - 1);
		addBlocklistEntry("alice", "+493012345676", TODAY);
		addBlocklistEntry("alice", "+493012345677", TODAY + 1000);
		for (int n = 1; n <= 7; n++) {
			corroborate("+49301234567" + n, 2);
		}

		// Default: previous full UTC day.
		assertEquals(List.of("+493012345674", "+493012345675"), phones(export(null, null)));

		// Clamped to [today - 10 days, today).
		assertEquals(List.of("+493012345672", "+493012345673", "+493012345674", "+493012345675"),
			phones(export(0L, NOW + 100 * DAY)));

		// Explicit window.
		assertEquals(List.of("+493012345672"), phones(export(TODAY - 6 * DAY, TODAY - 4 * DAY)));

		// Empty window.
		assertEquals(List.of(), phones(export(TODAY - 4 * DAY, TODAY - 6 * DAY)));

		PartnerExport.Window window = PartnerExport.window(0L, NOW + 100 * DAY, NOW);
		assertEquals(TODAY - 10 * DAY, window.from());
		assertEquals(TODAY, window.to());
	}

	@Test
	void testExportSenders() {
		_db.createUser("alice", "Alice", "de", "+49");
		_db.createUser("bob", "Bob", "de", "+49");
		_db.createUser("carol", "Carol", "de", "+49");
		addBlocklistEntry("alice", "+493012345671", YESTERDAY + 1000);
		addBlocklistEntry("bob", "+493012345671", YESTERDAY + 2000);
		addBlocklistEntry("alice", "+493012345672", YESTERDAY + 3000);
		corroborate("+493012345671", 2);
		corroborate("+493012345672", 2);

		List<PartnerReport> feed = export(null, null);
		assertEquals(3, feed.size());
		assertEquals("+493012345671", feed.get(0).phone());
		assertEquals("+493012345671", feed.get(1).phone());
		assertNotEquals(feed.get(0).senderId(), feed.get(1).senderId(), "Different users, different senders.");
		assertEquals(feed.get(0).senderId(), feed.get(2).senderId(), "Same user, same sender.");
		assertEquals(3, feed.stream().map(PartnerReport::id).distinct().count());
	}

	/**
	 * Allow-list entry flipped to the block list is a report at the time of the flip.
	 */
	@Test
	void testExportFlip() {
		_db.createUser("alice", "Alice", "de", "+49");
		_db.addRating("alice", number("+493012345671"), "+49", Rating.A_LEGITIMATE, null, "de", YESTERDAY - DAY);
		_db.addRating("alice", number("+493012345671"), "+49", Rating.D_POLL, null, "de", YESTERDAY + 1000);
		corroborate("+493012345671", 2);

		List<PartnerReport> feed = export(null, null);
		assertEquals(List.of("+493012345671"), phones(feed));
		assertEquals(YESTERDAY + 1000, feed.get(0).created());
		assertEquals(6, feed.get(0).typeId());
	}

	// ---- corroboration filter ----

	@Test
	void testUncorroboratedNotExported() {
		_db.createUser("alice", "Alice", "de", "+49");
		_db.addRating("alice", number("+493012345691"), "+49", Rating.G_FRAUD, "Test", "de", YESTERDAY + 1000);
		assertEquals(1, votes("+493012345691"));

		assertEquals(List.of(), export(null, null));
	}

	@Test
	void testCorroboratedBySecondUser() {
		_db.createUser("alice", "Alice", "de", "+49");
		_db.createUser("bob", "Bob", "de", "+49");
		_db.addRating("alice", number("+493012345691"), "+49", Rating.G_FRAUD, null, "de", YESTERDAY + 1000);
		assertEquals(List.of(), export(null, null));

		// Evaluated when the feed is requested: alice's report shows up once bob corroborates it.
		_db.addRating("bob", number("+493012345691"), "+49", Rating.C_PING, null, "de", YESTERDAY + 2000);
		assertEquals(2, votes("+493012345691"));

		List<PartnerReport> feed = export(null, null);
		assertEquals(List.of("+493012345691", "+493012345691"), phones(feed));
		assertEquals(YESTERDAY + 1000, feed.get(0).created());
		assertEquals(YESTERDAY + 2000, feed.get(1).created());
		assertNotEquals(feed.get(0).senderId(), feed.get(1).senderId());
	}

	@Test
	void testCorroboratedByImport() throws Exception {
		_db.createUser("alice", "Alice", "de", "+49");
		_db.addRating("alice", number("+493012345681"), "+49", Rating.G_FRAUD, null, "de", YESTERDAY + 500);
		assertEquals(List.of(), export(null, null));

		// The fixture holds one counted Nize report for +493012345681.
		service().runImport(_db, fixtureClient(FIXTURE, new ArrayList<>()), NOW);
		assertEquals(2, votes("+493012345681"));

		List<PartnerReport> feed = export(null, null);
		assertEquals(1, feed.size(), "Only the PhoneBlock user's report, never the imported one.");
		assertEquals("+493012345681", feed.get(0).phone());
		assertEquals(YESTERDAY + 500, feed.get(0).created());
		assertEquals(8, feed.get(0).typeId());
	}

	@Test
	void testRemovedBlockNotExported() {
		_db.createUser("alice", "Alice", "de", "+49");
		_db.createUser("bob", "Bob", "de", "+49");
		_db.addRating("alice", number("+493012345691"), "+49", Rating.G_FRAUD, null, "de", YESTERDAY + 1000);
		_db.addRating("bob", number("+493012345691"), "+49", Rating.G_FRAUD, null, "de", YESTERDAY + 2000);
		assertEquals(2, export(null, null).size());

		// Alice removes her (test) block again.
		try (SqlSession session = _db.openSession()) {
			long userId = session.getMapper(Users.class).getUserId("alice");
			assertTrue(session.getMapper(BlockList.class).removePersonalization(userId,
				NumberAnalyzer.getPhoneId(number("+493012345691"))));
			session.commit();
		}

		List<PartnerReport> feed = export(null, null);
		assertEquals(1, feed.size());
		assertEquals(YESTERDAY + 2000, feed.get(0).created(), "Only bob's report remains.");
	}

	@Test
	void testCallsDoNotAddVotes() {
		_db.createUser("alice", "Alice", "de", "+49");
		_db.createUser("bob", "Bob", "de", "+49");
		_db.addRating("alice", number("+493012345691"), "+49", Rating.G_FRAUD, null, "de", YESTERDAY + 1000);
		interceptCall("bob", "+493012345691", YESTERDAY + 2000);
		interceptCall("bob", "+493012345691", YESTERDAY + 3000);
		interceptCall("alice", "+493012345691", YESTERDAY + 4000);

		assertEquals(1, votes("+493012345691"), "Intercepted calls do not add votes.");
	}

	// ---- import ----

	private static final String FIXTURE = """
		{
		  "comments": [
		    {"id": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa01", "phone": "+493012345681", "senderId": "s1",
		     "created": %1$d, "text": "Robocall, bank offer", "score": 1, "name": "Bank XYZ", "typeId": 9},
		    {"id": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa02", "phone": "+493012345682", "senderId": "s2",
		     "created": %2$d, "score": 2},
		    {"id": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa03", "phone": "+493012345683", "senderId": "s3",
		     "created": %2$d, "text": "Mein Arzt", "score": 1, "typeId": 2},
		    {"id": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa04", "phone": "+493012345684", "senderId": "s4",
		     "created": %2$d, "text": "Alles gut", "score": 4},
		    {"id": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa05", "phone": "+493012345685", "senderId": "s5",
		     "created": %2$d, "typeId": 1},
		    {"id": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa06", "phone": "+493012345686", "senderId": "s6",
		     "created": %2$d, "text": "  ", "typeId": 7},
		    {"id": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa07", "phone": "not-a-number", "senderId": "s7",
		     "created": %2$d, "score": 1}
		  ]
		}
		""".formatted(YESTERDAY + 1000, YESTERDAY + 2000);

	@Test
	void testClassify() {
		assertNull(NizeImportService.classify(1, 2));
		assertNull(NizeImportService.classify(null, 2));
		assertNull(NizeImportService.classify(3, 8));
		assertNull(NizeImportService.classify(5, null));
		assertEquals(Rating.B_MISSED, NizeImportService.classify(1, null));
		assertEquals(Rating.B_MISSED, NizeImportService.classify(2, 1));
		assertEquals(Rating.E_ADVERTISING, NizeImportService.classify(1, 9));
		assertEquals(Rating.E_ADVERTISING, NizeImportService.classify(2, 5));
		assertEquals(Rating.G_FRAUD, NizeImportService.classify(1, 4));
		assertEquals(Rating.G_FRAUD, NizeImportService.classify(null, 7));
		assertEquals(Rating.G_FRAUD, NizeImportService.classify(null, 8));
		assertEquals(Rating.F_GAMBLE, NizeImportService.classify(null, 3));
		assertEquals(Rating.D_POLL, NizeImportService.classify(null, 6));
		assertEquals(Rating.C_PING, NizeImportService.classify(null, 10));
		assertEquals(Rating.B_MISSED, NizeImportService.classify(null, 11));
		assertNull(NizeImportService.classify(null, 1));
		assertNull(NizeImportService.classify(null, null));
		assertNull(NizeImportService.classify(null, 12));
		assertNull(NizeImportService.classify(0, 9));
	}

	@Test
	void testImport() throws Exception {
		List<long[]> requests = new ArrayList<>();
		NizeImportService service = service();
		int counted = service.runImport(_db, fixtureClient(FIXTURE, requests), NOW);

		assertEquals(3, counted);
		assertEquals(1, requests.size());
		assertArrayEquals(new long[] {YESTERDAY, TODAY}, requests.get(0));

		assertImported();

		// The position advanced, a second run on the same day fetches nothing.
		assertEquals(0, service.runImport(_db, fixtureClient(FIXTURE, requests), NOW));
		assertEquals(1, requests.size());

		// Importing the same reports again (e.g. an overlapping window) changes nothing.
		try (SqlSession session = _db.openSession()) {
			int again = service.importReports(_db, session, PartnerReport.parseFeed(new StringReader(FIXTURE)).reports(),
				new ArrayList<>());
			session.commit();
			assertEquals(0, again);
		}
		assertImported();

		// Imported reports never appear in the own feed, even on corroborated numbers.
		corroborate("+493012345681", 2);
		assertEquals(List.of(), export(null, null));
		assertEquals(List.of(), export(0L, NOW));
	}

	@Test
	void testRoundTrip() throws Exception {
		_db.createUser("alice", "Alice", "de", "+49");
		_db.addRating("alice", number("+493012345671"), "+49", Rating.G_FRAUD, "Fake bank hotline", "de", YESTERDAY + 500);
		corroborate("+493012345671", 1);

		service().runImport(_db, fixtureClient(FIXTURE, new ArrayList<>()), NOW);

		assertEquals(List.of("+493012345671"), phones(export(null, null)));
	}

	private void assertImported() throws Exception {
		assertEquals(1, votes("+493012345681"));
		assertEquals(1, ratingCount("+493012345681", "ADVERTISING"));
		assertEquals(List.of("nize|Robocall, bank offer|E_ADVERTISING|aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa01"),
			comments("+493012345681"));

		assertEquals(1, votes("+493012345682"));
		assertEquals(List.of(), comments("+493012345682"));

		assertEquals(0, votes("+493012345683"));
		assertEquals(List.of(), comments("+493012345683"));
		assertEquals(0, votes("+493012345684"));
		assertEquals(List.of(), comments("+493012345684"));
		assertEquals(0, votes("+493012345685"));

		assertEquals(1, votes("+493012345686"));
		assertEquals(1, ratingCount("+493012345686", "FRAUD"));
		assertEquals(List.of(), comments("+493012345686"), "Blank text, no comment row.");

		try (SqlSession session = _db.openSession()) {
			ExternalReports ledger = session.getMapper(ExternalReports.class);
			assertEquals(3, ledger.countReports(NizeImportService.SOURCE));
			assertTrue(ledger.hasReport(NizeImportService.SOURCE, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa02"));
			assertFalse(ledger.hasReport(NizeImportService.SOURCE, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa03"));

			// Never a personal entry.
			try (Statement statement = session.getConnection().createStatement();
					ResultSet result = statement.executeQuery("select count(1) from PERSONALIZATION")) {
				result.next();
				assertEquals(0, result.getInt(1));
			}
		}
	}

	@Test
	void testImportRespectsWhitelist() throws Exception {
		String phoneId = NumberAnalyzer.getPhoneId(number("+493012345681"));
		try (SqlSession session = _db.openSession()) {
			try (PreparedStatement statement = session.getConnection().prepareStatement("insert into WHITELIST (PHONE) values (?)")) {
				statement.setString(1, phoneId);
				statement.executeUpdate();
			}
			session.commit();
		}

		assertEquals(2, service().runImport(_db, fixtureClient(FIXTURE, new ArrayList<>()), NOW));
		assertEquals(0, votes("+493012345681"));
		assertEquals(List.of(), comments("+493012345681"));
	}

	@Test
	void testImportCatchUp() throws Exception {
		setNextDay(LocalDate.of(2026, 11, 1));

		List<long[]> requests = new ArrayList<>();
		service().runImport(_db, fixtureClient("{\"comments\": []}", requests), NOW);

		// At most 10 days back, up to yesterday.
		assertEquals(10, requests.size());
		assertEquals(TODAY - 10 * DAY, requests.get(0)[0]);
		assertEquals(YESTERDAY, requests.get(9)[0]);
		assertEquals("2026-11-20", nextDay());
	}

	@Test
	void testImportErrorStops() throws Exception {
		setNextDay(LocalDate.of(2026, 11, 17));

		List<long[]> requests = new ArrayList<>();
		PartnerFeedClient client = (from, to) -> {
			requests.add(new long[] {from, to});
			if (from == TODAY - 2 * DAY) {
				return PartnerReport.parseFeed(new StringReader(
					"{\"comments\": [{\"id\": \"x\", \"phone\": \"+493012345681\", \"created\": 1, \"score\": 1}], \"error\": {\"code\": 500}}"));
			}
			return PartnerReport.parseFeed(new StringReader("{\"comments\": []}"));
		};
		assertEquals(0, service().runImport(_db, client, NOW));

		// 17th imported, 18th failed and stays for the next run.
		assertEquals(2, requests.size());
		assertEquals("2026-11-18", nextDay());
		assertEquals(0, votes("+493012345681"));

		PartnerFeedClient failing = (from, to) -> {
			throw new IOException("HTTP 500");
		};
		assertEquals(0, service().runImport(_db, failing, NOW));
		assertEquals("2026-11-18", nextDay());
	}

	/**
	 * The partner capability is never part of a normally created token, the operator grants it by
	 * a DB update.
	 */
	@Test
	void testPartnerTokenCapability() throws Exception {
		_db.createUser("partner", "Partner", "de", "+49");
		de.haumacher.phoneblock.db.settings.AuthToken token = _db.createAPIToken("partner", NOW, "test", "Nize");
		assertFalse(token.isAccessPartner());
		assertFalse(_db.checkAuthToken(token.getToken(), NOW, "test", false).getAuthorization().isAccessPartner());

		try (Connection connection = _dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement("update TOKENS set ACCESS_PARTNER = true where ID = ?")) {
			statement.setLong(1, token.getId());
			assertEquals(1, statement.executeUpdate());
		}

		assertTrue(_db.checkAuthToken(token.getToken(), NOW, "test", false).getAuthorization().isAccessPartner());
	}

	/**
	 * Runs the real {@code db-migration-54.sql} on a schema without its objects.
	 */
	@Test
	void testMigration54() throws Exception {
		try (Connection connection = _dataSource.getConnection(); Statement statement = connection.createStatement()) {
			statement.execute("DROP TABLE EXTERNAL_REPORTS");
			statement.execute("DROP INDEX PERSONALIZATION_CREATED_IDX");
			statement.execute("ALTER TABLE TOKENS DROP COLUMN ACCESS_PARTNER");
		}

		try (Connection connection = _dataSource.getConnection();
				java.io.InputStream in = SpamReports.class.getResourceAsStream("db-migration-54.sql")) {
			assertNotNull(in, "db-migration-54.sql must be on the classpath");
			org.apache.ibatis.jdbc.ScriptRunner sr = new org.apache.ibatis.jdbc.ScriptRunner(connection);
			sr.setAutoCommit(true);
			sr.setStopOnError(true);
			sr.setLogWriter(null);
			sr.runScript(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
		}

		try (Connection connection = _dataSource.getConnection(); Statement statement = connection.createStatement()) {
			try (ResultSet result = statement.executeQuery("select count(1) from TOKENS where ACCESS_PARTNER")) {
				result.next();
				assertEquals(0, result.getInt(1));
			}
		}
		try (SqlSession session = _db.openSession()) {
			ExternalReports ledger = session.getMapper(ExternalReports.class);
			ledger.addReport("test", "1", "030123456789", null, 1, Rating.B_MISSED);
			assertTrue(ledger.hasReport("test", "1"));
		}
	}

	// ---- helpers ----

	private static NizeImportService service() {
		return new NizeImportService(null, null, IndexUpdateService.NONE);
	}

	private static PartnerFeedClient fixtureClient(String json, List<long[]> requests) {
		return (from, to) -> {
			requests.add(new long[] {from, to});
			return PartnerReport.parseFeed(new StringReader(json));
		};
	}

	private static PhoneNumer number(String phone) {
		PhoneNumer result = NumberAnalyzer.parsePhoneNumber(phone, "+49");
		assertNotNull(result, phone);
		return result;
	}

	private void addBlocklistEntry(String login, String phone, long created) {
		PhoneNumer number = number(phone);
		try (SqlSession session = _db.openSession()) {
			long userId = session.getMapper(Users.class).getUserId(login);
			session.getMapper(BlockList.class).addPersonalization(userId, NumberAnalyzer.getPhoneId(number),
				NumberAnalyzer.getPhoneHash(number), created);
			session.commit();
		}
	}

	/**
	 * Adds community votes from another (non-user) source, e.g. an FTC-style import, so that the
	 * number passes the corroboration filter {@link PartnerExport#MIN_VOTES}.
	 */
	private void corroborate(String phone, int votes) {
		try (SqlSession session = _db.openSession()) {
			_db.processVotesAndPublish(session.getMapper(SpamReports.class), number(phone), "+49", votes, YESTERDAY - DAY);
			session.commit();
		}
	}

	private void interceptCall(String login, String phone, long time) {
		PhoneNumer number = number(phone);
		try (SqlSession session = _db.openSession()) {
			long userId = session.getMapper(Users.class).getUserId(login);
			_db.recordCall(session.getMapper(SpamReports.class), session.getMapper(BlockList.class), userId, number,
				NumberAnalyzer.getPhoneId(number), "+49", time);
			session.commit();
		}
	}


	private List<PartnerReport> export(Long from, Long to) {
		try (SqlSession session = _db.openSession()) {
			return PartnerExport.export(session.getMapper(ExternalReports.class), SECRET, PartnerExport.window(from, to, NOW));
		}
	}

	private static List<String> phones(List<PartnerReport> feed) {
		return feed.stream().map(PartnerReport::phone).collect(Collectors.toList());
	}

	private static String toJson(List<PartnerReport> feed) {
		StringWriter out = new StringWriter();
		try (JsonWriter json = new JsonWriter(new WriterAdapter(out))) {
			PartnerReport.writeFeed(json, feed);
		} catch (IOException ex) {
			throw new AssertionError(ex);
		}
		return out.toString();
	}

	private int votes(String phone) {
		try (SqlSession session = _db.openSession()) {
			Integer votes = session.getMapper(SpamReports.class).getVotes(NumberAnalyzer.getPhoneId(number(phone)));
			return votes == null ? 0 : votes.intValue();
		}
	}

	private int ratingCount(String phone, String column) throws Exception {
		try (SqlSession session = _db.openSession();
				PreparedStatement statement = session.getConnection().prepareStatement("select " + column + " from NUMBERS where PHONE = ?")) {
			statement.setString(1, NumberAnalyzer.getPhoneId(number(phone)));
			try (ResultSet result = statement.executeQuery()) {
				return result.next() ? result.getInt(1) : 0;
			}
		}
	}

	private List<String> comments(String phone) throws Exception {
		List<String> result = new ArrayList<>();
		try (SqlSession session = _db.openSession();
				PreparedStatement statement = session.getConnection().prepareStatement(
					"select SERVICE, COMMENT, RATING, ID, USERID from COMMENTS where PHONE = ? order by ID")) {
			statement.setString(1, NumberAnalyzer.getPhoneId(number(phone)));
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					assertNull(rows.getObject(5), "Imported comments have no user.");
					result.add(rows.getString(1) + "|" + rows.getString(2) + "|" + rows.getString(3) + "|" + rows.getString(4));
				}
			}
		}
		return result;
	}

	private void setNextDay(LocalDate day) {
		try (SqlSession session = _db.openSession()) {
			session.getMapper(Users.class).addProperty(NizeImportService.PROPERTY_NEXT_DAY, day.toString());
			session.commit();
		}
	}

	private String nextDay() {
		try (SqlSession session = _db.openSession()) {
			return session.getMapper(Users.class).getProperty(NizeImportService.PROPERTY_NEXT_DAY);
		}
	}
}
