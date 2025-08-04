/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.answerbot;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.ab.DBAnswerbotInfo;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.Users;

/**
 * Service for automatically cleaning up old call records based on retention policies.
 */
public class CallRetentionService {
    
    private static final Logger LOG = LoggerFactory.getLogger(CallRetentionService.class);
    
    private static volatile CallRetentionService INSTANCE;
    
    private final ScheduledExecutorService scheduler;
    private boolean started = false;
    
    private CallRetentionService() {
        this.scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "CallRetentionService");
            t.setDaemon(true);
            return t;
        });
    }
    
    /**
     * Gets the singleton instance of the retention service.
     */
    public static CallRetentionService getInstance() {
        if (INSTANCE == null) {
            synchronized (CallRetentionService.class) {
                if (INSTANCE == null) {
                    INSTANCE = new CallRetentionService();
                }
            }
        }
        return INSTANCE;
    }
    
    /**
     * Starts the retention service with daily cleanup.
     */
    public synchronized void start() {
        if (started) {
            return;
        }
        
        LOG.info("Starting call retention service with daily cleanup");
        
        // Run cleanup every 24 hours, starting 1 hour after startup
        scheduler.scheduleAtFixedRate(
            this::performCleanup, 
            1, // Initial delay: 1 hour
            24, // Period: 24 hours  
            TimeUnit.HOURS
        );
        
        started = true;
    }
    
    /**
     * Stops the retention service.
     */
    public synchronized void stop() {
        if (!started) {
            return;
        }
        
        LOG.info("Stopping call retention service");
        scheduler.shutdown();
        started = false;
    }
    
    /**
     * Performs the cleanup of old call records for all bots with retention enabled.
     */
    public void performCleanup() {
        LOG.info("Starting scheduled call retention cleanup");
        
        DB db = DBService.getInstance();
        try (SqlSession session = db.openSession()) {
            Users users = session.getMapper(Users.class);
            
            // Get all answerbots with retention enabled (period != NEVER)
            List<DBAnswerbotInfo> botsWithRetention = users.getAnswerbotsWithRetention();
            
            int totalCleaned = 0;
            
            for (DBAnswerbotInfo bot : botsWithRetention) {
                try {
                    int cleaned = cleanupBot(users, bot);
                    totalCleaned += cleaned;
                    
                    if (cleaned > 0) {
                        LOG.info("Cleaned {} old call records for bot {} (user: {})", 
                            cleaned, bot.getId(), bot.getUserId());
                    }
                } catch (Exception ex) {
                    LOG.error("Failed to cleanup calls for bot " + bot.getId(), ex);
                }
            }
            
            if (totalCleaned > 0) {
                session.commit();
                LOG.info("Completed call retention cleanup, removed {} total call records", totalCleaned);
            } else {
                LOG.debug("No old call records found for cleanup");
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
        RetentionPeriod retentionPeriod = RetentionPeriod.valueOf(bot.getRetentionPeriod());
        
        if (!retentionPeriod.isEnabled()) {
            return 0;
        }
        
        long cutoffTime = retentionPeriod.getCutoffTime();
        
        LOG.debug("Cleaning calls older than {} for bot {} (cutoff: {})", 
            retentionPeriod, bot.getId(), cutoffTime);
        
        return users.deleteCallsOlderThan(bot.getId(), cutoffTime);
    }
    
    /**
     * Manually triggers cleanup for a specific bot.
     * 
     * @param botId The bot ID to clean up
     * @return Number of records cleaned up
     */
    public int cleanupBot(long botId) {
        DB db = DBService.getInstance();
        try (SqlSession session = db.openSession()) {
            Users users = session.getMapper(Users.class);
            
            DBAnswerbotInfo bot = users.getAnswerBot(botId);
            if (bot == null) {
                LOG.warn("Cannot cleanup calls for non-existing bot: {}", botId);
                return 0;
            }
            
            int cleaned = cleanupBot(users, bot);
            if (cleaned > 0) {
                session.commit();
                LOG.info("Manual cleanup removed {} call records for bot {}", cleaned, botId);
            }
            
            return cleaned;
        } catch (Exception ex) {
            LOG.error("Failed to manually cleanup calls for bot " + botId, ex);
            return 0;
        }
    }
}
