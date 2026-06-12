/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.sql.DataSource;

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
	 * Test that version updates are triggered when votes cross the minVisibleVotes threshold,
	 * and that subsequent votes are picked up via recent-activity detection.
	 */
	@Test
	void testVersionUpdateOnThresholdCrossing() {
		// Set minVisibleVotes to 10 (default)
		_db.setMinVisibleVotes(10);

		// Anchor near "now" so the confidence-model values (#338) read fresh —
		// votes here become decoded SPAM_EVIDENCE projected to the request
		// moment; the synthetic time = 1000 the original tests used would
		// decay completely by the time the API reads them back.
		long time = System.currentTimeMillis() - 10_000L;

		// Add votes to a number, crossing the threshold of 10
		// Each call to processVotes adds the specified votes
		for (int i = 0; i < 5; i++) {
			processVotes("0123456789", 2, time++);
		}
		// Now the number has 10 votes, crossing the threshold

		// Assign version to pending updates (lastAssignTime starts at 0, set to time after)
		long assignTime1 = time;
		long version1 = assignVersions(assignTime1);

		// Get the full blocklist
		Blocklist fullList = _db.getBlockListAPI();
		assertEquals(1, fullList.getNumbers().size());
		assertEquals("+49123456789", fullList.getNumbers().get(0).getPhone());
		assertEquals(10, fullList.getNumbers().get(0).getVotes());

		// Add more votes (no threshold crossing, picked up via recent-activity)
		for (int i = 0; i < 5; i++) {
			processVotes("0123456789", 2, time++);
		}
		// Now the number has 20 votes

		long version2 = assignVersions(time);
		assertTrue(version2 > version1, "Version should increment for recently-active number");

		// Get incremental update since version1
		Blocklist update = _db.getBlocklistUpdateAPI(version1);
		assertEquals(1, update.getNumbers().size());
		assertEquals("+49123456789", update.getNumbers().get(0).getPhone());
		assertEquals(20, update.getNumbers().get(0).getVotes());
	}

	/**
	 * Numbers below minVisibleVotes are published (bucket 4 — low-threshold
	 * CardDAV lists need them) but never appear in the API blocklist, whose
	 * server-side filter is minVisibleVotes.
	 */
	@Test
	void testNoVersionUpdateBelowThreshold() {
		// Set minVisibleVotes to 10
		_db.setMinVisibleVotes(10);

		// Anchor near "now" so the confidence-model values (#338) read fresh —
		// votes here become decoded SPAM_EVIDENCE projected to the request
		// moment; the synthetic time = 1000 the original tests used would
		// decay completely by the time the API reads them back.
		long time = System.currentTimeMillis() - 10_000L;

		// Get initial version
		long initialVersion = getCurrentVersion();

		// Add votes that stay below the minVisibleVotes threshold
		processVotes("0111222333", 2, time++); // 2 votes - bucket 2
		processVotes("0111222333", 2, time++); // 4 votes - bucket 4
		processVotes("0111222333", 2, time++); // 6 votes - still bucket 4
		processVotes("0111222333", 2, time++); // 8 votes - still bucket 4

		// Publication happens from bucket 2 upward — the version moves even
		// though the number stays below the API visibility filter.
		long newVersion = assignVersions();
		assertTrue(newVersion > initialVersion, "Bucket flips below minVisibleVotes are published, too");

		// The number must NOT appear in the API blocklist (below minVisibleVotes)
		Blocklist fullList = _db.getBlockListAPI();
		assertTrue(fullList.getNumbers().isEmpty(), "Numbers below minVisibleVotes should not appear in blocklist");

		// Another sweep without bucket movement leaves the version untouched —
		// 8 votes stay in bucket 4.
		assertEquals(newVersion, assignVersions(), "No bucket flip, no version bump");
	}

	/**
	 * Test incremental sync with multiple numbers at different vote levels.
	 */
	@Test
	void testIncrementalSyncMultipleNumbers() {
		_db.setMinVisibleVotes(10);

		// Anchor near "now" so the confidence-model values (#338) read fresh —
		// votes here become decoded SPAM_EVIDENCE projected to the request
		// moment; the synthetic time = 1000 the original tests used would
		// decay completely by the time the API reads them back.
		long time = System.currentTimeMillis() - 10_000L;

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

		// Anchor near "now" so the confidence-model values (#338) read fresh —
		// votes here become decoded SPAM_EVIDENCE projected to the request
		// moment; the synthetic time = 1000 the original tests used would
		// decay completely by the time the API reads them back.
		long time = System.currentTimeMillis() - 10_000L;

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

		// Anchor near "now" so the confidence-model values (#338) read fresh —
		// votes here become decoded SPAM_EVIDENCE projected to the request
		// moment; the synthetic time = 1000 the original tests used would
		// decay completely by the time the API reads them back.
		long time = System.currentTimeMillis() - 10_000L;

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

		// Anchor near "now" so the confidence-model values (#338) read fresh —
		// votes here become decoded SPAM_EVIDENCE projected to the request
		// moment; the synthetic time = 1000 the original tests used would
		// decay completely by the time the API reads them back.
		long time = System.currentTimeMillis() - 10_000L;

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

	/**
	 * Test that numbers falling below the visibility threshold appear in
	 * incremental sync with votes=0 (the removal signal). With the
	 * decay-aware model after #342 there is no separate ACTIVE flag —
	 * raising {@code minVisibleVotes} simulates the natural decay-below-
	 * threshold transition without waiting for time to pass.
	 */
	@Test
	void testArchivedNumbersAppearInIncrementalSync() throws Exception {
		_db.setMinVisibleVotes(10);

		long time = System.currentTimeMillis() - 10_000L;

		// Add a number with 10 votes (above minVisibleVotes = 10)
		for (int i = 0; i < 5; i++) {
			processVotes("0333444555", 2, time++);
		}

		long version1 = assignVersions();

		Blocklist list1 = _db.getBlockListAPI();
		assertEquals(1, list1.getNumbers().size());
		assertEquals("+49333444555", list1.getNumbers().get(0).getPhone());
		assertEquals(10, list1.getNumbers().get(0).getVotes());

		// Simulate full decay: zero out SPAM_EVIDENCE directly so the row's
		// visibility class flips from "above threshold" at the last snapshot
		// to "below threshold" now. The next sweep notices the flip and bumps
		// VERSION; clients on ?since=N see the row with votes=0.
		try (Connection conn = _dataSource.getConnection();
				PreparedStatement stmt = conn.prepareStatement(
					"update NUMBERS set SPAM_EVIDENCE = 0 where PHONE = ?")) {
			stmt.setString(1, "0333444555");
			assertEquals(1, stmt.executeUpdate());
		}

		long version2 = assignVersions();
		assertTrue(version2 > version1, "Version should increment after visibility-class flip");

		Blocklist update = _db.getBlocklistUpdateAPI(version1);
		assertEquals(1, update.getNumbers().size());
		assertEquals("+49333444555", update.getNumbers().get(0).getPhone());
		assertEquals(0, update.getNumbers().get(0).getVotes(),
			"Row that fell below the new threshold appears as a removal (votes=0)");

		Blocklist list2 = _db.getBlockListAPI();
		assertTrue(list2.getNumbers().isEmpty(),
			"Row below current visibility threshold must not appear in full blocklist");
	}

	/**
	 * Votes that stay inside the published bucket cause no re-publication:
	 * no version bump, no incremental-sync traffic. Only crossing a bucket
	 * boundary republishes the number (#342).
	 */
	@Test
	void testNoUpdateWithinBucket() {
		_db.setMinVisibleVotes(10);

		// Anchor near "now" so the confidence-model values (#338) read fresh —
		// votes here become decoded SPAM_EVIDENCE projected to the request
		// moment; the synthetic time = 1000 the original tests used would
		// decay completely by the time the API reads them back.
		long time = System.currentTimeMillis() - 10_000L;

		// Create a number with 10 votes (bucket 10)
		for (int i = 0; i < 5; i++) {
			processVotes("0200300400", 2, time++);
		}

		long version1 = assignVersions(time);
		assertTrue(version1 > DB.INITIAL_BLOCKLIST_VERSION);

		// Add 2 more votes (12 total — still bucket 10, next boundary is 20)
		processVotes("0200300400", 2, time++);

		long version2 = assignVersions(time);
		assertEquals(version1, version2, "Votes inside the bucket must not republish");

		Blocklist update = _db.getBlocklistUpdateAPI(version1);
		assertTrue(update.getNumbers().isEmpty(), "No bucket flip, no incremental entry");

		// Published votes stay at the frozen bucket floor.
		Blocklist fullList = _db.getBlockListAPI();
		assertEquals(1, fullList.getNumbers().size());
		assertEquals(10, fullList.getNumbers().get(0).getVotes(), "Published votes are the bucket floor");

		// Crossing the next boundary (20) republishes.
		for (int i = 0; i < 4; i++) {
			processVotes("0200300400", 2, time++);
		}
		long version3 = assignVersions(time);
		assertTrue(version3 > version2, "Bucket flip must republish");

		Blocklist update2 = _db.getBlocklistUpdateAPI(version2);
		assertEquals(1, update2.getNumbers().size());
		assertEquals("+49200300400", update2.getNumbers().get(0).getPhone());
		assertEquals(20, update2.getNumbers().get(0).getVotes(), "Votes are the new bucket floor");
		assertTrue(update2.getNumbers().get(0).getLastActivity() > 0, "lastActivity should be non-zero");
	}

	/**
	 * Test that lastActivity is consistent between full sync and incremental sync.
	 */
	@Test
	void testLastActivityInFullAndIncrementalSync() {
		_db.setMinVisibleVotes(10);

		// Anchor near "now" so the confidence-model values (#338) read fresh —
		// votes here become decoded SPAM_EVIDENCE projected to the request
		// moment; the synthetic time = 1000 the original tests used would
		// decay completely by the time the API reads them back.
		long time = System.currentTimeMillis() - 10_000L;

		// Create a number with 10 votes
		for (int i = 0; i < 5; i++) {
			processVotes("0300400500", 2, time++);
		}

		long version1 = assignVersions();

		// Full sync
		Blocklist fullList = _db.getBlockListAPI();
		assertEquals(1, fullList.getNumbers().size());
		long fullLastActivity = fullList.getNumbers().get(0).getLastActivity();
		assertTrue(fullLastActivity > 0, "Full sync should have non-zero lastActivity");

		// Incremental sync since before the version
		Blocklist update = _db.getBlocklistUpdateAPI(DB.INITIAL_BLOCKLIST_VERSION);
		assertEquals(1, update.getNumbers().size());
		long incLastActivity = update.getNumbers().get(0).getLastActivity();

		assertEquals(fullLastActivity, incLastActivity, "Full and incremental sync should return the same lastActivity");
	}

	private void processVotes(String phone, int votes, long time) {
		_db.processVotes(NumberAnalyzer.analyze(phone, "+49"), "+49", votes, time);
	}

	/**
	 * Runs a publication sweep (simulates BlocklistVersionService). Uses
	 * {@code System.currentTimeMillis()} as the sweep moment so the projected
	 * bucket thresholds in {@link DB#maxRawSpamAt} stay finite.
	 */
	private long assignVersions() {
		return assignVersions(System.currentTimeMillis());
	}

	/**
	 * Runs a publication sweep using the given timestamp as "now".
	 */
	private long assignVersions(long now) {
		return _db.publishBlocklist(now);
	}

	private long getCurrentVersion() {
		return _db.getBlocklistVersion();
	}
}
