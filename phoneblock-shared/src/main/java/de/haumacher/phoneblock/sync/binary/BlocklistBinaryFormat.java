/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.sync.binary;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * On-disk layout of the binary blocklist file. See the package overview for the
 * rationale behind the exact/prefix split per list.
 *
 * <h2>File layout (version 2)</h2>
 *
 * Two lists in one file: the shared community list and the user's personal
 * black/white list. Either list may be empty (all counts zero); the
 * personal list typically is for a brand-new user.
 *
 * <pre>
 *   Offset Size  Field
 *   ------ ----  ----------------------------------------------------------------
 *        0   4   magic = 'P','B','B','L'   (little-endian uint32, {@link #MAGIC})
 *        4   2   version                  (little-endian uint16, {@link #VERSION})
 *        6   2   reserved                 (must be zero)
 *        8  12   community ListHeader
 *       20  12   personal  ListHeader
 *       32  8 * communityExactCount   community exact records
 *      ...  8 * communityPrefixCount  community prefix records
 *      ...  8 * personalExactCount    personal exact records
 *      ...  8 * personalPrefixCount   personal prefix records
 *
 *   ListHeader (12 bytes):
 *        0   4   prefixLengths           (16-bit bitmap in the low half, high zero)
 *        4   4   exactCount              (little-endian uint32)
 *        8   4   prefixCount             (little-endian uint32)
 * </pre>
 *
 * All multi-byte fields are little-endian to match the ESP32, so the dongle
 * can memory-map a section and read 8-byte records directly. Exact/wildcard
 * is implied by section membership; the record bits do not carry that
 * distinction.
 */
public final class BlocklistBinaryFormat {

	private BlocklistBinaryFormat() {
		// Static utility class.
	}

	/**
	 * Magic value at offset 0. When written as a little-endian {@code uint32},
	 * the file starts with the four ASCII bytes {@code 'P','B','B','L'}.
	 */
	public static final int MAGIC = 0x4C424250;

	/** Current format version. */
	public static final int VERSION = 2;

	/** Size of one list header (community or personal) in bytes. */
	public static final int LIST_HEADER_SIZE = 12;

	/** Fixed file header size in bytes: magic + version + two list headers. */
	public static final int HEADER_SIZE = 8 + 2 * LIST_HEADER_SIZE;

	/** Size of one record in bytes. */
	public static final int RECORD_SIZE = 8;

	/** Per-list section sizes and prefix-length bitmap. */
	public static final class ListHeader {

		private final int _prefixLengths;

		private final int _exactCount;

		private final int _prefixCount;

		/**
		 * Creates a list header.
		 *
		 * @param prefixLengths 16-bit bitmap of prefix lengths present in the
		 *                      prefix section; bit {@code L} (1..15) set iff at
		 *                      least one entry has exactly {@code L} digits.
		 *                      Bits 16..31 must be zero.
		 * @param exactCount    Number of records in the exact section.
		 * @param prefixCount   Number of records in the prefix section.
		 */
		public ListHeader(int prefixLengths, int exactCount, int prefixCount) {
			_prefixLengths = prefixLengths;
			_exactCount = exactCount;
			_prefixCount = prefixCount;
		}

		/**
		 * Bitmap of prefix lengths present in the prefix section: bit {@code L}
		 * (1..15) is set iff at least one prefix entry has exactly {@code L}
		 * digits. The dongle's wildcard lookup uses this to skip lengths that
		 * have no entries.
		 */
		public int prefixLengths() {
			return _prefixLengths;
		}

		/** Number of 8-byte records in this list's exact section. */
		public int exactCount() {
			return _exactCount;
		}

		/** Number of 8-byte records in this list's prefix section. */
		public int prefixCount() {
			return _prefixCount;
		}

	}

	/** Parsed file header. */
	public static final class Header {

		private final int _version;

		private final ListHeader _community;

		private final ListHeader _personal;

		Header(int version, ListHeader community, ListHeader personal) {
			_version = version;
			_community = community;
			_personal = personal;
		}

		/** File format version, currently {@link BlocklistBinaryFormat#VERSION}. */
		public int version() {
			return _version;
		}

		/** Sizes of the community list's exact and prefix sections. */
		public ListHeader community() {
			return _community;
		}

		/** Sizes of the user's personal black/white list. May be all-zero. */
		public ListHeader personal() {
			return _personal;
		}

	}

	/**
	 * Writes the file header (magic, version, both list descriptors).
	 *
	 * @throws IOException On write failure.
	 */
	public static void writeHeader(OutputStream out, ListHeader community, ListHeader personal) throws IOException {
		byte[] hdr = new byte[HEADER_SIZE];
		writeU32(hdr, 0, MAGIC);
		writeU16(hdr, 4, VERSION);
		writeU16(hdr, 6, 0);
		writeListHeader(hdr, 8, community);
		writeListHeader(hdr, 8 + LIST_HEADER_SIZE, personal);
		out.write(hdr);
	}

	private static void writeListHeader(byte[] dst, int off, ListHeader h) {
		writeU32(dst, off, h.prefixLengths() & 0xFFFF);
		writeU32(dst, off + 4, h.exactCount());
		writeU32(dst, off + 8, h.prefixCount());
	}

	/**
	 * Reads and validates the header.
	 *
	 * @throws IOException              On read failure.
	 * @throws IllegalArgumentException If the magic value or version is unknown.
	 */
	public static Header readHeader(InputStream in) throws IOException {
		byte[] hdr = readFully(in, HEADER_SIZE);
		int magic = readU32(hdr, 0);
		if (magic != MAGIC) {
			throw new IllegalArgumentException(
				"Not a binary blocklist file: bad magic 0x" + Integer.toHexString(magic));
		}
		int version = readU16(hdr, 4);
		if (version != VERSION) {
			throw new IllegalArgumentException(
				"Unsupported binary blocklist version: " + version + " (expected " + VERSION + ")");
		}
		ListHeader community = readListHeader(hdr, 8);
		ListHeader personal = readListHeader(hdr, 8 + LIST_HEADER_SIZE);
		return new Header(version, community, personal);
	}

	private static ListHeader readListHeader(byte[] src, int off) {
		int prefixLengths = readU32(src, off);
		int exactCount = readU32(src, off + 4);
		int prefixCount = readU32(src, off + 8);
		if (exactCount < 0 || prefixCount < 0) {
			throw new IllegalArgumentException(
				"Section count too large for 31-bit signed int: exact=" + (exactCount & 0xFFFFFFFFL)
					+ ", prefix=" + (prefixCount & 0xFFFFFFFFL));
		}
		return new ListHeader(prefixLengths & 0xFFFF, exactCount, prefixCount);
	}

	/** Reads {@code count} little-endian {@code uint64} records into a fresh array. */
	public static long[] readRecords(InputStream in, int count) throws IOException {
		long[] out = new long[count];
		byte[] buf = new byte[RECORD_SIZE];
		for (int i = 0; i < count; i++) {
			readFully(in, buf, 0, RECORD_SIZE);
			long v = 0L;
			for (int j = 0; j < 8; j++) {
				v |= (buf[j] & 0xFFL) << (8 * j);
			}
			out[i] = v;
		}
		return out;
	}

	private static void writeU16(byte[] dst, int off, int value) {
		dst[off] = (byte) value;
		dst[off + 1] = (byte) (value >>> 8);
	}

	private static void writeU32(byte[] dst, int off, int value) {
		dst[off] = (byte) value;
		dst[off + 1] = (byte) (value >>> 8);
		dst[off + 2] = (byte) (value >>> 16);
		dst[off + 3] = (byte) (value >>> 24);
	}

	private static int readU16(byte[] src, int off) {
		return (src[off] & 0xFF) | ((src[off + 1] & 0xFF) << 8);
	}

	private static int readU32(byte[] src, int off) {
		return (src[off] & 0xFF)
			| ((src[off + 1] & 0xFF) << 8)
			| ((src[off + 2] & 0xFF) << 16)
			| ((src[off + 3] & 0xFF) << 24);
	}

	private static byte[] readFully(InputStream in, int n) throws IOException {
		byte[] buf = new byte[n];
		readFully(in, buf, 0, n);
		return buf;
	}

	private static void readFully(InputStream in, byte[] buf, int off, int len) throws IOException {
		int read = 0;
		while (read < len) {
			int n = in.read(buf, off + read, len - read);
			if (n < 0) {
				throw new EOFException("Unexpected end of stream after " + read + " of " + len + " bytes");
			}
			read += n;
		}
	}

}
