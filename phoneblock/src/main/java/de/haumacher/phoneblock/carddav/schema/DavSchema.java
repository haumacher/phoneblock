/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.carddav.schema;

import static de.haumacher.phoneblock.util.DomUtil.qname;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;

/**
 * Constants defining the <code>WebDAV</code> schema.
 */
public interface DavSchema {

	String DAV_NS = "DAV:";
	String DAV_PREFIX = XMLConstants.DEFAULT_NS_PREFIX;
	
	QName DAV_MULTISTATUS = qname(DAV_NS, "multistatus", DAV_PREFIX);
	QName DAV_RESPONSE = qname(DAV_NS, "response", DAV_PREFIX);
	QName DAV_PROPSTAT = qname(DAV_NS, "propstat", DAV_PREFIX);
	QName DAV_STATUS = qname(DAV_NS, "status", DAV_PREFIX);
	QName DAV_HREF = qname(DAV_NS, "href", DAV_PREFIX);
	QName DAV_DISPLAYNAME = qname(DAV_NS, "displayname", DAV_PREFIX);
	QName DAV_RESOURCETYPE = qname(DAV_NS, "resourcetype", DAV_PREFIX);
	QName DAV_COLLECTION = qname(DAV_NS, "collection", DAV_PREFIX);
	QName DAV_GETETAG = qname(DAV_NS, "getetag", DAV_PREFIX);
	QName DAV_PROPFIND = qname(DAV_NS, "propfind", DAV_PREFIX);
	QName DAV_PROP = qname(DAV_NS, "prop", DAV_PREFIX);
	QName DAV_CURRENT_USER_PRINCIPAL = qname(DAV_NS, "current-user-principal", DAV_PREFIX);

}
