/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.ftc;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.haumacher.phoneblock.analysis.NumberAnalyzer;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.SpamReports;
import de.haumacher.phoneblock.db.TestDB;
import de.haumacher.phoneblock.db.Users;
import de.haumacher.phoneblock.ftc.FtcApiClient.Complaint;
import de.haumacher.phoneblock.ftc.FtcApiClient.RateLimitException;
import de.haumacher.phoneblock.scheduler.SchedulerService;

/**
 * Test case for importing FTC complaints through {@link FtcApiClient}: response parsing, the
 * hour-by-hour import loop, paging, and resuming after the rate limit.
 */
class TestFtcApiImport {

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

	@Test
	void testParseResponse() throws Exception {
		try (InputStream in = getClass().getResourceAsStream("test-ftc-api-page.json")) {
			assertNotNull(in, "Test JSON fixture must exist");

			List<Complaint> complaints = FtcApiClient.parse(new InputStreamReader(in, StandardCharsets.UTF_8));

			assertEquals(List.of(
				new Complaint("a1", "8885550101", "2026-10-28 14:57:27", "Vacation  & timeshares"),
				new Complaint("a2", "8885550102", "2026-10-28 14:57:26", null)),
				complaints);
		}
	}

	@Test
	void testGarbageCreatedDateIgnored() {
		assertNull(FtcApiClient.parseCreatedDate(new Complaint("x", "8885550101", "20261235", null)));
		assertNull(FtcApiClient.parseCreatedDate(new Complaint("x", "8885550101", null, null)));
		assertEquals(LocalDateTime.of(2026, 10, 29, 15, 14, 55),
			FtcApiClient.parseCreatedDate(new Complaint("x", "8885550101", "2026-10-29 15:14:55", null)));
	}

	@Test
	void testImportsCompleteHoursAndPages() throws Exception {
		LocalDateTime start = LocalDateTime.of(2026, 10, 27, 10, 0);
		setNextHour(start);

		FakeClient client = new FakeClient(LocalDateTime.of(2026, 10, 27, 12, 30));

		// Hour 10: more than one page, with an overlap between the pages.
		for (int n = 0; n < FtcApiClient.PAGE_SIZE + 5; n++) {
			client.add("h10-" + n, "88855501" + String.format("%02d", n % 10), "2026-10-27 10:15:00");
		}
		client._duplicateFirstOfSecondPage = true;

		// Hour 11: one complaint.
		client.add("h11", "8885550200", "2026-10-27 11:59:59");

		// Hour 12: not yet complete, must not be imported.
		client.add("h12", "8885550300", "2026-10-27 12:10:00");

		int count = new FtcImportService(null, null).runImport(_db, client);
		assertEquals(FtcApiClient.PAGE_SIZE + 5 + 1, count);

		assertEquals(LocalDateTime.of(2026, 10, 27, 12, 0).toString(), getNextHour());

		// 55 complaints spread over 10 numbers.
		assertEquals(6, votes("8885550100"));
		assertEquals(5, votes("8885550109"));
		assertEquals(1, votes("8885550200"));
		assertNull(votes("8885550300"));

		// The next run continues with hour 12.
		client._newest = LocalDateTime.of(2026, 10, 28, 0, 0);
		new FtcImportService(null, null).runImport(_db, client);
		assertEquals(1, votes("8885550300"));
		assertEquals(LocalDateTime.of(2026, 10, 28, 0, 0).toString(), getNextHour());
	}

	@Test
	void testRateLimitKeepsCompletedHours() throws Exception {
		setNextHour(LocalDateTime.of(2026, 10, 27, 10, 0));

		FakeClient client = new FakeClient(LocalDateTime.of(2026, 10, 27, 13, 0));
		client.add("h10", "8885550100", "2026-10-27 10:15:00");
		client.add("h11", "8885550200", "2026-10-27 11:15:00");
		client.add("h12", "8885550300", "2026-10-27 12:15:00");
		client._rateLimitAt = LocalDateTime.of(2026, 10, 27, 11, 0);

		assertThrows(RateLimitException.class, () -> new FtcImportService(null, null).runImport(_db, client));

		assertEquals(1, votes("8885550100"));
		assertNull(votes("8885550200"));
		assertEquals(LocalDateTime.of(2026, 10, 27, 11, 0).toString(), getNextHour());

		// Resuming imports each remaining complaint exactly once.
		client._rateLimitAt = null;
		new FtcImportService(null, null).runImport(_db, client);
		assertEquals(1, votes("8885550100"));
		assertEquals(1, votes("8885550200"));
		assertEquals(1, votes("8885550300"));
		assertEquals(LocalDateTime.of(2026, 10, 27, 13, 0).toString(), getNextHour());
	}

	private void setNextHour(LocalDateTime hour) {
		try (SqlSession session = _db.openSession()) {
			session.getMapper(Users.class).addProperty(FtcImportService.PROPERTY_NEXT_HOUR, hour.toString());
			session.commit();
		}
	}

	private String getNextHour() {
		try (SqlSession session = _db.openSession()) {
			return session.getMapper(Users.class).getProperty(FtcImportService.PROPERTY_NEXT_HOUR);
		}
	}

	private Integer votes(String tenDigit) {
		try (SqlSession session = _db.openSession()) {
			String phoneId = NumberAnalyzer.getPhoneId(NumberAnalyzer.parsePhoneNumber("+1" + tenDigit, "+1"));
			return session.getMapper(SpamReports.class).getVotes(phoneId);
		}
	}

	/**
	 * In-memory replacement for the FTC API.
	 */
	static class FakeClient extends FtcApiClient {

		final List<Complaint> _complaints = new ArrayList<>();

		LocalDateTime _newest;

		LocalDateTime _rateLimitAt;

		boolean _duplicateFirstOfSecondPage;

		FakeClient(LocalDateTime newest) {
			super("test");
			_newest = newest;
		}

		void add(String id, String phone, String created) {
			_complaints.add(new Complaint(id, phone, created, "Other"));
		}

		@Override
		public LocalDateTime fetchNewestCreatedDate(LocalDateTime now) {
			return _newest;
		}

		@Override
		public List<Complaint> fetchPage(LocalDateTime from, LocalDateTime to, int offset) throws IOException {
			if (from.equals(_rateLimitAt)) {
				throw new RateLimitException("test");
			}

			List<Complaint> matches = new ArrayList<>();
			for (Complaint complaint : _complaints) {
				LocalDateTime created = LocalDateTime.parse(complaint.createdDate(), DATE_TIME_FORMAT);
				if (!created.isBefore(from) && !created.isAfter(to)) {
					matches.add(complaint);
				}
			}

			int first = offset;
			if (_duplicateFirstOfSecondPage && offset > 0) {
				// Simulate unstable ordering: the second page repeats the last entry of the first.
				first--;
			}
			int last = Math.min(matches.size(), offset + PAGE_SIZE);
			return first >= matches.size() ? List.of() : new ArrayList<>(matches.subList(first, last));
		}
	}
}
