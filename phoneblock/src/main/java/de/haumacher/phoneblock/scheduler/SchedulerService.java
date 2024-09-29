/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.scheduler;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service providing a {@link ScheduledExecutorService}.
 */
public class SchedulerService implements ServletContextListener {

	private static final Logger LOG = LoggerFactory.getLogger(SchedulerService.class);

	private ScheduledExecutorService _executor;

	private static SchedulerService _instance;

	@Override
	public void contextInitialized(ServletContextEvent sce) {
		LOG.info("Starting scheduler.");
		_executor = new ScheduledThreadPoolExecutor(10);
		
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
	}

	/** 
	 * The executor.
	 */
	public ScheduledExecutorService executor() {
		return _executor;
	}

}
