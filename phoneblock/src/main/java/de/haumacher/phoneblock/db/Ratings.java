/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import de.haumacher.phoneblock.db.model.RatingInfo;

/**
 * Utilities for {@link de.haumacher.phoneblock.db.model.Rating}s.
 */
public class Ratings {

	/** 
	 * The number of votes the given {@link Ratings} is worth.
	 */
	public static int getVotes(de.haumacher.phoneblock.db.model.Rating rating) {
		switch (rating) {
			case A_LEGITIMATE: return -2; 
			case B_MISSED: return 2; 
			case C_PING: return 2; 
			case D_POLL: return 2; 
			case E_ADVERTISING: return 2; 
			case F_GAMBLE: return 2; 
			case G_FRAUD: return 4;
		}
		throw new IllegalArgumentException("No such rating: " + rating);
	}

	/**
	 * The label to display this {@link Ratings}.
	 */
	public static String getLabel(de.haumacher.phoneblock.db.model.Rating rating) {
		switch (rating) {
			case A_LEGITIMATE: return "Seriös"; 
			case B_MISSED: return "Unbekannt"; 
			case C_PING: return "Ping-Anruf"; 
			case D_POLL: return "Umfrage"; 
			case E_ADVERTISING: return "Werbung"; 
			case F_GAMBLE: return "Gewinnspiel"; 
			case G_FRAUD: return "Betrug";
		}
		throw new IllegalArgumentException("No such rating: " + rating);
	}

	/**
	 * The CSS class for visualizing the given rating.
	 */
	public static String getCssClass(de.haumacher.phoneblock.db.model.Rating rating) {
		switch (rating) {
			case A_LEGITIMATE: return "is-legitimate"; 
			case B_MISSED: return "is-missed";
			case C_PING: return "is-ping"; 
			case D_POLL: return "is-poll"; 
			case E_ADVERTISING: return "is-advertising"; 
			case F_GAMBLE: return "is-gamble"; 
			case G_FRAUD: return "is-fraud";
		}
		throw new IllegalArgumentException("No such rating: " + rating);
	}

	/**
	 * The RGB color values to display the given rating in a chart.
	 */
	public static String getRGB(de.haumacher.phoneblock.db.model.Rating rating) {
		switch (rating) {
		case A_LEGITIMATE: return "72, 199, 142"; 
		case B_MISSED: return "170, 172, 170";
		case C_PING: return "31, 94, 220"; 
		case D_POLL: return "157, 31, 220"; 
		case E_ADVERTISING: return "241, 207, 70"; 
		case F_GAMBLE: return "241, 122, 70"; 
		case G_FRAUD: return "241, 70, 104";
		}
		throw new IllegalArgumentException("No such rating: " + rating);
	}

	/**
	 * Comparator function for {@link RatingInfo}s.
	 */
	public static int compare(RatingInfo i1, RatingInfo i2) {
		int voteCompare = Integer.compare(i1.getVotes(), i2.getVotes());
		if (voteCompare != 0) {
			return voteCompare;
		}
		return Integer.compare(i1.getRating().ordinal(), i2.getRating().ordinal());
	}
}
