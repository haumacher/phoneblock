/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.carddav.resource;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.namespace.QName;

import org.apache.ibatis.session.SqlSession;

import de.haumacher.phoneblock.carddav.schema.CardDavSchema;
import de.haumacher.phoneblock.db.BlockList;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.SpamReports;
import de.haumacher.phoneblock.db.Users;

/**
 * {@link Resource} representing a collection of {@link AddressBookResource}s.
 */
public class AddressBookResource extends Resource {

	private final String _principal;

	/** 
	 * Creates a {@link AddressBookResource}.
	 */
	public AddressBookResource(String rootUrl, String resourcePath, String principal) {
		super(rootUrl, resourcePath);
		_principal = principal;
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
		return allPhoneNumbers()
			.stream()
			.sorted()
			.map(r -> new AddressResource(getRootUrl(), getResourcePath() + r, _principal))
			.collect(Collectors.toList());
	}

	private Set<String> allPhoneNumbers() {
		try (SqlSession session = DBService.getInstance().openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			BlockList blocklist = session.getMapper(BlockList.class);
			Users users = session.getMapper(Users.class);
			
			long currentUser = users.getUserId(_principal);
			
			Set<String> result = reports.getSpamList(DB.MIN_VOTES);
			result.removeAll(blocklist.getExcluded(currentUser));
			result.addAll(blocklist.getPersonalizations(currentUser));
			
			return result;
		}
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
		return new AddressResource(getRootUrl(), url.substring(getRootUrl().length() + getResourcePath().length()), _principal);
	}

	@Override
	public String getEtag() {
		return Integer.toString(allPhoneNumbers().stream().map(r -> r.hashCode()).reduce(0, (x, y) -> x + y));
	}
}
