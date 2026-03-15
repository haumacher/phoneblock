/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.mail.check.scraper;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scrapes disposable e-mail domains from Mohmal.
 */
public class MohmalScraper implements DisposableScraper {

	private static final Pattern DOMAIN_PATTERN = Pattern.compile("@([\\w.-]+\\.\\w{2,})");

	@Override
	public String getId() {
		return "mohmal";
	}

	@Override
	public String getUrl() {
		return "https://www.mohmal.com/en";
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
		DisposableScraper.run(new MohmalScraper());
	}

}
