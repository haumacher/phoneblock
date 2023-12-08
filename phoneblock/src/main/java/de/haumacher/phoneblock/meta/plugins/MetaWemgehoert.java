/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.meta.plugins;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.crawl.FetchBlockedException;
import de.haumacher.phoneblock.crawl.FetchService;
import de.haumacher.phoneblock.db.model.Rating;
import de.haumacher.phoneblock.db.model.UserComment;

/**
 * Retrieves user comments.
 */
public class MetaWemgehoert extends AbstractMetaSearch {
	
	private static final Logger LOG = LoggerFactory.getLogger(MetaWemgehoert.class);

	private static final String SOURCE = "wemgehoert.de";

	@Override
	public List<UserComment> doFetchComments(String phone) throws FetchBlockedException {
		Document document;
		try {
			document = load("https://www.wemgehoert.de/nummer/" + phone);
		} catch (IOException ex) {
			return notFound(LOG, phone, ex);
		}
		
		DateFormat dateFormat = new SimpleDateFormat("dd. MM. yyyy");
		List<UserComment> result = new ArrayList<>();
		for (Element element : document.select("div.comment-item")) {
			List<Element> ratingText = element.select("p.comment-text");
			if (ratingText.isEmpty()) {
				continue;
			}
			String text = ratingText.get(0).text();
			
			Elements ratingElements = element.select("strong.rank");
			if (ratingElements.isEmpty()) {
				continue;
			}
			Element ratingElement = ratingElements.get(0);
			String rating = ratingElement.attr("class");
			if (rating.isEmpty()) {
				continue;
			}
			
			if (rating.indexOf("r3") >= 0) {
				continue;
			}
			
			String dateString = element.select("span.date").text();
			if (dateString.isEmpty()) {
				LOG.warn("No date: " + phone);
				continue;
			}
			
			Date date;
			try {
				date = dateFormat.parse(dateString);
			} catch (ParseException ex) {
				LOG.warn("Invalid date for " + phone + ": " + dateString);
				continue;
			}
			
			boolean negative;
			if (rating.indexOf("r1") >= 0 || rating.indexOf("r2") >= 0) {
				negative = true;
			} else {
				negative = false;
			}
			
			result.add(UserComment.create()
				.setPhone(phone)
				.setRating(negative ? Rating.B_MISSED : Rating.A_LEGITIMATE)
				.setComment(text)
				.setCreated(date.getTime())
				.setService(getService()));
		}

		return result;
	}
	
	@Override
	protected String getService() {
		return SOURCE;
	}

	/**
	 * Main for debugging only.
	 */
	public static void main(String[] args) {
		long before = System.currentTimeMillis();
		System.out.println(new MetaWemgehoert().setFetcher(new FetchService()).fetchComments("01805266900"));
		System.out.println("Took " + Duration.ofMillis(System.currentTimeMillis() - before).toSeconds() + " seconds.");
	}

}
