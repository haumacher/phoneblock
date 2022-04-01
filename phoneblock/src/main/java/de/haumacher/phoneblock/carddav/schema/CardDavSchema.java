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
	QName CARDDAV_ADDRESSBOOK = qname(CARDDAV_NS, "addressbook");
	QName CARDDAV_ADDRESS_DATA = qname(CARDDAV_NS, "address-data");
	QName CARDDAV_ADDRESSBOOK_MULTIGET = qname(CARDDAV_NS, "addressbook-multiget");
	QName CARDDAV_ADDRESSBOOK_DESCRIPTION = qname(CARDDAV_NS, "addressbook-description");
	QName CARDDAV_SUPPORTED_ADDRESS_DATA = qname(CARDDAV_NS, "supported-address-data");
	QName CARDDAV_MAX_RESOURCE_SIZE = qname(CARDDAV_NS, "max-resource-size");
	QName CARDDAV_ADDRESSBOOK_HOME_SET = qname(CARDDAV_NS, "addressbook-home-set");

}
