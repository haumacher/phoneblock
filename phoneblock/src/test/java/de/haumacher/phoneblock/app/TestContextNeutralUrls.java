/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import de.haumacher.phoneblock.util.ServletUtil;

/**
 * Test for the parts that make one WAR serve every context path (#577): the answerbot UI's base
 * href and the pac4j default URLs.
 */
class TestContextNeutralUrls {

	@Test
	void baseHrefFollowsContextPath() {
		String page = "<html><head>\n  <base href=\"/ab/\">\n  <title>PhoneBlock Anrufbeantworter</title>";
		assertEquals("<html><head>\n  <base href=\"/pb-test/ab/\">\n  <title>PhoneBlock Anrufbeantworter</title>",
				AnswerbotIndexServlet.rewriteBaseHref(page, "/pb-test"));
	}

	@Test
	void baseHrefForRootContext() {
		assertEquals("<base href=\"/ab/\">", AnswerbotIndexServlet.rewriteBaseHref("<base href=\"/phoneblock/ab/\">", ""));
	}

	@Test
	void baseHrefPlaceholderAndSelfClosingTag() {
		assertEquals("<base href=\"/phoneblock/ab/\">", AnswerbotIndexServlet.rewriteBaseHref("<base href=\"$FLUTTER_BASE_HREF\" />", "/phoneblock"));
	}

	@Test
	void pageWithoutBaseHrefIsUnchanged() {
		assertEquals("<html></html>", AnswerbotIndexServlet.rewriteBaseHref("<html></html>", "/phoneblock"));
	}

	@Test
	void contextRelativePathsGetThePrefix() {
		assertEquals("/pb-test/setup.jsp", ServletUtil.withContextPath("/pb-test", "/setup.jsp"));
		assertEquals("/pb-test/", ServletUtil.withContextPath("/pb-test", "/"));
	}

	@Test
	void prefixedRelativeAndAbsoluteUrlsAreKept() {
		assertEquals("/pb-test/setup.jsp", ServletUtil.withContextPath("/pb-test", "/pb-test/setup.jsp"));
		assertEquals("/pb-test", ServletUtil.withContextPath("/pb-test", "/pb-test"));
		assertEquals("/pb-test/pb-tester/x", ServletUtil.withContextPath("/pb-test", "/pb-tester/x"), "a path that merely shares the prefix is context-relative");
		assertEquals("setup.jsp", ServletUtil.withContextPath("/pb-test", "setup.jsp"));
		assertEquals("https://phoneblock.net/", ServletUtil.withContextPath("/pb-test", "https://phoneblock.net/"));
		assertEquals("//cdn.example/x", ServletUtil.withContextPath("/pb-test", "//cdn.example/x"));
	}

	@Test
	void rootContextLeavesPathsAlone() {
		assertEquals("/setup.jsp", ServletUtil.withContextPath("", "/setup.jsp"));
	}
}
