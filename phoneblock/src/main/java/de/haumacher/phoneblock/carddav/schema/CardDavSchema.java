/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.carddav.schema;

import static de.haumacher.phoneblock.util.DomUtil.*;

import javax.xml.namespace.QName;

/**
 * Constants defining the <code>carddav</code> schema.
 */
public interface CardDavSchema {

	String CARDDAV_NS = "urn:ietf:params:xml:ns:carddav";
	String CARDDAV_PREFIX = "c";
	
	QName CARDDAV_ADDRESSBOOK = qname(CARDDAV_NS, "addressbook", CARDDAV_PREFIX);
	QName CARDDAV_ADDRESS_DATA = qname(CARDDAV_NS, "address-data", CARDDAV_PREFIX);
	QName CARDDAV_ADDRESSBOOK_MULTIGET = qname(CARDDAV_NS, "addressbook-multiget", CARDDAV_PREFIX);
	QName CARDDAV_ADDRESSBOOK_DESCRIPTION = qname(CARDDAV_NS, "addressbook-description", CARDDAV_PREFIX);
	QName CARDDAV_SUPPORTED_ADDRESS_DATA = qname(CARDDAV_NS, "supported-address-data", CARDDAV_PREFIX);
	QName CARDDAV_MAX_RESOURCE_SIZE = qname(CARDDAV_NS, "max-resource-size", CARDDAV_PREFIX);
	QName CARDDAV_ADDRESSBOOK_HOME_SET = qname(CARDDAV_NS, "addressbook-home-set", CARDDAV_PREFIX);

}
