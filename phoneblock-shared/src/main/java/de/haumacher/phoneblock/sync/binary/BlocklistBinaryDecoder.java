/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.sync.binary;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import de.haumacher.phoneblock.sync.binary.BlocklistBinaryEncoder.Entry;
import de.haumacher.phoneblock.sync.binary.BlocklistBinaryFormat.Header;

/**
 * Reads a binary blocklist file back into its constituent sections.
 *
 * <p>
 * The decoder is primarily a tool for tests and for the answerbot side (which
 * may eventually use the same file format as the dongle). The dongle itself
 * does not need a full decoder &mdash; it only reads the header plus performs
 * binary searches over the record sections.
 * </p>
 */
public final class BlocklistBinaryDecoder {

	private BlocklistBinaryDecoder() {
		// Static utility class.
	}

	/** Decoded contents of a binary blocklist file. */
	public static final class DecodedBlocklist {

		private final Header _header;

		private final long[] _exactRecords;

		private final long[] _prefixRecords;

		DecodedBlocklist(Header header, long[] exactRecords, long[] prefixRecords) {
			_header = header;
			_exactRecords = exactRecords;
			_prefixRecords = prefixRecords;
		}

		public Header header() {
			return _header;
		}

		/** Exact records, sorted by unsigned 64-bit value. */
		public long[] exactRecords() {
			return _exactRecords;
		}

		/** Prefix records, sorted by unsigned 64-bit value. */
		public long[] prefixRecords() {
			return _prefixRecords;
		}

	}

	/**
	 * Parses a binary blocklist file from {@code in}.
	 *
	 * @param in Source to read from. Not closed by this method.
	 * @throws IOException              On read failure or truncation.
	 * @throws IllegalArgumentException If the file is malformed.
	 */
	public static DecodedBlocklist read(InputStream in) throws IOException {
		Header header = BlocklistBinaryFormat.readHeader(in);
		long[] exact = BlocklistBinaryFormat.readRecords(in, header.exactCount());
		long[] prefix = BlocklistBinaryFormat.readRecords(in, header.prefixCount());
		return new DecodedBlocklist(header, exact, prefix);
	}

	/**
	 * Reconstructs the original {@link Entry entries} from a decoded blocklist.
	 * Useful for round-trip testing.
	 */
	public static List<Entry> toEntries(DecodedBlocklist decoded) {
		List<Entry> result = new ArrayList<>(decoded.exactRecords().length + decoded.prefixRecords().length);
		for (long r : decoded.exactRecords()) {
			result.add(toEntry(r));
		}
		for (long r : decoded.prefixRecords()) {
			result.add(toEntry(r));
		}
		return result;
	}

	private static Entry toEntry(long record) {
		String digits = BlocklistRecord.digits(BlocklistRecord.keyOf(record));
		return new Entry(digits, BlocklistRecord.isWildcard(record), BlocklistRecord.isBlack(record));
	}

}
