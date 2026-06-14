/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.render.controller;

/**
 * View model for a single personal wildcard-prefix block on the blacklist page (#377).
 */
public class WildcardEntry {

	private final String phone;
	private final String display;

	public WildcardEntry(String phone, String display) {
		this.phone = phone;
		this.display = display;
	}

	/** Phone-ID prefix as stored in the database (used to identify the row in form posts). */
	public String getPhone() {
		return phone;
	}

	/** The prefix in international format, shown to the user (a trailing {@code *} is added in the view). */
	public String getDisplay() {
		return display;
	}

}
