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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.carddav.resource.AddressBookCache;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

/**
 * Service for assigning version numbers to blocklist changes for incremental synchronization.
 * Runs at a configurable interval (default: daily at 22:00) to process all pending updates,
 * timed so the new release is in place before the bulk of FritzBox CardDAV syncs at night.
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

	private int _scheduleHour = 22;
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

		// #300 follow-up: rebuild the block aggregation once at startup so the wildcard-block
		// gate is correct immediately (the on-disk tables hold stale counts from before this
		// version). The daily assignVersions sweep keeps it fresh afterwards.
		try {
			_dbService.db().recomputeBlockAggregation(System.currentTimeMillis());
		} catch (Exception ex) {
			LOG.error("Initial block aggregation recompute failed", ex);
		}

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
			LOG.warn("Invalid schedule hour {}, using default 22", _scheduleHour);
			_scheduleHour = 22;
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
		LOG.info("Starting scheduled blocklist publication");

		DB db = _dbService.db();

		// #300 follow-up: rebuild the block aggregation (wildcard-block detection) from the
		// current NUMBERS evidence — counts how many numbers per block are *currently* spam, so
		// stale-but-listed blocks stay detected and faded blocks drop out.
		try {
			db.recomputeBlockAggregation(System.currentTimeMillis());
		} catch (Exception ex) {
			LOG.error("Block aggregation recompute failed", ex);
		}

		// #342: bucket-based publication. The sweep compares the live bucket
		// of every candidate number with the published bucket in the
		// BLOCKLIST table and writes only actual flips — including
		// decay-induced removals, which become tombstones. Hard delete of
		// long-faded NUMBERS rows is the subject of #341.
		try {
			long before = db.getBlocklistVersion();
			long version = db.publishBlocklist(System.currentTimeMillis());

			if (version != before) {
				LOG.info("Completed blocklist publication, new version is {}.", version);

				// CardDAV serves the published state — invalidate its caches so users
				// see the fresh release on the next sync without waiting for TTL expiry.
				AddressBookCache cache = AddressBookCache.getInstance();
				if (cache != null) {
					cache.flushAllCaches();
				}
			} else {
				LOG.debug("No blocklist changes to publish.");
			}
		} catch (Exception ex) {
			LOG.error("Blocklist publication failed", ex);
		}
	}

}
