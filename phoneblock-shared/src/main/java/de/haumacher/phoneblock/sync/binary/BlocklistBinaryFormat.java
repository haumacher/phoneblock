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
 * rationale behind the two-section split.
 *
 * <h2>File layout</h2>
 *
 * <pre>
 *   Offset Size  Field
 *   ------ ----  ----------------------------------------------------------------
 *        0   4   magic = 'P','B','B','L'   (little-endian uint32, {@link #MAGIC})
 *        4   2   version                  (little-endian uint16, {@link #VERSION})
 *        6   2   prefixLengths bitmap     (bit L set = prefix length L present)
 *        8   4   exactCount               (little-endian uint32)
 *       12   4   prefixCount              (little-endian uint32)
 *       16   8 * exactCount   exact records, LE uint64, sorted unsigned-ascending
 *      ...   8 * prefixCount  prefix records, LE uint64, sorted unsigned-ascending
 * </pre>
 *
 * All multi-byte fields are little-endian to match the ESP32, so the dongle can
 * memory-map a section and read 8-byte records directly. Exact/wildcard is
 * implied by section membership; the record bits do not carry that distinction.
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
	public static final int VERSION = 1;

	/** Fixed header size in bytes. */
	public static final int HEADER_SIZE = 16;

	/** Size of one record in bytes. */
	public static final int RECORD_SIZE = 8;

	/**
	 * Writes the file header.
	 *
	 * @param out            Sink to write to.
	 * @param prefixLengths  16-bit bitmap of prefix lengths that occur in the
	 *                       prefix section. Bit {@code L} (1..15) is set iff
	 *                       there is at least one prefix entry with length
	 *                       {@code L}.
	 * @param exactCount     Number of records in the exact section.
	 * @param prefixCount    Number of records in the prefix section.
	 * @throws IOException On write failure.
	 */
	public static void writeHeader(OutputStream out, int prefixLengths, int exactCount, int prefixCount)
			throws IOException {
		byte[] hdr = new byte[HEADER_SIZE];
		writeU32(hdr, 0, MAGIC);
		writeU16(hdr, 4, VERSION);
		writeU16(hdr, 6, prefixLengths);
		writeU32(hdr, 8, exactCount);
		writeU32(hdr, 12, prefixCount);
		out.write(hdr);
	}

	/** Parsed file header. */
	public static final class Header {

		private final int _version;

		private final int _prefixLengths;

		private final int _exactCount;

		private final int _prefixCount;

		Header(int version, int prefixLengths, int exactCount, int prefixCount) {
			_version = version;
			_prefixLengths = prefixLengths;
			_exactCount = exactCount;
			_prefixCount = prefixCount;
		}

		/** File format version, currently {@link BlocklistBinaryFormat#VERSION}. */
		public int version() {
			return _version;
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

		/** Number of 8-byte records in the exact section. */
		public int exactCount() {
			return _exactCount;
		}

		/** Number of 8-byte records in the prefix section. */
		public int prefixCount() {
			return _prefixCount;
		}

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
		int prefixLengths = readU16(hdr, 6);
		int exactCount = readU32(hdr, 8);
		int prefixCount = readU32(hdr, 12);
		if (exactCount < 0 || prefixCount < 0) {
			throw new IllegalArgumentException(
				"Section count too large for 31-bit signed int: exact=" + (exactCount & 0xFFFFFFFFL)
					+ ", prefix=" + (prefixCount & 0xFFFFFFFFL));
		}
		return new Header(version, prefixLengths, exactCount, prefixCount);
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
