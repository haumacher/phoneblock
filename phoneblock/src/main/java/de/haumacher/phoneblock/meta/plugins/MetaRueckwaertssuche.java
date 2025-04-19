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
import org.jsoup.nodes.TextNode;
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
public class MetaRueckwaertssuche extends AbstractMetaSearch {

	private static final Logger LOG = LoggerFactory.getLogger(MetaRueckwaertssuche.class);
	private static final String SOURCE = "rueckwaertssuche.de";

	@Override
	public List<UserComment> doFetchComments(String phone) throws FetchBlockedException {
		Document document;
		try {
			document = load("https://www.rueckwaertssuche-telefonbuch.de/" + phone);
		} catch (IOException ex) {
			return notFound(LOG, phone, ex);
		}
		
		DateFormat dateFormat = new SimpleDateFormat("ss.MM.yyyy");
		List<UserComment> result = new ArrayList<>();
		{
			for (Element element : document.select("div.anruf")) {
				Elements description = element.select("div.desc");
				if (description.isEmpty()) {
					LOG.warn("No description: " + phone);
					continue;
				}
				
				List<TextNode> textNodes = description.first().textNodes();
				if (textNodes.isEmpty()) {
					LOG.warn("No text: " + phone);
					continue;
				}
				
				String text = textNodes.stream().map(n -> n.text()).collect(Collectors.joining()).trim();
				if (text.isEmpty()) {
					LOG.warn("No text content: " + phone);
					continue;
				}
				
				String ratingText = element.select("h2 > span.calltype").text();
				if (ratingText.isEmpty()) {
					LOG.warn("No rating class: " + phone);
					continue;
				}
				
				String dateString = element.select("div.count").text().trim();
				if (dateString.isEmpty()) {
					LOG.warn("No date: " + phone);
					continue;
				}
				
				int index = dateString.indexOf("Eintrag vom ");
				if (index < 0) {
					LOG.warn("No date: " + phone);
					continue;
				}
				
				dateString = dateString.substring(index + "Eintrag vom ".length());
				
				Date date;
				try {
					date = dateFormat.parse(dateString);
				} catch (ParseException ex) {
					LOG.warn("Invalid date for " + phone + ": " + dateString);
					continue;
				}
				
				Rating rating;
				switch (ratingText) {
				case "Seriöse Nummer":
					rating = Rating.A_LEGITIMATE;
					break;
					
				case "Telefonterror":
					rating = Rating.C_PING;
					break;
					
				case "Meinungsumfrage":
					rating = Rating.D_POLL;
					break;
					
				case "Aggressive Werbung":
				case "Telemarketing":
					rating = Rating.E_ADVERTISING;
					break;
					
				case "Gewinnspiel":
					rating = Rating.F_GAMBLE;
					break;
					
				case "Inkasso":
				case "Kostenfalle":
					rating = Rating.G_FRAUD;
					break;
					
				case "Sonstiges":
				case "Unbekannt":
					continue;
					
				default: 
					LOG.warn("Unknown rating for " + phone + ": " + ratingText);
					continue;
				}
				
				
				result.add(UserComment.create()
					.setPhone(phone)
					.setRating(rating)
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
		System.out.println(new MetaRueckwaertssuche().setFetcher(new FetchService()).fetchComments("015783349220").stream().map(x -> x.toString()).collect(Collectors.joining("\n")));
		System.out.println("Took " + Duration.ofMillis(System.currentTimeMillis() - before).toSeconds() + " seconds.");
	}

}
