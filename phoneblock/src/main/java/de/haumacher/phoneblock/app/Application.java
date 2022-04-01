/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import de.haumacher.phoneblock.crawl.CrawlerService;
import de.haumacher.phoneblock.db.DBService;

/**
 * Central controller of services.
 */
@WebListener
public class Application implements ServletContextListener {
	
	ServletContextListener[] _services = {
		new DBService(),
		new CrawlerService()
	};

	@Override
	public void contextInitialized(ServletContextEvent sce) {
		for (int n = 0, cnt = _services.length; n < cnt; n++) {
			_services[n].contextInitialized(sce);
		}
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		for (int n = _services.length - 1; n > 0; n--) {
			try {
				_services[n].contextInitialized(sce);
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
		}
	}

}
