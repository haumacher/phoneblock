/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import de.haumacher.phoneblock.crawl.CrawlerService;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.index.IndexUpdateService;
import de.haumacher.phoneblock.index.google.GoogleUpdateService;
import de.haumacher.phoneblock.index.indexnow.IndexNowUpdateService;
import de.haumacher.phoneblock.mail.MailServiceStarter;
import de.haumacher.phoneblock.scheduler.SchedulerService;

/**
 * Central controller of services.
 */
@WebListener
public class Application implements ServletContextListener {
	
	private ServletContextListener[] _services;
	
	/** 
	 * Creates a {@link Application}.
	 *
	 */
	public Application() {
		IndexUpdateService indexer;
		SchedulerService scheduler;
		_services = new ServletContextListener[] {
			scheduler = new SchedulerService(),
			indexer = IndexUpdateService.async(scheduler, IndexUpdateService.tee(
				new IndexNowUpdateService(),
				new GoogleUpdateService())),
			new DBService(indexer),
			new CrawlerService(),
			new MailServiceStarter()
		};
	}

	@Override
	public void contextInitialized(ServletContextEvent sce) {
		System.out.println("Starting phoneblock application.");
		for (int n = 0, cnt = _services.length; n < cnt; n++) {
			_services[n].contextInitialized(sce);
		}
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		System.out.println("Stopping phoneblock application.");
		for (int n = _services.length - 1; n >= 0; n--) {
			try {
				_services[n].contextDestroyed(sce);
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
		}
	}

}
