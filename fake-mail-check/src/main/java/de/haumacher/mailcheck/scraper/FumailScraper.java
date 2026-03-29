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
 * Scrapes disposable e-mail domains from fumail.co.
 *
 * <p>
 * Domains are listed in a {@code <select>} dropdown as
 * {@code <option value="domain.tld">domain.tld</option>} elements.
 * </p>
 */
public class FumailScraper implements DisposableScraper {

	/** Matches domain names inside {@code <option>} value attributes. */
	private static final Pattern OPTION_PATTERN =
		Pattern.compile("<option[^>]*value\\s*=\\s*\"([\\w.-]+\\.\\w{2,})\"");

	@Override
	public String getId() {
		return "fumail";
	}

	@Override
	public String getUrl() {
		return "https://fumail.co";
	}

	@Override
	public Set<String> scrape(String pageContent) {
		Set<String> domains = new HashSet<>();
		Matcher matcher = OPTION_PATTERN.matcher(pageContent);
		while (matcher.find()) {
			domains.add(matcher.group(1).toLowerCase().trim());
		}
		return domains;
	}

	public static void main(String[] args) throws IOException {
		DisposableScraper.run(new FumailScraper());
	}

}
