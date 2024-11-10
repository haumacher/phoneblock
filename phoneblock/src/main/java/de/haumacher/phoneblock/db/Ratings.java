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
        return switch (rating) {
            case A_LEGITIMATE -> -1;
            case B_MISSED, G_FRAUD, C_PING, D_POLL, E_ADVERTISING, F_GAMBLE -> 1;
        };
    }

	/**
	 * The label to display this {@link Ratings}.
	 */
	public static String getLabel(de.haumacher.phoneblock.db.model.Rating rating) {
        return switch (rating) {
            case A_LEGITIMATE -> "Seriös";
            case B_MISSED -> "Unbekannt";
            case C_PING -> "Ping-Anruf";
            case D_POLL -> "Umfrage";
            case E_ADVERTISING -> "Werbung";
            case F_GAMBLE -> "Gewinnspiel";
            case G_FRAUD -> "Betrug";
        };
    }

	/**
	 * The CSS class for visualizing the given rating.
	 */
	public static String getCssClass(de.haumacher.phoneblock.db.model.Rating rating) {
        return switch (rating) {
            case A_LEGITIMATE -> "is-legitimate";
            case B_MISSED -> "is-missed";
            case C_PING -> "is-ping";
            case D_POLL -> "is-poll";
            case E_ADVERTISING -> "is-advertising";
            case F_GAMBLE -> "is-gamble";
            case G_FRAUD -> "is-fraud";
        };
    }

	/**
	 * The RGB color values to display the given rating in a chart.
	 */
	public static String getRGB(de.haumacher.phoneblock.db.model.Rating rating) {
        return switch (rating) {
            case A_LEGITIMATE -> "72, 199, 142";
            case B_MISSED -> "170, 172, 170";
            case C_PING -> "31, 94, 220";
            case D_POLL -> "157, 31, 220";
            case E_ADVERTISING -> "241, 207, 70";
            case F_GAMBLE -> "241, 122, 70";
            case G_FRAUD -> "241, 70, 104";
        };
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
