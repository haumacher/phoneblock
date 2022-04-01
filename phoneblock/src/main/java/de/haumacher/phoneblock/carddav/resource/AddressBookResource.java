/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.carddav.resource;

import java.util.Arrays;
import java.util.Collection;

import javax.xml.namespace.QName;

import de.haumacher.phoneblock.carddav.schema.CardDavSchema;

/**
 * TODO
 *
 * @author <a href="mailto:haui@haumacher.de">Bernhard Haumacher</a>
 */
public class AddressBookResource extends Resource {

	private static int _etag = 1;
	
	/** 
	 * Creates a {@link AddressBookResource}.
	 */
	public AddressBookResource(String rootUrl, String resourcePath, String principal) {
		super(rootUrl, resourcePath);
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
	public Collection<Resource> list() {
		return Arrays.asList(
			new AddressResource(getRootUrl(), getResourcePath() + "123456"),
			new AddressResource(getRootUrl(), getResourcePath() + "789654"));
	}
	
	@Override
	protected String getDisplayName() {
		return "BLOCKLIST";
	}
	
	@Override
	public Resource get(String url) {
		if (!url.startsWith(getRootUrl())) {
			return null;
		}
		if (!url.startsWith(getResourcePath(), getRootUrl().length())) {
			return null;
		}
		return new AddressResource(getRootUrl(), url.substring(getRootUrl().length() + getResourcePath().length()));
	}

	@Override
	protected String getEtag() {
		return Integer.toString(_etag++);
	}
}
