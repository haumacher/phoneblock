/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.crawl;

import java.io.IOException;
import java.net.MalformedURLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Graze web source for nuisance call sources.
 */
public class WebCrawler extends AbstractWebCrawler {
	
	private static final Pattern STARS_PATTERN = Pattern.compile("stars-(\\d+)");

	SimpleDateFormat _dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm");
	
	private long _notBefore;

	/**
	 * Creates a {@link WebCrawler}.
	 * 
	 * @param url
	 *        The URL to crawl.
	 * @param notBefore
	 *        The time of the latest spam report that has already been processed.
	 */
	public WebCrawler(String url, long notBefore, SpamReporter reporter) throws MalformedURLException {
		super(url, reporter);
		
		_notBefore = notBefore;
	}

	@Override
	protected long tryProcess() throws IOException, ParseException {
		long now = System.currentTimeMillis();
		
		Document document = fetch();
		
		Element comments = document.getElementById("comments");
		Elements rows = comments.getElementsByTag("tbody").get(0).getElementsByTag("tr");
		
		for (Element row : rows) {
			Elements columns = row.getElementsByTag("td");
			
			String caller = columns.get(0).text();
			
			long time = getEntryTime(row);
			if (time <= _notBefore) {
				System.out.println("Skipping: " + caller);
				continue;
			}
			
			if (!caller.startsWith("0")) {
				// A local number without prefix - makes no sense in a shared address book.
				System.out.println("Ignoring: " + caller);
				continue;
			}
			
			String ratingClass = columns.get(1).attr("class");
			Matcher ratingMatcher = STARS_PATTERN.matcher(ratingClass);
			if (ratingMatcher.find()) {
				int rating = Integer.parseInt(ratingMatcher.group(1));
				reportCaller(caller, rating, time);
			}
		}
		
		_notBefore = getEntryTime(rows.first());
		
		long oldest = getEntryTime(rows.last());
		long resultRange = now - oldest;
		
		long delay = Math.min(60 * MINUTES + _rnd.nextInt(10 * MINUTES), resultRange / 2);
		return delay;
	}

	private long getEntryTime(Element resultRow) throws ParseException {
		return _dateFormat.parse(resultRow.getElementsByTag("td").get(4).text()).getTime();
	}

}
