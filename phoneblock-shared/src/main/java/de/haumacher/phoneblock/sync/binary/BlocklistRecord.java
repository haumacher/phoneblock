/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.sync.binary;

/**
 * Encoding of a single blocklist entry into a {@code uint64} record.
 *
 * <h2>Record layout (MSB to LSB)</h2>
 * <pre>
 *   Bit 63..8 : 56-bit base-11 key (16 symbol slots, left-aligned)
 *   Bit  7..1 : reserved (always zero)
 *   Bit  0    : 0 = white (legit), 1 = black (spam)
 * </pre>
 *
 * <p>
 * Whether the entry is exact or a wildcard prefix is encoded by section
 * membership in the file (see the package overview), not in the record bits.
 * </p>
 *
 * <h2>Key encoding</h2>
 *
 * Each of the 16 slots carries one base-11 symbol:
 *
 * <pre>
 *   Symbol 0       : terminator / right-pad (sorts before any digit)
 *   Symbol 1..10   : digit 0..9
 * </pre>
 *
 * E.164 allows up to 15 significant digits, so the 16th slot is always a
 * terminator and acts as an implicit length sentinel. Because the terminator
 * sorts <em>before</em> any digit, a key truncated to {@code L} digits compares
 * strictly less than any longer key starting with the same digits &mdash; which
 * is exactly the prefix relation we need.
 *
 * <p>
 * {@code 11^16 = 45_949_729_863_572_161 < 2^56}, so the key fits in 56 bits
 * with room to spare.
 * </p>
 *
 * <h2>Sort order</h2>
 *
 * Records are sorted by their full {@code uint64} value. Bit 63 of a record can
 * be set (for keys whose most significant slot is &ge; 8), so comparisons must
 * be <em>unsigned</em>.
 */
public final class BlocklistRecord {

	private BlocklistRecord() {
		// Static utility class.
	}

	/** Maximum number of significant digits in an E.164 number. */
	public static final int MAX_DIGITS = 15;

	/** Number of symbol slots in the base-11 key (max digits + 1 terminator slot). */
	public static final int SLOTS = 16;

	/** Bit position where the key begins (i.e. number of flag/reserved bits below). */
	public static final int KEY_SHIFT = 8;

	/** Bitmask covering the key portion of a record. */
	public static final long KEY_MASK = ~0xFFL;

	/** Bit 0: set if the entry is a spam (black) entry, clear for legit (white) entries. */
	public static final long FLAG_BLACK = 1L;

	/**
	 * Mask covering the bits relevant for the binary-search comparison: key
	 * plus reserved bits, i.e. everything except the black/white payload bit.
	 */
	public static final long SEARCH_MASK = ~FLAG_BLACK;

	/**
	 * Powers of 11 from {@code 11^0} up to and including {@code 11^16}.
	 * {@code POW11[i]} is the place value of slot {@code SLOTS - 1 - i} (in other
	 * words: how much one symbol at the {@code i}-th-least-significant slot
	 * contributes to the key).
	 */
	public static final long[] POW11 = pow11Table();

	private static long[] pow11Table() {
		long[] result = new long[SLOTS + 1];
		long v = 1L;
		for (int i = 0; i <= SLOTS; i++) {
			result[i] = v;
			v *= 11L;
		}
		return result;
	}

	/**
	 * Encodes a digit string into the 56-bit base-11 key.
	 *
	 * @param digits Decimal digits {@code '0'..'9'}; up to {@link #MAX_DIGITS}
	 *               digits. Must not contain a {@code +} sign, spaces or other
	 *               non-digit characters; the caller is responsible for
	 *               normalising the number to plain E.164 digits before encoding.
	 * @return The 56-bit key. Always non-negative, fits in {@code long}.
	 * @throws IllegalArgumentException If the input contains non-digit characters
	 *                                  or has more than {@link #MAX_DIGITS} digits.
	 */
	public static long key(CharSequence digits) {
		int n = digits.length();
		if (n > MAX_DIGITS) {
			throw new IllegalArgumentException(
				"Number has " + n + " digits, max is " + MAX_DIGITS + ": '" + digits + "'");
		}
		long k = 0L;
		for (int i = 0; i < SLOTS; i++) {
			int symbol;
			if (i < n) {
				char c = digits.charAt(i);
				if (c < '0' || c > '9') {
					throw new IllegalArgumentException(
						"Non-digit character '" + c + "' at index " + i + " in '" + digits + "'");
				}
				symbol = (c - '0') + 1;
			} else {
				symbol = 0;
			}
			k = k * 11L + symbol;
		}
		return k;
	}

	/**
	 * Assembles a complete record from a key and the black/white flag.
	 *
	 * <p>
	 * The exact-vs-wildcard distinction is not encoded in the record &mdash;
	 * the encoder places the record into either the exact or the prefix
	 * section depending on the source entry, and the lookup recovers that
	 * information from which section it searched.
	 * </p>
	 *
	 * @param key   The 56-bit base-11 key, as returned by {@link #key}.
	 * @param black {@code true} for a spam entry, {@code false} for a legit
	 *              entry.
	 * @return The complete {@code uint64} record.
	 */
	public static long record(long key, boolean black) {
		return (key << KEY_SHIFT) | (black ? FLAG_BLACK : 0L);
	}

	/**
	 * Truncates a key to its leading {@code length} digit slots, zeroing the rest.
	 *
	 * <p>
	 * Used during prefix lookup: the incoming query is encoded with
	 * {@link #key(CharSequence)} once, then this method produces the key that a
	 * prefix entry of the given length would have.
	 * </p>
	 *
	 * @param key    The full key.
	 * @param length Number of leading digit slots to retain. {@code 0..SLOTS}.
	 * @return The truncated key with the trailing slots reset to the terminator.
	 */
	public static long truncate(long key, int length) {
		if (length >= SLOTS) {
			return key;
		}
		if (length <= 0) {
			return 0L;
		}
		long step = POW11[SLOTS - length];
		return key - (key % step);
	}

	/** Returns the key portion (bits 63..8) of a record, shifted down. */
	public static long keyOf(long record) {
		return (record >>> KEY_SHIFT) & 0x00FF_FFFF_FFFF_FFFFL;
	}

	/** Returns {@code true} if the record's black/spam flag is set. */
	public static boolean isBlack(long record) {
		return (record & FLAG_BLACK) != 0L;
	}

	/**
	 * Number of significant digit slots in a key (i.e. position of the first
	 * terminator). For a 15-digit number this returns 15; for a 5-digit prefix
	 * it returns 5; the empty key returns 0.
	 */
	public static int length(long key) {
		for (int i = 0; i < SLOTS; i++) {
			long step = POW11[SLOTS - 1 - i];
			int symbol = (int) ((key / step) % 11L);
			if (symbol == 0) {
				return i;
			}
		}
		return SLOTS;
	}

	/**
	 * Decodes a key back into its digit string. The result has between {@code 0}
	 * and {@link #MAX_DIGITS} characters.
	 */
	public static String digits(long key) {
		StringBuilder sb = new StringBuilder(MAX_DIGITS);
		for (int i = 0; i < SLOTS; i++) {
			long step = POW11[SLOTS - 1 - i];
			int symbol = (int) ((key / step) % 11L);
			if (symbol == 0) {
				break;
			}
			sb.append((char) ('0' + symbol - 1));
		}
		return sb.toString();
	}

}
