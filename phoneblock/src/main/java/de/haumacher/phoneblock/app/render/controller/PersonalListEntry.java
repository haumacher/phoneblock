/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.render.controller;

import de.haumacher.phoneblock.app.render.RatingDisplay;

/**
 * View model for a single entry on the personal blacklist or whitelist page,
 * carrying the user's own comment and rating in addition to the phone number.
 */
public class PersonalListEntry {

	private final String phone;
	private final String comment;
	private final RatingDisplay rating;

	public PersonalListEntry(String phone, String comment, RatingDisplay rating) {
		this.phone = phone;
		this.comment = comment;
		this.rating = rating;
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

}
