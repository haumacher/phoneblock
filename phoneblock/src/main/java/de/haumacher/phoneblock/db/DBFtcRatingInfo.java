/*
 * Copyright (c) 2024 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

/**
 * Data class for FTC rating breakdown from the FTC_REPORTS/FTC_SUBJECTS tables.
 */
public class DBFtcRatingInfo {

	private String _rating;
	private int _votes;

	/**
	 * Creates a {@link DBFtcRatingInfo}.
	 */
	public DBFtcRatingInfo(String rating, int votes) {
		_rating = rating;
		_votes = votes;
	}

	/**
	 * The rating category (e.g. "G_FRAUD", "E_ADVERTISING").
	 */
	public String getRating() {
		return _rating;
	}

	/** @see #getRating() */
	public void setRating(String rating) {
		_rating = rating;
	}

	/**
	 * The total number of complaint votes for this rating category.
	 */
	public int getVotes() {
		return _votes;
	}

	/** @see #getVotes() */
	public void setVotes(int votes) {
		_votes = votes;
	}
}
