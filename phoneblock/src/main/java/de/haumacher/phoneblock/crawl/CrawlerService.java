/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.crawl;

import java.net.MalformedURLException;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;

/**
 * Thread running the {@link WebCrawler}.
 */
public class CrawlerService implements ServletContextListener {

	private Thread _crawlerThread;

	@Override
	public void contextInitialized(ServletContextEvent sce) {
		try {
			DB db = DBService.getInstance();
			
			long notBefore = db.getLastSpamReport();
			
			_crawlerThread = new Thread(new WebCrawler(notBefore) {
				@Override
				protected void reportCaller(String caller, int rating, long time) {
					db.addSpam(caller, -(rating - 3), time);
				}
			});
			_crawlerThread.start();
		} catch (MalformedURLException ex) {
			ex.printStackTrace();
		}
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		if (_crawlerThread != null) {
			_crawlerThread.interrupt();
			_crawlerThread = null;
		}
	}

}
