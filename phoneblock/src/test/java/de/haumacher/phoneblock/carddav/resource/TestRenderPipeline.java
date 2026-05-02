/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.carddav.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLStreamWriter;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import de.haumacher.phoneblock.analysis.NumberBlock;
import de.haumacher.phoneblock.carddav.schema.CardDavSchema;
import de.haumacher.phoneblock.carddav.schema.DavSchema;

/**
 * Smoke tests for the StAX-based render pipeline. Constructs fixture resources,
 * renders a multistatus response into a {@link ByteArrayOutputStream}, parses
 * the bytes back into a DOM and asserts the structural shape.
 *
 * Per Phase 1 of {@code 2026-05-02-carddav-render-pipeline.md}: enough
 * coverage to confirm the new pipeline produces correctly-namespaced,
 * structurally-equivalent output. Goldfile-level coverage (Ebene 1) follows
 * in a later commit.
 */
class TestRenderPipeline {

	private static final String ROOT_URL = "https://phoneblock.net/phoneblock/contacts";
	private static final String SERVER_ROOT = "/phoneblock/contacts";

	private static final List<QName> COLLECTION_PROPS = List.of(
		DavSchema.DAV_RESOURCETYPE,
		DavSchema.DAV_DISPLAYNAME,
		DavSchema.DAV_GETETAG,
		CardDavSchema.CALENDARSERVER_GETCTAG);

	private static final List<QName> ADDRESS_PROPS = List.of(
		DavSchema.DAV_GETETAG,
		CardDavSchema.CARDDAV_ADDRESS_DATA);

	private static AddressBookResource fixtureBook() {
		List<NumberBlock> blocks = List.of(
			new NumberBlock("+491521", Arrays.asList("+491521010", "+491521011")),
			new NumberBlock("+493012", Arrays.asList("+493012345")));
		return new AddressBookResource(ROOT_URL, SERVER_ROOT,
			"/addresses/alice/", "alice", blocks,
			CollectionEtag.forFullPipeline(blocks, 42));
	}

	private static Document render(RenderHook hook) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		XMLStreamWriter writer = MultiStatusWriter.open(out);
		try {
			hook.write(writer);
		} finally {
			MultiStatusWriter.close(writer);
		}
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		return factory.newDocumentBuilder().parse(new ByteArrayInputStream(out.toByteArray()));
	}

	@FunctionalInterface
	private interface RenderHook {
		void write(XMLStreamWriter writer) throws Exception;
	}

	@Test
	void multistatusRoot_isCorrectlyNamespaced() throws Exception {
		Document doc = render(writer -> {
			// Just open and close the envelope.
		});
		Element root = doc.getDocumentElement();
		assertEquals("multistatus", root.getLocalName());
		assertEquals(DavSchema.DAV_NS, root.getNamespaceURI());
	}

	@Test
	void addressBook_propfind_writesOwnResponse() throws Exception {
		AddressBookResource book = fixtureBook();
		RenderContext ctx = new RenderContext("alice");

		Document doc = render(writer -> book.propfind(ctx, null, writer, COLLECTION_PROPS));

		NodeList responses = doc.getElementsByTagNameNS(DavSchema.DAV_NS, "response");
		assertEquals(1, responses.getLength());

		Element response = (Element) responses.item(0);
		String href = singleText(response, DavSchema.DAV_NS, "href");
		assertEquals(ROOT_URL + "/addresses/alice/", href);

		// resourcetype contains <d:collection/> + <c:addressbook/>
		Element resourceType = descendant(response, DavSchema.DAV_NS, "resourcetype");
		assertNotNull(resourceType);
		assertNotNull(childElement(resourceType, DavSchema.DAV_NS, "collection"));
		assertNotNull(childElement(resourceType, CardDavSchema.CARDDAV_NS, "addressbook"));

		// displayname = BLOCKLIST
		assertEquals("BLOCKLIST", singleText(response, DavSchema.DAV_NS, "displayname"));

		// getetag and getctag carry the same hash; quote only on getetag.
		String quotedEtag = singleText(response, DavSchema.DAV_NS, "getetag");
		String unquotedEtag = singleText(response, CardDavSchema.CALENDARSERVER_NS, "getctag");
		assertTrue(quotedEtag.startsWith("\"") && quotedEtag.endsWith("\""), "getetag should be quoted: " + quotedEtag);
		assertEquals(quotedEtag, "\"" + unquotedEtag + "\"");
		assertEquals(book.getEtag(), unquotedEtag);

		// All four propstat blocks have status 200.
		NodeList propstats = response.getElementsByTagNameNS(DavSchema.DAV_NS, "propstat");
		assertEquals(COLLECTION_PROPS.size(), propstats.getLength());
		for (int i = 0; i < propstats.getLength(); i++) {
			Element propstat = (Element) propstats.item(i);
			assertEquals("HTTP/1.1 200 OK", singleText(propstat, DavSchema.DAV_NS, "status"));
		}
	}

	@Test
	void addressBook_children_renderedFromList() throws Exception {
		AddressBookResource book = fixtureBook();
		RenderContext ctx = new RenderContext("alice");

		Document doc = render(writer -> {
			for (Resource child : book.list()) {
				child.propfind(ctx, book, writer, ADDRESS_PROPS);
			}
		});

		NodeList responses = doc.getElementsByTagNameNS(DavSchema.DAV_NS, "response");
		assertEquals(2, responses.getLength());

		for (int i = 0; i < responses.getLength(); i++) {
			Element response = (Element) responses.item(i);
			String href = singleText(response, DavSchema.DAV_NS, "href");
			assertTrue(href.startsWith(ROOT_URL + "/addresses/alice/"), "child href: " + href);

			String addressData = singleText(response, CardDavSchema.CARDDAV_NS, "address-data");
			assertTrue(addressData.startsWith("BEGIN:VCARD"), "address-data should be vCard: " + addressData);
		}
	}

	@Test
	void addressBook_renderMultiGet_mixesFoundAndMissing() throws Exception {
		AddressBookResource book = fixtureBook();
		RenderContext ctx = new RenderContext("alice");

		String existingHref = ROOT_URL + "/addresses/alice/+491521";
		// Out-of-collection URL — get(url) rejects it and renderMultiGet emits a 404.
		// (A within-collection but unknown id would return a stub resource for the
		// PUT path, which is not what this test asserts.)
		String missingHref = ROOT_URL + "/elsewhere/foo";

		Document doc = render(writer -> book.renderMultiGet(ctx, writer,
			List.of(existingHref, missingHref), List.of(CardDavSchema.CARDDAV_ADDRESS_DATA)));

		NodeList responses = doc.getElementsByTagNameNS(DavSchema.DAV_NS, "response");
		assertEquals(2, responses.getLength());

		Element first = (Element) responses.item(0);
		assertEquals(existingHref, singleText(first, DavSchema.DAV_NS, "href"));
		assertEquals("HTTP/1.1 200 OK", singleText(first, DavSchema.DAV_NS, "status"));
		assertNotNull(descendant(first, DavSchema.DAV_NS, "prop"));

		Element second = (Element) responses.item(1);
		assertEquals(missingHref, singleText(second, DavSchema.DAV_NS, "href"));
		assertEquals("HTTP/1.1 404 Not Found", singleText(second, DavSchema.DAV_NS, "status"));
		// 404 case: no <d:prop> at all, just status.
		assertNull(descendant(second, DavSchema.DAV_NS, "prop"));
	}

	@Test
	void resource_unsupportedProperty_writes404Status() throws Exception {
		AddressBookResource book = fixtureBook();
		RenderContext ctx = new RenderContext("alice");
		QName unsupported = new QName(DavSchema.DAV_NS, "principal-URL", "");

		Document doc = render(writer -> book.propfind(ctx, null, writer, List.of(unsupported)));

		Element response = (Element) doc.getElementsByTagNameNS(DavSchema.DAV_NS, "response").item(0);
		Element propstat = childElement(response, DavSchema.DAV_NS, "propstat");
		assertEquals("HTTP/1.1 404 Not Found", singleText(propstat, DavSchema.DAV_NS, "status"));
		Element prop = childElement(propstat, DavSchema.DAV_NS, "prop");
		assertNotNull(prop);
		// Empty prop element on 404.
		assertEquals(0, prop.getElementsByTagName("*").getLength());
	}

	@Test
	void principal_currentUserPrincipal_emitsHrefForAuthenticated() throws Exception {
		PrincipalResource principal = new PrincipalResource(ROOT_URL, "/principals/alice", "alice");
		RenderContext ctx = new RenderContext("alice");

		Document doc = render(writer -> principal.propfind(ctx, null, writer,
			List.of(DavSchema.DAV_CURRENT_USER_PRINCIPAL,
				CardDavSchema.CARDDAV_ADDRESSBOOK_HOME_SET)));

		Element response = (Element) doc.getElementsByTagNameNS(DavSchema.DAV_NS, "response").item(0);

		Element cup = descendant(response, DavSchema.DAV_NS, "current-user-principal");
		assertEquals(ROOT_URL + "/principals/alice", singleText(cup, DavSchema.DAV_NS, "href"));

		Element home = descendant(response, CardDavSchema.CARDDAV_NS, "addressbook-home-set");
		assertEquals(ROOT_URL + "/addresses/alice/", singleText(home, DavSchema.DAV_NS, "href"));
	}

	/**
	 * Phase-3 regression: the lightweight {@link AddressBookCollectionResource}
	 * must render byte-identical Depth: 0 output to the heavy
	 * {@link AddressBookResource} when both carry the same ETag — otherwise
	 * the two paths would emit different XML for the same client poll.
	 */
	@Test
	void addressBookCollection_lightweight_matchesHeavyForCollectionProps() throws Exception {
		AddressBookResource heavy = fixtureBook();
		RenderContext ctx = new RenderContext("alice");

		AddressBookCollectionResource light = new AddressBookCollectionResource(
			ROOT_URL, "/addresses/alice/", heavy.getEtag());

		Document heavyDoc = render(writer -> heavy.propfind(ctx, null, writer, COLLECTION_PROPS));
		Document lightDoc = render(writer -> light.propfind(ctx, null, writer, COLLECTION_PROPS));

		Element heavyResponse = (Element) heavyDoc.getElementsByTagNameNS(DavSchema.DAV_NS, "response").item(0);
		Element lightResponse = (Element) lightDoc.getElementsByTagNameNS(DavSchema.DAV_NS, "response").item(0);

		// Property values must coincide one-by-one.
		assertEquals(singleText(heavyResponse, DavSchema.DAV_NS, "displayname"),
			singleText(lightResponse, DavSchema.DAV_NS, "displayname"));
		assertEquals(singleText(heavyResponse, DavSchema.DAV_NS, "getetag"),
			singleText(lightResponse, DavSchema.DAV_NS, "getetag"));
		assertEquals(singleText(heavyResponse, CardDavSchema.CALENDARSERVER_NS, "getctag"),
			singleText(lightResponse, CardDavSchema.CALENDARSERVER_NS, "getctag"));

		// resourcetype contents are identical (both have collection + addressbook children).
		Element heavyType = descendant(heavyResponse, DavSchema.DAV_NS, "resourcetype");
		Element lightType = descendant(lightResponse, DavSchema.DAV_NS, "resourcetype");
		assertNotNull(childElement(lightType, DavSchema.DAV_NS, "collection"));
		assertNotNull(childElement(lightType, CardDavSchema.CARDDAV_NS, "addressbook"));
		assertNotNull(childElement(heavyType, DavSchema.DAV_NS, "collection"));
		assertNotNull(childElement(heavyType, CardDavSchema.CARDDAV_NS, "addressbook"));
	}

	@Test
	void addressBookCollection_lightweight_listIsEmpty() {
		AddressBookCollectionResource light = new AddressBookCollectionResource(
			ROOT_URL, "/addresses/alice/", "abc123");
		assertEquals(0, light.list().size());
	}

	@Test
	void principal_currentUserPrincipal_unauthenticated_returns404() throws Exception {
		PrincipalResource principal = new PrincipalResource(ROOT_URL, "/principals/alice", "alice");
		RenderContext ctx = new RenderContext(null);

		Document doc = render(writer -> principal.propfind(ctx, null, writer,
			List.of(DavSchema.DAV_CURRENT_USER_PRINCIPAL)));

		Element response = (Element) doc.getElementsByTagNameNS(DavSchema.DAV_NS, "response").item(0);
		Element propstat = childElement(response, DavSchema.DAV_NS, "propstat");
		assertEquals("HTTP/1.1 404 Not Found", singleText(propstat, DavSchema.DAV_NS, "status"));
	}

	/**
	 * Returns the text content of the first descendant element with the given
	 * qualified name within {@code parent}. Asserts the element exists.
	 */
	private static String singleText(Element parent, String ns, String localName) {
		Element child = descendant(parent, ns, localName);
		assertNotNull(child, () -> "Expected descendant {" + ns + "}" + localName);
		return child.getTextContent();
	}

	/**
	 * First descendant element with the given qualified name, or {@code null}.
	 */
	private static Element descendant(Element parent, String ns, String localName) {
		if (parent == null) {
			return null;
		}
		NodeList all = parent.getElementsByTagNameNS(ns, localName);
		return all.getLength() == 0 ? null : (Element) all.item(0);
	}

	private static Element childElement(Element parent, String ns, String localName) {
		if (parent == null) {
			return null;
		}
		NodeList children = parent.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			if (children.item(i) instanceof Element child
				&& ns.equals(child.getNamespaceURI())
				&& localName.equals(child.getLocalName())) {
				return child;
			}
		}
		return null;
	}
}
