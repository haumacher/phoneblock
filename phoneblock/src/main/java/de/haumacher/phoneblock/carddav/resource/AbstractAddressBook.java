/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.carddav.resource;

import javax.xml.namespace.QName;

import de.haumacher.phoneblock.carddav.schema.CardDavSchema;

/**
 * Common base for the two flavors of an address book collection:
 * {@link AddressBookResource} (full materialization with children) and
 * {@link AddressBookCollectionResource} (lightweight, metadata-only — used to
 * answer Depth: 0 PROPFINDs without constructing the per-child wrappers).
 *
 * <p>
 * Both flavors emit identical {@code resourcetype}, {@code displayname} and
 * {@code getctag}/{@code getetag} property values for the collection itself.
 * The difference is whether children participate in the response.
 * </p>
 */
public abstract class AbstractAddressBook extends Resource {

	public AbstractAddressBook(String rootUrl, String resourcePath) {
		super(rootUrl, resourcePath);
	}

	@Override
	protected final boolean isCollection() {
		return true;
	}

	@Override
	protected final QName getResourceType() {
		return CardDavSchema.CARDDAV_ADDRESSBOOK;
	}

	@Override
	protected final String getDisplayName() {
		return "BLOCKLIST";
	}
}
