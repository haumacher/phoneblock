/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.sync.binary;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.TreeSet;

import de.haumacher.phoneblock.sync.binary.BlocklistBinaryFormat.ListHeader;

/**
 * Serializes the community and personal {@link Entry blocklist entries} into
 * the on-device binary file format described by {@link BlocklistBinaryFormat}.
 *
 * <p>
 * Records of each list are split into exact / prefix sections, sorted
 * unsigned-ascending, and written little-endian so that the ESP32 dongle can
 * read them without byte-swapping. Within a list, duplicate entries (same key
 * + same wildcard flag + same black flag) are silently deduplicated.
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
	 * Writes a community-only file (personal section empty). Convenience
	 * wrapper around {@link #write(OutputStream, Iterable, Iterable)}.
	 */
	public static void write(OutputStream out, Iterable<Entry> communityEntries) throws IOException {
		write(out, communityEntries, Collections.emptyList());
	}

	/**
	 * Writes the combined community + personal blocklist file.
	 *
	 * @param out                Sink to write to. The encoder writes a
	 *                           self-contained file and does <em>not</em>
	 *                           close the stream.
	 * @param communityEntries   Community-list entries. Order is irrelevant.
	 * @param personalEntries    Personal-list entries (typically a handful).
	 *                           May be empty.
	 * @throws IOException              On write failure.
	 * @throws IllegalArgumentException If an entry's digits are invalid.
	 */
	public static void write(OutputStream out, Iterable<Entry> communityEntries, Iterable<Entry> personalEntries)
			throws IOException {
		List community = collect(communityEntries);
		List personal = collect(personalEntries);

		BlocklistBinaryFormat.writeHeader(out, community.header(), personal.header());

		writeRecords(out, community.exact);
		writeRecords(out, community.prefix);
		writeRecords(out, personal.exact);
		writeRecords(out, personal.prefix);
	}

	private static List collect(Iterable<Entry> entries) {
		List result = new List();
		for (Entry e : entries) {
			long record = BlocklistRecord.record(BlocklistRecord.key(e.digits()), e.black());
			if (e.wildcard()) {
				result.prefix.add(record);
			} else {
				result.exact.add(record);
			}
		}
		return result;
	}

	private static void writeRecords(OutputStream out, TreeSet<Long> records) throws IOException {
		byte[] buf = new byte[BlocklistBinaryFormat.RECORD_SIZE];
		for (Long r : records) {
			long v = r.longValue();
			for (int i = 0; i < buf.length; i++) {
				buf[i] = (byte) (v >>> (8 * i));
			}
			out.write(buf);
		}
	}

	/** One list's deduplicated, sorted records, ready to emit. */
	private static final class List {
		final TreeSet<Long> exact = new TreeSet<>(Long::compareUnsigned);
		final TreeSet<Long> prefix = new TreeSet<>(Long::compareUnsigned);

		ListHeader header() {
			int prefixLengths = 0;
			for (Long r : prefix) {
				int len = BlocklistRecord.length(BlocklistRecord.keyOf(r));
				if (len >= 1 && len <= BlocklistRecord.MAX_DIGITS) {
					prefixLengths |= (1 << len);
				}
			}
			return new ListHeader(prefixLengths, exact.size(), prefix.size());
		}
	}

}
