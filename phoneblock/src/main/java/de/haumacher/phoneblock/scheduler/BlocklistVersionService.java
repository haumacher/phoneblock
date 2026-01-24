/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.scheduler;

import java.util.Calendar;
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
import de.haumacher.phoneblock.db.SpamReports;
import de.haumacher.phoneblock.db.Users;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

/**
 * Service for assigning version numbers to blocklist changes for incremental synchronization.
 * Runs at a configurable interval (default: daily at 3:00 AM) to process all pending updates.
 *
 * <p>The schedule can be configured via JNDI or system properties:</p>
 * <ul>
 * <li><code>blocklist/version/hour</code> - Hour of day to run (0-23), default: 3</li>
 * <li><code>blocklist/version/minute</code> - Minute of hour to run (0-59), default: 0</li>
 * <li><code>blocklist/version/intervalMinutes</code> - Interval between runs in minutes, default: 1440 (24 hours)</li>
 * <li><code>blocklist/version/initialDelayMinutes</code> - Initial delay in minutes for testing (overrides schedule), default: -1 (disabled)</li>
 * </ul>
 *
 * <p>For testing, you can trigger the job immediately and run it frequently by setting:</p>
 * <code>blocklist.version.initialDelayMinutes=0 -Dblocklist.version.intervalMinutes=5</code>
 */
public class BlocklistVersionService implements ServletContextListener {

	private static final Logger LOG = LoggerFactory.getLogger(BlocklistVersionService.class);

	private static volatile BlocklistVersionService INSTANCE;

	private final SchedulerService _schedulerService;

	private final DBService _dbService;

	private ScheduledFuture<?> _task;

	private int _scheduleHour = 3;
	private int _scheduleMinute = 0;
	private long _initialDelayMinutes = -1; // -1 means use calculated delay based on schedule time
	private long _intervalMinutes = 1440; // 24 hours in minutes (1440 = 24 * 60)

	public BlocklistVersionService(SchedulerService scheduler, DBService dbService) {
		_schedulerService = scheduler;
		_dbService = dbService;
	}

	/**
	 * Gets the singleton instance of the version service.
	 */
	public static BlocklistVersionService getInstance() {
		return INSTANCE;
	}

	@Override
	public void contextInitialized(ServletContextEvent sce) {
		loadConfig();

		LOG.info("Starting blocklist version service: schedule={}:{:02d}, interval={} minutes",
			_scheduleHour, _scheduleMinute, _intervalMinutes);

		long initialDelay;
		if (_initialDelayMinutes >= 0) {
			// Use configured initial delay for testing
			initialDelay = _initialDelayMinutes * 60 * 1000;
			LOG.info("Using configured initial delay: {} minutes", _initialDelayMinutes);
		} else {
			// Calculate delay until next scheduled time
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

			initialDelay = firstRun.getTimeInMillis() - System.currentTimeMillis();
		}

		// Run version assignment at configured interval
		_task = _schedulerService.scheduler().scheduleAtFixedRate(
			this::assignVersions,
			initialDelay,
			_intervalMinutes * 60 * 1000, // Convert minutes to milliseconds
			TimeUnit.MILLISECONDS
		);

		if (INSTANCE == null) {
			INSTANCE = this;
		}
	}

	/**
	 * Loads configuration from JNDI or system properties.
	 *
	 * <p>Configuration properties:</p>
	 * <ul>
	 * <li><code>blocklist/version/hour</code> - Hour of day to run (0-23), default: 3</li>
	 * <li><code>blocklist/version/minute</code> - Minute of hour to run (0-59), default: 0</li>
	 * <li><code>blocklist/version/intervalMinutes</code> - Interval between runs in minutes, default: 1440 (24 hours)</li>
	 * <li><code>blocklist/version/initialDelayMinutes</code> - Initial delay in minutes for testing (overrides schedule calculation), default: -1 (disabled)</li>
	 * </ul>
	 */
	private void loadConfig() {
		try {
			InitialContext initCtx = new InitialContext();
			Context envCtx = (Context) initCtx.lookup("java:comp/env");

			try {
				_scheduleHour = ((Number) envCtx.lookup("blocklist/version/hour")).intValue();
			} catch (NamingException ex) {
				String value = System.getProperty("blocklist.version.hour");
				if (value != null) {
					_scheduleHour = Integer.parseInt(value);
				}
			}

			try {
				_scheduleMinute = ((Number) envCtx.lookup("blocklist/version/minute")).intValue();
			} catch (NamingException ex) {
				String value = System.getProperty("blocklist.version.minute");
				if (value != null) {
					_scheduleMinute = Integer.parseInt(value);
				}
			}

			try {
				_intervalMinutes = ((Number) envCtx.lookup("blocklist/version/intervalMinutes")).longValue();
			} catch (NamingException ex) {
				String value = System.getProperty("blocklist.version.intervalMinutes");
				if (value != null) {
					_intervalMinutes = Long.parseLong(value);
				}
			}

			try {
				_initialDelayMinutes = ((Number) envCtx.lookup("blocklist/version/initialDelayMinutes")).longValue();
			} catch (NamingException ex) {
				String value = System.getProperty("blocklist.version.initialDelayMinutes");
				if (value != null) {
					_initialDelayMinutes = Long.parseLong(value);
				}
			}
		} catch (NamingException ex) {
			LOG.info("Not using JNDI configuration: {}", ex.getMessage());
		}

		// Validate configuration
		if (_scheduleHour < 0 || _scheduleHour > 23) {
			LOG.warn("Invalid schedule hour {}, using default 3", _scheduleHour);
			_scheduleHour = 3;
		}
		if (_scheduleMinute < 0 || _scheduleMinute > 59) {
			LOG.warn("Invalid schedule minute {}, using default 0", _scheduleMinute);
			_scheduleMinute = 0;
		}
		if (_intervalMinutes < 1) {
			LOG.warn("Invalid interval {} minutes, using default 1440 (24 hours)", _intervalMinutes);
			_intervalMinutes = 1440;
		}
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		LOG.info("Stopping blocklist version service");
		if (_task != null) {
			_task.cancel(false);
		}

		if (INSTANCE == this) {
			INSTANCE = null;
		}
	}

	/**
	 * Assigns version numbers to all pending blocklist updates.
	 * This is called automatically at the configured schedule, or can be manually triggered for testing.
	 */
	public void assignVersions() {
		LOG.info("Starting scheduled blocklist version assignment");

		DB db = _dbService.db();
		try (SqlSession session = db.openSession()) {
			Users users = session.getMapper(Users.class);
			SpamReports reports = session.getMapper(SpamReports.class);

			// Get current version
			String versionStr = users.getProperty("blocklist.version");
			long currentVersion = (versionStr != null) ? Long.parseLong(versionStr) : DB.INITIAL_BLOCKLIST_VERSION;

			// Increment version
			long newVersion = currentVersion + 1;

			// Assign new version to all pending updates
			int updated = reports.assignVersionToPendingUpdates(newVersion);

			if (updated > 0) {
				// Update global version counter
				if (versionStr != null) {
					users.updateProperty("blocklist.version", String.valueOf(newVersion));
				} else {
					users.addProperty("blocklist.version", String.valueOf(newVersion));
				}

				session.commit();
				LOG.info("Completed blocklist version assignment: version {} assigned to {} entries", newVersion, updated);
			} else {
				LOG.debug("No pending blocklist updates to process.");
			}
		} catch (Exception ex) {
			LOG.error("Blocklist version assignment failed", ex);
		}
	}

}
