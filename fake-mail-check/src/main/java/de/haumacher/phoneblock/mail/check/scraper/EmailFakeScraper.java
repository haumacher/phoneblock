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
 * Scrapes disposable e-mail domains from emailfake.com.
 *
 * <p>
 * Domains are listed in the dropdown as
 * {@code <p onclick="change_dropdown_list(this.innerHTML)" id="domain.tld">domain.tld</p>}
 * elements.
 * </p>
 */
public class EmailFakeScraper implements DisposableScraper {

	private static final Pattern DOMAIN_PATTERN =
		Pattern.compile("change_dropdown_list\\(this\\.innerHTML\\)\"\\s+id=\"([\\w.-]+\\.\\w{2,})\"");

	@Override
	public String getId() {
		return "emailfake";
	}

	@Override
	public String getUrl() {
		return "https://emailfake.com";
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
		DisposableScraper.run(new EmailFakeScraper());
	}

}
