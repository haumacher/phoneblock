/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.carddav.resource;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.xml.namespace.QName;

import de.haumacher.phoneblock.analysis.NumberBlock;
import de.haumacher.phoneblock.carddav.schema.DavSchema;

/**
 * Pre-rendered {@code <d:propstat>} fragments of one {@link NumberBlock} of a
 * shared (per-{@link ListType}) block list.
 *
 * <p>
 * Every Fritz!Box sync downloads the complete address book: a Depth: 1 PROPFIND
 * listing all cards and a series of {@code addressbook-multiget} REPORTs fetching
 * all of them, without ever sending {@code If-None-Match}. For all users sharing
 * a common list, the per-card part of these responses is identical except for the
 * {@code href}, which contains the user name. The href is rendered per request;
 * the propstat part following it is rendered once per block and requested
 * property list, and is then copied into each response.
 * </p>
 *
 * <p>
 * The fragments live as long as the {@link AddressBookCache} entry of the common
 * list they belong to, so they are dropped together with it when a new blocklist
 * release is published or the cache entry expires.
 * </p>
 */
final class BlockFragments {

	/**
	 * Maximum number of distinct property lists cached per block and response
	 * kind. Real clients use a handful of fixed requests; anything beyond is
	 * rendered on the fly instead of growing the cache.
	 */
	static final int MAX_VARIANTS = 8;

	private final ConcurrentMap<List<QName>, byte[]> _propfind = new ConcurrentHashMap<>();

	private final ConcurrentMap<List<QName>, byte[]> _multiGet = new ConcurrentHashMap<>();

	/**
	 * Whether the response to the given properties is independent of the
	 * requesting user and can therefore be shared.
	 */
	static boolean isShareable(List<QName> properties) {
		return !properties.contains(DavSchema.DAV_CURRENT_USER_PRINCIPAL);
	}

	/**
	 * The cached PROPFIND propstats for the given property list, or
	 * {@code null}, if not yet rendered.
	 */
	byte[] getPropfind(List<QName> properties) {
		return _propfind.get(properties);
	}

	/**
	 * Remembers the PROPFIND propstats for the given property list.
	 */
	void putPropfind(List<QName> properties, byte[] fragment) {
		put(_propfind, properties, fragment);
	}

	/**
	 * The cached multiget propstat for the given property list, or
	 * {@code null}, if not yet rendered.
	 */
	byte[] getMultiGet(List<QName> properties) {
		return _multiGet.get(properties);
	}

	/**
	 * Remembers the multiget propstat for the given property list.
	 */
	void putMultiGet(List<QName> properties, byte[] fragment) {
		put(_multiGet, properties, fragment);
	}

	private static void put(ConcurrentMap<List<QName>, byte[]> cache, List<QName> properties, byte[] fragment) {
		if (cache.size() < MAX_VARIANTS) {
			cache.putIfAbsent(List.copyOf(properties), fragment);
		}
	}
}
