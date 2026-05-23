/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.sync.binary;

import java.io.IOException;
import java.io.OutputStream;
import java.util.TreeSet;

/**
 * Serializes a list of {@link Entry blocklist entries} into the on-device
 * binary file format described by {@link BlocklistBinaryFormat}. One list
 * (community or personal) per call.
 *
 * <p>
 * Records are split into exact / prefix sections, sorted unsigned-ascending,
 * and written little-endian so that the ESP32 dongle can read them without
 * byte-swapping. Duplicate entries (same key + same wildcard flag + same
 * black flag) are silently deduplicated.
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
	 * @param out     Sink to write to. The encoder writes a self-contained
	 *                file and does <em>not</em> close the stream.
	 * @param entries Entries to encode. Order is irrelevant.
	 * @throws IOException              On write failure.
	 * @throws IllegalArgumentException If an entry's digits are invalid.
	 */
	public static void write(OutputStream out, Iterable<Entry> entries) throws IOException {
		TreeSet<Long> exact = new TreeSet<>(Long::compareUnsigned);
		TreeSet<Long> prefix = new TreeSet<>(Long::compareUnsigned);

		for (Entry e : entries) {
			long record = BlocklistRecord.record(BlocklistRecord.key(e.digits()), e.black());
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

}
