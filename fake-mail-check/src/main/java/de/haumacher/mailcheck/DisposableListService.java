/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.mailcheck;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.mailcheck.db.Domains;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

/**
 * Service that daily imports the community-maintained disposable e-mail domain
 * blocklist from GitHub and inserts new domains into the DOMAIN_CHECK table.
 *
 * <p>
 * Source: <a href="https://github.com/disposable-email-domains/disposable-email-domains">
 * disposable-email-domains</a>
 * </p>
 *
 * <p>
 * The service uses HTTP ETag caching to avoid re-downloading when the list has
 * not changed. Already known domains are skipped so that manual overrides in the
 * database are preserved.
 * </p>
 */
public class DisposableListService implements ServletContextListener {

	private static final Logger LOG = LoggerFactory.getLogger(DisposableListService.class);

	private static final String SOURCE_SYSTEM = "disposable-list";

	private static final String BLOCKLIST_URL =
		"https://raw.githubusercontent.com/disposable-email-domains/disposable-email-domains/refs/heads/main/disposable_email_blocklist.conf";

	/** Property key in the PROPERTIES table for caching the HTTP ETag. */
	private static final String PROPERTY_ETAG = "disposable-list.etag";

	private final ScheduledExecutorService _scheduler;
	private final SqlSessionFactory _sessionFactory;
	private final PropertyStore _propertyStore;

	private ScheduledFuture<?> _task;

	/**
	 * Creates a {@link DisposableListService}.
	 */
	public DisposableListService(ScheduledExecutorService scheduler, SqlSessionFactory sessionFactory, PropertyStore propertyStore) {
		_scheduler = scheduler;
		_sessionFactory = sessionFactory;
		_propertyStore = propertyStore;
	}

	@Override
	public void contextInitialized(ServletContextEvent sce) {
		LOG.info("Starting disposable domain list import service.");

		// Schedule daily at 04:00.
		Calendar firstRun = Calendar.getInstance();
		firstRun.set(Calendar.HOUR_OF_DAY, 4);
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

		_task = _scheduler.scheduleAtFixedRate(
			this::runImport,
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
	 * Downloads the disposable domain blocklist and inserts new domains.
	 */
	public void runImport() {
		LOG.info("Starting disposable domain list import.");

		try {
			// Load cached ETag.
			String lastEtag = _propertyStore.getProperty(PROPERTY_ETAG);

			HttpURLConnection connection = (HttpURLConnection) URI.create(BLOCKLIST_URL).toURL().openConnection();
			connection.setConnectTimeout(30_000);
			connection.setReadTimeout(60_000);
			if (lastEtag != null) {
				connection.setRequestProperty("If-None-Match", lastEtag);
			}

			int responseCode = connection.getResponseCode();

			if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
				LOG.info("Disposable domain list unchanged (304 Not Modified).");
				return;
			}

			if (responseCode != HttpURLConnection.HTTP_OK) {
				LOG.warn("Failed to download disposable domain list: HTTP {}", responseCode);
				return;
			}

			// Read and store the new ETag.
			String newEtag = connection.getHeaderField("ETag");

			long now = System.currentTimeMillis();
			int added = 0;

			try (SqlSession session = _sessionFactory.openSession()) {
				Domains domains = session.getMapper(Domains.class);

				try (InputStream in = connection.getInputStream();
					 BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {

					String line;
					while ((line = reader.readLine()) != null) {
						line = line.trim();

						// Skip empty lines and comments.
						if (line.isEmpty() || line.startsWith("#")) {
							continue;
						}

						String domain = line.toLowerCase();

						// Skip already known domains.
						if (domains.checkDomain(domain) != null) {
							continue;
						}

						domains.insertDomain(domain, true, now, SOURCE_SYSTEM, null, null);
						added++;
					}
				}

				session.commit();
			}

			// Persist ETag.
			if (newEtag != null) {
				_propertyStore.setProperty(PROPERTY_ETAG, newEtag);
			}

			LOG.info("Disposable domain list import completed. New domains added: {}", added);
		} catch (Exception ex) {
			LOG.error("Disposable domain list import failed.", ex);
		}
	}
}
