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

import de.haumacher.phoneblock.crawl.FetchService;
import de.haumacher.phoneblock.db.model.Rating;
import de.haumacher.phoneblock.db.model.UserComment;

/**
 * Retrieves user comments.
 */
public class MetaWemgehoert extends AbstractMetaSearch {

	private static final String SOURCE = "wemgehoert.de";

	@Override
	public List<UserComment> fetchComments(String phone) throws Throwable {
		Document document = load("https://www.wemgehoert.de/nummer/" + phone);
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
			
			Date date = dateFormat.parse(element.select("span.date").text());
			
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
				.setService(SOURCE));
		}

		return result;
	}
	
	/**
	 * Main for debugging only.
	 */
	public static void main(String[] args) throws Throwable {
		System.out.println(new MetaWemgehoert().setFetcher(new FetchService()).fetchComments("01805266900"));
	}

}
