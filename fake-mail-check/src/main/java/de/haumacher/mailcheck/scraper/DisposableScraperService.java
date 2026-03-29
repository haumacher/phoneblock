/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.mailcheck.scraper;

import java.io.IOException;
import java.util.Calendar;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.mailcheck.EmailNormalizer;
import de.haumacher.mailcheck.db.Domains;
import de.haumacher.mailcheck.model.DomainStatus;
import de.haumacher.mailcheck.dns.MxLookup;
import de.haumacher.mailcheck.dns.MxResult;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

/**
 * Service that actively scrapes known disposable e-mail providers to discover
 * their currently offered domains and inserts new ones into the DOMAIN_CHECK table.
 *
 * <p>
 * Runs daily at 05:00, one hour after the passive {@link de.haumacher.mailcheck.DisposableListService}.
 * </p>
 */
public class DisposableScraperService implements ServletContextListener {

	private static final Logger LOG = LoggerFactory.getLogger(DisposableScraperService.class);

	private static final int BATCH_SIZE = 100;

	private static final List<DisposableScraper> SCRAPERS = List.of(
		new YOPmailScraper(),
		new FakeMailGeneratorScraper(),
		new GuerrillaMailScraper(),
		new MohmalScraper(),
		new EmailFakeScraper(),
		new TMailorScraper(),
		new FumailScraper(),
		new PurpleMailScraper()
	);

	private final Supplier<ScheduledExecutorService> _scheduler;
	private final SqlSessionFactory _sessionFactory;

	private ScheduledFuture<?> _task;

	/**
	 * Creates a {@link DisposableScraperService}.
	 *
	 * @param scheduler Supplier for the scheduler (resolved lazily in {@link #contextInitialized}).
	 */
	public DisposableScraperService(Supplier<ScheduledExecutorService> scheduler, SqlSessionFactory sessionFactory) {
		_scheduler = scheduler;
		_sessionFactory = sessionFactory;
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

		_task = _scheduler.get().scheduleAtFixedRate(
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
	public void runScrape() {
		LOG.info("Starting disposable domain scraping.");

		for (DisposableScraper scraper : SCRAPERS) {
			try {
				scrapeProvider(scraper);
			} catch (Exception ex) {
				LOG.error("Scraping failed for provider '{}': {}", scraper.getId(), ex.getMessage(), ex);
			}
		}

		LOG.info("Disposable domain scraping completed.");
	}

	private void scrapeProvider(DisposableScraper scraper) throws IOException {
		LOG.info("Scraping provider '{}' from: {}", scraper.getId(), scraper.getUrl());

		Set<String> domains = scraper.fetchDomains();

		if (domains.isEmpty()) {
			LOG.warn("No domains found for provider '{}'.", scraper.getId());
			return;
		}

		long now = System.currentTimeMillis();
		int added = 0;

		try (SqlSession session = _sessionFactory.openSession()) {
			Domains domainMapper = session.getMapper(Domains.class);

			for (String domain : domains) {
				// Skip domains of well-known public providers (e.g. gmail.com).
				if (EmailNormalizer.toCanonicalPublicAddress("test@" + domain) != null) {
					continue;
				}

				// Skip already known domains.
				if (domainMapper.checkDomain(domain) != null) {
					continue;
				}

				MxResult mx = MxLookup.lookup(domain);
				String mxHost = mx.mxHost() != null ? mx.mxHost() : "-";
				domainMapper.insertDomain(domain, DomainStatus.DISPOSABLE.protocolName(), now, scraper.getId(), mxHost, mx.mxIp());
				LOG.info("New domain: {} (MX: {}, IP: {})", domain, mx.mxHost(), mx.mxIp());
				added++;

				if (added % BATCH_SIZE == 0) {
					session.commit();
					LOG.info("Committed batch ({} domains so far).", added);
				}
			}

			session.commit();
		}

		LOG.info("Provider '{}': {} domains found, {} new domains added.", scraper.getId(), domains.size(), added);
	}

}
