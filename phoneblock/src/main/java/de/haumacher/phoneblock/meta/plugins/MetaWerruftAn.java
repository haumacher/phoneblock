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
import java.util.stream.Collectors;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.app.api.model.Rating;
import de.haumacher.phoneblock.app.api.model.UserComment;
import de.haumacher.phoneblock.crawl.FetchBlockedException;
import de.haumacher.phoneblock.crawl.FetchService;

/**
 * Retrieves user comments.
 */
public class MetaWerruftAn extends AbstractMetaSearch {

	private static final Logger LOG = LoggerFactory.getLogger(MetaWerruftAn.class);
	private static final String SOURCE = "wer-ruftan.de";

	@Override
	public List<UserComment> doFetchComments(String phone) throws FetchBlockedException {
		Document document;
		try {
			document = load("https://wer-ruftan.de/Nummer/" + phone);
		} catch (IOException ex) {
			return notFound(LOG, phone, ex);
		}
		
		DateFormat dateFormat = new SimpleDateFormat("ss/MM/yyyy");
		List<UserComment> result = new ArrayList<>();
		{
			for (Element element : document.getElementsByClass("numberTable")) {
				Elements tds = element.select("tbody > tr > td");
				if (tds.size() < 3) {
					continue;
				}
				
				List<Element> commentElements = element.select("p.numberDescShort");
				if (commentElements.isEmpty()) {
					LOG.warn("No comment text: " + phone);
					continue;
				}
				String text = commentElements.get(0).text();
				
				List<Element> ratingElements = tds.get(1).select("span.numberSign");
				if (ratingElements.isEmpty()) {
					continue;
				}
				
				String rating = ratingElements.get(0).attr("class");
				if (rating.isEmpty()) {
					LOG.warn("No rating class: " + phone);
					continue;
				}
				
				String dateString = tds.get(0).select("span").text().trim();
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
				if (rating.contains("redButton") || rating.contains("orangeButton")) {
					negative = true;
				} else if (rating.contains("greenButton")) {
					negative = false;
				} else {
					continue;
				}
				
				result.add(UserComment.create()
					.setPhone(phone)
					.setRating(negative ? Rating.B_MISSED : Rating.A_LEGITIMATE)
					.setComment(text)
					.setLang("de")
					.setCreated(date.getTime())
					.setService(getService()));
			}
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
		System.out.println(new MetaWerruftAn().setFetcher(new FetchService()).fetchComments("01805266900").stream().map(x -> x.toString()).collect(Collectors.joining("\n")));
		System.out.println("Took " + Duration.ofMillis(System.currentTimeMillis() - before).toSeconds() + " seconds.");
	}

}
