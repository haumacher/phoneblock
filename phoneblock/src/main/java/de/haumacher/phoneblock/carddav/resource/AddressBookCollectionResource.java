/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.carddav.resource;

/**
 * Lightweight {@link AbstractAddressBook} that carries only the collection-
 * level metadata (ETag), without materializing per-block child resources.
 *
 * <p>
 * Used to answer Depth: 0 PROPFINDs on the address-book URL — the by far
 * dominant pattern from iOS, which polls {@code getctag}/{@code getetag} every
 * ~18 minutes and only descends to Depth: 1 when the ctag changed. Producing
 * the small Depth: 0 response from this resource avoids the construction of
 * ~1000 {@link AddressResource} wrappers per poll.
 * </p>
 *
 * <p>
 * The ETag must be byte-identical to what {@link AddressBookResource#getEtag}
 * would return for the same user — guaranteed by both being derived through
 * {@link CollectionEtag} from the same inputs.
 * </p>
 */
public class AddressBookCollectionResource extends AbstractAddressBook {

	private final String _etag;

	public AddressBookCollectionResource(String rootUrl, String resourcePath, String etag) {
		super(rootUrl, resourcePath);
		_etag = etag;
	}

	@Override
	public String getEtag() {
		return _etag;
	}
}
