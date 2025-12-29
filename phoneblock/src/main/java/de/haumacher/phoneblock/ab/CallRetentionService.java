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
                    int cleaned = db.removeOutdatedCalls(users, bot);
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
    
}
