/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.answerbot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import de.haumacher.phoneblock.app.api.model.AccountSettings;

/**
 * Test for the resolution of the blocking options of a self-hosted {@link AnswerBot}: local
 * configuration first, then the owner's PhoneBlock account settings, then the defaults (#570).
 */
class TestBlockingOptions {

	@Test
	void accountSettingsApplyWhenNotConfiguredLocally() {
		CustomerConfig user = new CustomerConfig();
		AccountSettings account = AccountSettings.create().setMinVotes(2).setWildcards(true);

		assertTrue(AnswerBot.needsAccountSettings(user));
		assertEquals(2, AnswerBot.effectiveMinVotes(user, account));
		assertTrue(AnswerBot.effectiveWildcard(user, account));
	}

	@Test
	void localConfigurationOverridesAccountSettings() {
		CustomerConfig user = new CustomerConfig();
		user.setMinVotes(10);
		user.setWildcard(false);
		AccountSettings account = AccountSettings.create().setMinVotes(2).setWildcards(true);

		assertFalse(AnswerBot.needsAccountSettings(user));
		assertEquals(10, AnswerBot.effectiveMinVotes(user, account));
		assertFalse(AnswerBot.effectiveWildcard(user, account));
	}

	@Test
	void partialLocalConfigurationStillNeedsAccountSettings() {
		CustomerConfig user = new CustomerConfig();
		user.setMinVotes(10);
		AccountSettings account = AccountSettings.create().setMinVotes(2).setWildcards(true);

		assertTrue(AnswerBot.needsAccountSettings(user));
		assertEquals(10, AnswerBot.effectiveMinVotes(user, account));
		assertTrue(AnswerBot.effectiveWildcard(user, account));
	}

	@Test
	void defaultsWithoutAccountSettings() {
		CustomerConfig user = new CustomerConfig();

		assertEquals(AnswerBot.DEFAULT_MIN_VOTES, AnswerBot.effectiveMinVotes(user, null));
		assertEquals(AnswerBot.DEFAULT_WILDCARD, AnswerBot.effectiveWildcard(user, null));
	}

	@Test
	void legacyServerWithoutMinVotesFallsBackToDefault() {
		// An older server answers /api/account without the minVotes field, which the model reads as 0.
		CustomerConfig user = new CustomerConfig();
		AccountSettings account = AccountSettings.create().setMinVotes(0).setWildcards(true);

		assertEquals(AnswerBot.DEFAULT_MIN_VOTES, AnswerBot.effectiveMinVotes(user, account));
		assertTrue(AnswerBot.effectiveWildcard(user, account));
	}
}
