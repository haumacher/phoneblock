/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.mail.check.scraper;

import java.util.Set;

/**
 * A scraper that extracts disposable e-mail domains from a provider's web page.
 */
public interface DisposableScraper {

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

}
