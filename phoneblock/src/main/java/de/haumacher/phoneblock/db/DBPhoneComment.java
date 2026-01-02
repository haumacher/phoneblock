/*
 * Copyright (c) 2025 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import de.haumacher.phoneblock.app.api.model.Rating;

/**
 * Database result class holding a phone number with its associated user comment and rating.
 */
public class DBPhoneComment {

	private final String phone;
	private final String comment;
	private final Rating rating;

	/**
	 * Creates a {@link DBPhoneComment}.
	 */
	public DBPhoneComment(String phone, String comment, Rating rating) {
		this.phone = phone;
		this.comment = comment;
		this.rating = rating;
	}

	/**
	 * The phone number.
	 */
	public String getPhone() {
		return phone;
	}

	/**
	 * The user's comment for this phone number (may be null).
	 */
	public String getComment() {
		return comment;
	}

	/**
	 * The user's rating for this phone number (may be null).
	 */
	public Rating getRating() {
		return rating;
	}

}
