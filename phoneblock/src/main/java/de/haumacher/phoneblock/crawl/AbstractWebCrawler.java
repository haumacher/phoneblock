/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.crawl;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for crawler implementations that search the web for spam reports.
 */
public abstract class AbstractWebCrawler implements Runnable {

	private static final Logger LOG = LoggerFactory.getLogger(AbstractWebCrawler.class);

	protected static final int MINUTES = 1000 * 60;
	
	private URL _url;

	private boolean _stopped;

	private SpamReporter _reporter;

	private FetchService _fetcher;
	
	/**
	 * Creates a {@link AbstractWebCrawler}.
	 *
	 * @param url The URL to fetch.
	 * @param reporter The sink of generated spam reports. 
	 */
	public AbstractWebCrawler(FetchService fetcher, String url, SpamReporter reporter) throws MalformedURLException {
		_fetcher = fetcher;
		_reporter = reporter;
		_url = new URL(url);
	}

	@Override
	public void run() {
		while (true) {
			if (isStopped()) {
				LOG.info("Stopping crawler.");
				return;
			}
			
			long delay = process();
			
			LOG.info("Crawler sleeping for " + (delay / MINUTES) + " minutes.");
			try {
				Thread.sleep(delay);
			} catch (InterruptedException ex) {
				LOG.info("Stopping crawler.");
				return;
			}
		}
	}
	
	/**
	 * Fetches web page and analyzes it.
	 *
	 * @return The number of milliseconds to wait before the next fetch should be performed.
	 */
	public long process() {
		try {
			return tryProcess();
		} catch (Throwable ex) {
			LOG.error("Failed to crawl.", ex);

			return 20 * MINUTES;
		}
	}
	
	/** 
	 * Implementation of {@link #process()}.
	 */
	protected abstract long tryProcess() throws Throwable;

	/** 
	 * Fetches contents from the configured URL.
	 */
	protected Document fetch() throws IOException, FetchBlockedException {
		return _fetcher.fetch(_url);
	}

	/** 
	 * Requests to stop.
	 */
	public synchronized void stop() {
		_stopped = true;
		notifyAll();
	}

	/** 
	 * Whether this instance has been {@link #isStopped() stopped}.
	 */
	public synchronized boolean isStopped() {
		return _stopped;
	}

	/** 
	 * Forwards the spam report to the configured {@link SpamReporter}.
	 */
	protected void reportCaller(String caller, int rating, long time) {
		_reporter.reportCaller(caller, rating, time);
	}

}
