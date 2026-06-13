/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.render.controller;

import de.haumacher.phoneblock.app.render.RatingDisplay;

/**
 * View model for a single entry on the personal blacklist or whitelist page,
 * carrying the user's own comment and rating along with the community vote
 * counts for the underlying phone number.
 */
public class PersonalListEntry {

	private final String phone;
	private final String comment;
	private final RatingDisplay rating;
	private final int votes;
	private final int votesWildcard;

	public PersonalListEntry(String phone, String comment, RatingDisplay rating, int votes, int votesWildcard) {
		this.phone = phone;
		this.comment = comment;
		this.rating = rating;
		this.votes = votes;
		this.votesWildcard = votesWildcard;
	}

	/** Phone number ID as stored in the database (used to identify the row in form posts). */
	public String getPhone() {
		return phone;
	}

	/** Free-text comment the user attached to their rating, or {@code null}. */
	public String getComment() {
		return comment;
	}

	/** Display helper exposing CSS / icon / label-key for the user's rating, or {@code null}. */
	public RatingDisplay getRating() {
		return rating;
	}

	/** Direct community votes recorded for this exact phone number. */
	public int getVotes() {
		return votes;
	}

	/** Combined community votes including matching wildcard/aggregation blocks. */
	public int getVotesWildcard() {
		return votesWildcard;
	}

}
