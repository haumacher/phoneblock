/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.exchange;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
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
import de.haumacher.phoneblock.app.api.model.UserComment;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.ExternalReports;
import de.haumacher.phoneblock.db.SpamReports;
import de.haumacher.phoneblock.db.Users;
import de.haumacher.phoneblock.index.IndexUpdateService;
import de.haumacher.phoneblock.scheduler.SchedulerService;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

/**
 * Daily import of the spam reports of the partner service Nize (caller-ID app), see
 * <code>docs/plans/2026-11-23-partner-report-exchange-design.md</code>.
 *
 * <p>
 * The service fetches the partner's feed one full UTC day at a time, starting with the first day
 * not yet imported (property {@value #PROPERTY_NEXT_DAY}), at most {@value PartnerExport#MAX_DAYS}
 * days back (the partner's history limit), up to and including yesterday. A day is committed
 * together with the advanced position; a failed fetch stops the run and is retried with the next
 * run.
 * </p>
 *
 * <p>
 * Only reports classified as spam (see {@link #classify(Integer, Integer)}) are counted. A counted
 * report is recorded in the ledger {@code EXTERNAL_REPORTS} (deduplicated by its id) and counts as
 * one vote at its creation time through {@link DB#addRating(SpamReports, PhoneNumer, String, UserComment, boolean)},
 * which also creates a {@code COMMENTS} row (with {@code SERVICE = 'nize'} and no user), if the
 * report has a text. The import never writes {@code PERSONALIZATION}, so imported reports can
 * never appear in the own partner feed ({@link PartnerReportsServlet}). Reports that are not
 * counted are not stored at all.
 * </p>
 *
 * <p>Configuration via JNDI or system properties:</p>
 * <ul>
 * <li><code>nize/enabled</code> - Whether the import is active (default: false)</li>
 * <li><code>nize/url</code> - The feed URL (default: {@value #DEFAULT_URL})</li>
 * <li><code>nize/token</code> - The bearer token for the feed (required)</li>
 * <li><code>nize/schedule/hour</code> - Hour of day to run (0-23, default: 7)</li>
 * <li><code>nize/schedule/minute</code> - Minute of hour to run (0-59, default: 0)</li>
 * </ul>
 */
public class NizeImportService implements ServletContextListener {

	private static final Logger LOG = LoggerFactory.getLogger(NizeImportService.class);

	/** Source tag in {@code EXTERNAL_REPORTS.SOURCE} and {@code COMMENTS.SERVICE}. */
	public static final String SOURCE = "nize";

	/** The partner's feed URL. */
	public static final String DEFAULT_URL = "https://callerid.cubeapps.io/api/v1/external/comments";

	/**
	 * Property key in the PROPERTIES table holding the first UTC day not yet imported (ISO date).
	 */
	static final String PROPERTY_NEXT_DAY = "nize.sync.next_day";

	/** Partner category "Trustworthy number". */
	private static final int TYPE_TRUSTWORTHY = 2;

	/**
	 * Language of imported comments. The partner's comments carry no language; the locale is only
	 * used to select comments for display. The partner's international user base makes English the
	 * best guess.
	 */
	private static final String COMMENT_LANG = "en";

	/** Maximum length of the ledger's ID and SENDER columns. */
	private static final int MAX_ID_LENGTH = 64;

	private final SchedulerService _schedulerService;
	private final DBService _dbService;
	private final IndexUpdateService _indexer;

	private boolean _enabled = false;
	private String _url = DEFAULT_URL;
	private String _token;
	private int _scheduleHour = 7;
	private int _scheduleMinute = 0;
	private PartnerFeedClient _client;
	private ScheduledFuture<?> _task;
	private final AtomicBoolean _running = new AtomicBoolean();

	/**
	 * Creates a {@link NizeImportService}.
	 */
	public NizeImportService(SchedulerService scheduler, DBService dbService, IndexUpdateService indexer) {
		_schedulerService = scheduler;
		_dbService = dbService;
		_indexer = indexer;
	}

	@Override
	public void contextInitialized(ServletContextEvent sce) {
		loadConfig();
		if (!_enabled) {
			LOG.info("Nize import disabled.");
			return;
		}
		if (_token == null || _token.isBlank()) {
			LOG.error("Nize import enabled but no token configured (nize/token), import disabled.");
			return;
		}
		_client = PartnerFeedClient.http(_url.strip(), _token.strip());

		LOG.info("Starting Nize import service: url={}, schedule={}:{}", _url, _scheduleHour,
			String.format("%02d", _scheduleMinute));

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
	}

	/**
	 * Entry point called by the scheduler.
	 */
	void runImport() {
		if (!_running.compareAndSet(false, true)) {
			LOG.info("Nize import already running.");
			return;
		}
		try {
			runImport(_dbService.db(), _client, System.currentTimeMillis());
		} catch (Exception ex) {
			LOG.error("Nize import failed.", ex);
		} finally {
			_running.set(false);
		}
	}

	/**
	 * Imports all complete UTC days since the last import, at most {@value PartnerExport#MAX_DAYS}
	 * days back.
	 *
	 * @return The number of reports counted.
	 */
	int runImport(DB db, PartnerFeedClient client, long now) {
		LocalDate today = LocalDate.ofEpochDay(Math.floorDiv(now, PartnerExport.DAY_MILLIS));
		LocalDate oldest = today.minusDays(PartnerExport.MAX_DAYS);

		int total = 0;
		try (SqlSession session = db.openSession()) {
			Users users = session.getMapper(Users.class);

			String nextDayStr = users.getProperty(PROPERTY_NEXT_DAY);
			LocalDate day = nextDayStr != null ? LocalDate.parse(nextDayStr) : today.minusDays(1);
			if (day.isBefore(oldest)) {
				LOG.warn("Nize import: days {} to {} are no longer available and are skipped.", day, oldest.minusDays(1));
				day = oldest;
			}

			for (; day.isBefore(today); day = day.plusDays(1)) {
				long from = day.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
				long to = from + PartnerExport.DAY_MILLIS;

				PartnerReport.Feed feed;
				try {
					feed = client.fetch(from, to);
				} catch (IOException ex) {
					LOG.warn("Nize import: fetching {} failed, retrying with the next run: {}", day, ex.getMessage());
					break;
				}
				if (feed.error()) {
					LOG.warn("Nize import: feed for {} reported an error, retrying with the next run.", day);
					break;
				}

				List<PhoneNumer> becameSpam = new ArrayList<>();
				int counted = importReports(db, session, feed.reports(), becameSpam);
				total += counted;

				String position = day.plusDays(1).toString();
				if (nextDayStr != null) {
					users.updateProperty(PROPERTY_NEXT_DAY, position);
				} else {
					users.addProperty(PROPERTY_NEXT_DAY, position);
					nextDayStr = position;
				}
				session.commit();

				for (PhoneNumer number : becameSpam) {
					_indexer.publishUpdate(number);
				}

				LOG.info("Nize import: {} of {} reports counted for {}.", counted, feed.reports().size(), day);
			}
		}
		return total;
	}

	/**
	 * Counts the given partner reports, without commit.
	 *
	 * @param becameSpam Output: the numbers that crossed the spam threshold.
	 * @return The number of reports counted.
	 */
	int importReports(DB db, SqlSession session, List<PartnerReport> reports, List<PhoneNumer> becameSpam) {
		SpamReports spamReports = session.getMapper(SpamReports.class);
		ExternalReports ledger = session.getMapper(ExternalReports.class);

		int counted = 0;
		for (PartnerReport report : reports) {
			Rating rating = classify(report.score(), report.typeId());
			if (rating == null) {
				continue;
			}

			String id = report.id();
			if (id == null || id.isBlank() || report.created() == null || report.phone() == null) {
				LOG.debug("Skipping incomplete Nize report: {}", report);
				continue;
			}
			id = id.strip();
			if (id.length() > MAX_ID_LENGTH) {
				LOG.debug("Skipping Nize report with overlong id: {}", id);
				continue;
			}

			PhoneNumer number = parseInternational(report.phone());
			if (number == null) {
				LOG.debug("Skipping Nize report with unparseable phone number: {}", report.phone());
				continue;
			}
			String phoneId = NumberAnalyzer.getPhoneId(number);

			// Never record community votes for globally whitelisted numbers.
			if (spamReports.isWhiteListed(phoneId)) {
				continue;
			}

			if (ledger.hasReport(SOURCE, id)) {
				continue;
			}

			long created = report.created().longValue();
			String sender = report.senderId();
			if (sender != null && sender.length() > MAX_ID_LENGTH) {
				sender = sender.substring(0, MAX_ID_LENGTH);
			}
			ledger.addReport(SOURCE, id, phoneId, sender, created, rating);

			String text = report.text();
			UserComment comment = UserComment.create()
				.setId(id)
				.setPhone(phoneId)
				.setRating(rating)
				.setComment(text == null || text.isBlank() ? null : text.strip())
				.setLang(COMMENT_LANG)
				.setService(SOURCE)
				.setCreated(created);

			if (db.addRating(spamReports, number, number.getCountryCode(), comment, true)) {
				becameSpam.add(number);
			}
			counted++;
		}
		return counted;
	}

	/**
	 * Parses a phone number in international format (the partner delivers E.164 numbers).
	 *
	 * @return The number, or <code>null</code> if it is not a valid number in international format.
	 */
	static PhoneNumer parseInternational(String phone) {
		String normalized = NumberAnalyzer.normalizeNumber(phone);
		if (!normalized.startsWith("+") && !normalized.startsWith("00")) {
			// A national number cannot be resolved, the partner's users are not bound to a country.
			return null;
		}
		return NumberAnalyzer.parsePhoneNumber(normalized, NumberAnalyzer.GERMAN_DIAL_PREFIX);
	}

	/**
	 * Classifies a partner report.
	 *
	 * <ol>
	 * <li>category "trustworthy" (2): skip</li>
	 * <li>score 3 or higher: skip</li>
	 * <li>score 1 or 2: spam, category from the type, B_MISSED if missing or unknown</li>
	 * <li>no score and a spam category (3 to 11): spam with that category</li>
	 * <li>anything else: skip</li>
	 * </ol>
	 *
	 * @param score The partner's score (1 = spam, 5 = trustworthy), <code>null</code> if absent.
	 * @param typeId The partner's category, <code>null</code> if absent.
	 * @return The rating to count, or <code>null</code> if the report is not counted.
	 */
	static Rating classify(Integer score, Integer typeId) {
		if (typeId != null && typeId.intValue() == TYPE_TRUSTWORTHY) {
			return null;
		}
		if (score != null) {
			int value = score.intValue();
			if (value == 1 || value == 2) {
				Rating rating = category(typeId);
				return rating == null ? Rating.B_MISSED : rating;
			}
			return null;
		}
		return category(typeId);
	}

	/**
	 * The rating of a partner spam category.
	 *
	 * @return The rating, or <code>null</code> for a missing, unknown (1), trustworthy (2), or
	 *         undefined category.
	 */
	static Rating category(Integer typeId) {
		if (typeId == null) {
			return null;
		}
		return switch (typeId.intValue()) {
			case 3 -> Rating.F_GAMBLE;
			case 4, 7, 8 -> Rating.G_FRAUD;
			case 5, 9 -> Rating.E_ADVERTISING;
			case 6 -> Rating.D_POLL;
			case 10 -> Rating.C_PING;
			case 11 -> Rating.B_MISSED;
			default -> null;
		};
	}

	/**
	 * Loads configuration from JNDI or system properties.
	 */
	private void loadConfig() {
		Context envCtx;
		try {
			envCtx = (Context) new InitialContext().lookup("java:comp/env");
		} catch (NamingException ex) {
			LOG.info("Not using JNDI configuration for Nize import: {}", ex.getMessage());
			envCtx = null;
		}

		String enabled = lookup(envCtx, "nize/enabled");
		_enabled = enabled != null && Boolean.parseBoolean(enabled.strip());

		String url = lookup(envCtx, "nize/url");
		if (url != null && !url.isBlank()) {
			_url = url;
		}

		_token = lookup(envCtx, "nize/token");

		_scheduleHour = lookupInt(envCtx, "nize/schedule/hour", _scheduleHour);
		_scheduleMinute = lookupInt(envCtx, "nize/schedule/minute", _scheduleMinute);

		if (_scheduleHour < 0 || _scheduleHour > 23) {
			LOG.warn("Invalid Nize schedule hour {}, using default 7", _scheduleHour);
			_scheduleHour = 7;
		}
		if (_scheduleMinute < 0 || _scheduleMinute > 59) {
			LOG.warn("Invalid Nize schedule minute {}, using default 0", _scheduleMinute);
			_scheduleMinute = 0;
		}
	}

	/**
	 * Looks up a JNDI entry (e.g. <code>nize/url</code>), falling back to the system property with
	 * dots (<code>nize.url</code>). Values are never logged, the token is secret.
	 */
	private static String lookup(Context envCtx, String name) {
		if (envCtx != null) {
			try {
				Object value = envCtx.lookup(name);
				if (value != null) {
					return String.valueOf(value);
				}
			} catch (NamingException ex) {
				// Fall back to system properties.
			}
		}
		return System.getProperty(name.replace('/', '.'));
	}

	private static int lookupInt(Context envCtx, String name, int defaultValue) {
		String value = lookup(envCtx, name);
		if (value == null || value.isBlank()) {
			return defaultValue;
		}
		try {
			return Integer.parseInt(value.strip());
		} catch (NumberFormatException ex) {
			LOG.warn("Invalid value for {}: {}", name, value);
			return defaultValue;
		}
	}
}
