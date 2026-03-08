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
import java.util.Map;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.opencsv.CSVReaderHeaderAware;

import de.haumacher.phoneblock.app.api.model.PhoneInfo;
import de.haumacher.phoneblock.app.api.model.Rating;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.FtcReports;
import de.haumacher.phoneblock.db.TestDB;
import de.haumacher.phoneblock.scheduler.SchedulerService;
import de.haumacher.phoneblock.shared.PhoneHash;

/**
 * Integration test for {@link DB#getFtcPhoneInfo(SqlSession, byte[])}.
 *
 * <p>
 * Verifies that FTC complaint data imported via {@link FtcImportService} is
 * correctly returned through the hash-based lookup path, with proper vote
 * aggregation and rating determination from FTC subject mapping.
 * </p>
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

		// Import the test CSV data into FTC tables.
		MessageDigest digest = PhoneHash.createPhoneDigest();
		Map<String, Integer> subjectCache = new HashMap<>();

		try (SqlSession session = _db.openSession()) {
			FtcReports reports = session.getMapper(FtcReports.class);

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
					String robocall = row.get("Recorded_Message_Or_Robocall");

					if (phoneNumber == null || createdDate == null) {
						continue;
					}

					service.processRow(session, reports, subjectCache, digest,
						phoneNumber.strip(), createdDate.strip(), subject, robocall);
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
	 * Test that getFtcPhoneInfo returns correct votes and rating for a number
	 * with known FTC complaints (two complaints for "Vacation & timeshares").
	 */
	@Test
	void testHashLookupReturnsVotesAndRating() {
		byte[] hash = PhoneHash.getPhoneHash(PhoneHash.createPhoneDigest(), "+18886749072");

		try (SqlSession session = _db.openSession()) {
			PhoneInfo info = _db.getFtcPhoneInfo(session, hash);

			assertNotNull(info, "FTC data should exist for +18886749072");
			assertEquals("+18886749072", info.getPhone());
			assertEquals(2, info.getVotes(), "Two FTC complaints for this number");
			assertEquals(Rating.E_ADVERTISING, info.getRating(),
				"'Vacation  & timeshares' maps to E_ADVERTISING");
		}
	}

	/**
	 * Test that getFtcPhoneInfo returns correct data for a number with a
	 * fraud-related subject ("Reducing your debt...").
	 */
	@Test
	void testHashLookupFraudRating() {
		byte[] hash = PhoneHash.getPhoneHash(PhoneHash.createPhoneDigest(), "+18335883781");

		try (SqlSession session = _db.openSession()) {
			PhoneInfo info = _db.getFtcPhoneInfo(session, hash);

			assertNotNull(info, "FTC data should exist for +18335883781");
			assertEquals("+18335883781", info.getPhone());
			assertEquals(1, info.getVotes());
			assertEquals(Rating.G_FRAUD, info.getRating(),
				"'Reducing your debt...' maps to G_FRAUD");
		}
	}

	/**
	 * Test that getFtcPhoneInfo returns correct data for a number with a
	 * ping-call subject ("Dropped call or no message").
	 */
	@Test
	void testHashLookupPingRating() {
		byte[] hash = PhoneHash.getPhoneHash(PhoneHash.createPhoneDigest(), "+13023092191");

		try (SqlSession session = _db.openSession()) {
			PhoneInfo info = _db.getFtcPhoneInfo(session, hash);

			assertNotNull(info, "FTC data should exist for +13023092191");
			assertEquals("+13023092191", info.getPhone());
			assertEquals(1, info.getVotes());
			assertEquals(Rating.C_PING, info.getRating(),
				"'Dropped call or no message' maps to C_PING");
		}
	}

	/**
	 * Test that a number with no FTC data returns null.
	 */
	@Test
	void testHashLookupUnknownNumberReturnsNull() {
		byte[] hash = PhoneHash.getPhoneHash(PhoneHash.createPhoneDigest(), "+15555555555");

		try (SqlSession session = _db.openSession()) {
			PhoneInfo info = _db.getFtcPhoneInfo(session, hash);

			assertNull(info, "Unknown number should return null");
		}
	}

	/**
	 * Test that a number with only NULL-rated subjects (like "Other") still has
	 * votes but gets the default rating (A_LEGITIMATE) since no rated subjects
	 * are found.
	 *
	 * <p>
	 * +16266631604 has 1 FTC complaint with subject "Other", which has a NULL
	 * rating in FTC_SUBJECTS. The getFtcPhoneInfo method should still return the
	 * number with its vote count, but without a meaningful spam rating because
	 * getFtcRatingsByPhone filters out NULL-rated subjects.
	 * </p>
	 */
	@Test
	void testHashLookupNullRatedSubjectDefaultRating() {
		byte[] hash = PhoneHash.getPhoneHash(PhoneHash.createPhoneDigest(), "+16266631604");

		try (SqlSession session = _db.openSession()) {
			PhoneInfo info = _db.getFtcPhoneInfo(session, hash);

			assertNotNull(info, "FTC data should exist for +16266631604");
			assertEquals("+16266631604", info.getPhone());
			assertEquals(1, info.getVotes(), "One FTC complaint for this number");
			// "Other" has NULL rating, so getFtcRatingsByPhone returns empty list,
			// and the rating stays at the default A_LEGITIMATE.
			assertEquals(Rating.A_LEGITIMATE, info.getRating(),
				"No rated subjects means default A_LEGITIMATE rating");
		}
	}
}
