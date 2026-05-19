/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.render.controller;

import java.util.List;

import de.haumacher.phoneblock.db.BlockList;

/**
 * Renders the dedicated page that lets a user manage their personal whitelist.
 */
public class WhitelistController extends PersonalListController {

	public static final String PATH = "/whitelist";

	@Override
	protected List<String> loadEntries(BlockList blocklist, long userId) {
		return blocklist.getWhiteList(userId);
	}

	@Override
	protected String attributeName() {
		return "whitelist";
	}

}
