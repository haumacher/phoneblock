/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.diag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.haumacher.phoneblock.diag.SegmentedLogReader.PollResult;

/**
 * Tests the tinylog-segment tailer against a temp directory of fake segments.
 */
public class TestSegmentedLogReader {

	@TempDir
	Path dir;

	private Path base() {
		return dir.resolve("phoneblock.log");
	}

	private void writeSegment(long count, String content) throws IOException {
		Files.writeString(dir.resolve("phoneblock.log." + count), content, StandardCharsets.UTF_8);
	}

	private void append(long count, String content) throws IOException {
		Path p = dir.resolve("phoneblock.log." + count);
		Files.writeString(p, Files.readString(p) + content, StandardCharsets.UTF_8);
	}

	@Test
	public void testFreshReadIgnoresLatestAndPartialLine() throws IOException {
		Files.writeString(base(), "SHOULD BE IGNORED (latest copy)\n");
		writeSegment(1, "a1\na2\n");
		writeSegment(2, "b1\nb2\n");
		writeSegment(3, "c1\npartial-no-newline"); // active segment, trailing partial

		SegmentedLogReader reader = new SegmentedLogReader(base());
		PollResult r = reader.poll(-1, 0, 1000);

		assertEquals(List.of("a1", "a2", "b1", "b2", "c1"), r.lines());
		assertEquals(3, r.segment());
		assertFalse(r.moreAvailable());
		assertFalse(r.gapDetected());

		// The partial line is consumed only once it is completed.
		append(3, "-now-complete\n");
		PollResult r2 = reader.poll(r.segment(), r.offset(), 1000);
		assertEquals(List.of("partial-no-newline-now-complete"), r2.lines());
	}

	@Test
	public void testMaxLinesResume() throws IOException {
		writeSegment(1, "a1\na2\n");
		writeSegment(2, "b1\nb2\n");

		SegmentedLogReader reader = new SegmentedLogReader(base());
		PollResult r1 = reader.poll(-1, 0, 3);
		assertEquals(List.of("a1", "a2", "b1"), r1.lines());
		assertTrue(r1.moreAvailable());

		PollResult r2 = reader.poll(r1.segment(), r1.offset(), 3);
		assertEquals(List.of("b2"), r2.lines());
		assertFalse(r2.moreAvailable());
	}

	@Test
	public void testRotationAdvancesToNewSegment() throws IOException {
		writeSegment(1, "a1\n");
		writeSegment(2, "b1\n"); // active

		SegmentedLogReader reader = new SegmentedLogReader(base());
		PollResult r1 = reader.poll(-1, 0, 1000);
		assertEquals(List.of("a1", "b1"), r1.lines());
		assertEquals(2, r1.segment());

		// Rotation: a new active segment .3 appears; .2 is now complete.
		writeSegment(3, "c1\nc2\n");
		PollResult r2 = reader.poll(r1.segment(), r1.offset(), 1000);
		assertEquals(List.of("c1", "c2"), r2.lines());
		assertEquals(3, r2.segment());
	}

	@Test
	public void testGapWhenSegmentPruned() throws IOException {
		// Cursor sits on segment 1, but retention has pruned everything below 5.
		writeSegment(5, "e1\ne2\n");
		writeSegment(6, "f1\n");

		SegmentedLogReader reader = new SegmentedLogReader(base());
		PollResult r = reader.poll(1, 40, 1000);
		assertTrue(r.gapDetected());
		assertEquals(List.of("e1", "e2", "f1"), r.lines());
		assertEquals(6, r.segment());
	}

	@Test
	public void testNothingNewWhenCaughtUp() throws IOException {
		writeSegment(1, "a1\n");
		SegmentedLogReader reader = new SegmentedLogReader(base());
		PollResult r1 = reader.poll(-1, 0, 1000);
		PollResult r2 = reader.poll(r1.segment(), r1.offset(), 1000);
		assertTrue(r2.lines().isEmpty());
		assertFalse(r2.moreAvailable());
	}
}
