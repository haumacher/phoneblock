/*
 * Copyright (c) 2025 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

/**
 * Database result class holding a phone number with its associated user comment.
 */
public class DBPhoneComment {

	private final String phone;
	private final String comment;

	/**
	 * Creates a {@link DBPhoneComment}.
	 */
	public DBPhoneComment(String phone, String comment) {
		this.phone = phone;
		this.comment = comment;
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

}
