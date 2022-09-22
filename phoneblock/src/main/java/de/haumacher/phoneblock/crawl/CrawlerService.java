/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.crawl;

import java.io.IOException;
import java.util.Properties;

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
			Properties properties = new Properties();
			properties.load(CrawlerService.class.getResourceAsStream("/phoneblock.properties"));
			String url = properties.getProperty("crawler.url");
			if (url == null || url.isEmpty() || url.startsWith("${")) {
				System.out.println("No crawler URL configured, skipping.");
				return;
			}
			
			DB db = DBService.getInstance();
			
			Long notBefore = db.getLastSpamReport();
			
			SpamReporter reporter = new SpamReporter() {
				@Override
				public void reportCaller(String caller, int rating, long time) {
					System.out.println(fmt(20, caller) + " " + "x*****".substring(rating));
					
					db.processVotes(AddressResource.normalizeNumber(caller), -(rating - 3), time);
				}
				
				private String fmt(int cols, String str) {
					StringBuilder result = new StringBuilder(str);
					while (result.length() < cols) {
						result.append(' ');
					}
					return result.toString();
				}

			};
			
			_crawler = new WebCrawler(url, notBefore == null ? System.currentTimeMillis() : notBefore.longValue(), reporter);
			_crawlerThread = new Thread(_crawler);
			_crawlerThread.start();
		} catch (IOException ex) {
			System.out.println("Failed to start crawler service.");
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
