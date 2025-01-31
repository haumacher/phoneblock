/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.crawl;

import java.io.IOException;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.analysis.NumberAnalyzer;
import de.haumacher.phoneblock.app.api.model.PhoneNumer;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.meta.MetaSearchService;

/**
 * Thread running the {@link WebCrawler}.
 */
public class CrawlerService implements ServletContextListener {

	private static final Logger LOG = LoggerFactory.getLogger(CrawlerService.class);

	private Thread _crawlerThread;
	private WebCrawler _crawler;

	private FetchService _fetcher;

	private MetaSearchService _metaSearch;

	/** 
	 * Creates a {@link CrawlerService}.
	 */
	public CrawlerService(FetchService fetcher, MetaSearchService metaSearch) {
		_fetcher = fetcher;
		_metaSearch = metaSearch;
	}

	@Override
	public void contextInitialized(ServletContextEvent sce) {
		LOG.info("Starting crawler service.");
		
		try {
			String url = lookupCrawlerUrl();
			if (url == null || url.isEmpty() || url.startsWith("${")) {
				LOG.warn("No crawler URL configured, skipping.");
				return;
			}
			
			DB db = DBService.getInstance();
			
			Long notBefore = db.getLastSpamReport();
			
			SpamReporter reporter = new SpamReporter() {
				@Override
				public void reportCaller(String phoneText, int rating, long time) {
					LOG.info(fmt(20, phoneText) + " " + "x*****".substring(rating));
					
					PhoneNumer number = NumberAnalyzer.parsePhoneNumber(phoneText);
					if (number == null) {
						return;
					}

					int votes = -(rating - 3);
					_metaSearch.scheduleMetaSearch(votes, time, number);
				}
				
				private String fmt(int cols, String str) {
					StringBuilder result = new StringBuilder(str);
					while (result.length() < cols) {
						result.append(' ');
					}
					return result.toString();
				}

			};
			
			_crawler = new WebCrawler(_fetcher, url, notBefore == null ? System.currentTimeMillis() : notBefore.longValue(), reporter);
			_crawlerThread = new Thread(_crawler);
			_crawlerThread.start();
		} catch (IOException ex) {
			LOG.error("Failed to start crawler service.", ex);
		}
	}

	private String lookupCrawlerUrl() {
		try {
			InitialContext initCtx = new InitialContext();
			Context envCtx = (Context) initCtx.lookup("java:comp/env");
			
			return (String) envCtx.lookup("crawler/url");
		} catch (NamingException ex) {
			LOG.info(ex.getMessage());
		}

		return null;
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		LOG.info("Stopping crawler service.");
		if (_crawler != null) {
			_crawler.stop();
		}
		
		if (_crawlerThread != null) {
			_crawlerThread.interrupt();
			_crawlerThread = null;
		}
	}

}
