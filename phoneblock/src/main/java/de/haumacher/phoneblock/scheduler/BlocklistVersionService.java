/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.scheduler;

import java.util.Calendar;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

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
 * Runs daily at 3:00 AM to process all pending updates.
 */
public class BlocklistVersionService implements ServletContextListener {

	private static final Logger LOG = LoggerFactory.getLogger(BlocklistVersionService.class);

	private static volatile BlocklistVersionService INSTANCE;

	private final SchedulerService _schedulerService;

	private final DBService _dbService;

	private ScheduledFuture<?> _task;

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
		LOG.info("Starting blocklist version service with daily version assignment");

		Calendar firstRun = Calendar.getInstance();
		firstRun.set(Calendar.HOUR_OF_DAY, 3);
		firstRun.set(Calendar.MINUTE, 0);
		firstRun.set(Calendar.SECOND, 0);
		firstRun.set(Calendar.MILLISECOND, 0);

		Calendar inOneHour = Calendar.getInstance();
		inOneHour.add(Calendar.HOUR, 1);

		if (firstRun.before(inOneHour)) {
			firstRun.add(Calendar.DAY_OF_MONTH, 1);
		}

		// Run version assignment every day at 3:00 AM.
		_task = _schedulerService.scheduler().scheduleAtFixedRate(
			this::assignVersions,
			firstRun.getTimeInMillis() - System.currentTimeMillis(), // Initial delay
			24 * 60 * 60 * 1000, // Period: 24 hours
			TimeUnit.MILLISECONDS
		);

		if (INSTANCE == null) {
			INSTANCE = this;
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
	 * This is called automatically at 3:00 AM daily, or can be manually triggered.
	 */
	public void assignVersions() {
		LOG.info("Starting scheduled blocklist version assignment");

		DB db = _dbService.db();
		try (SqlSession session = db.openSession()) {
			Users users = session.getMapper(Users.class);
			SpamReports reports = session.getMapper(SpamReports.class);

			// Get current version
			String versionStr = users.getProperty("blocklist.version");
			long currentVersion = (versionStr != null) ? Long.parseLong(versionStr) : 0L;

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
