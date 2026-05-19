/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.render.controller;

import java.util.List;

import org.thymeleaf.context.WebContext;

import de.haumacher.phoneblock.app.api.model.Rating;
import de.haumacher.phoneblock.app.render.RatingDisplay;
import de.haumacher.phoneblock.db.BlockList;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Renders the dedicated page that lets a user manage their personal blacklist.
 */
public class BlacklistController extends PersonalListController {

	public static final String PATH = "/blacklist";

	/** Rating choices offered when blocking a number from the web UI. */
	private static final List<RatingDisplay> RATING_CHOICES = List.of(
		new RatingDisplay(Rating.B_MISSED),
		new RatingDisplay(Rating.C_PING),
		new RatingDisplay(Rating.D_POLL),
		new RatingDisplay(Rating.E_ADVERTISING),
		new RatingDisplay(Rating.F_GAMBLE),
		new RatingDisplay(Rating.G_FRAUD));

	@Override
	protected List<String> loadEntries(BlockList blocklist, long userId) {
		return blocklist.getPersonalizations(userId);
	}

	@Override
	protected String attributeName() {
		return "blacklist";
	}

	@Override
	protected void fillContext(WebContext ctx, HttpServletRequest request) {
		super.fillContext(ctx, request);
		request.setAttribute("blacklistRatings", RATING_CHOICES);
	}

}
