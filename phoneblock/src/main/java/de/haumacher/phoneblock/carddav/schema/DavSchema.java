/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.carddav.schema;

import static de.haumacher.phoneblock.util.DomUtil.*;

import javax.xml.namespace.QName;

/**
 * Constants defining the <code>WebDAV</code> schema.
 */
public interface DavSchema {

	String DAV_NS = "DAV:";
	
	QName DAV_MULTISTATUS = qname(DAV_NS, "multistatus");
	QName DAV_RESPONSE = qname(DAV_NS, "response");
	QName DAV_PROPSTAT = qname(DAV_NS, "propstat");
	QName DAV_STATUS = qname(DAV_NS, "status");
	QName DAV_HREF = qname(DAV_NS, "href");
	QName DAV_DISPLAYNAME = qname(DAV_NS, "displayname");
	QName DAV_RESOURCETYPE = qname(DAV_NS, "resourcetype");
	QName DAV_COLLECTION = qname(DAV_NS, "collection");
	QName DAV_GETETAG = qname(DAV_NS, "getetag");
	QName DAV_PROPFIND = qname(DAV_NS, "propfind");
	QName DAV_PROP = qname(DAV_NS, "prop");
	QName DAV_CURRENT_USER_PRINCIPAL = qname(DAV_NS, "current-user-principal");

}
