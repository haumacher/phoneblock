/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.mail.check.scraper;

import java.io.IOException;
import java.util.Calendar;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.mail.check.EmailNormalizer;
import de.haumacher.phoneblock.mail.check.db.Domains;
import de.haumacher.phoneblock.scheduler.SchedulerService;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

/**
 * Service that actively scrapes known disposable e-mail providers to discover
 * their currently offered domains and inserts new ones into the DOMAIN_CHECK table.
 *
 * <p>
 * Runs daily at 05:00, one hour after the passive {@link de.haumacher.phoneblock.mail.check.DisposableListService}.
 * </p>
 */
public class DisposableScraperService implements ServletContextListener {

	private static final Logger LOG = LoggerFactory.getLogger(DisposableScraperService.class);

	private static final List<DisposableScraper> SCRAPERS = List.of(
		new YOPmailScraper(),
		new FakeMailGeneratorScraper(),
		new GuerrillaMailScraper(),
		new MohmalScraper()
	);

	private final SchedulerService _schedulerService;
	private final DBService _dbService;

	private ScheduledFuture<?> _task;

	/**
	 * Creates a {@link DisposableScraperService}.
	 */
	public DisposableScraperService(SchedulerService scheduler, DBService dbService) {
		_schedulerService = scheduler;
		_dbService = dbService;
	}

	@Override
	public void contextInitialized(ServletContextEvent sce) {
		LOG.info("Starting disposable domain scraper service.");

		// Schedule daily at 05:00.
		Calendar firstRun = Calendar.getInstance();
		firstRun.set(Calendar.HOUR_OF_DAY, 5);
		firstRun.set(Calendar.MINUTE, 0);
		firstRun.set(Calendar.SECOND, 0);
		firstRun.set(Calendar.MILLISECOND, 0);

		// Run next day if the schedule for today has already passed.
		Calendar inOneMinute = Calendar.getInstance();
		inOneMinute.add(Calendar.MINUTE, 1);
		if (firstRun.before(inOneMinute)) {
			firstRun.add(Calendar.DAY_OF_MONTH, 1);
		}

		long initialDelay = firstRun.getTimeInMillis() - System.currentTimeMillis();

		_task = _schedulerService.scheduler().scheduleAtFixedRate(
			this::runScrape,
			initialDelay,
			24 * 60 * 60 * 1000L,
			TimeUnit.MILLISECONDS
		);
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		if (_task != null) {
			_task.cancel(false);
		}
	}

	/**
	 * Scrapes all configured providers and inserts newly discovered domains.
	 */
	void runScrape() {
		LOG.info("Starting disposable domain scraping.");

		DB db = _dbService.db();

		for (DisposableScraper scraper : SCRAPERS) {
			try {
				scrapeProvider(db, scraper);
			} catch (Exception ex) {
				LOG.error("Scraping failed for provider '{}': {}", scraper.getId(), ex.getMessage(), ex);
			}
		}

		LOG.info("Disposable domain scraping completed.");
	}

	private void scrapeProvider(DB db, DisposableScraper scraper) throws IOException {
		LOG.info("Scraping provider '{}' from: {}", scraper.getId(), scraper.getUrl());

		Set<String> domains = scraper.fetchDomains();

		if (domains.isEmpty()) {
			LOG.warn("No domains found for provider '{}'.", scraper.getId());
			return;
		}

		long now = System.currentTimeMillis();
		int added = 0;

		try (SqlSession session = db.openSession()) {
			Domains domainMapper = session.getMapper(Domains.class);

			for (String domain : domains) {
				// Skip domains of well-known public providers (e.g. gmail.com).
				if (EmailNormalizer.normalize("test@" + domain) != null) {
					continue;
				}

				// Skip already known domains.
				if (domainMapper.checkDomain(domain) != null) {
					continue;
				}

				domainMapper.insertDomain(domain, true, now, scraper.getId(), null, null);
				added++;
			}

			session.commit();
		}

		LOG.info("Provider '{}': {} domains found, {} new domains added.", scraper.getId(), domains.size(), added);
	}

}
