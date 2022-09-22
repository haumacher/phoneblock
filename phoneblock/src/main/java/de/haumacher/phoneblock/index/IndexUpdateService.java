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
	
	static IndexUpdateService tee(IndexUpdateService... services) {
		return new IndexUpdateService() {
			
			@Override
			public void contextInitialized(ServletContextEvent sce) {
				for (IndexUpdateService service : services) {
					service.contextInitialized(sce);
				}
			}
			
			@Override
			public void contextDestroyed(ServletContextEvent sce) {
				for (IndexUpdateService service : services) {
					service.contextDestroyed(sce);
				}
			}
			
			@Override
			public void publishUpdate(String path) {
				for (IndexUpdateService service : services) {
					service.publishUpdate(path);
				}
			}
		};
	}

}
