/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.carddav.resource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.xml.namespace.QName;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.analysis.NumberTree;
import de.haumacher.phoneblock.blocklist.BlockList;
import de.haumacher.phoneblock.blocklist.Bucket;
import de.haumacher.phoneblock.carddav.schema.CardDavSchema;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.SpamReports;

/**
 * {@link Resource} representing a collection of {@link AddressBookResource}s.
 */
public class AddressBookResource extends Resource {

	private static final Logger LOG = LoggerFactory.getLogger(AddressBookResource.class);

	private final String _principal;
	private String _serverRoot;

	/** 
	 * Creates a {@link AddressBookResource}.
	 * 
	 * @param rootUrl The full URl (including protocol) of the CardDAV servlet.
	 * @param serverRoot The absolute path of the CardDAV servlet relative to the server.
	 */
	public AddressBookResource(String rootUrl, String serverRoot, String resourcePath, String principal) {
		super(rootUrl, resourcePath);
		_serverRoot = serverRoot;
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
		ArrayList<Resource> result = new ArrayList<>();
		for (Bucket bucket : getBlockList()) {
			result.add(new AddressResource(getRootUrl(), getResourcePath() + bucket.getIndex(), _principal, bucket));
		}
		return result;
	}

	private BlockList getBlockList() {
		BlockList blockList = new BlockList(100);
		try (SqlSession session = DBService.getInstance().openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			
			List<String> numbers = reports.getBlockList(4);
			NumberTree tree = new NumberTree();
			for (String phone : numbers) {
				tree.insert(phone);
			}
			tree.createBlockEntries(blockList);
		}
		return blockList;
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
				// Relative URL.
				return new AddressResource(rootUrl, getResourcePath() + url, _principal);
			}
		}
		if (!url.startsWith(getResourcePath(), prefixLength)) {
			LOG.warn("Received invalid contact URL outside address book '" + getResourcePath() + "': " + url);
			return null;
		}
		
		return new AddressResource(rootUrl, url.substring(prefixLength + getResourcePath().length()), _principal);
	}

	@Override
	public String getEtag() {
		return Integer.toString(getBlockList().stream().map(r -> r.hashCode()).reduce(0, (x, y) -> x + y));
	}
}
