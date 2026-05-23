/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.sync.binary;

import de.haumacher.phoneblock.sync.binary.BlocklistBinaryDecoder.DecodedList;

/**
 * Reference implementation of the on-device blocklist lookup, used for testing
 * and as the spec for the C port on the dongle.
 *
 * <h2>Algorithm</h2>
 *
 * For an incoming query {@code q}:
 * <ol>
 *   <li>Encode {@code key(q)} once.</li>
 *   <li>Binary-search the exact section for any record whose key matches the
 *       full query.</li>
 *   <li>For each prefix length {@code L} present in
 *       {@link BlocklistBinaryFormat.Header#prefixLengths()}, truncate the query
 *       key to {@code L} digits and binary-search the prefix section.</li>
 *   <li>Returns the verdict of the longest matching record (exact &gt;
 *       longer-prefix &gt; shorter-prefix).</li>
 * </ol>
 *
 * <p>
 * The binary search compares records masked with
 * {@link BlocklistRecord#SEARCH_MASK} (i.e. ignoring the black/white payload
 * bit), so a single match is found regardless of the entry's color.
 * </p>
 *
 * <h2>Single vs. combined lookup</h2>
 *
 * A single instance corresponds to <em>one</em> list (the community list or the
 * user's personal list). The longest-match rule applies <em>within</em> that
 * list. Composition of community and personal lists (personal-first
 * override) lives in the caller:
 *
 * <pre>
 *   Verdict v = personal.lookup(digits);
 *   if (v != UNKNOWN) return v;
 *   return community.lookup(digits) == SPAM ? SPAM : LEGIT;
 * </pre>
 */
public final class BlocklistLookup {

	/** Verdict for a single blocklist lookup. */
	public enum Verdict {
		/** The number is on the spam (black) list. */
		SPAM,

		/** The number is on the legit (white) list. */
		LEGIT,

		/** No matching entry. */
		UNKNOWN
	}

	/**
	 * Records of the exact section, sorted unsigned-ascending. Each entry is
	 * a full 15-digit-or-shorter E.164 number encoded per {@link
	 * BlocklistRecord}. Searched with one binary-search per lookup.
	 */
	private final long[] _exact;

	/**
	 * Records of the prefix section, sorted unsigned-ascending. Each entry
	 * encodes a wildcard prefix {@code <digits>*}. Searched once per length
	 * bit set in {@link #_prefixLengths}, from longest to shortest, so the
	 * longest matching prefix wins.
	 */
	private final long[] _prefix;

	/**
	 * Bitmap of prefix lengths actually present in {@link #_prefix}: bit
	 * {@code L} (1..15) is set iff the prefix section contains at least one
	 * entry of exactly {@code L} digits. Drives the wildcard-search loop &mdash;
	 * unset bits skip a binary search entirely.
	 */
	private final int _prefixLengths;

	/**
	 * Builds a lookup over one decoded list (community or personal). Lookups
	 * over the community and personal sections of a file each get their own
	 * instance; the caller composes them (see the class-level javadoc).
	 */
	public static BlocklistLookup of(DecodedList list) {
		return new BlocklistLookup(
			list.exactRecords(),
			list.prefixRecords(),
			list.header().prefixLengths());
	}

	/**
	 * @param exact          See {@link #_exact}.
	 * @param prefix         See {@link #_prefix}.
	 * @param prefixLengths  See {@link #_prefixLengths}.
	 */
	BlocklistLookup(long[] exact, long[] prefix, int prefixLengths) {
		_exact = exact;
		_prefix = prefix;
		_prefixLengths = prefixLengths;
	}

	/**
	 * Looks up the verdict for a fully-normalised E.164 digit string.
	 *
	 * <p>
	 * Inputs longer than {@link BlocklistRecord#MAX_DIGITS} are silently
	 * truncated to that length: anything past digit 15 is sub-addressing /
	 * extension inside the destination, not part of the E.164 number, so the
	 * matchable identity of the call is its 15-digit prefix. An over-long
	 * input therefore can never hit an exact entry that is not itself a
	 * 15-digit number, but it can still be caught by a wildcard prefix &mdash;
	 * which is exactly the spammer-dials-with-extension case.
	 * </p>
	 */
	public Verdict lookup(CharSequence digits) {
		if (digits.length() > BlocklistRecord.MAX_DIGITS) {
			digits = digits.subSequence(0, BlocklistRecord.MAX_DIGITS);
		}
		long queryKey = BlocklistRecord.key(digits);

		// Search target: key in bits 63..8, flags zero. find() masks bit 0.
		long exactTarget = queryKey << BlocklistRecord.KEY_SHIFT;
		int idx = find(_exact, _exact.length - 1, exactTarget);
		if (idx >= 0) {
			return BlocklistRecord.isBlack(_exact[idx]) ? Verdict.SPAM : Verdict.LEGIT;
		}

		// As L shrinks, truncate(queryKey, L) is monotonically non-increasing
		// (slots get cleared, never set), so the previous miss's insertion
		// point is a valid upper bound for every subsequent search: any record
		// matching the next, smaller target must live strictly to its left.
		int hi = _prefix.length - 1;
		for (int L = BlocklistRecord.MAX_DIGITS; L >= 1 && hi >= 0; L--) {
			if ((_prefixLengths & (1 << L)) == 0) {
				continue;
			}
			long prefixTarget = BlocklistRecord.truncate(queryKey, L) << BlocklistRecord.KEY_SHIFT;
			idx = find(_prefix, hi, prefixTarget);
			if (idx >= 0) {
				return BlocklistRecord.isBlack(_prefix[idx]) ? Verdict.SPAM : Verdict.LEGIT;
			}
			hi = -idx - 2;
		}

		return Verdict.UNKNOWN;
	}

	/**
	 * Binary search over {@code sorted[0..hi]} masking bit 0. Returns the
	 * matching index, or {@code -(insertion point) - 1} if no entry matches
	 * (same convention as {@link java.util.Arrays#binarySearch(long[], long)}).
	 *
	 * <p>
	 * Both sides are compared with {@link Long#compareUnsigned}, so records
	 * with the most-significant key bit set (which puts bit 63 of the record
	 * at 1) sort correctly above records with that bit clear.
	 * </p>
	 *
	 * @param hi Upper bound (inclusive). Pass {@code sorted.length - 1} to
	 *           search the whole array; the prefix loop uses the previous
	 *           search's insertion point to keep the range shrinking.
	 */
	private static int find(long[] sorted, int hi, long target) {
		long needle = target & BlocklistRecord.SEARCH_MASK;
		int lo = 0;
		while (lo <= hi) {
			int mid = (lo + hi) >>> 1;
			long midMasked = sorted[mid] & BlocklistRecord.SEARCH_MASK;
			int c = Long.compareUnsigned(midMasked, needle);
			if (c < 0) {
				lo = mid + 1;
			} else if (c > 0) {
				hi = mid - 1;
			} else {
				return mid;
			}
		}
		return -(lo + 1);
	}

}
