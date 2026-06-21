/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.scheduler.SchedulerService;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

/**
 * Periodically logs H2 MVStore health metrics to diagnose unbounded database file growth.
 *
 * <p>
 * The database file can only shrink if no old page versions are referenced anymore. This monitor
 * logs the two indicators that distinguish the possible failure modes:
 * </p>
 *
 * <ul>
 * <li>A growing gap between <code>info.CURRENT_VERSION</code> and
 * <code>info.OLDEST_VERS_TO_KEEP</code> together with a long-lived uncommitted session means an
 * open transaction pins old chunks.</li>
 * <li>A dropping <code>info.FILL_RATE</code> / <code>info.CHUNKS_FILL_RATE</code> while
 * <code>info.FILE_SIZE</code> grows without any pinning session means background compaction cannot
 * keep up with the write churn.</li>
 * </ul>
 */
public class DBHealthMonitor implements ServletContextListener {

	private static final Logger LOG = LoggerFactory.getLogger(DBHealthMonitor.class);

	/**
	 * Default interval in minutes between two health snapshots.
	 */
	private static final int DEFAULT_INTERVAL_MINUTES = 5;

	/**
	 * Age in milliseconds after which an idle uncommitted transaction or a still-running statement
	 * is reported as suspicious.
	 */
	private static final long STALE_MILLIS = 60_000;

	private final SchedulerService _scheduler;

	private final DBService _dbService;

	private ScheduledFuture<?> _task;

	/**
	 * Creates a {@link DBHealthMonitor}.
	 */
	public DBHealthMonitor(SchedulerService scheduler, DBService dbService) {
		_scheduler = scheduler;
		_dbService = dbService;
	}

	@Override
	public void contextInitialized(ServletContextEvent sce) {
		int intervalMinutes = loadIntervalMinutes();
		if (intervalMinutes <= 0) {
			LOG.info("DB health monitor disabled.");
			return;
		}

		LOG.info("Starting DB health monitor with interval of {} minutes.", intervalMinutes);
		_task = _scheduler.scheduler().scheduleAtFixedRate(this::logHealth,
			1, intervalMinutes * 60L, TimeUnit.SECONDS);
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		if (_task != null) {
			_task.cancel(false);
			_task = null;
		}
	}

	/**
	 * Loads the monitor interval from JNDI (<code>db/monitorIntervalMinutes</code>) or the system
	 * property <code>db.monitorIntervalMinutes</code>. A value of <code>0</code> disables the
	 * monitor.
	 */
	private int loadIntervalMinutes() {
		try {
			InitialContext initCtx = new InitialContext();
			Context envCtx = (Context) initCtx.lookup("java:comp/env");
			return ((Number) envCtx.lookup("db/monitorIntervalMinutes")).intValue();
		} catch (NamingException ex) {
			String value = System.getProperty("db.monitorIntervalMinutes");
			if (value != null) {
				return Integer.parseInt(value);
			}
		}
		return DEFAULT_INTERVAL_MINUTES;
	}

	/**
	 * Takes one health snapshot and writes it to the log.
	 *
	 * <p>
	 * Uses a raw JDBC connection in auto-commit mode on purpose: a monitoring transaction kept open
	 * would itself pin old page versions and distort the very measurement it takes.
	 * </p>
	 */
	public void logHealth() {
		DB db = _dbService.db();
		if (db == null) {
			return;
		}

		try (Connection connection = db.dataSource().getConnection();
				Statement statement = connection.createStatement()) {
			Map<String, String> info = readStoreInfo(statement);
			int poolActive = _dbService.getActiveConnections();

			LOG.info("H2 health: fileSize={}, fillRate={}%, chunksFillRate={}%, chunkCount={}, "
					+ "pagesLive={}/{}, updateFailure={}, currentVersion={}, oldestVersionToKeep={}, "
					+ "fileWriteBytes={}, poolActive={}",
				info.get("info.FILE_SIZE"), info.get("info.FILL_RATE"), info.get("info.CHUNKS_FILL_RATE"),
				info.get("info.CHUNK_COUNT"), info.get("info.PAGE_COUNT_LIVE"), info.get("info.PAGE_COUNT"),
				info.get("info.UPDATE_FAILURE_PERCENT"), info.get("info.CURRENT_VERSION"),
				info.get("info.OLDEST_VERS_TO_KEEP"), info.get("info.FILE_WRITE_BYTES"),
				Integer.valueOf(poolActive));

			checkSessions(statement);
		} catch (Exception ex) {
			LOG.error("Failed to collect DB health metrics.", ex);
		}
	}

	private Map<String, String> readStoreInfo(Statement statement) throws Exception {
		Map<String, String> info = new LinkedHashMap<>();
		try (ResultSet result = statement.executeQuery(
				"SELECT SETTING_NAME, SETTING_VALUE FROM INFORMATION_SCHEMA.SETTINGS WHERE SETTING_NAME LIKE 'info.%'")) {
			while (result.next()) {
				info.put(result.getString(1), result.getString(2));
			}
		}
		return info;
	}

	/**
	 * Reports sessions that pin old page versions: idle sessions with uncommitted changes and
	 * statements running longer than {@link #STALE_MILLIS}.
	 */
	private void checkSessions(Statement statement) throws Exception {
		OffsetDateTime now = OffsetDateTime.now();
		try (ResultSet result = statement.executeQuery(
				"SELECT SESSION_ID, USER_NAME, SESSION_START, CONTAINS_UNCOMMITTED, SESSION_STATE, "
				+ "EXECUTING_STATEMENT, EXECUTING_STATEMENT_START, SLEEP_SINCE "
				+ "FROM INFORMATION_SCHEMA.SESSIONS")) {
			while (result.next()) {
				int sessionId = result.getInt("SESSION_ID");
				boolean uncommitted = result.getBoolean("CONTAINS_UNCOMMITTED");
				String state = result.getString("SESSION_STATE");
				String executing = result.getString("EXECUTING_STATEMENT");
				OffsetDateTime executingStart = result.getObject("EXECUTING_STATEMENT_START", OffsetDateTime.class);
				OffsetDateTime sleepSince = result.getObject("SLEEP_SINCE", OffsetDateTime.class);

				long statementAge = age(now, executingStart);
				long sleepAge = age(now, sleepSince);

				if (uncommitted && sleepAge > STALE_MILLIS) {
					LOG.warn("H2 session {} idles with uncommitted transaction since {} (state={}), "
							+ "pinning old page versions. Last statement: {}",
						Integer.valueOf(sessionId), sleepSince, state, executing);
				} else if (executing != null && statementAge > STALE_MILLIS) {
					LOG.warn("H2 session {} executes a statement since {} (state={}), "
							+ "pinning old page versions: {}",
						Integer.valueOf(sessionId), executingStart, state, executing);
				}
			}
		}
	}

	private long age(OffsetDateTime now, OffsetDateTime start) {
		return start == null ? 0 : Duration.between(start, now).toMillis();
	}

}
