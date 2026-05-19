/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.render.controller;

import java.util.List;

import de.haumacher.phoneblock.db.BlockList;

/**
 * Renders the dedicated page that lets a user manage their personal blacklist.
 */
public class BlacklistController extends PersonalListController {

	public static final String PATH = "/blacklist";

	@Override
	protected List<String> loadEntries(BlockList blocklist, long userId) {
		return blocklist.getPersonalizations(userId);
	}

	@Override
	protected String attributeName() {
		return "blacklist";
	}

}
