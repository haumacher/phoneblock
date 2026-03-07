/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Collections;
import java.util.Set;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.haumacher.phoneblock.analysis.NumberAnalyzer;
import de.haumacher.phoneblock.app.api.model.PhoneNumer;
import de.haumacher.phoneblock.app.api.model.Rating;
import de.haumacher.phoneblock.app.api.model.SearchResult;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.TestDB;
import de.haumacher.phoneblock.scheduler.SchedulerService;

/**
 * Test that details are suppressed for positively-rated numbers with few votes to protect personal contacts.
 */
class TestSearchPrivacy {

	private static final String PHONE = "004930123456";
	private static final String DIAL_PREFIX = "+49";
	private static final Set<String> LANGS = Collections.singleton("de");

	private DB _db;
	private SchedulerService _scheduler;
	private DataSource _dataSource;

	@BeforeEach
	void setUp() throws Exception {
		_scheduler = new SchedulerService();
		_scheduler.contextInitialized(null);

		_dataSource = TestDB.createTestDataSource();
		_db = new DB(_dataSource, _scheduler);
	}

	@AfterEach
	void tearDown() throws Exception {
		_db.shutdown();
		_db = null;

		try (Connection connection = _dataSource.getConnection()) {
			try (Statement statement = connection.createStatement()) {
				statement.execute("SHUTDOWN");
			}
		}

		_scheduler.contextDestroyed(null);
		_scheduler = null;
	}

	/**
	 * A number with fewer than {@link DB#MIN_LEGITIMATE} positive ratings should have its details suppressed.
	 */
	@Test
	void testFewLegitimateRatingsSuppressDetails() {
		long time = 1000;

		// Create users and add A_LEGITIMATE ratings (fewer than MIN_LEGITIMATE).
		for (int i = 0; i < DB.MIN_LEGITIMATE - 1; i++) {
			String user = "user-" + i;
			_db.createUser(user, "User " + i, "de", DIAL_PREFIX);
			addRating(user, PHONE, Rating.A_LEGITIMATE, "Mein Freund " + i, time++);
		}

		PhoneNumer number = NumberAnalyzer.analyze(PHONE, DIAL_PREFIX);
		try (SqlSession session = _db.openSession()) {
			SearchResult result = SearchServlet.analyzeDb(_db, session, number, DIAL_PREFIX, false, LANGS);

			assertTrue(result.getComments().isEmpty(), "Comments should be suppressed for few legitimate ratings");
			assertNull(result.getAiSummary(), "AI summary should be suppressed for few legitimate ratings");
			assertTrue(result.getSearches().isEmpty(), "Searches should be suppressed for few legitimate ratings");
			assertTrue(result.getRatings().isEmpty(), "Ratings map should be empty for few legitimate ratings");
		}
	}

	/**
	 * A number with at least {@link DB#MIN_LEGITIMATE} positive ratings should show full details.
	 */
	@Test
	void testEnoughLegitimateRatingsShowDetails() {
		long time = 1000;

		// Create users and add exactly MIN_LEGITIMATE A_LEGITIMATE ratings.
		for (int i = 0; i < DB.MIN_LEGITIMATE; i++) {
			String user = "user-" + i;
			_db.createUser(user, "User " + i, "de", DIAL_PREFIX);
			addRating(user, PHONE, Rating.A_LEGITIMATE, "Serioeser Anrufer " + i, time++);
		}

		PhoneNumer number = NumberAnalyzer.analyze(PHONE, DIAL_PREFIX);
		try (SqlSession session = _db.openSession()) {
			SearchResult result = SearchServlet.analyzeDb(_db, session, number, DIAL_PREFIX, false, LANGS);

			assertFalse(result.getComments().isEmpty(), "Comments should be shown when enough legitimate ratings exist");
			assertFalse(result.getRatings().isEmpty(), "Ratings map should be populated when enough legitimate ratings exist");
		}
	}

	/**
	 * A spam number (positive votes) should always show full details regardless of legitimate rating count.
	 */
	@Test
	void testSpamNumberAlwaysShowsDetails() {
		long time = 1000;

		// Add spam ratings to make votes positive.
		_db.createUser("spam-reporter-1", "Reporter 1", "de", DIAL_PREFIX);
		addRating("spam-reporter-1", PHONE, Rating.G_FRAUD, "Betrug!", time++);
		_db.createUser("spam-reporter-2", "Reporter 2", "de", DIAL_PREFIX);
		addRating("spam-reporter-2", PHONE, Rating.G_FRAUD, "Abzocke!", time++);

		// Also add one legitimate rating (fewer than MIN_LEGITIMATE), but net votes remain positive.
		_db.createUser("legit-user", "Legit", "de", DIAL_PREFIX);
		addRating("legit-user", PHONE, Rating.A_LEGITIMATE, "Kenne ich", time++);

		PhoneNumer number = NumberAnalyzer.analyze(PHONE, DIAL_PREFIX);
		try (SqlSession session = _db.openSession()) {
			SearchResult result = SearchServlet.analyzeDb(_db, session, number, DIAL_PREFIX, false, LANGS);

			assertFalse(result.getRatings().isEmpty(), "Ratings should be shown for spam numbers");
			assertFalse(result.getComments().isEmpty(), "Comments should be shown for spam numbers");
		}
	}

	private void addRating(String userName, String phoneId, Rating rating, String comment, long now) {
		_db.addRating(userName, NumberAnalyzer.analyze(phoneId, DIAL_PREFIX), DIAL_PREFIX, rating, comment, "de", now);
	}
}
