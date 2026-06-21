/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */

/**
 * On-device binary blocklist format for the PhoneBlock dongle.
 *
 * <h2>File layout</h2>
 *
 * A file holds two sorted record sections:
 *
 * <ul>
 *   <li><b>Exact section</b> &mdash; full E.164 numbers ({@code 4930123456}).</li>
 *   <li><b>Prefix section</b> &mdash; wildcard entries ({@code 4930*}) that
 *       match any number starting with the given digits.</li>
 * </ul>
 *
 * Each record is an 8-byte little-endian {@code uint64} packing a 56-bit
 * base-11 key plus a black/white payload bit; see {@link
 * de.haumacher.phoneblock.sync.binary.BlocklistRecord} for the bit layout and
 * {@link de.haumacher.phoneblock.sync.binary.BlocklistBinaryFormat} for the
 * file header.
 *
 * <h2>Why two physical sections instead of one</h2>
 *
 * The common-case lookup &mdash; a legit number that is on neither list
 * &mdash; should be a single binary search. Mixing exact and prefix entries
 * into one stream would force the lookup to issue, in addition to the exact
 * search, one binary search <em>per occurring prefix length</em> against the
 * combined data set just to find out that there are no prefix hits either.
 *
 * <p>
 * With separate sections the lookup performs:
 * </p>
 *
 * <ol>
 *   <li>One binary search over the exact section for the full query key.</li>
 *   <li>For each length bit set in
 *       {@link de.haumacher.phoneblock.sync.binary.BlocklistBinaryFormat.Header#prefixLengths()},
 *       one binary search over the prefix section for the query truncated to
 *       that length.</li>
 * </ol>
 *
 * In practice the prefix section holds at most a few hundred records (number
 * blocks, area codes), so it may even be linearly scanned. The exact section
 * stays the unambiguously dominant cost on the hot path.
 *
 * <h2>Why there is no wildcard flag in the record</h2>
 *
 * Section membership already tells the reader whether an entry is exact or a
 * wildcard prefix &mdash; encoding it again in a record bit would be
 * redundant. Bit 1 of every record is therefore reserved (always zero); it
 * could carry future per-entry metadata if a real need arises.
 *
 * <h2>Personalisation</h2>
 *
 * The community list and the user's personal black/white list travel as two
 * separate files (one list per file), fetched from two server endpoints. The
 * dongle writes them as independent SPIFFS files and runs one
 * {@link de.haumacher.phoneblock.sync.binary.BlocklistLookup} over each.
 * Splitting at the wire level lets the community variant be cache- and
 * CDN-shareable across all users that share a {@code minVotes} threshold,
 * while the personal variant stays per-user and dynamic.
 *
 * <p>
 * Lookup composition (&ldquo;personal first, then community&rdquo;) and the
 * longest-match tie-break inside the personal list are implemented by the
 * caller of {@link de.haumacher.phoneblock.sync.binary.BlocklistLookup}; see
 * that class's javadoc for the canonical pattern.
 * </p>
 */
package de.haumacher.phoneblock.sync.binary;
