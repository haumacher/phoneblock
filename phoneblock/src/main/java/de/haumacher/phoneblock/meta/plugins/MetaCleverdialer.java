/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.meta.plugins;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.crawl.FetchService;
import de.haumacher.phoneblock.db.model.Rating;
import de.haumacher.phoneblock.db.model.UserComment;

/**
 * Retrieves user comments.
 */
public class MetaCleverdialer extends AbstractMetaSearch {

	private static final Logger LOG = LoggerFactory.getLogger(MetaCleverdialer.class);
	
	private static final String SOURCE = "cleverdialer.de";
	
	@Override
	public List<UserComment> fetchComments(String phone) throws Throwable {
		Document document = load("https://www.cleverdialer.de/telefonnummer/" + phone);
		DateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");
		List<UserComment> result = new ArrayList<>();
		Element commentsList = document.select("table.recent-comments").first();
		if (commentsList != null) {
			for (Element element : commentsList.select("tbody > tr")) {
				List<Element> ratingText = element.select("td.comment");
				if (ratingText.isEmpty()) {
					LOG.warn("No rating text.");
					continue;
				}
				String text = ratingText.get(0).text();
				
				Elements ratingElements = element.select("td.stars > span");
				if (ratingElements.isEmpty()) {
					continue;
				}
				
				String rating = ratingElements.first().attr("class");
				if (rating.isEmpty()) {
					LOG.warn("No rating class: " + ratingText);
					continue;
				}
				
				if (rating.equals("stars-3")) {
					continue;
				}
				
				String dateString = element.select("td.date-time").text().trim();
				if (dateString.isEmpty()) {
					LOG.warn("No date: " + ratingText);
					continue;
				}
				Date date = dateFormat.parse(dateString);
				
				boolean negative;
				if (rating.equals("stars-1") || rating.equals("stars-2")) {
					negative = true;
				} else {
					negative = false;
				}
				
				result.add(UserComment.create()
					.setPhone(phone)
					.setRating(negative ? Rating.B_MISSED : Rating.A_LEGITIMATE)
					.setComment(text)
					.setCreated(date.getTime())
					.setService(SOURCE));
			}
		}

		return result;
	}
	
	/**
	 * Main for debugging only.
	 */
	public static void main(String[] args) throws Throwable {
		System.out.println(new MetaCleverdialer().setFetcher(new FetchService()).fetchComments("015212000519"));
	}

}
