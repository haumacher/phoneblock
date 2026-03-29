/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.mailcheck.scraper;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scrapes disposable e-mail domains from TMailor / temp-mail.io.
 *
 * <p>
 * Domains are fetched from the public JSON API at
 * {@code https://api.internal.temp-mail.io/api/v3/domains} which returns
 * entries like {@code {"name":"bltiwd.com","type":"public",...}}.
 * </p>
 */
public class TMailorScraper implements DisposableScraper {

	/** Matches the {@code "name"} field values in the JSON response. */
	private static final Pattern DOMAIN_PATTERN =
		Pattern.compile("\"name\"\\s*:\\s*\"([\\w.-]+\\.\\w{2,})\"");

	@Override
	public String getId() {
		return "tmailor";
	}

	@Override
	public String getUrl() {
		return "https://api.internal.temp-mail.io/api/v3/domains";
	}

	@Override
	public Set<String> scrape(String pageContent) {
		Set<String> domains = new HashSet<>();
		Matcher matcher = DOMAIN_PATTERN.matcher(pageContent);
		while (matcher.find()) {
			domains.add(matcher.group(1).toLowerCase().trim());
		}
		return domains;
	}

	public static void main(String[] args) throws IOException {
		DisposableScraper.run(new TMailorScraper());
	}

}
