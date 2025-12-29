/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.ab;

import java.util.Calendar;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.ab.proto.RetentionPeriod;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.Users;
import de.haumacher.phoneblock.scheduler.SchedulerService;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

/**
 * Service for automatically cleaning up old call records based on retention policies.
 */
public class CallRetentionService implements ServletContextListener {
    
    private static final Logger LOG = LoggerFactory.getLogger(CallRetentionService.class);
    
    private static volatile CallRetentionService INSTANCE;
    
    private final SchedulerService _schedulerService;

	private ScheduledFuture<?> _task;

	private DBService _dbService;
    
    public CallRetentionService(SchedulerService scheduler, DBService dbService) {
        _schedulerService = scheduler;
		_dbService = dbService;
    }
    
    /**
     * Gets the singleton instance of the retention service.
     */
    public static CallRetentionService getInstance() {
        return INSTANCE;
    }
    
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        LOG.info("Starting call retention service with daily cleanup");
        
        Calendar firstRun = Calendar.getInstance();
        firstRun.set(Calendar.HOUR, 4);
        firstRun.set(Calendar.MINUTE, 0);
        firstRun.set(Calendar.SECOND, 0);
        firstRun.set(Calendar.MILLISECOND, 0);
        
        Calendar inOneHour = Calendar.getInstance();
        inOneHour.add(Calendar.HOUR, 1);
        
		if (firstRun.before(inOneHour)) {
			firstRun.add(Calendar.DAY_OF_MONTH, 1);
		}
        
        // Run cleanup every day at 4:00.
        _task = _schedulerService.scheduler().scheduleAtFixedRate(
            this::performCleanup, 
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
        LOG.info("Stopping call retention service");
        _task.cancel(false);

        if (INSTANCE == this) {
        	INSTANCE = null;
        }
    }
    
    /**
     * Performs the cleanup of old call records for all bots with retention enabled.
     */
    public void performCleanup() {
        LOG.info("Starting scheduled call retention cleanup");
        
        DB db = _dbService.db();
        try (SqlSession session = db.openSession()) {
            Users users = session.getMapper(Users.class);

            int totalCleaned = 0;
            
            // Get all answerbots with retention enabled (period != NEVER)
            for (DBAnswerbotInfo bot : users.getAnswerbotsWithRetention()) {
                try {
                    int cleaned = cleanupBot(users, bot);
                    totalCleaned += cleaned;
                } catch (Exception ex) {
                    LOG.error("Failed to cleanup calls for bot " + bot.getId(), ex);
                }
            }
            
            if (totalCleaned > 0) {
                session.commit();
                LOG.info("Completed call retention cleanup, removed {} total call records.", totalCleaned);
            } else {
                LOG.debug("No old call records found for cleanup.");
            }
        } catch (Exception ex) {
            LOG.error("Call retention cleanup failed", ex);
        }
    }
    
    /**
     * Cleans up old call records for a specific bot.
     * 
     * @param users The database mapper
     * @param bot The bot information including retention settings
     * @return Number of records cleaned up
     */
    private int cleanupBot(Users users, DBAnswerbotInfo bot) {
        RetentionPeriod retentionPeriod = bot.getRetentionPeriod();
        if (retentionPeriod == RetentionPeriod.NEVER) {
            return 0;
        }
        
        long cutoffTime = System.currentTimeMillis() - 24 * 60 * 60 * 1000 * switch (retentionPeriod) {
			case MONTH -> 30L;
			case NEVER -> throw new AssertionError("Unreachable.");
			case QUARTER -> 90L;
			case WEEK -> 7L;
			case YEAR -> 365L;
        };
        
        int deleted = users.deleteCallsOlderThan(bot.getId(), cutoffTime);
        
        LOG.debug("Cleaned {} calls older than {} for bot {} (cutoff: {}).", 
                deleted, retentionPeriod, bot.getId(), cutoffTime);
        
		return deleted;
    }
    
}
