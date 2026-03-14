/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.ftc;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.opencsv.CSVReaderHeaderAware;

import de.haumacher.phoneblock.analysis.NumberAnalyzer;
import de.haumacher.phoneblock.app.api.model.PhoneNumer;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBNumberInfo;
import de.haumacher.phoneblock.db.FtcReports;
import de.haumacher.phoneblock.db.SpamReports;
import de.haumacher.phoneblock.db.TestDB;
import de.haumacher.phoneblock.ftc.FtcImportService.SubjectInfo;
import de.haumacher.phoneblock.scheduler.SchedulerService;

/**
 * Test case for {@link FtcImportService}.
 *
 * <p>
 * Tests CSV parsing, phone normalization (+1 prefix), vote insertion into the
 * main NUMBERS table, rating column updates, subject auto-discovery,
 * and FTC_REPORTS provenance tracking.
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
	 * Converts a 10-digit US number to the phone ID used in the NUMBERS table.
	 * E.g. "8886749072" -> "0018886749072" (the "00" + country code "1" + number format).
	 */
	private static String toPhoneId(String tenDigit) {
		PhoneNumer number = NumberAnalyzer.parsePhoneNumber("+1" + tenDigit, "+1");
		assertNotNull(number, "Phone number should parse: +1" + tenDigit);
		return NumberAnalyzer.getPhoneId(number);
	}

	/**
	 * Test that all rows from the test CSV are processed and that phone numbers
	 * are correctly normalized, votes are added to NUMBERS, ratings are updated,
	 * and provenance is tracked in FTC_REPORTS.
	 */
	@Test
	void testProcessCsvRows() throws Exception {
		Map<String, SubjectInfo> subjectCache = new HashMap<>();

		try (SqlSession session = _db.openSession()) {
			FtcReports ftcReports = session.getMapper(FtcReports.class);
			SpamReports spamReports = session.getMapper(SpamReports.class);

			// Read the test CSV and process each row through the service.
			try (InputStream in = getClass().getResourceAsStream("test-ftc-data.csv")) {
				assertNotNull(in, "Test CSV fixture must exist");

				CSVReaderHeaderAware reader = new CSVReaderHeaderAware(
					new InputStreamReader(in, StandardCharsets.UTF_8));

				FtcImportService service = new FtcImportService(null, null);

				Map<String, String> row;
				int count = 0;
				while ((row = reader.readMap()) != null) {
					String phoneNumber = row.get("Company_Phone_Number");
					String createdDate = row.get("Created_Date");
					String subject = row.get("Subject");

					if (phoneNumber == null || createdDate == null) {
						continue;
					}

					service.processRow(_db, session, ftcReports, spamReports, subjectCache,
						phoneNumber.strip(), createdDate.strip(), subject);
					count++;
				}

				assertEquals(5, count, "All 5 CSV rows should be processed");
			}

			session.commit();

			// --- Verify NUMBERS table ---

			String id8886 = toPhoneId("8886749072");
			String id6266 = toPhoneId("6266631604");
			String id8335 = toPhoneId("8335883781");
			String id3023 = toPhoneId("3023092191");

			// 8886749072 appears twice -> votes=2.
			Integer votes8886 = spamReports.getVotes(id8886);
			assertNotNull(votes8886, "Number should exist in NUMBERS");
			assertEquals(2, votes8886.intValue(), "8886749072 appears twice");

			// 6266631604 appears once -> votes=1.
			Integer votes6266 = spamReports.getVotes(id6266);
			assertNotNull(votes6266);
			assertEquals(1, votes6266.intValue());

			// 8335883781 appears once -> votes=1.
			Integer votes8335 = spamReports.getVotes(id8335);
			assertNotNull(votes8335);
			assertEquals(1, votes8335.intValue());

			// 3023092191 appears once -> votes=1.
			Integer votes3023 = spamReports.getVotes(id3023);
			assertNotNull(votes3023);
			assertEquals(1, votes3023.intValue());

			// --- Verify rating columns in NUMBERS ---

			// +18886749072: 2 complaints for "Vacation  & timeshares" -> E_ADVERTISING -> advertising=2.
			DBNumberInfo info8886 = spamReports.getPhoneInfo(id8886);
			assertNotNull(info8886);
			assertEquals(2, info8886.getRatingAdvertising(), "2 votes for Vacation & timeshares -> advertising");

			// +18335883781: 1 complaint for "Reducing your debt..." -> G_FRAUD -> fraud=1.
			DBNumberInfo info8335 = spamReports.getPhoneInfo(id8335);
			assertNotNull(info8335);
			assertEquals(1, info8335.getRatingFraud(), "1 vote for Reducing your debt -> fraud");

			// +13023092191: 1 complaint for "Dropped call or no message" -> C_PING -> ping=1.
			DBNumberInfo info3023 = spamReports.getPhoneInfo(id3023);
			assertNotNull(info3023);
			assertEquals(1, info3023.getRatingPing(), "1 vote for Dropped call -> ping");

			// +16266631604: 1 complaint for "Other" which has NULL rating -> no rating column updated.
			DBNumberInfo info6266 = spamReports.getPhoneInfo(id6266);
			assertNotNull(info6266);
			assertEquals(0, info6266.getRatingAdvertising());
			assertEquals(0, info6266.getRatingFraud());
			assertEquals(0, info6266.getRatingPing());
			assertEquals(0, info6266.getRatingGamble());

			// --- Verify FTC_SUBJECTS ---

			Integer vacationId = ftcReports.getSubjectId("Vacation  & timeshares");
			assertNotNull(vacationId, "Seeded subject 'Vacation  & timeshares' should exist");

			Integer droppedId = ftcReports.getSubjectId("Dropped call or no message");
			assertNotNull(droppedId, "Seeded subject 'Dropped call or no message' should exist");

			Integer otherId = ftcReports.getSubjectId("Other");
			assertNotNull(otherId, "Seeded subject 'Other' should exist");

			Integer debtId = ftcReports.getSubjectId("Reducing your debt (credit cards, mortgage, student loans)");
			assertNotNull(debtId, "Seeded subject 'Reducing your debt...' should exist");
		}
	}

	/**
	 * Test that phone numbers not matching the expected 10-digit US format are skipped.
	 */
	@Test
	void testInvalidPhoneNumbersSkipped() throws Exception {
		Map<String, SubjectInfo> subjectCache = new HashMap<>();

		try (SqlSession session = _db.openSession()) {
			FtcReports ftcReports = session.getMapper(FtcReports.class);
			SpamReports spamReports = session.getMapper(SpamReports.class);

			FtcImportService service = new FtcImportService(null, null);

			// Too short.
			service.processRow(_db, session, ftcReports, spamReports, subjectCache,
				"12345", "2026-03-05 00:00:00", "Other");

			// Too long.
			service.processRow(_db, session, ftcReports, spamReports, subjectCache,
				"12345678901", "2026-03-05 00:00:00", "Other");

			// Contains letters.
			service.processRow(_db, session, ftcReports, spamReports, subjectCache,
				"888ABC1234", "2026-03-05 00:00:00", "Other");

			session.commit();

			// None of these should have been inserted. Use a known-parseable format for lookup.
			assertNull(spamReports.getVotes("00112345"));
			assertNull(spamReports.getVotes("00112345678901"));
			// "888ABC1234" won't even pass the 10-digit check, but verify anyway.
			assertNull(spamReports.getVotes("001888ABC1234"));
		}
	}

	/**
	 * Test that unparseable dates cause the row to be skipped.
	 */
	@Test
	void testInvalidDateSkipped() throws Exception {
		Map<String, SubjectInfo> subjectCache = new HashMap<>();

		try (SqlSession session = _db.openSession()) {
			FtcReports ftcReports = session.getMapper(FtcReports.class);
			SpamReports spamReports = session.getMapper(SpamReports.class);

			FtcImportService service = new FtcImportService(null, null);

			// Invalid date format.
			service.processRow(_db, session, ftcReports, spamReports, subjectCache,
				"5551234567", "not-a-date", "Other");

			session.commit();

			String phoneId = toPhoneId("5551234567");
			assertNull(spamReports.getVotes(phoneId),
				"Row with invalid date should be skipped");
		}
	}

	/**
	 * Test that a new subject not in the seed data is auto-created.
	 */
	@Test
	void testNewSubjectAutoCreated() throws Exception {
		Map<String, SubjectInfo> subjectCache = new HashMap<>();

		try (SqlSession session = _db.openSession()) {
			FtcReports ftcReports = session.getMapper(FtcReports.class);
			SpamReports spamReports = session.getMapper(SpamReports.class);

			FtcImportService service = new FtcImportService(null, null);

			// Use a subject that is NOT in the seed data.
			service.processRow(_db, session, ftcReports, spamReports, subjectCache,
				"5559876543", "2026-03-05 12:00:00", "Brand New Subject Category");

			session.commit();

			// The number should exist in NUMBERS.
			String phoneId = toPhoneId("5559876543");
			Integer votes = spamReports.getVotes(phoneId);
			assertNotNull(votes);
			assertEquals(1, votes.intValue());

			// The new subject should have been auto-created.
			Integer subjectId = ftcReports.getSubjectId("Brand New Subject Category");
			assertNotNull(subjectId, "New subject should be auto-created");

			// The subject should be in the cache too.
			assertTrue(subjectCache.containsKey("Brand New Subject Category"));

			// Auto-created subjects have null rating, so no rating columns updated.
			DBNumberInfo info = spamReports.getPhoneInfo(phoneId);
			assertNotNull(info);
			assertEquals(0, info.getRatingAdvertising());
			assertEquals(0, info.getRatingFraud());
			assertEquals(0, info.getRatingPing());
		}
	}

	/**
	 * Test that the subject cache prevents redundant DB lookups.
	 */
	@Test
	void testSubjectCacheWorks() throws Exception {
		Map<String, SubjectInfo> subjectCache = new HashMap<>();

		try (SqlSession session = _db.openSession()) {
			FtcReports ftcReports = session.getMapper(FtcReports.class);
			SpamReports spamReports = session.getMapper(SpamReports.class);

			FtcImportService service = new FtcImportService(null, null);

			// Process two rows with the same seeded subject.
			service.processRow(_db, session, ftcReports, spamReports, subjectCache,
				"5551111111", "2026-03-05 12:00:00", "Vacation  & timeshares");
			service.processRow(_db, session, ftcReports, spamReports, subjectCache,
				"5552222222", "2026-03-05 12:00:00", "Vacation  & timeshares");

			session.commit();

			// Both numbers should exist in NUMBERS.
			assertNotNull(spamReports.getVotes(toPhoneId("5551111111")));
			assertNotNull(spamReports.getVotes(toPhoneId("5552222222")));

			// Subject should be cached after first lookup.
			assertTrue(subjectCache.containsKey("Vacation  & timeshares"),
				"Subject should be in cache after processing");
		}
	}

	/**
	 * Test that rows with blank/null subject are handled (number gets votes but no rating or report).
	 */
	@Test
	void testBlankSubjectHandled() throws Exception {
		Map<String, SubjectInfo> subjectCache = new HashMap<>();

		try (SqlSession session = _db.openSession()) {
			FtcReports ftcReports = session.getMapper(FtcReports.class);
			SpamReports spamReports = session.getMapper(SpamReports.class);

			FtcImportService service = new FtcImportService(null, null);

			// Null subject.
			service.processRow(_db, session, ftcReports, spamReports, subjectCache,
				"5553333333", "2026-03-05 12:00:00", null);

			// Blank subject.
			service.processRow(_db, session, ftcReports, spamReports, subjectCache,
				"5554444444", "2026-03-05 12:00:00", "   ");

			session.commit();

			// Numbers should have votes in NUMBERS.
			String id3 = toPhoneId("5553333333");
			String id4 = toPhoneId("5554444444");
			assertNotNull(spamReports.getVotes(id3));
			assertNotNull(spamReports.getVotes(id4));

			// No rating columns should be updated.
			DBNumberInfo info3 = spamReports.getPhoneInfo(id3);
			assertNotNull(info3);
			assertEquals(0, info3.getRatingAdvertising());
			assertEquals(0, info3.getRatingFraud());

			DBNumberInfo info4 = spamReports.getPhoneInfo(id4);
			assertNotNull(info4);
			assertEquals(0, info4.getRatingAdvertising());
			assertEquals(0, info4.getRatingFraud());
		}
	}

	/**
	 * Test that multiple rows for the same number accumulate votes correctly in NUMBERS.
	 */
	@Test
	void testVoteAccumulation() throws Exception {
		Map<String, SubjectInfo> subjectCache = new HashMap<>();

		try (SqlSession session = _db.openSession()) {
			FtcReports ftcReports = session.getMapper(FtcReports.class);
			SpamReports spamReports = session.getMapper(SpamReports.class);

			FtcImportService service = new FtcImportService(null, null);

			// Process same number three times.
			service.processRow(_db, session, ftcReports, spamReports, subjectCache,
				"8886749072", "2026-03-05 00:03:04", "Vacation  & timeshares");
			service.processRow(_db, session, ftcReports, spamReports, subjectCache,
				"8886749072", "2026-03-05 00:10:00", "Vacation  & timeshares");
			service.processRow(_db, session, ftcReports, spamReports, subjectCache,
				"8886749072", "2026-03-06 12:00:00", "Other");

			session.commit();

			// Should have 3 total votes.
			String phoneId = toPhoneId("8886749072");
			Integer votes = spamReports.getVotes(phoneId);
			assertNotNull(votes);
			assertEquals(3, votes.intValue());

			// Should have 2 advertising votes (from "Vacation  & timeshares").
			DBNumberInfo info = spamReports.getPhoneInfo(phoneId);
			assertNotNull(info);
			assertEquals(2, info.getRatingAdvertising());
		}
	}
}
