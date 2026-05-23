/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.sync.binary;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

/**
 * Serializes a set of {@link Entry blocklist entries} into the on-device binary
 * format. See {@link BlocklistBinaryFormat} for the exact byte layout.
 *
 * <p>
 * The encoder splits entries into two sorted sections (exact / prefix), computes
 * the prefix-length bitmap and writes the resulting file directly to an
 * {@link OutputStream}. Records are emitted little-endian so that the ESP32
 * dongle can read them without byte-swapping.
 * </p>
 */
public final class BlocklistBinaryEncoder {

	private BlocklistBinaryEncoder() {
		// Static utility class.
	}

	/** Input entry for the encoder. */
	public static final class Entry {

		private final String _digits;

		private final boolean _wildcard;

		private final boolean _black;

		/**
		 * Creates an entry.
		 *
		 * @param digits   E.164 digits, already normalised. Up to
		 *                 {@link BlocklistRecord#MAX_DIGITS} characters, {@code '0'..'9'}
		 *                 only.
		 * @param wildcard {@code true} for a prefix-wildcard entry that matches any
		 *                 number starting with {@code digits}, {@code false} for an
		 *                 exact match.
		 * @param black    {@code true} for a spam entry, {@code false} for a legit
		 *                 entry.
		 */
		public Entry(String digits, boolean wildcard, boolean black) {
			_digits = digits;
			_wildcard = wildcard;
			_black = black;
		}

		/** The entry's digits. */
		public String digits() {
			return _digits;
		}

		/** Whether the entry is a wildcard prefix entry. */
		public boolean wildcard() {
			return _wildcard;
		}

		/** Whether the entry is a spam (black) entry. */
		public boolean black() {
			return _black;
		}

	}

	/**
	 * Writes the given entries to {@code out} in binary blocklist format.
	 *
	 * <p>
	 * Duplicate entries (same key + same wildcard flag + same black flag) are
	 * silently deduplicated. The output is sorted by full unsigned 64-bit record
	 * value within each section, which keeps records with the same key adjacent.
	 * </p>
	 *
	 * @param out     Sink to write to. The encoder writes a self-contained file
	 *                and does <em>not</em> close the stream.
	 * @param entries Entries to encode. Order is irrelevant.
	 * @throws IOException              On write failure.
	 * @throws IllegalArgumentException If an entry's digits are invalid.
	 */
	public static void write(OutputStream out, Iterable<Entry> entries) throws IOException {
		TreeSet<Long> exact = new TreeSet<>(Long::compareUnsigned);
		TreeSet<Long> prefix = new TreeSet<>(Long::compareUnsigned);

		for (Entry e : entries) {
			long key = BlocklistRecord.key(e.digits());
			long record = BlocklistRecord.record(key, e.wildcard(), e.black());
			if (e.wildcard()) {
				prefix.add(record);
			} else {
				exact.add(record);
			}
		}

		int prefixLengths = 0;
		for (Long r : prefix) {
			int len = BlocklistRecord.length(BlocklistRecord.keyOf(r));
			if (len >= 1 && len <= BlocklistRecord.MAX_DIGITS) {
				prefixLengths |= (1 << len);
			}
		}

		BlocklistBinaryFormat.writeHeader(out, prefixLengths, exact.size(), prefix.size());
		writeRecords(out, exact);
		writeRecords(out, prefix);
	}

	/**
	 * Convenience overload that accepts the two sections separately. The caller
	 * is responsible for ensuring that {@code exactEntries} contains no wildcard
	 * entries and {@code prefixEntries} contains only wildcard entries.
	 */
	public static void write(OutputStream out, List<Entry> exactEntries, List<Entry> prefixEntries) throws IOException {
		List<Entry> all = new ArrayList<>(exactEntries.size() + prefixEntries.size());
		all.addAll(exactEntries);
		all.addAll(prefixEntries);
		write(out, all);
	}

	private static void writeRecords(OutputStream out, TreeSet<Long> records) throws IOException {
		byte[] buf = new byte[8];
		for (Long r : records) {
			long v = r.longValue();
			for (int i = 0; i < 8; i++) {
				buf[i] = (byte) (v >>> (8 * i));
			}
			out.write(buf);
		}
		Arrays.fill(buf, (byte) 0);
	}

}
