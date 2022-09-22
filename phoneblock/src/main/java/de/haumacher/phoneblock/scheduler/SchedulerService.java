/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.scheduler;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

/**
 * Service providing a {@link ScheduledExecutorService}.
 */
public class SchedulerService implements ServletContextListener {

	private ScheduledExecutorService _executor;

	@Override
	public void contextInitialized(ServletContextEvent sce) {
		_executor = new ScheduledThreadPoolExecutor(2);
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		try {
			_executor.awaitTermination(5, TimeUnit.SECONDS);
		} catch (InterruptedException ex) {
			ex.printStackTrace();
		}
	}

	/** 
	 * The executor.
	 */
	public ScheduledExecutorService executor() {
		return _executor;
	}

}
