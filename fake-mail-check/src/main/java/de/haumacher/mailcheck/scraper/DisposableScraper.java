/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.mailcheck.scraper;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * A scraper that extracts disposable e-mail domains from a provider's web page.
 */
public interface DisposableScraper {

	/** User-Agent header sent with HTTP requests. */
	String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

	/**
	 * Stable identifier used as SOURCE_SYSTEM in DOMAIN_CHECK.
	 */
	String getId();

	/**
	 * The URL to fetch.
	 */
	String getUrl();

	/**
	 * Extracts domain names from the given page content.
	 *
	 * @param pageContent The HTML content of the provider page.
	 * @return A set of domain names (lowercase, trimmed).
	 */
	Set<String> scrape(String pageContent);

	/**
	 * Fetches the provider page and extracts domains.
	 */
	default Set<String> fetchDomains() throws IOException {
		HttpURLConnection connection = (HttpURLConnection) URI.create(getUrl()).toURL().openConnection();
		connection.setConnectTimeout(30_000);
		connection.setReadTimeout(60_000);
		connection.setRequestProperty("User-Agent", USER_AGENT);

		int responseCode = connection.getResponseCode();
		if (responseCode != HttpURLConnection.HTTP_OK) {
			throw new IOException("HTTP " + responseCode + " from " + getUrl());
		}

		String body;
		try (InputStream in = connection.getInputStream()) {
			body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}

		return scrape(body);
	}

	/**
	 * Runs the given scraper and prints the discovered domains to stdout.
	 */
	static void run(DisposableScraper scraper) throws IOException {
		Set<String> domains = scraper.fetchDomains();
		domains.stream().sorted().forEach(System.out::println);
		System.out.println("Total: " + domains.size());
	}

}
