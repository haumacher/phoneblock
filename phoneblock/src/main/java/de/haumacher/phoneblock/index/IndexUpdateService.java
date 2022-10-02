/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.index;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import de.haumacher.phoneblock.scheduler.SchedulerService;

/**
 * Service notifying an index about a changed URL in the application.
 */
public interface IndexUpdateService extends ServletContextListener {

	/**
	 * No-op {@link IndexUpdateService}
	 */
	IndexUpdateService NONE = new IndexUpdateService() {
		@Override
		public void publishUpdate(String path) {
			// Ignore.
		}

		@Override
		public void contextInitialized(ServletContextEvent sce) {
			// Ignore.
		}

		@Override
		public void contextDestroyed(ServletContextEvent sce) {
			// Ignore.
		}
	};

	/** 
	 * Adds the given application path to the index.
	 */
	void publishUpdate(String path);
	
	/**
	 * Process index updates asynchronously.
	 */
	static IndexUpdateService async(SchedulerService scheduler, IndexUpdateService service) {
		return new IndexUpdateService() {
			@Override
			public void contextInitialized(ServletContextEvent sce) {
				service.contextInitialized(sce);
			}
			
			@Override
			public void contextDestroyed(ServletContextEvent sce) {
				service.contextDestroyed(sce);
			}
			
			@Override
			public void publishUpdate(String path) {
				scheduler.executor().submit(() -> service.publishUpdate(path));
			}
		};
	}

	/**
	 * Distribute index updates to multiple services.
	 */
	static IndexUpdateService tee(IndexUpdateService... services) {
		return new IndexUpdateMultiplexer(services);
	}

}
