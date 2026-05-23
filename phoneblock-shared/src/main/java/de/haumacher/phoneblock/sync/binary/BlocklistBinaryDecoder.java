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
import de.haumacher.phoneblock.sync.binary.BlocklistBinaryFormat.ListHeader;

/**
 * Reads a binary blocklist file back into its constituent sections.
 *
 * <p>
 * The decoder is primarily a tool for tests and for the answerbot side. The
 * dongle itself does not need a full decoder &mdash; it only reads the header
 * plus performs binary searches over the record sections.
 * </p>
 */
public final class BlocklistBinaryDecoder {

	private BlocklistBinaryDecoder() {
		// Static utility class.
	}

	/** One decoded list (community or personal). */
	public static final class DecodedList {

		private final ListHeader _header;

		private final long[] _exactRecords;

		private final long[] _prefixRecords;

		DecodedList(ListHeader header, long[] exactRecords, long[] prefixRecords) {
			_header = header;
			_exactRecords = exactRecords;
			_prefixRecords = prefixRecords;
		}

		/** Sizes and the prefix-length bitmap for this list. */
		public ListHeader header() {
			return _header;
		}

		/**
		 * Exact records, sorted unsigned-ascending. Length equals
		 * {@link ListHeader#exactCount()}.
		 */
		public long[] exactRecords() {
			return _exactRecords;
		}

		/**
		 * Prefix records, sorted unsigned-ascending. Length equals
		 * {@link ListHeader#prefixCount()}.
		 */
		public long[] prefixRecords() {
			return _prefixRecords;
		}

	}

	/** Decoded contents of a binary blocklist file. */
	public static final class DecodedBlocklist {

		private final Header _header;

		private final DecodedList _community;

		private final DecodedList _personal;

		DecodedBlocklist(Header header, DecodedList community, DecodedList personal) {
			_header = header;
			_community = community;
			_personal = personal;
		}

		/** Parsed file header (version + both list descriptors). */
		public Header header() {
			return _header;
		}

		/** The community list. */
		public DecodedList community() {
			return _community;
		}

		/** The user's personal black/white list. May be empty. */
		public DecodedList personal() {
			return _personal;
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
		DecodedList community = readList(in, header.community());
		DecodedList personal = readList(in, header.personal());
		return new DecodedBlocklist(header, community, personal);
	}

	private static DecodedList readList(InputStream in, ListHeader hdr) throws IOException {
		long[] exact = BlocklistBinaryFormat.readRecords(in, hdr.exactCount());
		long[] prefix = BlocklistBinaryFormat.readRecords(in, hdr.prefixCount());
		return new DecodedList(hdr, exact, prefix);
	}

	/**
	 * Reconstructs the original {@link Entry entries} from one decoded list.
	 * Useful for round-trip testing. The exact/wildcard distinction is
	 * recovered from which section a record came out of.
	 */
	public static List<Entry> toEntries(DecodedList list) {
		List<Entry> result = new ArrayList<>(list.exactRecords().length + list.prefixRecords().length);
		for (long r : list.exactRecords()) {
			result.add(toEntry(r, false));
		}
		for (long r : list.prefixRecords()) {
			result.add(toEntry(r, true));
		}
		return result;
	}

	private static Entry toEntry(long record, boolean wildcard) {
		String digits = BlocklistRecord.digits(BlocklistRecord.keyOf(record));
		return new Entry(digits, wildcard, BlocklistRecord.isBlack(record));
	}

}
