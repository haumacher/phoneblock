/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.ftc;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.opencsv.CSVReaderHeaderAware;

import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBFtcNumberInfo;
import de.haumacher.phoneblock.db.DBFtcRatingInfo;
import de.haumacher.phoneblock.db.FtcReports;
import de.haumacher.phoneblock.db.TestDB;
import de.haumacher.phoneblock.scheduler.SchedulerService;
import de.haumacher.phoneblock.shared.PhoneHash;

/**
 * Test case for {@link FtcImportService}.
 *
 * <p>
 * Tests CSV parsing, phone normalization (+1 prefix), SHA-1 computation,
 * vote aggregation across duplicate numbers, subject auto-discovery,
 * and FTC_REPORTS breakdown by subject.
 * </p>
 */
class TestFtcImportService {

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

	/**
	 * Test that all rows from the test CSV are processed and that phone numbers
	 * are correctly normalized, hashed, aggregated, and broken down by subject.
	 */
	@Test
	void testProcessCsvRows() throws Exception {
		MessageDigest digest = PhoneHash.createPhoneDigest();
		Map<String, Integer> subjectCache = new HashMap<>();

		try (SqlSession session = _db.openSession()) {
			FtcReports reports = session.getMapper(FtcReports.class);

			// Read the test CSV and process each row through the service.
			try (InputStream in = getClass().getResourceAsStream("test-ftc-data.csv")) {
				assertNotNull(in, "Test CSV fixture must exist");

				CSVReaderHeaderAware reader = new CSVReaderHeaderAware(
					new InputStreamReader(in, StandardCharsets.UTF_8));

				// Create the service with null dependencies (we only call processRow).
				FtcImportService service = new FtcImportService(null, null);

				Map<String, String> row;
				int count = 0;
				while ((row = reader.readMap()) != null) {
					String phoneNumber = row.get("Company_Phone_Number");
					String createdDate = row.get("Created_Date");
					String subject = row.get("Subject");
					String robocall = row.get("Recorded_Message_Or_Robocall");

					if (phoneNumber == null || createdDate == null) {
						continue;
					}

					service.processRow(session, reports, subjectCache, digest,
						phoneNumber.strip(), createdDate.strip(), subject, robocall);
					count++;
				}

				assertEquals(5, count, "All 5 CSV rows should be processed");
			}

			session.commit();

			// --- Verify FTC_NUMBERS ---

			// 8886749072 appears twice -> votes=2, robocalls=1 (first Y, second N).
			DBFtcNumberInfo num8886 = reports.getFtcNumber("+18886749072");
			assertNotNull(num8886, "Number +18886749072 should exist");
			assertEquals(2, num8886.getVotes(), "8886749072 appears twice");
			assertEquals(1, num8886.getRobocalls(), "8886749072 has 1 robocall (Y) and 1 non-robocall (N)");
			assertTrue(num8886.getFirstReported() > 0, "First reported should be set");
			assertTrue(num8886.getLastReported() >= num8886.getFirstReported(), "Last reported >= first reported");

			// 6266631604 appears once -> votes=1, robocalls=1.
			DBFtcNumberInfo num6266 = reports.getFtcNumber("+16266631604");
			assertNotNull(num6266, "Number +16266631604 should exist");
			assertEquals(1, num6266.getVotes());
			assertEquals(1, num6266.getRobocalls());

			// 8335883781 appears once -> votes=1, robocalls=1.
			DBFtcNumberInfo num8335 = reports.getFtcNumber("+18335883781");
			assertNotNull(num8335, "Number +18335883781 should exist");
			assertEquals(1, num8335.getVotes());
			assertEquals(1, num8335.getRobocalls());

			// 3023092191 appears once -> votes=1, robocalls=0 (N).
			DBFtcNumberInfo num3023 = reports.getFtcNumber("+13023092191");
			assertNotNull(num3023, "Number +13023092191 should exist");
			assertEquals(1, num3023.getVotes());
			assertEquals(0, num3023.getRobocalls(), "3023092191 is not a robocall");

			// --- Verify SHA-1 hash lookup ---

			byte[] expectedHash = PhoneHash.getPhoneHash(PhoneHash.createPhoneDigest(), "+18886749072");
			DBFtcNumberInfo byHash = reports.getFtcNumberByHash(expectedHash);
			assertNotNull(byHash, "Should find number by SHA-1 hash");
			assertEquals("+18886749072", byHash.getPhone());
			assertEquals(2, byHash.getVotes());

			// --- Verify FTC_SUBJECTS ---

			// "Vacation  & timeshares" is a seeded subject -> should have a rating.
			Integer vacationId = reports.getSubjectId("Vacation  & timeshares");
			assertNotNull(vacationId, "Seeded subject 'Vacation  & timeshares' should exist");

			// "Dropped call or no message" is also seeded.
			Integer droppedId = reports.getSubjectId("Dropped call or no message");
			assertNotNull(droppedId, "Seeded subject 'Dropped call or no message' should exist");

			// "Other" is also seeded.
			Integer otherId = reports.getSubjectId("Other");
			assertNotNull(otherId, "Seeded subject 'Other' should exist");

			// "Reducing your debt..." is also seeded.
			Integer debtId = reports.getSubjectId("Reducing your debt (credit cards, mortgage, student loans)");
			assertNotNull(debtId, "Seeded subject 'Reducing your debt...' should exist");

			// --- Verify FTC_REPORTS (subject breakdown) ---

			// +18886749072 has 2 reports both for "Vacation  & timeshares".
			List<DBFtcRatingInfo> ratings8886 = reports.getFtcRatingsByPhone("+18886749072");
			assertFalse(ratings8886.isEmpty(), "Should have rating breakdown for +18886749072");
			// "Vacation  & timeshares" maps to E_ADVERTISING.
			boolean foundAdvertising = false;
			for (DBFtcRatingInfo ri : ratings8886) {
				if ("E_ADVERTISING".equals(ri.getRating())) {
					assertEquals(2, ri.getVotes(), "2 votes for Vacation & timeshares -> E_ADVERTISING");
					foundAdvertising = true;
				}
			}
			assertTrue(foundAdvertising, "E_ADVERTISING rating should be present for +18886749072");

			// +18335883781 has 1 report for "Reducing your debt..." which maps to G_FRAUD.
			List<DBFtcRatingInfo> ratings8335 = reports.getFtcRatingsByPhone("+18335883781");
			assertFalse(ratings8335.isEmpty(), "Should have rating breakdown for +18335883781");
			assertEquals("G_FRAUD", ratings8335.get(0).getRating());
			assertEquals(1, ratings8335.get(0).getVotes());

			// +13023092191 has 1 report for "Dropped call or no message" which maps to C_PING.
			List<DBFtcRatingInfo> ratings3023 = reports.getFtcRatingsByPhone("+13023092191");
			assertFalse(ratings3023.isEmpty(), "Should have rating breakdown for +13023092191");
			assertEquals("C_PING", ratings3023.get(0).getRating());
			assertEquals(1, ratings3023.get(0).getVotes());

			// +16266631604 has 1 report for "Other" which has NULL rating, so getFtcRatingsByPhone
			// filters it out (WHERE s.RATING IS NOT NULL).
			List<DBFtcRatingInfo> ratings6266 = reports.getFtcRatingsByPhone("+16266631604");
			assertTrue(ratings6266.isEmpty(),
				"'Other' has NULL rating so no rated breakdown for +16266631604");
		}
	}

	/**
	 * Test that phone numbers not matching the expected 10-digit US format are skipped.
	 */
	@Test
	void testInvalidPhoneNumbersSkipped() throws Exception {
		MessageDigest digest = PhoneHash.createPhoneDigest();
		Map<String, Integer> subjectCache = new HashMap<>();

		try (SqlSession session = _db.openSession()) {
			FtcReports reports = session.getMapper(FtcReports.class);

			FtcImportService service = new FtcImportService(null, null);

			// Too short.
			service.processRow(session, reports, subjectCache, digest,
				"12345", "2026-03-05 00:00:00", "Other", "N");

			// Too long.
			service.processRow(session, reports, subjectCache, digest,
				"12345678901", "2026-03-05 00:00:00", "Other", "N");

			// Contains letters.
			service.processRow(session, reports, subjectCache, digest,
				"888ABC1234", "2026-03-05 00:00:00", "Other", "N");

			session.commit();

			// None of these should have been inserted.
			assertNull(reports.getFtcNumber("+112345"));
			assertNull(reports.getFtcNumber("+112345678901"));
			assertNull(reports.getFtcNumber("+1888ABC1234"));
		}
	}

	/**
	 * Test that unparseable dates cause the row to be skipped.
	 */
	@Test
	void testInvalidDateSkipped() throws Exception {
		MessageDigest digest = PhoneHash.createPhoneDigest();
		Map<String, Integer> subjectCache = new HashMap<>();

		try (SqlSession session = _db.openSession()) {
			FtcReports reports = session.getMapper(FtcReports.class);

			FtcImportService service = new FtcImportService(null, null);

			// Invalid date format.
			service.processRow(session, reports, subjectCache, digest,
				"5551234567", "not-a-date", "Other", "N");

			session.commit();

			assertNull(reports.getFtcNumber("+15551234567"),
				"Row with invalid date should be skipped");
		}
	}

	/**
	 * Test that a new subject not in the seed data is auto-created.
	 */
	@Test
	void testNewSubjectAutoCreated() throws Exception {
		MessageDigest digest = PhoneHash.createPhoneDigest();
		Map<String, Integer> subjectCache = new HashMap<>();

		try (SqlSession session = _db.openSession()) {
			FtcReports reports = session.getMapper(FtcReports.class);

			FtcImportService service = new FtcImportService(null, null);

			// Use a subject that is NOT in the seed data.
			service.processRow(session, reports, subjectCache, digest,
				"5559876543", "2026-03-05 12:00:00", "Brand New Subject Category", "Y");

			session.commit();

			// The number should exist.
			DBFtcNumberInfo num = reports.getFtcNumber("+15559876543");
			assertNotNull(num);
			assertEquals(1, num.getVotes());

			// The new subject should have been auto-created.
			Integer subjectId = reports.getSubjectId("Brand New Subject Category");
			assertNotNull(subjectId, "New subject should be auto-created");

			// The subject should be in the cache too.
			assertTrue(subjectCache.containsKey("Brand New Subject Category"));

			// Since auto-created subjects have no RATING, getFtcRatingsByPhone returns empty.
			List<DBFtcRatingInfo> ratings = reports.getFtcRatingsByPhone("+15559876543");
			assertTrue(ratings.isEmpty(),
				"Auto-created subject has NULL rating, so no rating breakdown");
		}
	}

	/**
	 * Test that the subject cache prevents redundant DB lookups.
	 */
	@Test
	void testSubjectCacheWorks() throws Exception {
		MessageDigest digest = PhoneHash.createPhoneDigest();
		Map<String, Integer> subjectCache = new HashMap<>();

		try (SqlSession session = _db.openSession()) {
			FtcReports reports = session.getMapper(FtcReports.class);

			FtcImportService service = new FtcImportService(null, null);

			// Process two rows with the same seeded subject.
			service.processRow(session, reports, subjectCache, digest,
				"5551111111", "2026-03-05 12:00:00", "Vacation  & timeshares", "Y");
			service.processRow(session, reports, subjectCache, digest,
				"5552222222", "2026-03-05 12:00:00", "Vacation  & timeshares", "N");

			session.commit();

			// Both numbers should exist.
			assertNotNull(reports.getFtcNumber("+15551111111"));
			assertNotNull(reports.getFtcNumber("+15552222222"));

			// Subject should be cached after first lookup.
			assertTrue(subjectCache.containsKey("Vacation  & timeshares"),
				"Subject should be in cache after processing");
		}
	}

	/**
	 * Test that rows with blank/null subject are handled (number is inserted but no report).
	 */
	@Test
	void testBlankSubjectHandled() throws Exception {
		MessageDigest digest = PhoneHash.createPhoneDigest();
		Map<String, Integer> subjectCache = new HashMap<>();

		try (SqlSession session = _db.openSession()) {
			FtcReports reports = session.getMapper(FtcReports.class);

			FtcImportService service = new FtcImportService(null, null);

			// Null subject.
			service.processRow(session, reports, subjectCache, digest,
				"5553333333", "2026-03-05 12:00:00", null, "Y");

			// Blank subject.
			service.processRow(session, reports, subjectCache, digest,
				"5554444444", "2026-03-05 12:00:00", "   ", "N");

			session.commit();

			// Numbers should still be inserted.
			assertNotNull(reports.getFtcNumber("+15553333333"));
			assertNotNull(reports.getFtcNumber("+15554444444"));

			// No rating breakdown since no subject was linked.
			assertTrue(reports.getFtcRatingsByPhone("+15553333333").isEmpty());
			assertTrue(reports.getFtcRatingsByPhone("+15554444444").isEmpty());
		}
	}

	/**
	 * Test timestamp range tracking: first_reported is the minimum and last_reported is the maximum
	 * across multiple rows for the same number.
	 */
	@Test
	void testTimestampRange() throws Exception {
		MessageDigest digest = PhoneHash.createPhoneDigest();
		Map<String, Integer> subjectCache = new HashMap<>();

		try (SqlSession session = _db.openSession()) {
			FtcReports reports = session.getMapper(FtcReports.class);

			FtcImportService service = new FtcImportService(null, null);

			// First row: 2026-03-05 00:03:04 UTC
			service.processRow(session, reports, subjectCache, digest,
				"8886749072", "2026-03-05 00:03:04", "Vacation  & timeshares", "Y");

			// Second row: 2026-03-05 00:10:00 UTC (later)
			service.processRow(session, reports, subjectCache, digest,
				"8886749072", "2026-03-05 00:10:00", "Vacation  & timeshares", "N");

			session.commit();

			DBFtcNumberInfo num = reports.getFtcNumber("+18886749072");
			assertNotNull(num);

			// first_reported should be the earlier timestamp.
			// last_reported should be the later timestamp.
			assertTrue(num.getFirstReported() < num.getLastReported(),
				"First reported should be earlier than last reported");
		}
	}
}
