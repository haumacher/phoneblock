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
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import de.haumacher.phoneblock.crawl.FetchService;
import de.haumacher.phoneblock.db.model.Rating;
import de.haumacher.phoneblock.db.model.UserComment;

/**
 * Retrieves user comments.
 */
public class MetaAnruferBewertung extends AbstractMetaSearch {

	private static final String SOURCE = "anrufer-bewertung.de";

	@Override
	public List<UserComment> fetchComments(String phone) throws Throwable {
		Document document = load("https://www.anrufer-bewertung.de/" + phone);
		DateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");
		List<UserComment> result = new ArrayList<>();
		for (Element element : document.selectXpath("//div[@class='mainproducttext']")) {
			List<TextNode> ratingText = element.selectXpath("small[1]/following-sibling::br[1]/following-sibling::text()[1]", TextNode.class);
			if (ratingText.isEmpty()) {
				continue;
			}
			String text = ratingText.get(0).text();
			
			Elements ratingElements = element.selectXpath("div[1]");
			if (ratingElements.isEmpty()) {
				continue;
			}
			
			Elements dateElements = element.select("small");
			if (dateElements.isEmpty()) {
				continue;
			}
			String dateText = dateElements.first().text().trim();
			if (!dateText.startsWith("(Datum: ")) {
				continue;
			}
			Date date = dateFormat.parse(dateText.substring("(Datum: ".length()));
			
			Element ratingElement = ratingElements.get(0);
			String style = ratingElement.attr("style");
			if (style.isEmpty()) {
				continue;
			}
			
			if (style.indexOf("background-position: -40px") >= 0) {
				continue;
			}
			
			boolean negative;
			if (style.indexOf("background-position: -80px") >= 0) {
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
		System.out.println(new MetaAnruferBewertung().setFetcher(new FetchService()).fetchComments("01805266900"));
	}

}
