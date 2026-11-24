/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.carddav.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.namespace.QName;

import org.junit.jupiter.api.Test;

import de.haumacher.phoneblock.analysis.NumberBlock;
import de.haumacher.phoneblock.carddav.resource.AddressBookCache.CommonList;
import de.haumacher.phoneblock.carddav.schema.CardDavSchema;
import de.haumacher.phoneblock.carddav.schema.DavSchema;

/**
 * Pre-rendered per-block fragments shared between all users of a common list
 * must produce byte-identical responses to the per-request rendering.
 */
class TestBlockFragments {

	private static final String ROOT_URL = "https://phoneblock.net/phoneblock/contacts";
	private static final String SERVER_ROOT = "/phoneblock/contacts";

	/** Property list of the Fritz!Box Depth: 1 PROPFIND. */
	private static final List<QName> LIST_PROPS = List.of(
		DavSchema.DAV_RESOURCETYPE,
		DavSchema.DAV_GETETAG,
		DavSchema.DAV_DISPLAYNAME);

	/** Property list of the multiget REPORT. */
	private static final List<QName> CARD_PROPS = List.of(
		DavSchema.DAV_GETETAG,
		CardDavSchema.CARDDAV_ADDRESS_DATA);

	private static final List<QName> USER_PROPS = List.of(
		DavSchema.DAV_GETETAG,
		DavSchema.DAV_CURRENT_USER_PRINCIPAL);

	private static final NumberBlock BUCKET =
		new NumberBlock("+49301", Arrays.asList("+493011000", "+493011001", "+493011002"));
	private static final NumberBlock SINGLE =
		new NumberBlock("+493029999", List.of("+493029999"));
	private static final NumberBlock WILDCARD =
		new NumberBlock("+4930888*", List.of("+4930888*"));

	private static final CommonList COMMON = new CommonList(List.of(BUCKET, SINGLE, WILDCARD));

	private static AddressBookResource book(String user, List<NumberBlock> blocks, boolean shared) {
		return new AddressBookResource(ROOT_URL, SERVER_ROOT, "/addresses/" + user + "/", user,
			blocks, "etag-" + user, shared ? COMMON::fragments : block -> null);
	}

	private static String propfind(AddressBookResource book, String user, List<QName> props) throws Exception {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		MultiStatusWriter out = MultiStatusWriter.open(buffer);
		RenderContext ctx = new RenderContext(user);
		book.propfind(ctx, null, out, props);
		for (Resource child : sorted(book)) {
			child.propfind(ctx, book, out, props);
		}
		out.close();
		return buffer.toString(StandardCharsets.UTF_8);
	}

	private static String multiGet(AddressBookResource book, String user, List<QName> props) throws Exception {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		MultiStatusWriter out = MultiStatusWriter.open(buffer);
		List<String> hrefs = new ArrayList<>();
		for (Resource child : sorted(book)) {
			hrefs.add(child.url(book));
		}
		hrefs.add(ROOT_URL + "/elsewhere/missing");
		book.renderMultiGet(new RenderContext(user), out, hrefs, props);
		out.close();
		return buffer.toString(StandardCharsets.UTF_8);
	}

	private static List<Resource> sorted(AddressBookResource book) {
		List<Resource> result = new ArrayList<>(book.list());
		result.sort((a, b) -> a.getResourcePath().compareTo(b.getResourcePath()));
		return result;
	}

	@Test
	void sharedPropfind_matchesLiveRendering_forEveryUser() throws Exception {
		List<NumberBlock> blocks = COMMON.blocks();
		for (String user : List.of("alice", "bob", "alice")) {
			String live = propfind(book(user, blocks, false), user, LIST_PROPS);
			String shared = propfind(book(user, blocks, true), user, LIST_PROPS);
			assertEquals(live, shared, "PROPFIND for " + user);
			assertTrue(shared.contains(ROOT_URL + "/addresses/" + user + "/+49301<"), shared);
		}
	}

	@Test
	void envelope_isUnchanged() throws Exception {
		// The bytes the former StAX-written envelope produced; clients in the field parse exactly this.
		String result = propfind(book("alice", List.of(), true), "alice", LIST_PROPS);
		assertTrue(result.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
			+ "<multistatus xmlns=\"DAV:\" xmlns:c=\"urn:ietf:params:xml:ns:carddav\""
			+ " xmlns:s=\"http://calendarserver.org/ns/\"><response><href>"), result);
		assertTrue(result.endsWith("</response></multistatus>"), result);
	}

	@Test
	void sharedMultiGet_matchesLiveRendering_forEveryUser() throws Exception {
		List<NumberBlock> blocks = COMMON.blocks();
		for (String user : List.of("alice", "bob", "alice")) {
			String live = multiGet(book(user, blocks, false), user, CARD_PROPS);
			String shared = multiGet(book(user, blocks, true), user, CARD_PROPS);
			assertEquals(live, shared, "REPORT for " + user);
			assertTrue(shared.contains("TEL;TYPE=WORK:+493011002"), shared);
			assertTrue(shared.contains("HTTP/1.1 404 Not Found"), shared);
		}
	}

	@Test
	void fragmentsAreCachedPerPropertyList() throws Exception {
		CommonList common = new CommonList(List.of(BUCKET));
		AddressBookResource book = new AddressBookResource(ROOT_URL, SERVER_ROOT, "/addresses/alice/", "alice",
			common.blocks(), "etag", common::fragments);
		BlockFragments fragments = common.fragments(BUCKET);
		assertNull(fragments.getPropfind(LIST_PROPS));

		propfind(book, "alice", LIST_PROPS);
		multiGet(book, "alice", CARD_PROPS);

		assertNotNull(fragments.getPropfind(LIST_PROPS));
		assertNull(fragments.getPropfind(CARD_PROPS));
		assertNotNull(fragments.getMultiGet(CARD_PROPS));
	}

	@Test
	void userDependentProperties_areNotShared() throws Exception {
		CommonList common = new CommonList(List.of(BUCKET));
		for (String user : List.of("alice", "bob")) {
			AddressBookResource book = new AddressBookResource(ROOT_URL, SERVER_ROOT, "/addresses/" + user + "/",
				user, common.blocks(), "etag", common::fragments);
			String result = propfind(book, user, USER_PROPS);
			assertTrue(result.contains("/principals/" + user + "<"), result);
			assertFalse(result.contains("/principals/" + (user.equals("alice") ? "bob" : "alice") + "<"), result);
		}
		assertNull(common.fragments(BUCKET).getPropfind(USER_PROPS));
	}

	@Test
	void personalBlocks_areRenderedPerRequest() throws Exception {
		NumberBlock personal = new NumberBlock("+493055555", List.of("+493055555"));
		List<NumberBlock> blocks = new ArrayList<>(COMMON.blocks());
		blocks.add(personal);

		assertNull(COMMON.fragments(personal));
		// Same name as a common block, but a different instance: never served from the common cache.
		assertNull(COMMON.fragments(new NumberBlock("+493029999", List.of("+493029999"))));

		String live = propfind(book("carol", blocks, false), "carol", LIST_PROPS);
		String shared = propfind(book("carol", blocks, true), "carol", LIST_PROPS);
		assertEquals(live, shared);
		assertTrue(shared.contains("/addresses/carol/+493055555<"), shared);
	}

	@Test
	void variantsAreBounded() throws Exception {
		CommonList common = new CommonList(List.of(BUCKET));
		AddressBookResource book = new AddressBookResource(ROOT_URL, SERVER_ROOT, "/addresses/alice/", "alice",
			common.blocks(), "etag", common::fragments);
		List<QName> props = new ArrayList<>(List.of(DavSchema.DAV_GETETAG));
		for (int n = 0; n < BlockFragments.MAX_VARIANTS + 3; n++) {
			props.add(new QName("urn:test", "p" + n));
			String live = propfind(new AddressBookResource(ROOT_URL, SERVER_ROOT, "/addresses/alice/", "alice",
				common.blocks(), "etag"), "alice", props);
			assertEquals(live, propfind(book, "alice", props));
		}
		assertNull(common.fragments(BUCKET).getPropfind(props));
	}
}
