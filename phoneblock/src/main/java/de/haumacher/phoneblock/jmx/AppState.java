/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.jmx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.ab.SipService;
import de.haumacher.phoneblock.chatgpt.ChatGPTService;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.index.IndexUpdateService;

/**
 * {@link AppStateMBean} implementation.
 */
public class AppState implements AppStateMBean {
	
	private static final Logger LOG = LoggerFactory.getLogger(SipService.class);

	private IndexUpdateService _updater;
	private DB _db;
	private ChatGPTService _gpt;
	private SipService _sip;

	/** 
	 * Creates a {@link AppState}.
	 */
	public AppState(IndexUpdateService updater, DB db, ChatGPTService gpt, SipService sip) {
		_updater = updater;
		_db = db;
		_gpt = gpt;
		_sip = sip;
	}

	@Override
	public int getUsers() {
		return _db.getUsers();
	}

	@Override
	public int getInactiveUsers() {
		return _db.getInactiveUsers();
	}

	@Override
	public int getVotes() {
		return _db.getVotes();
	}

	@Override
	public int getRatings() {
		return _db.getRatings();
	}

	@Override
	public int getSearches() {
		return _db.getSearches();
	}
	
	@Override
	public int getActiveNumbers() {
		return _db.getActiveReportCount();
	}
	
	@Override
	public int getArchivedNumbers() {
		return _db.getArchivedReportCount();
	}
	
	@Override
	public void triggerIndexUpdate(String path) {
		_updater.publishPathUpdate(path);
	}
	
	@Override
	public void triggerWelcomeMails() {
		_db.sendWelcomeMails();
	}

	@Override
	public void triggerServiceMails() {
		_db.sendSupportMails();
	}
	
	@Override
	public void triggerSummaryCreation(String phone) {
		for (String number : phone.split(",")) {
			_gpt.createSummary(number.trim());
		}
	}
	
	@Override
	public void triggerAnswerBotRegistration(String userName, boolean enabled) {
		try {
			_sip.enableAnwserBot(userName, enabled);
		} catch (Exception ex) {
			LOG.error("Failed to change answer bot state for user '" + userName + "'.", ex);
			throw new RuntimeException("Failed to change answer bot state for user '" + userName + "': " + ex.getMessage());
		}
	}
	
}
