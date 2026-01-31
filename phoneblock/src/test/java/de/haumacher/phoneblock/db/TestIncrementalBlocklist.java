/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.haumacher.phoneblock.analysis.NumberAnalyzer;
import de.haumacher.phoneblock.app.api.model.BlockListEntry;
import de.haumacher.phoneblock.app.api.model.Blocklist;
import de.haumacher.phoneblock.scheduler.SchedulerService;

/**
 * Test cases for incremental blocklist synchronization.
 */
public class TestIncrementalBlocklist {

	private DB _db;
	private SchedulerService _scheduler;
	private DataSource _dataSource;

	@BeforeEach
	public void setUp() throws Exception {
		_scheduler = new SchedulerService();
		_scheduler.contextInitialized(null);

		_dataSource = TestDB.createTestDataSource();
		_db = new DB(_dataSource, _scheduler);
	}

	@AfterEach
	public void tearDown() throws Exception {
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
	 * Test that version updates are triggered when votes cross the minVisibleVotes threshold.
	 */
	@Test
	void testVersionUpdateOnThresholdCrossing() {
		// Set minVisibleVotes to 10 (default)
		_db.setMinVisibleVotes(10);

		long time = 1000;

		// Add votes to a number, crossing the threshold of 10
		// Each call to processVotes adds the specified votes
		for (int i = 0; i < 5; i++) {
			processVotes("0123456789", 2, time++);
		}
		// Now the number has 10 votes, crossing the threshold

		// Assign version to pending updates
		long version1 = assignVersions();

		// Get the full blocklist
		Blocklist fullList = _db.getBlockListAPI();
		assertEquals(1, fullList.getNumbers().size());
		assertEquals("+49123456789", fullList.getNumbers().get(0).getPhone());
		assertEquals(10, fullList.getNumbers().get(0).getVotes());

		// Add more votes to cross the 20 threshold
		for (int i = 0; i < 5; i++) {
			processVotes("0123456789", 2, time++);
		}
		// Now the number has 20 votes

		long version2 = assignVersions();
		assertTrue(version2 > version1, "Version should increment after threshold crossing");

		// Get incremental update since version1
		Blocklist update = _db.getBlocklistUpdateAPI(version1);
		assertEquals(1, update.getNumbers().size());
		assertEquals("+49123456789", update.getNumbers().get(0).getPhone());
		assertEquals(20, update.getNumbers().get(0).getVotes());
	}

	/**
	 * Test that changes below minVisibleVotes do NOT trigger version updates.
	 */
	@Test
	void testNoVersionUpdateBelowThreshold() {
		// Set minVisibleVotes to 10
		_db.setMinVisibleVotes(10);

		long time = 1000;

		// Get initial version
		long initialVersion = getCurrentVersion();

		// Add votes that stay below the minVisibleVotes threshold
		// This crosses thresholds 2 and 4, but not 10
		processVotes("0111222333", 2, time++); // 2 votes - crosses threshold 2
		processVotes("0111222333", 2, time++); // 4 votes - crosses threshold 4
		processVotes("0111222333", 2, time++); // 6 votes - no threshold crossing
		processVotes("0111222333", 2, time++); // 8 votes - no threshold crossing

		// Assign versions - should NOT assign any since all crossings are below minVisibleVotes=10
		long newVersion = assignVersions();

		// Version should not have changed
		assertEquals(initialVersion, newVersion, "Version should not change for crossings below minVisibleVotes");

		// The number should NOT appear in the blocklist (below minVisibleVotes)
		Blocklist fullList = _db.getBlockListAPI();
		assertTrue(fullList.getNumbers().isEmpty(), "Numbers below minVisibleVotes should not appear in blocklist");
	}

	/**
	 * Test incremental sync with multiple numbers at different vote levels.
	 */
	@Test
	void testIncrementalSyncMultipleNumbers() {
		_db.setMinVisibleVotes(10);

		long time = 1000;

		// Number A: will cross threshold 10
		for (int i = 0; i < 5; i++) {
			processVotes("0100000001", 2, time++);
		}

		// Number B: stays below threshold (8 votes)
		for (int i = 0; i < 4; i++) {
			processVotes("0100000002", 2, time++);
		}

		// Number C: will cross threshold 20
		for (int i = 0; i < 10; i++) {
			processVotes("0100000003", 2, time++);
		}

		long version1 = assignVersions();

		// Check full blocklist - should have A and C, not B
		Blocklist fullList = _db.getBlockListAPI();
		Set<String> phones = fullList.getNumbers().stream()
			.map(BlockListEntry::getPhone)
			.collect(Collectors.toSet());

		assertEquals(2, phones.size());
		assertTrue(phones.contains("+49100000001"), "Number A should be in blocklist");
		assertFalse(phones.contains("+49100000002"), "Number B should NOT be in blocklist (below threshold)");
		assertTrue(phones.contains("+49100000003"), "Number C should be in blocklist");

		// Now add more votes to B to cross threshold 10
		processVotes("0100000002", 2, time++); // Now at 10 votes

		long version2 = assignVersions();
		assertTrue(version2 > version1);

		// Incremental update should only show B
		Blocklist update = _db.getBlocklistUpdateAPI(version1);
		assertEquals(1, update.getNumbers().size());
		assertEquals("+49100000002", update.getNumbers().get(0).getPhone());
		assertEquals(10, update.getNumbers().get(0).getVotes());
	}

	/**
	 * Test that numbers dropping below minVisibleVotes are returned as deletions (votes=0).
	 */
	@Test
	void testDeletionWhenDroppingBelowThreshold() {
		_db.setMinVisibleVotes(10);

		long time = 1000;

		// Add a number with 10 votes
		for (int i = 0; i < 5; i++) {
			processVotes("0555666777", 2, time++);
		}

		long version1 = assignVersions();

		// Verify number is in blocklist
		Blocklist list1 = _db.getBlockListAPI();
		assertEquals(1, list1.getNumbers().size());
		assertEquals(10, list1.getNumbers().get(0).getVotes());

		// Remove votes to drop below threshold
		processVotes("0555666777", -2, time++); // Now at 8 votes

		long version2 = assignVersions();
		assertTrue(version2 > version1);

		// Incremental update should show the number as deleted (votes=0)
		Blocklist update = _db.getBlocklistUpdateAPI(version1);
		assertEquals(1, update.getNumbers().size());
		assertEquals("+49555666777", update.getNumbers().get(0).getPhone());
		assertEquals(0, update.getNumbers().get(0).getVotes(), "Number below threshold should be returned with votes=0");

		// Full blocklist should no longer include this number
		Blocklist list2 = _db.getBlockListAPI();
		assertTrue(list2.getNumbers().isEmpty());
	}

	/**
	 * Test with different minVisibleVotes settings.
	 */
	@Test
	void testDifferentMinVisibleVotes() {
		// Set a lower threshold
		_db.setMinVisibleVotes(4);

		long time = 1000;

		// Add 4 votes - should cross threshold 4
		processVotes("0999888777", 2, time++);
		processVotes("0999888777", 2, time++);

		long version = assignVersions();
		assertTrue(version > DB.INITIAL_BLOCKLIST_VERSION);

		// Should appear in blocklist with minVotes=4
		Blocklist list = _db.getBlockListAPI();
		assertEquals(1, list.getNumbers().size());
		assertEquals(4, list.getNumbers().get(0).getVotes());
	}

	/**
	 * Test empty incremental update when no changes occurred.
	 */
	@Test
	void testEmptyIncrementalUpdate() {
		_db.setMinVisibleVotes(10);

		long time = 1000;

		// Add a number
		for (int i = 0; i < 5; i++) {
			processVotes("0777888999", 2, time++);
		}

		long version1 = assignVersions();

		// Request update with current version - should be empty
		Blocklist update = _db.getBlocklistUpdateAPI(version1);
		assertTrue(update.getNumbers().isEmpty(), "No changes since current version");
		assertEquals(version1, update.getVersion());
	}

	private void processVotes(String phone, int votes, long time) {
		_db.processVotes(NumberAnalyzer.analyze(phone, "+49"), "+49", votes, time);
	}

	/**
	 * Assigns version numbers to pending updates (simulates BlocklistVersionService).
	 */
	private long assignVersions() {
		try (SqlSession session = _db.openSession()) {
			Users users = session.getMapper(Users.class);
			SpamReports reports = session.getMapper(SpamReports.class);

			String versionStr = users.getProperty("blocklist.version");
			long currentVersion = (versionStr != null) ? Long.parseLong(versionStr) : DB.INITIAL_BLOCKLIST_VERSION;

			int updated = reports.assignVersionToPendingUpdates(currentVersion + 1);

			if (updated > 0) {
				long newVersion = currentVersion + 1;
				if (versionStr != null) {
					users.updateProperty("blocklist.version", String.valueOf(newVersion));
				} else {
					users.addProperty("blocklist.version", String.valueOf(newVersion));
				}
				session.commit();
				return newVersion;
			}

			return currentVersion;
		}
	}

	private long getCurrentVersion() {
		try (SqlSession session = _db.openSession()) {
			Users users = session.getMapper(Users.class);
			String versionStr = users.getProperty("blocklist.version");
			return (versionStr != null) ? Long.parseLong(versionStr) : DB.INITIAL_BLOCKLIST_VERSION;
		}
	}
}
