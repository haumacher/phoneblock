/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.diag;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.mail.MailService;
import de.haumacher.phoneblock.mail.MailServiceStarter;
import de.haumacher.phoneblock.scheduler.SchedulerService;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

/**
 * Diagnostics ingestion (Phase 1): a scheduled reader that tails the server log,
 * recognizes source-specific error lines (the dongle reports, first), normalizes
 * and scrubs them, and rolls up the {@code DIAG_*} aggregate tables. Decoupled
 * from the request path — nothing writes diagnostics synchronously on a request;
 * the log file is the buffer.
 *
 * <p>Config via JNDI ({@code java:comp/env/diagnostics/*}) or {@code -Ddiagnostics.*}
 * system properties; see {@code JNDI-CONFIGURATION.md}.</p>
 */
public class DiagnosticsService implements ServletContextListener {

	private static final Logger LOG = LoggerFactory.getLogger(DiagnosticsService.class);

	private static final String STREAM_ID = "server";

	private final SchedulerService _scheduler;
	private final DBService _db;
	private final MailServiceStarter _mail;

	private SegmentedLogReader _reader;
	private DiagnosticsAggregator _aggregator;
	private List<LineRecognizer> _recognizers;
	private DiagnosticsMatcher _matcher;
	private Notifier _notifier;

	private boolean _enabled;
	private Path _logFile;
	private int _intervalMinutes = 5;
	private int _sampleCap = 20;
	private int _retentionDays = 30;
	private int _maxLinesPerPoll = 50_000;
	private int _matchIntervalMinutes = 60;
	private int _quietDays = 3;
	private int _userDailyCap = 3;
	private int _globalDailyCap = 100;

	private ScheduledFuture<?> _ingestTask;
	private ScheduledFuture<?> _retentionTask;
	private ScheduledFuture<?> _matchTask;

	public DiagnosticsService(SchedulerService scheduler, DBService db, MailServiceStarter mail) {
		_scheduler = scheduler;
		_db = db;
		_mail = mail;
	}

	@Override
	public void contextInitialized(ServletContextEvent sce) {
		loadConfig();
		if (!_enabled) {
			LOG.info("Diagnostics ingestion disabled.");
			return;
		}

		_reader = new SegmentedLogReader(_logFile);
		_aggregator = new DiagnosticsAggregator(_sampleCap);
		_recognizers = List.of(new DongleRecognizer());
		_matcher = new DiagnosticsMatcher(_quietDays);
		MailService mailService = _mail == null ? null : _mail.getMailService();
		_notifier = new MailNotifier(_db, mailService, _userDailyCap, _globalDailyCap);

		LOG.info("Starting diagnostics ingestion from {} every {} min (sample cap {}, retention {} d); "
			+ "matcher every {} min.", _logFile, _intervalMinutes, _sampleCap, _retentionDays, _matchIntervalMinutes);

		long periodMs = _intervalMinutes * 60L * 1000L;
		_ingestTask = _scheduler.scheduler().scheduleAtFixedRate(
			this::safeRunIngest, 30_000L, periodMs, TimeUnit.MILLISECONDS);
		_retentionTask = _scheduler.scheduler().scheduleAtFixedRate(
			this::safeRunRetention, 60_000L, 24L * 60 * 60 * 1000, TimeUnit.MILLISECONDS);
		_matchTask = _scheduler.scheduler().scheduleAtFixedRate(
			this::safeRunMatch, 90_000L, _matchIntervalMinutes * 60L * 1000L, TimeUnit.MILLISECONDS);
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		if (_ingestTask != null) {
			_ingestTask.cancel(false);
		}
		if (_retentionTask != null) {
			_retentionTask.cancel(false);
		}
		if (_matchTask != null) {
			_matchTask.cancel(false);
		}
	}

	private void safeRunIngest() {
		try {
			runIngest();
		} catch (Exception ex) {
			LOG.error("Diagnostics ingestion failed.", ex);
		}
	}

	private void safeRunMatch() {
		try {
			runMatch();
		} catch (Exception ex) {
			LOG.error("Diagnostics matcher failed.", ex);
		}
	}

	/**
	 * Evaluates the active rules against the current aggregates: classifies,
	 * projects (SHADOW) or sends (LIVE) notifications, and rearms quiet matches.
	 * Public for verification.
	 */
	public void runMatch() {
		try (SqlSession session = _db.db().openSession()) {
			DiagnosticsMapper mapper = session.getMapper(DiagnosticsMapper.class);
			_matcher.run(mapper, _notifier, System.currentTimeMillis());
			session.commit();
		}
	}

	private void safeRunRetention() {
		try {
			runRetention();
		} catch (Exception ex) {
			LOG.error("Diagnostics sample retention failed.", ex);
		}
	}

	/**
	 * Reads all currently-available new log lines and rolls up aggregates, one
	 * transaction per {@code maxLinesPerPoll} batch. Public for verification.
	 */
	public void runIngest() throws Exception {
		if (!Files.isDirectory(_logFile.getParent())) {
			LOG.warn("Diagnostics log directory {} not present — skipping ingest.", _logFile.getParent());
			return;
		}

		DB db = _db.db();
		int totalLines = 0;
		int totalEvents = 0;

		try (SqlSession session = db.openSession()) {
			DiagnosticsMapper mapper = session.getMapper(DiagnosticsMapper.class);

			IngestCursor cursor = mapper.getCursor(STREAM_ID);
			long segment = cursor != null ? cursor.getSegmentCount() : -1;
			long offset = cursor != null ? cursor.getByteOffset() : 0;
			long lastTs = cursor != null ? cursor.getLastLineTs() : 0;

			// Bound one run so a huge backlog can't hold one transaction open
			// forever; the fixed-rate schedule picks up the rest next tick.
			for (int iteration = 0; iteration < 1000; iteration++) {
				SegmentedLogReader.PollResult result = _reader.poll(segment, offset, _maxLinesPerPoll);

				if (result.gapDetected()) {
					LOG.warn("Diagnostics reader fell behind retention; resumed at segment {} (lines lost).",
						result.segment());
				}

				for (String line : result.lines()) {
					totalLines++;
					TinylogLine parsed = TinylogLine.parse(line);
					if (parsed == null) {
						continue;
					}
					for (LineRecognizer recognizer : _recognizers) {
						DiagEvent event = recognizer.recognize(parsed);
						if (event != null) {
							_aggregator.apply(mapper, event);
							totalEvents++;
							if (event.timestampMs() > lastTs) {
								lastTs = event.timestampMs();
							}
							break;
						}
					}
				}

				segment = result.segment();
				offset = result.offset();
				mapper.upsertCursor(STREAM_ID, segment, offset, lastTs, System.currentTimeMillis());
				session.commit();

				if (!result.moreAvailable()) {
					break;
				}
			}
		}

		if (totalEvents > 0) {
			LOG.info("Diagnostics ingest: scanned {} lines, recorded {} events.", totalLines, totalEvents);
		} else {
			LOG.debug("Diagnostics ingest: scanned {} lines, no new events.", totalLines);
		}
	}

	private void runRetention() {
		DB db = _db.db();
		long cutoff = System.currentTimeMillis() - _retentionDays * 24L * 60 * 60 * 1000;
		try (SqlSession session = db.openSession()) {
			DiagnosticsMapper mapper = session.getMapper(DiagnosticsMapper.class);
			int removed = mapper.purgeSamples(cutoff);
			session.commit();
			if (removed > 0) {
				LOG.info("Diagnostics retention: purged {} sample(s) older than {} days.", removed, _retentionDays);
			}
		}
	}

	private void loadConfig() {
		_enabled = boolProp("enabled", true);
		String file = strProp("logFile", "/var/log/tomcat10/phoneblock.log");
		_logFile = Path.of(file);
		_intervalMinutes = intProp("intervalMinutes", _intervalMinutes);
		_sampleCap = intProp("sampleCap", _sampleCap);
		_retentionDays = intProp("retentionDays", _retentionDays);
		_maxLinesPerPoll = intProp("maxLinesPerPoll", _maxLinesPerPoll);
		_matchIntervalMinutes = intProp("matchIntervalMinutes", _matchIntervalMinutes);
		_quietDays = intProp("quietDays", _quietDays);
		_userDailyCap = intProp("userDailyCap", _userDailyCap);
		_globalDailyCap = intProp("globalDailyCap", _globalDailyCap);
	}

	private static String strProp(String name, String defaultValue) {
		try {
			InitialContext initCtx = new InitialContext();
			Context envCtx = (Context) initCtx.lookup("java:comp/env");
			Object value = envCtx.lookup("diagnostics/" + name);
			if (value != null) {
				return value.toString();
			}
		} catch (NamingException ex) {
			// Fall through to system property / default.
		}
		return System.getProperty("diagnostics." + name, defaultValue);
	}

	private static int intProp(String name, int defaultValue) {
		String value = strProp(name, null);
		if (value == null) {
			return defaultValue;
		}
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException ex) {
			LOG.warn("Invalid integer for diagnostics.{}: '{}' — using {}.", name, value, defaultValue);
			return defaultValue;
		}
	}

	private static boolean boolProp(String name, boolean defaultValue) {
		String value = strProp(name, null);
		return value == null ? defaultValue : Boolean.parseBoolean(value.trim());
	}
}
