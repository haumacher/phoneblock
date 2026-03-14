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
import de.haumacher.phoneblock.app.api.model.PhoneInfo;
import de.haumacher.phoneblock.app.api.model.PhoneNumer;
import de.haumacher.phoneblock.app.api.model.Rating;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.FtcReports;
import de.haumacher.phoneblock.db.SpamReports;
import de.haumacher.phoneblock.db.TestDB;
import de.haumacher.phoneblock.ftc.FtcImportService.SubjectInfo;
import de.haumacher.phoneblock.scheduler.SchedulerService;

/**
 * Integration test verifying that FTC complaint data imported via
 * {@link FtcImportService} is accessible through the standard
 * {@link DB#getPhoneApiInfo(String)} lookup path.
 */
class TestFtcSpamCheck {

	private DB _db;
	private SchedulerService _scheduler;
	private DataSource _dataSource;

	@BeforeEach
	void setUp() throws Exception {
		_scheduler = new SchedulerService();
		_scheduler.contextInitialized(null);

		_dataSource = TestDB.createTestDataSource();
		_db = new DB(_dataSource, _scheduler);

		// Import the test CSV data.
		Map<String, SubjectInfo> subjectCache = new HashMap<>();

		try (SqlSession session = _db.openSession()) {
			FtcReports ftcReports = session.getMapper(FtcReports.class);
			SpamReports spamReports = session.getMapper(SpamReports.class);

			try (InputStream in = getClass().getResourceAsStream("test-ftc-data.csv")) {
				assertNotNull(in, "Test CSV fixture must exist");

				CSVReaderHeaderAware reader = new CSVReaderHeaderAware(
					new InputStreamReader(in, StandardCharsets.UTF_8));

				FtcImportService service = new FtcImportService(null, null);

				Map<String, String> row;
				while ((row = reader.readMap()) != null) {
					String phoneNumber = row.get("Company_Phone_Number");
					String createdDate = row.get("Created_Date");
					String subject = row.get("Subject");

					if (phoneNumber == null || createdDate == null) {
						continue;
					}

					service.processRow(_db, session, ftcReports, spamReports, subjectCache,
						phoneNumber.strip(), createdDate.strip(), subject);
				}
			}

			session.commit();
		}
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
	 */
	private static String toPhoneId(String tenDigit) {
		PhoneNumer number = NumberAnalyzer.parsePhoneNumber("+1" + tenDigit, "+1");
		assertNotNull(number, "Phone number should parse: +1" + tenDigit);
		return NumberAnalyzer.getPhoneId(number);
	}

	/**
	 * Test that getPhoneApiInfo returns correct votes and rating for a number
	 * with FTC complaints (two complaints for "Vacation & timeshares").
	 */
	@Test
	void testLookupReturnsVotesAndRating() {
		PhoneInfo info = _db.getPhoneApiInfo(toPhoneId("8886749072"));

		assertNotNull(info, "Phone info should exist");
		assertEquals(2, info.getVotes(), "Two FTC complaints for this number");
		assertEquals(Rating.E_ADVERTISING, info.getRating(),
			"'Vacation  & timeshares' maps to E_ADVERTISING");
	}

	/**
	 * Test that getPhoneApiInfo returns correct data for a number with a
	 * fraud-related subject ("Reducing your debt...").
	 */
	@Test
	void testLookupFraudRating() {
		PhoneInfo info = _db.getPhoneApiInfo(toPhoneId("8335883781"));

		assertNotNull(info, "Phone info should exist");
		assertEquals(1, info.getVotes());
		assertEquals(Rating.G_FRAUD, info.getRating(),
			"'Reducing your debt...' maps to G_FRAUD");
	}

	/**
	 * Test that getPhoneApiInfo returns correct data for a number with a
	 * ping-call subject ("Dropped call or no message").
	 */
	@Test
	void testLookupPingRating() {
		PhoneInfo info = _db.getPhoneApiInfo(toPhoneId("3023092191"));

		assertNotNull(info, "Phone info should exist");
		assertEquals(1, info.getVotes());
		assertEquals(Rating.C_PING, info.getRating(),
			"'Dropped call or no message' maps to C_PING");
	}

	/**
	 * Test that an unknown number returns a legitimate rating (default).
	 */
	@Test
	void testLookupUnknownNumber() {
		PhoneInfo info = _db.getPhoneApiInfo(toPhoneId("5555555555"));

		assertNotNull(info, "getPhoneApiInfo always returns a result");
		assertEquals(0, info.getVotes(), "Unknown number should have 0 votes");
		assertEquals(Rating.A_LEGITIMATE, info.getRating());
	}

	/**
	 * Test that a number with only NULL-rated subjects (like "Other") has
	 * votes but defaults to B_MISSED rating (since votes > 0 but no specific
	 * rating category dominates).
	 */
	@Test
	void testLookupNullRatedSubject() {
		PhoneInfo info = _db.getPhoneApiInfo(toPhoneId("6266631604"));

		assertNotNull(info, "Phone info should exist");
		assertEquals(1, info.getVotes(), "One FTC complaint for this number");
		// With votes > 0 but no rating category, the dominant rating is B_MISSED.
		assertEquals(Rating.B_MISSED, info.getRating());
	}
}
