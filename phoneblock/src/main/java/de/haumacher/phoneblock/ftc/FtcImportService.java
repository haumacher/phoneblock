/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.ftc;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.analysis.NumberAnalyzer;
import de.haumacher.phoneblock.app.api.model.PhoneNumer;
import de.haumacher.phoneblock.app.api.model.Rating;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.FtcReports;
import de.haumacher.phoneblock.db.SpamReports;
import de.haumacher.phoneblock.db.Users;
import de.haumacher.phoneblock.ftc.FtcApiClient.Complaint;
import de.haumacher.phoneblock.ftc.FtcApiClient.RateLimitException;
import de.haumacher.phoneblock.scheduler.SchedulerService;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

/**
 * Service that imports FTC (Federal Trade Commission) Do-Not-Call complaint data
 * from the FTC's complaints API (see {@link FtcApiClient}).
 *
 * <p>
 * The service runs daily at a configurable time and fetches the complaints created since the last
 * import, one hour of complaints at a time. The FTC uploads complaints in batches about a day
 * late, so an hour is only imported once the API holds a complaint created after it. Phone
 * numbers are normalized to E.164 format (prepending +1 for US numbers) and fed directly into the
 * main NUMBERS table as regular spam reports. Provenance is tracked in the FTC_REPORTS and
 * FTC_SUBJECTS tables.
 * </p>
 *
 * <p>Configuration via JNDI or system properties:</p>
 * <ul>
 * <li><code>ftc/enabled</code> - Whether the import is active (default: false)</li>
 * <li><code>ftc/api-key</code> - The api.data.gov key for the FTC API (required)</li>
 * <li><code>ftc/schedule/hour</code> - Hour of day to run (0-23, default: 6)</li>
 * <li><code>ftc/schedule/minute</code> - Minute of hour to run (0-59, default: 0)</li>
 * </ul>
 */
public class FtcImportService implements ServletContextListener {

	private static final Logger LOG = LoggerFactory.getLogger(FtcImportService.class);

	/**
	 * Property key in the PROPERTIES table holding the start of the first hour not yet imported
	 * (ISO local date-time, UTC).
	 */
	static final String PROPERTY_NEXT_HOUR = "ftc.sync.next_hour";

	/** Number of days to bootstrap when no previous import exists (~5 weeks). */
	private static final int DEFAULT_BOOTSTRAP_DAYS = 35;

	/** Delay before resuming an import that hit the API's rate limit. */
	private static final long RATE_LIMIT_RETRY_MINUTES = 60;

	/** Dial prefix for US phone numbers. */
	private static final String US_DIAL_PREFIX = "+1";

	private final SchedulerService _schedulerService;
	private final DBService _dbService;

	private boolean _enabled = false;
	private String _apiKey;
	private int _scheduleHour = 6;
	private int _scheduleMinute = 0;
	private FtcApiClient _client;
	private ScheduledFuture<?> _task;
	private ScheduledFuture<?> _retry;
	private final AtomicBoolean _running = new AtomicBoolean();

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
		if (_apiKey == null || _apiKey.isBlank()) {
			LOG.error("FTC import enabled but no API key configured (ftc/api-key), import disabled.");
			return;
		}
		_client = new FtcApiClient(_apiKey.strip());

		LOG.info("Starting FTC import service: schedule={}:{}", _scheduleHour, String.format("%02d", _scheduleMinute));

		// Calculate delay until next scheduled time (same pattern as BlocklistVersionService).
		Calendar firstRun = Calendar.getInstance();
		firstRun.set(Calendar.HOUR_OF_DAY, _scheduleHour);
		firstRun.set(Calendar.MINUTE, _scheduleMinute);
		firstRun.set(Calendar.SECOND, 0);
		firstRun.set(Calendar.MILLISECOND, 0);

		// Run next day, if the schedule for today has already passed.
		Calendar inOneMinute = Calendar.getInstance();
		inOneMinute.add(Calendar.MINUTE, 1);
		if (firstRun.before(inOneMinute)) {
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
		synchronized (this) {
			if (_retry != null) {
				_retry.cancel(false);
			}
		}
	}

	/**
	 * Entry point called by the scheduler.
	 */
	void runImport() {
		if (!_running.compareAndSet(false, true)) {
			LOG.info("FTC import already running.");
			return;
		}
		try {
			runImport(_dbService.db(), _client);
		} catch (RateLimitException ex) {
			LOG.warn("FTC API rate limit reached, resuming import in {} minutes.", RATE_LIMIT_RETRY_MINUTES);
			scheduleRetry();
		} catch (Exception ex) {
			LOG.error("FTC import failed.", ex);
		} finally {
			_running.set(false);
		}
	}

	private synchronized void scheduleRetry() {
		if (_retry != null && !_retry.isDone()) {
			return;
		}
		_retry = _schedulerService.scheduler().schedule((Runnable) this::runImport, RATE_LIMIT_RETRY_MINUTES, TimeUnit.MINUTES);
	}

	/**
	 * Imports all complete hours since the last import.
	 *
	 * <p>
	 * Reads the first hour not yet imported from the PROPERTIES table. If no previous import
	 * exists, bootstraps from today minus {@link #DEFAULT_BOOTSTRAP_DAYS}. Then imports each hour
	 * before the hour of the newest complaint available, committing the hour's votes together with
	 * the advanced position. An import interrupted by an error resumes with the interrupted hour.
	 * </p>
	 *
	 * @return number of complaints imported.
	 */
	int runImport(DB db, FtcApiClient client) throws IOException {
		LOG.info("Starting FTC import.");

		try (SqlSession session = db.openSession()) {
			Users users = session.getMapper(Users.class);
			FtcReports ftcReports = session.getMapper(FtcReports.class);
			SpamReports spamReports = session.getMapper(SpamReports.class);

			String nextHourStr = users.getProperty(PROPERTY_NEXT_HOUR);
			LocalDateTime hour = nextHourStr != null
				? LocalDateTime.parse(nextHourStr)
				: LocalDate.now(ZoneOffset.UTC).minusDays(DEFAULT_BOOTSTRAP_DAYS).atStartOfDay();

			LocalDateTime newest = client.fetchNewestCreatedDate(LocalDateTime.now(ZoneOffset.UTC));
			if (newest == null) {
				LOG.warn("FTC API returned no complaints.");
				return 0;
			}
			LocalDateTime end = newest.truncatedTo(ChronoUnit.HOURS);

			Map<String, SubjectInfo> subjectCache = new HashMap<>();
			int total = 0;
			try {
				for (; hour.isBefore(end); hour = hour.plusHours(1)) {
					total += importHour(db, session, client, ftcReports, spamReports, subjectCache, hour);

					String position = hour.plusHours(1).toString();
					if (nextHourStr != null) {
						users.updateProperty(PROPERTY_NEXT_HOUR, position);
					} else {
						users.addProperty(PROPERTY_NEXT_HOUR, position);
						nextHourStr = position;
					}
					session.commit();
				}
			} finally {
				LOG.info("FTC import: {} complaints imported, next hour to import: {}, complaints available until: {}.",
					total, hour, newest);
			}
			return total;
		}
	}

	/**
	 * Fetches and processes all complaints created in the given hour.
	 *
	 * @return number of complaints processed.
	 */
	int importHour(DB db, SqlSession session, FtcApiClient client, FtcReports ftcReports, SpamReports spamReports,
			Map<String, SubjectInfo> subjectCache, LocalDateTime hour) throws IOException {
		LocalDateTime last = hour.plusHours(1).minusSeconds(1);

		// Pages may overlap, if complaints with the same creation date are ordered differently in
		// subsequent requests.
		Set<String> seen = new HashSet<>();
		int count = 0;
		for (int offset = 0; ; offset += FtcApiClient.PAGE_SIZE) {
			List<Complaint> page = client.fetchPage(hour, last, offset);
			for (Complaint complaint : page) {
				if (complaint.id() != null && !seen.add(complaint.id())) {
					continue;
				}
				if (complaint.phoneNumber() == null || complaint.createdDate() == null) {
					continue;
				}

				processRow(db, session, ftcReports, spamReports, subjectCache,
					complaint.phoneNumber().strip(), complaint.createdDate().strip(), complaint.subject());
				count++;
			}
			if (page.size() < FtcApiClient.PAGE_SIZE) {
				break;
			}
		}
		LOG.debug("Imported {} FTC complaints for {}.", count, hour);
		return count;
	}

	/**
	 * Processes one complaint: normalizes the phone number, feeds votes into the
	 * main NUMBERS table, resolves the complaint subject for rating updates,
	 * and upserts provenance data into FTC_REPORTS.
	 */
	void processRow(DB db, SqlSession session, FtcReports ftcReports, SpamReports spamReports,
					Map<String, SubjectInfo> subjectCache,
					String phoneNumber, String createdDate, String subject) {
		// Validate: FTC data has bare 10-digit US numbers like "8886749072".
		if (phoneNumber.length() != 10 || !phoneNumber.chars().allMatch(Character::isDigit)) {
			return;
		}

		// Normalize to E.164: prepend "+1".
		String phone = US_DIAL_PREFIX + phoneNumber;

		// Parse Created_Date to unix millis.
		long createdMillis;
		try {
			LocalDateTime dateTime = LocalDateTime.parse(createdDate, FtcApiClient.DATE_TIME_FORMAT);
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
				_apiKey = String.valueOf(envCtx.lookup("ftc/api-key"));
			} catch (NamingException ex) {
				_apiKey = System.getProperty("ftc.api-key");
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
			_enabled = Boolean.parseBoolean(System.getProperty("ftc.enabled"));
			_apiKey = System.getProperty("ftc.api-key");
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
