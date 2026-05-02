/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.carddav;

import static de.haumacher.phoneblock.util.DomUtil.createDocumentBuilder;
import static de.haumacher.phoneblock.util.DomUtil.elements;
import static de.haumacher.phoneblock.util.DomUtil.filter;
import static de.haumacher.phoneblock.util.DomUtil.qname;
import static de.haumacher.phoneblock.util.DomUtil.qnames;
import static de.haumacher.phoneblock.util.DomUtil.toList;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import de.haumacher.phoneblock.carddav.schema.CardDavSchema;
import de.haumacher.phoneblock.carddav.schema.DavSchema;

/**
 * Parses CardDAV request bodies (PROPFIND, REPORT addressbook-multiget) into
 * plain Java values. Pure functions over an {@link InputStream}, no servlet
 * dependency.
 */
public final class CardDavRequestParser {

	private CardDavRequestParser() {
		// no instances
	}

	/**
	 * Parses a {@code <d:propfind>} body into the list of requested property
	 * names.
	 */
	public static List<QName> parsePropfindBody(InputStream body) throws IOException, SAXException {
		DocumentBuilder builder = createDocumentBuilder();
		Document doc = builder.parse(body);
		return qnames(toList(elements(doc, DavSchema.DAV_PROPFIND, DavSchema.DAV_PROP)));
	}

	/**
	 * Parses a {@code <c:addressbook-multiget>} body into the list of addressed
	 * resource URLs and the list of requested property names.
	 *
	 * @return {@code null} if the body is not an
	 *         {@code addressbook-multiget} report.
	 */
	public static MultiGetRequest parseMultiGetBody(InputStream body) throws IOException, SAXException {
		DocumentBuilder builder = createDocumentBuilder();
		Document doc = builder.parse(body);
		if (!CardDavSchema.CARDDAV_ADDRESSBOOK_MULTIGET.equals(qname(doc.getDocumentElement()))) {
			return null;
		}

		List<QName> properties = qnames(toList(
			elements(doc, CardDavSchema.CARDDAV_ADDRESSBOOK_MULTIGET, DavSchema.DAV_PROP)));

		List<String> hrefs = new ArrayList<>();
		for (Element href : filter(
				elements(filter(elements(doc), CardDavSchema.CARDDAV_ADDRESSBOOK_MULTIGET)),
				DavSchema.DAV_HREF)) {
			hrefs.add(href.getTextContent());
		}

		return new MultiGetRequest(Collections.unmodifiableList(hrefs),
			Collections.unmodifiableList(properties));
	}

	/**
	 * Parsed result of a {@code <c:addressbook-multiget>} body.
	 */
	public record MultiGetRequest(List<String> hrefs, List<QName> properties) {
	}
}
