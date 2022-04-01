/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.carddav.resource;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import javax.xml.namespace.QName;

import de.haumacher.phoneblock.carddav.schema.CardDavSchema;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.SpamReport;

/**
 * TODO
 *
 * @author <a href="mailto:haui@haumacher.de">Bernhard Haumacher</a>
 */
public class AddressBookResource extends Resource {

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
		return allReports().stream().map(
			r -> new AddressResource(getRootUrl(), getResourcePath() + r.getPhone())).collect(Collectors.toList());
	}

	private List<SpamReport> allReports() {
		return DBService.getInstance().getSpamReports(3);
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
		return Integer.toString(allReports().stream().map(r -> r.getPhone().hashCode()).reduce(0, (x, y) -> x + y));
	}
}
