/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.analysis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;

/**
 * A block of phone numbers that can be assigned to a single address book entry.
 *
 * <p>
 * Identified by its {@link #getName() name} (the prefix that defines the bucket)
 * and carrying its members in sorted order — the sort is a property of the block
 * itself so callers can rely on it without re-sorting. The {@link #contentHash()}
 * is computed once at construction, since the block is immutable.
 * </p>
 *
 * @author <a href="mailto:haui@haumacher.de">Bernhard Haumacher</a>
 */
public final class NumberBlock {

	private final String _name;
	private final List<String> _numbers;
	private final String _contentHash;
	private final String _vCard;

	/**
	 * Creates a {@link NumberBlock}.
	 *
	 * @param name
	 *        Identifier of the block. The prefix-bucketing algorithm uses the bucket prefix;
	 *        a singleton block created on lookup uses the number itself.
	 * @param numbers
	 *        Members of the block. Copied and sorted ascending.
	 */
	public NumberBlock(String name, Collection<String> numbers) {
		_name = name;
		List<String> sorted = new ArrayList<>(numbers);
		Collections.sort(sorted);
		_numbers = Collections.unmodifiableList(sorted);
		_contentHash = computeContentHash(name, _numbers);
		_vCard = buildVCard(name, _numbers);
	}

	/**
	 * Identifier of the block — the bucket prefix it was created with.
	 */
	public String getName() {
		return _name;
	}

	/**
	 * Members of the block, sorted ascending.
	 */
	public List<String> getNumbers() {
		return _numbers;
	}

	/**
	 * Number of members in the block.
	 */
	public int size() {
		return _numbers.size();
	}

	/**
	 * Pre-rendered vCard 3.0 entry for this block. Constant for the lifetime of
	 * the block, so it is built once at construction and reused across renders.
	 */
	public String vCardContent() {
		return _vCard;
	}

	/**
	 * Truncated SHA-1 (12 hex chars) over the block's name and members. Two blocks
	 * with the same name and members produce the same hash; any change in either
	 * produces a different hash.
	 */
	public String contentHash() {
		return _contentHash;
	}

	private static String buildVCard(String name, List<String> sortedNumbers) {
		StringBuilder buf = new StringBuilder();
		buf.append("BEGIN:VCARD\n");
		buf.append("VERSION:3.0\n");
		buf.append("UID:").append(name).append('\n');
		buf.append("FN:SPAM: ");
		if (sortedNumbers.size() == 1) {
			buf.append(sortedNumbers.get(0));
		} else {
			buf.append(name).append("...");
		}
		buf.append('\n');
		buf.append("CATEGORIES:SPAM\n");
		for (String n : sortedNumbers) {
			buf.append("TEL;TYPE=WORK:").append(n).append('\n');
		}
		buf.append("END:VCARD");
		return buf.toString();
	}

	private static String computeContentHash(String name, List<String> sortedNumbers) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			md.update(name.getBytes(StandardCharsets.UTF_8));
			md.update((byte) 0);
			for (String n : sortedNumbers) {
				md.update(n.getBytes(StandardCharsets.UTF_8));
				md.update((byte) 0);
			}
			byte[] digest = md.digest();
			return HexFormat.of().formatHex(digest, 0, 6);
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-1 not available", ex);
		}
	}
}
