/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.scheduler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.mjsip.time.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

/**
 * Service providing a {@link ScheduledExecutorService}.
 */
public class SchedulerService implements ServletContextListener, Scheduler {

	/**
	 * The default core pool size of the scheduler.
	 */
	public static final int CORE_POOL_SIZE = 10;

	private static final Logger LOG = LoggerFactory.getLogger(SchedulerService.class);

	private ExecutorService _executor;

	private ScheduledExecutorService _scheduler;

	private static SchedulerService _instance;

	@Override
	public void contextInitialized(ServletContextEvent sce) {
		LOG.info("Starting scheduler.");
		_executor = Executors.newCachedThreadPool();
		_scheduler = Executors.newScheduledThreadPool(CORE_POOL_SIZE);
		
		_instance = this;
	}
	
	/**
	 * The singleton instance of this service.
	 */
	public static SchedulerService getInstance() {
		return _instance;
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		LOG.info("Shutting down scheduler.");
		if (_executor != null) {
			_executor.shutdownNow();
			
			try {
				boolean finished = _executor.awaitTermination(10, TimeUnit.SECONDS);
				if (!finished) {
					LOG.warn("Scheduler did not terminate in time.");
				}
			} catch (InterruptedException ex) {
				LOG.error("Stopping scheduler failed.", ex);
			}
		}

		LOG.info("Scheduler stopped.");
		
		if (_instance == this) {
			_instance = null;
		}
	}

	/** 
	 * The executor.
	 */
	public ExecutorService executor() {
		return _executor;
	}
	
	/** 
	 * The scheduler.
	 */
	public ScheduledExecutorService scheduler() {
		return _scheduler;
	}

}
