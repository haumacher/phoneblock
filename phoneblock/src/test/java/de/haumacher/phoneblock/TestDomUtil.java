/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock;

import static de.haumacher.phoneblock.util.DomUtil.*;

import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.namespace.QName;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import de.haumacher.phoneblock.util.DomUtil;
import junit.framework.TestCase;

/**
 * Test case for {@link DomUtil}.
 */
public class TestDomUtil extends TestCase {

	public void testNavigate() throws SAXException, IOException {
		Document doc = doc("<?xml version=\"1.0\" encoding=\"utf-8\"?><d:propfind xmlns:d=\"DAV:\"><d:prop><d:current-user-principal/></d:prop></d:propfind>");
		Set<QName> names = toList(elements(doc, qname("DAV:", "propfind"), qname("DAV:", "prop"))).stream().map(DomUtil::qname).collect(Collectors.toSet());
		assertEquals(Collections.singleton(qname("DAV:", "current-user-principal")), names);
	}

	private Document doc(String xml) throws SAXException, IOException {
		return DomUtil.getBuilder().parse(new InputSource(new StringReader(xml)));
	}
	
}
