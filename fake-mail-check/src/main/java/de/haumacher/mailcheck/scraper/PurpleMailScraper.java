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
 * Scrapes disposable e-mail domains from PurpleMail / neweymail.com.
 *
 * <p>
 * Domains are listed as plain text on the page, e.g.
 * {@code neweymail.com neweymail.net easyemail.cc ...}.
 * The pattern matches domain-like strings that appear in the page body.
 * </p>
 */
public class PurpleMailScraper implements DisposableScraper {

	/**
	 * Matches domain names that appear as standalone text (not inside URLs or attributes).
	 * Looks for domains preceded by whitespace or newline.
	 */
	private static final Pattern DOMAIN_PATTERN =
		Pattern.compile("(?:^|[\\s>])([a-z0-9][\\w.-]*\\.(?:com|net|cc|pro|email|org|io))(?=[\\s<]|$)");

	/** Known host domain to exclude from results. */
	private static final String HOST_DOMAIN = "neweymail.com";

	@Override
	public String getId() {
		return "purplemail";
	}

	@Override
	public String getUrl() {
		return "https://purplemail.neweymail.com";
	}

	@Override
	public Set<String> scrape(String pageContent) {
		Set<String> domains = new HashSet<>();
		Matcher matcher = DOMAIN_PATTERN.matcher(pageContent.toLowerCase());
		while (matcher.find()) {
			String domain = matcher.group(1).trim();
			// Skip common non-email domains that may appear in page markup.
			if (domain.contains("googleapis.com") || domain.contains("gstatic.com")
				|| domain.contains("cloudflare") || domain.contains("jsdelivr")
				|| domain.contains("bootstrap") || domain.contains("fontawesome")) {
				continue;
			}
			domains.add(domain);
		}
		return domains;
	}

	public static void main(String[] args) throws IOException {
		DisposableScraper.run(new PurpleMailScraper());
	}

}
