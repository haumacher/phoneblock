/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.diag;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tails tinylog's rolling log segments {@code <base>.<count>} (see
 * {@code writer.file = ….{count}} in {@code tinylog.properties}).
 *
 * <p>The {@code {count}} is monotonically increasing and each rolled segment is
 * immutable, so rotation detection needs no inode heuristics: the cursor is
 * {@code (segment, byteOffset)}, and a higher count simply appearing means the
 * current segment is complete. The fixed-name {@code latest} copy ({@code <base>}
 * with no suffix) is ignored — it duplicates the active segment and would
 * double-count.</p>
 *
 * <p>Crash-safety and bounds:</p>
 * <ul>
 *   <li>A fresh cursor ({@code segment &lt; 0}) starts at the oldest retained
 *       segment, so history is ingested once.</li>
 *   <li>If the cursor's segment has been pruned (reader fell &gt; {@code backups}
 *       behind), {@link PollResult#gapDetected()} is set and reading resumes at
 *       the oldest surviving segment.</li>
 *   <li>A trailing partial line (no {@code '\n'} yet) in the active segment is
 *       left unconsumed, so half-written lines are never ingested.</li>
 *   <li>{@code maxLines} bounds one poll; {@link PollResult#moreAvailable()}
 *       tells the caller to poll again.</li>
 * </ul>
 */
public class SegmentedLogReader {

	private final Path _dir;
	private final Pattern _segmentPattern;

	/**
	 * @param baseLogFile the {@code writer.latest} path, e.g.
	 *        {@code /var/log/tomcat10/phoneblock.log}; segments are its siblings
	 *        {@code phoneblock.log.<count>}.
	 */
	public SegmentedLogReader(Path baseLogFile) {
		Path parent = baseLogFile.getParent();
		_dir = parent != null ? parent : Path.of(".");
		String baseName = baseLogFile.getFileName().toString();
		_segmentPattern = Pattern.compile(Pattern.quote(baseName) + "\\.(\\d+)");
	}

	/** The outcome of one {@link #poll} call. */
	public record PollResult(List<String> lines, long segment, long offset,
			boolean gapDetected, boolean moreAvailable) {}

	/**
	 * Reads up to {@code maxLines} complete lines starting at {@code (fromSegment,
	 * fromOffset)} ({@code fromSegment < 0} = fresh start).
	 */
	public PollResult poll(long fromSegment, long fromOffset, int maxLines) throws IOException {
		TreeMap<Long, Path> segments = discoverSegments();
		if (segments.isEmpty()) {
			return new PollResult(List.of(), fromSegment, fromOffset, false, false);
		}

		long minCount = segments.firstKey();
		long maxCount = segments.lastKey();

		long seg;
		long off;
		boolean gap = false;
		if (fromSegment < 0) {
			seg = minCount;
			off = 0;
		} else if (segments.containsKey(fromSegment)) {
			seg = fromSegment;
			off = fromOffset;
		} else if (fromSegment < minCount) {
			// The cursor's segment has been pruned — resume from the oldest kept.
			gap = true;
			seg = minCount;
			off = 0;
		} else {
			// Cursor is at/after the newest known segment with nothing to read.
			return new PollResult(List.of(), fromSegment, fromOffset, false, false);
		}

		List<String> lines = new ArrayList<>();
		long curSeg = seg;
		long curOff = off;

		for (Long count : new ArrayList<>(segments.tailMap(seg).keySet())) {
			Path path = segments.get(count);
			long startOff = (count == seg) ? off : 0;
			SegmentRead read = readLines(path, startOff, maxLines - lines.size());
			lines.addAll(read.lines());

			if (read.hitLimit()) {
				// Stopped mid-segment on the line budget; resume here next poll.
				return new PollResult(lines, count, read.endOffset(), gap, true);
			}

			if (count == maxCount) {
				// Active segment fully drained to its last complete line.
				curSeg = count;
				curOff = read.endOffset();
			} else {
				// Rolled segment done; next byte to read is the next segment's start.
				Long next = segments.higherKey(count);
				curSeg = next != null ? next : count;
				curOff = 0;
			}
		}

		return new PollResult(lines, curSeg, curOff, gap, false);
	}

	private TreeMap<Long, Path> discoverSegments() throws IOException {
		TreeMap<Long, Path> result = new TreeMap<>();
		if (!Files.isDirectory(_dir)) {
			return result;
		}
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(_dir)) {
			for (Path p : stream) {
				Matcher m = _segmentPattern.matcher(p.getFileName().toString());
				if (m.matches()) {
					try {
						result.put(Long.parseLong(m.group(1)), p);
					} catch (NumberFormatException ex) {
						// Count larger than a long — not a tinylog segment we wrote.
					}
				}
			}
		}
		return result;
	}

	private record SegmentRead(List<String> lines, long endOffset, boolean hitLimit) {}

	/**
	 * Reads complete ({@code '\n'}-terminated) lines from {@code path} starting at
	 * {@code startOffset}, up to {@code maxLines}. {@code endOffset} is the byte
	 * position just past the last consumed newline (a trailing partial line is not
	 * consumed).
	 */
	private static SegmentRead readLines(Path path, long startOffset, int maxLines) throws IOException {
		List<String> out = new ArrayList<>();
		if (maxLines <= 0) {
			return new SegmentRead(out, startOffset, true);
		}
		try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
			long length = raf.length();
			if (startOffset >= length) {
				return new SegmentRead(out, startOffset, false);
			}
			raf.seek(startOffset);
			int avail = (int) Math.min(length - startOffset, Integer.MAX_VALUE);
			byte[] buf = new byte[avail];
			raf.readFully(buf);

			int i = 0;
			long endOffset = startOffset;
			while (i < buf.length) {
				int nl = indexOf(buf, (byte) '\n', i);
				if (nl < 0) {
					// Partial trailing line — leave it for the next poll.
					break;
				}
				int end = nl;
				if (end > i && buf[end - 1] == '\r') {
					end--;
				}
				out.add(new String(buf, i, end - i, StandardCharsets.UTF_8));
				i = nl + 1;
				endOffset = startOffset + i;
				if (out.size() >= maxLines) {
					boolean more = i < buf.length;
					return new SegmentRead(out, endOffset, more);
				}
			}
			return new SegmentRead(out, endOffset, false);
		}
	}

	private static int indexOf(byte[] buf, byte b, int from) {
		for (int i = from; i < buf.length; i++) {
			if (buf[i] == b) {
				return i;
			}
		}
		return -1;
	}
}
