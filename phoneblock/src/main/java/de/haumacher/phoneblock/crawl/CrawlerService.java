/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.crawl;

import java.net.MalformedURLException;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import de.haumacher.phoneblock.carddav.resource.AddressResource;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;

/**
 * Thread running the {@link WebCrawler}.
 */
public class CrawlerService implements ServletContextListener {

	private Thread _crawlerThread;
	private WebCrawler _crawler;

	@Override
	public void contextInitialized(ServletContextEvent sce) {
		System.out.println("Starting crawler service.");
		try {
			DB db = DBService.getInstance();
			
			Long notBefore = db.getLastSpamReport();
			
			_crawler = new WebCrawler(notBefore == null ? System.currentTimeMillis() : notBefore.longValue()) {
				@Override
				protected void reportCaller(String caller, int rating, long time) {
					db.processVotes(AddressResource.normalizeNumber(caller), -(rating - 3), time);
				}
			};
			_crawlerThread = new Thread(_crawler);
			_crawlerThread.start();
		} catch (MalformedURLException ex) {
			ex.printStackTrace();
		}
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		System.out.println("Stopping crawler service.");
		if (_crawler != null) {
			_crawler.stop();
		}
		
		if (_crawlerThread != null) {
			_crawlerThread.interrupt();
			_crawlerThread = null;
		}
	}

}
