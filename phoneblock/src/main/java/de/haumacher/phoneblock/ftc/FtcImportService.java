/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.ftc;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opencsv.CSVReaderHeaderAware;

import de.haumacher.phoneblock.analysis.NumberAnalyzer;
import de.haumacher.phoneblock.app.api.model.PhoneNumer;
import de.haumacher.phoneblock.app.api.model.Rating;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.FtcReports;
import de.haumacher.phoneblock.db.SpamReports;
import de.haumacher.phoneblock.db.Users;
import de.haumacher.phoneblock.scheduler.SchedulerService;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

/**
 * Service that imports FTC (Federal Trade Commission) Do-Not-Call complaint data
 * from daily CSV files published at ftc.gov.
 *
 * <p>
 * The service runs daily at a configurable time and downloads complaint data for
 * each day since the last successful import. Phone numbers are normalized to E.164
 * format (prepending +1 for US numbers) and fed directly into the main NUMBERS table
 * as regular spam reports. Provenance is tracked in the FTC_REPORTS and FTC_SUBJECTS tables.
 * </p>
 *
 * <p>Configuration via JNDI or system properties:</p>
 * <ul>
 * <li><code>ftc/enabled</code> - Whether the import is active (default: false)</li>
 * <li><code>ftc/schedule/hour</code> - Hour of day to run (0-23, default: 6)</li>
 * <li><code>ftc/schedule/minute</code> - Minute of hour to run (0-59, default: 0)</li>
 * </ul>
 */
public class FtcImportService implements ServletContextListener {

	private static final Logger LOG = LoggerFactory.getLogger(FtcImportService.class);

	/**
	 * URL pattern for daily FTC DNC complaint CSV files.
	 * The date placeholder is formatted as YYYY-MM-DD.
	 */
	private static final String FTC_CSV_URL_PATTERN =
		"https://www.ftc.gov/sites/default/files/DNC_Complaint_Numbers_%s.csv";

	/** Property key in the PROPERTIES table for tracking the last imported date. */
	private static final String PROPERTY_LAST_DATE = "ftc.sync.last_date";

	/** Number of days to bootstrap when no previous import exists (~5 weeks). */
	private static final int DEFAULT_BOOTSTRAP_DAYS = 35;

	/** Date-time format used in FTC CSV Created_Date column: "2026-03-05 00:03:04". */
	private static final DateTimeFormatter CREATED_DATE_FORMAT =
		DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	/** User-Agent header for HTTP requests (FTC blocks requests without a browser-like UA). */
	private static final String USER_AGENT = "Mozilla/5.0";

	/** Number of rows after which the SQL session is committed. */
	private static final int COMMIT_INTERVAL = 1000;

	/** Dial prefix for US phone numbers. */
	private static final String US_DIAL_PREFIX = "+1";

	private final SchedulerService _schedulerService;
	private final DBService _dbService;

	private boolean _enabled = false;
	private int _scheduleHour = 6;
	private int _scheduleMinute = 0;
	private ScheduledFuture<?> _task;

	/**
	 * Cached subject information (label to ID + rating).
	 */
	record SubjectInfo(int id, Rating rating) {}

	/**
	 * Creates a {@link FtcImportService}.
	 */
	public FtcImportService(SchedulerService scheduler, DBService dbService) {
		_schedulerService = scheduler;
		_dbService = dbService;
	}

	@Override
	public void contextInitialized(ServletContextEvent sce) {
		loadConfig();
		if (!_enabled) {
			LOG.info("FTC import disabled.");
			return;
		}

		LOG.info("Starting FTC import service: schedule={}:{}", _scheduleHour, String.format("%02d", _scheduleMinute));

		// Calculate delay until next scheduled time (same pattern as BlocklistVersionService).
		Calendar firstRun = Calendar.getInstance();
		firstRun.set(Calendar.HOUR_OF_DAY, _scheduleHour);
		firstRun.set(Calendar.MINUTE, _scheduleMinute);
		firstRun.set(Calendar.SECOND, 0);
		firstRun.set(Calendar.MILLISECOND, 0);

		Calendar inOneHour = Calendar.getInstance();
		inOneHour.add(Calendar.HOUR, 1);

		if (firstRun.before(inOneHour)) {
			firstRun.add(Calendar.DAY_OF_MONTH, 1);
		}

		long initialDelay = firstRun.getTimeInMillis() - System.currentTimeMillis();

		_task = _schedulerService.scheduler().scheduleAtFixedRate(
			this::runImport,
			initialDelay,
			24 * 60 * 60 * 1000L,
			TimeUnit.MILLISECONDS
		);
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		if (_task != null) {
			_task.cancel(false);
		}
	}

	/**
	 * Main import loop called by the scheduler.
	 *
	 * <p>
	 * Reads the last imported date from the PROPERTIES table. If no previous import
	 * exists, bootstraps from today minus {@link #DEFAULT_BOOTSTRAP_DAYS}. Then loops
	 * over each day from last+1 through yesterday, downloading and importing CSV data
	 * for each day.
	 * </p>
	 */
	void runImport() {
		LOG.info("Starting FTC import.");

		DB db = _dbService.db();
		try (SqlSession session = db.openSession()) {
			Users users = session.getMapper(Users.class);
			FtcReports ftcReports = session.getMapper(FtcReports.class);
			SpamReports spamReports = session.getMapper(SpamReports.class);

			// Determine start date.
			String lastDateStr = users.getProperty(PROPERTY_LAST_DATE);
			LocalDate startDate;
			if (lastDateStr != null) {
				startDate = LocalDate.parse(lastDateStr, DateTimeFormatter.ISO_LOCAL_DATE).plusDays(1);
			} else {
				startDate = LocalDate.now().minusDays(DEFAULT_BOOTSTRAP_DAYS);
			}

			LocalDate yesterday = LocalDate.now().minusDays(1);

			int totalImported = 0;
			for (LocalDate date = startDate; !date.isAfter(yesterday); date = date.plusDays(1)) {
				try {
					int count = importDay(db, session, ftcReports, spamReports, date);
					totalImported += count;

					// Update the last imported date in PROPERTIES.
					String dateValue = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
					if (lastDateStr != null) {
						users.updateProperty(PROPERTY_LAST_DATE, dateValue);
					} else {
						users.addProperty(PROPERTY_LAST_DATE, dateValue);
						lastDateStr = dateValue; // Switch to update mode for subsequent days.
					}

					session.commit();

					if (count > 0) {
						LOG.info("Imported {} FTC records for {}.", count, date);
					}
				} catch (IOException ex) {
					LOG.warn("Failed to download FTC CSV for {}: {}", date, ex.getMessage());
				}
			}

			LOG.info("FTC import completed. Total records imported: {}", totalImported);
		} catch (Exception ex) {
			LOG.error("FTC import failed.", ex);
		}
	}

	/**
	 * Downloads and parses one day's CSV file. Returns the number of rows imported.
	 *
	 * @param db the database instance.
	 * @param session the active SQL session.
	 * @param ftcReports the FtcReports mapper.
	 * @param spamReports the SpamReports mapper.
	 * @param date the date to import.
	 * @return number of rows imported.
	 * @throws IOException if the download fails (but not for HTTP 404, which returns 0).
	 */
	int importDay(DB db, SqlSession session, FtcReports ftcReports, SpamReports spamReports, LocalDate date) throws IOException {
		String url = String.format(FTC_CSV_URL_PATTERN, date.format(DateTimeFormatter.ISO_LOCAL_DATE));

		HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
		connection.setRequestProperty("User-Agent", USER_AGENT);
		connection.setConnectTimeout(30_000);
		connection.setReadTimeout(60_000);

		int responseCode = connection.getResponseCode();
		if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
			// No data for this day (weekend/holiday) - skip silently.
			LOG.debug("No FTC CSV for {} (404).", date);
			return 0;
		}
		if (responseCode != HttpURLConnection.HTTP_OK) {
			throw new IOException("HTTP " + responseCode + " for " + url);
		}

		Map<String, SubjectInfo> subjectCache = new HashMap<>();

		int count = 0;
		try (InputStream in = connection.getInputStream()) {
			CSVReaderHeaderAware reader = new CSVReaderHeaderAware(
				new InputStreamReader(in, StandardCharsets.UTF_8));

			Map<String, String> row;
			while ((row = reader.readMap()) != null) {
				String phoneNumber = row.get("Company_Phone_Number");
				String createdDate = row.get("Created_Date");
				String subject = row.get("Subject");

				if (phoneNumber == null || createdDate == null) {
					continue;
				}

				processRow(db, session, ftcReports, spamReports, subjectCache,
					phoneNumber.strip(), createdDate.strip(), subject);
				count++;

				if (count % COMMIT_INTERVAL == 0) {
					session.commit();
				}
			}
		} catch (Exception ex) {
			throw new IOException("Failed to parse FTC CSV for " + date + ": " + ex.getMessage(), ex);
		}

		return count;
	}

	/**
	 * Processes one CSV row: normalizes the phone number, feeds votes into the
	 * main NUMBERS table, resolves the complaint subject for rating updates,
	 * and upserts provenance data into FTC_REPORTS.
	 */
	void processRow(DB db, SqlSession session, FtcReports ftcReports, SpamReports spamReports,
					Map<String, SubjectInfo> subjectCache,
					String phoneNumber, String createdDate, String subject) {
		// Validate: FTC CSV has bare 10-digit US numbers like "8886749072".
		if (phoneNumber.length() != 10 || !phoneNumber.chars().allMatch(Character::isDigit)) {
			return;
		}

		// Normalize to E.164: prepend "+1".
		String phone = US_DIAL_PREFIX + phoneNumber;

		// Parse Created_Date to unix millis.
		long createdMillis;
		try {
			LocalDateTime dateTime = LocalDateTime.parse(createdDate, CREATED_DATE_FORMAT);
			createdMillis = dateTime.toInstant(ZoneOffset.UTC).toEpochMilli();
		} catch (Exception ex) {
			LOG.debug("Skipping row with unparseable date: {}", createdDate);
			return;
		}

		// Parse phone number for processVotesAndPublish.
		PhoneNumer number = NumberAnalyzer.parsePhoneNumber(phone, US_DIAL_PREFIX);
		if (number == null) {
			LOG.debug("Skipping unparseable phone number: {}", phone);
			return;
		}

		String phoneId = NumberAnalyzer.getPhoneId(number);

		// Add 1 vote to the main NUMBERS table.
		db.processVotesAndPublish(spamReports, number, US_DIAL_PREFIX, 1, createdMillis);

		// Resolve subject and update rating if applicable.
		if (subject != null && !subject.isBlank()) {
			subject = subject.strip();
			SubjectInfo subjectInfo = resolveSubject(ftcReports, subjectCache, subject);

			// Update rating column in NUMBERS if the subject has a non-null, non-B_MISSED rating.
			if (subjectInfo.rating() != null && subjectInfo.rating() != Rating.B_MISSED) {
				spamReports.updateRating(phoneId, subjectInfo.rating(), 1, createdMillis);
			}

			// Upsert FTC_REPORTS for provenance tracking.
			int updated = ftcReports.updateFtcReport(phone, subjectInfo.id(), 1);
			if (updated == 0) {
				ftcReports.insertFtcReport(phone, subjectInfo.id(), 1);
			}
		}
	}

	/**
	 * Resolves a subject label to its database ID and associated rating, using an in-memory cache.
	 * If the subject is not in the cache or database, it is inserted (with null rating).
	 */
	private SubjectInfo resolveSubject(FtcReports reports, Map<String, SubjectInfo> cache, String label) {
		SubjectInfo cached = cache.get(label);
		if (cached != null) {
			return cached;
		}

		Integer dbId = reports.getSubjectId(label);
		if (dbId == null) {
			reports.insertSubject(label);
			dbId = reports.getSubjectId(label);
		}

		String ratingStr = reports.getSubjectRating(dbId);
		Rating rating = ratingStr != null ? Rating.valueOfProtocol(ratingStr) : null;

		SubjectInfo info = new SubjectInfo(dbId, rating);
		cache.put(label, info);
		return info;
	}

	/**
	 * Loads configuration from JNDI or system properties.
	 */
	private void loadConfig() {
		try {
			InitialContext initCtx = new InitialContext();
			Context envCtx = (Context) initCtx.lookup("java:comp/env");

			try {
				Object value = envCtx.lookup("ftc/enabled");
				_enabled = Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
			} catch (NamingException ex) {
				String value = System.getProperty("ftc.enabled");
				if (value != null) {
					_enabled = Boolean.parseBoolean(value);
				}
			}

			try {
				_scheduleHour = ((Number) envCtx.lookup("ftc/schedule/hour")).intValue();
			} catch (NamingException ex) {
				String value = System.getProperty("ftc.schedule.hour");
				if (value != null) {
					_scheduleHour = Integer.parseInt(value);
				}
			}

			try {
				_scheduleMinute = ((Number) envCtx.lookup("ftc/schedule/minute")).intValue();
			} catch (NamingException ex) {
				String value = System.getProperty("ftc.schedule.minute");
				if (value != null) {
					_scheduleMinute = Integer.parseInt(value);
				}
			}
		} catch (NamingException ex) {
			LOG.info("Not using JNDI configuration for FTC import: {}", ex.getMessage());
		}

		// Validate.
		if (_scheduleHour < 0 || _scheduleHour > 23) {
			LOG.warn("Invalid FTC schedule hour {}, using default 6", _scheduleHour);
			_scheduleHour = 6;
		}
		if (_scheduleMinute < 0 || _scheduleMinute > 59) {
			LOG.warn("Invalid FTC schedule minute {}, using default 0", _scheduleMinute);
			_scheduleMinute = 0;
		}
	}
}
