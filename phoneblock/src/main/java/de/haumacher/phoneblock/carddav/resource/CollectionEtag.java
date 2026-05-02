/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.carddav.resource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.haumacher.phoneblock.analysis.NumberBlock;

/**
 * Composable hashes that make up a CardDAV collection ETag.
 *
 * <p>
 * The collection ETag is built from three inputs that change at very
 * different rates:
 * </p>
 * <ul>
 * <li><b>Blocks hash</b> — over the sorted block IDs and per-block content
 *     hashes. For users without effective exclusions this is precomputed
 *     once per {@code ListType} on the shared common-list cache, so the
 *     per-user ETag computation never iterates the (large) common block
 *     set.</li>
 * <li><b>Personal-singletons hash</b> — over the sorted phone numbers added
 *     as personal singletons after the common-list filter. Empty for
 *     common-only and full-pipeline users.</li>
 * <li><b>Settings hash</b> — {@code ListType.hashCode()}, optionally XORed
 *     with a personal-settings hash for users with personal data.</li>
 * </ul>
 *
 * <p>
 * The composition function {@link #compose} produces the final 12-hex-char
 * SHA-1 prefix that ends up in the {@code ETag}/{@code getctag} headers.
 * </p>
 */
public final class CollectionEtag {

	private CollectionEtag() {
		// no instances
	}

	/**
	 * Hash over a list of {@link NumberBlock}s, ordered by block name. Used
	 * either as the precomputed common-blocks hash on a shared common list,
	 * or as the per-user blocks hash on the full-pipeline path.
	 */
	public static String hashBlocks(List<NumberBlock> blocks) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			List<NumberBlock> sorted = new ArrayList<>(blocks);
			sorted.sort((a, b) -> a.getName().compareTo(b.getName()));
			for (NumberBlock block : sorted) {
				md.update(block.getName().getBytes(StandardCharsets.UTF_8));
				md.update((byte) 0);
				md.update(block.contentHash().getBytes(StandardCharsets.UTF_8));
				md.update((byte) 0);
			}
			return toHex12(md.digest());
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-1 not available", ex);
		}
	}

	/**
	 * Hash over a list of personal singleton phone numbers, sorted. An empty
	 * input yields the empty-input SHA-1 prefix.
	 */
	public static String hashPersonalSingletons(List<String> singletons) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			List<String> sorted = new ArrayList<>(singletons);
			Collections.sort(sorted);
			for (String s : sorted) {
				md.update(s.getBytes(StandardCharsets.UTF_8));
				md.update((byte) 0);
			}
			return toHex12(md.digest());
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-1 not available", ex);
		}
	}

	/**
	 * Combines three precomputed components into the final collection ETag.
	 */
	public static String compose(String blocksHash, String personalHash, int settingsHash) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			md.update(blocksHash.getBytes(StandardCharsets.UTF_8));
			md.update((byte) 0);
			md.update(personalHash.getBytes(StandardCharsets.UTF_8));
			md.update((byte) 0);
			md.update(Integer.toString(settingsHash).getBytes(StandardCharsets.UTF_8));
			return toHex12(md.digest());
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-1 not available", ex);
		}
	}

	/**
	 * Direct ETag for the full-pipeline path, which has no shared common
	 * list. Equivalent to {@code compose(hashBlocks(blocks),
	 * hashPersonalSingletons(emptyList), settingsHash)}.
	 */
	public static String forFullPipeline(List<NumberBlock> blocks, int settingsHash) {
		return compose(hashBlocks(blocks), hashPersonalSingletons(List.of()), settingsHash);
	}

	private static String toHex12(byte[] digest) {
		StringBuilder hex = new StringBuilder(12);
		for (int i = 0; i < 6; i++) {
			hex.append(String.format("%02x", digest[i]));
		}
		return hex.toString();
	}
}
