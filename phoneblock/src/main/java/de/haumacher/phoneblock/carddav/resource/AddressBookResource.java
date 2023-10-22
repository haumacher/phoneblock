/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.carddav.resource;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.xml.namespace.QName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.analysis.NumberBlock;
import de.haumacher.phoneblock.carddav.schema.CardDavSchema;

/**
 * {@link Resource} representing a collection of {@link AddressBookResource}s.
 */
public class AddressBookResource extends Resource {

	private static final Logger LOG = LoggerFactory.getLogger(AddressBookResource.class);

	private final String _principal;
	private String _serverRoot;

	private final Map<String, AddressResource> _addressById;

	/** 
	 * Creates a {@link AddressBookResource}.
	 * 
	 * @param rootUrl The full URl (including protocol) of the CardDAV servlet.
	 * @param serverRoot The absolute path of the CardDAV servlet relative to the server.
	 */
	AddressBookResource(String rootUrl, String serverRoot, String resourcePath, String principal, List<NumberBlock> numbers) {
		super(rootUrl, resourcePath);
		_serverRoot = serverRoot;
		_principal = principal;
		
		_addressById = numbers
			.stream()
			.map(r -> newAddressResource(r, r.getBlockId()))
			.collect(Collectors.toMap(AddressResource::getId, r -> r));
	}

	private AddressResource newAddressResource(NumberBlock block, String id) {
		return new AddressResource(block, getRootUrl(), getResourcePath() + id, _principal);
	}

	@Override
	protected boolean isCollection() {
		return true;
	}
	
	@Override
	protected QName getResourceType() {
		return CardDavSchema.CARDDAV_ADDRESSBOOK;
	}
	
	@Override
	public Collection<? extends Resource> list() {
		return _addressById.values();
	}

	@Override
	protected String getDisplayName() {
		return "BLOCKLIST";
	}
	
	@Override
	public Resource get(String url) {
		String rootUrl = getRootUrl();
		
		int prefixLength;
		if (url.startsWith(rootUrl)) {
			prefixLength = rootUrl.length();
		} else {
			if (url.startsWith("/")) {
				if (url.startsWith(_serverRoot)) {
					prefixLength = _serverRoot.length();
				} else {
					// Invalid URL.
					LOG.warn("Received invalid absolute contact URL outside service '" + rootUrl + "': " + url);
					return null;
				}
			} else if (url.indexOf(':') >= 0) {
				// Invalid URL.
				LOG.warn("Received invalid contact URL outside server '" + rootUrl + "': " + url);
				return null;
			} else {
				return lookupAddress(url);
			}
		}
		if (!url.startsWith(getResourcePath(), prefixLength)) {
			LOG.warn("Received invalid contact URL outside address book '" + getResourcePath() + "': " + url);
			return null;
		}
		
		return lookupAddress(url.substring(prefixLength + getResourcePath().length()));
	}

	public Resource lookupAddress(String id) {
		// Relative URL.
		AddressResource result = _addressById.get(id);
		if (result == null) {
			// Maybe this is a put operation, create an empty entry.
			return newAddressResource(new NumberBlock(id), id);
		}
		return result;
	}

	@Override
	public String getEtag() {
		return Integer.toString(_addressById.keySet().stream().map(r -> r.hashCode()).reduce(0, (x, y) -> x + y));
	}
}
