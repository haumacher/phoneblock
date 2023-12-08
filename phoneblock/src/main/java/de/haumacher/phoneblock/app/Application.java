/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinylog.configuration.Configuration;

import de.haumacher.phoneblock.carddav.resource.AddressBookCache;
import de.haumacher.phoneblock.chatgpt.ChatGPTService;
import de.haumacher.phoneblock.crawl.CrawlerService;
import de.haumacher.phoneblock.crawl.FetchService;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.index.IndexUpdateService;
import de.haumacher.phoneblock.index.google.GoogleUpdateService;
import de.haumacher.phoneblock.index.indexnow.IndexNowUpdateService;
import de.haumacher.phoneblock.jmx.ManagementService;
import de.haumacher.phoneblock.mail.MailServiceStarter;
import de.haumacher.phoneblock.meta.MetaSearchService;
import de.haumacher.phoneblock.scheduler.SchedulerService;

/**
 * Central controller of services.
 */
@WebListener
public class Application implements ServletContextListener {
	
	// Lately initialized, since configuration must be applied before.
	private static Logger LOG;
	
	private ServletContextListener[] _services;
	
	@Override
	public void contextInitialized(ServletContextEvent sce) {
		try {
			File configFile;
			try {
				InitialContext initCtx = new InitialContext();
				Context envCtx = (Context) initCtx.lookup("java:comp/env");
				String fileName = (String) envCtx.lookup("log/configfile");
				
				Properties properties = new Properties();
				configFile = new File(fileName);
				try (FileInputStream in = new FileInputStream(configFile)) {
					properties.load(in);
				}
				Map<String, String> settings = new HashMap<>();
				for (String property : properties.stringPropertyNames()) {
					settings.put(property, properties.getProperty(property));
				}
				Configuration.replace(settings);
			} finally {
				LOG = LoggerFactory.getLogger(Application.class);
			}
			LOG.info("Loaded log configuration from: " + configFile.getAbsolutePath());
		} catch (NamingException ex) {
			LOG.info(ex.getMessage() + ", using default log configuration.");
		} catch (IOException ex) {
			LOG.error("Failed to load log configuration, using default.", ex);
		}
		
		LOG.info("Starting phoneblock application.");
		
		IndexUpdateService indexer;
		SchedulerService scheduler;
		DBService db;
		FetchService fetcher;
		MetaSearchService metaSearch;
		MailServiceStarter mail;
		ChatGPTService gpt;
		_services = new ServletContextListener[] {
			scheduler = new SchedulerService(),
			indexer = IndexUpdateService.async(scheduler, IndexUpdateService.tee(
				new IndexNowUpdateService(),
				new GoogleUpdateService())),
			mail = new MailServiceStarter(),
			db = new DBService(indexer, scheduler, mail),
			fetcher = new FetchService(),
			metaSearch = new MetaSearchService(scheduler, fetcher, indexer),
			new CrawlerService(fetcher, metaSearch),
			gpt = new ChatGPTService(db, scheduler, indexer),
			new ManagementService(indexer, db, gpt),
			new AddressBookCache()
		};
		
		for (int n = 0, cnt = _services.length; n < cnt; n++) {
			_services[n].contextInitialized(sce);
		}
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		LOG.info("Stopping phoneblock application.");
		for (int n = _services.length - 1; n >= 0; n--) {
			try {
				_services[n].contextDestroyed(sce);
			} catch (Throwable ex) {
				LOG.error("Failed to shut down: " + _services[n], ex);
			}
		}
	}

}
