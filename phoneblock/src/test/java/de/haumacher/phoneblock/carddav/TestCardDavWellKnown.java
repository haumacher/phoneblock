/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.carddav;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Proxy;

import org.junit.jupiter.api.Test;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Test case for the CardDAV service discovery of {@link CardDavServlet} (RFC 6764, #524).
 */
public class TestCardDavWellKnown {

	private static final String CONTEXT = "/phoneblock";

	private static final class Result {
		Integer status;
		String location;
		String redirect;
	}

	@Test
	public void testWellKnownAtContextRoot() throws Exception {
		Result result = service("PROPFIND", "/.well-known/carddav", null);
		assertEquals(301, result.status);
		assertEquals(CONTEXT + "/contacts/", result.location);
		assertNull(result.redirect);
	}

	@Test
	public void testWellKnownBelowServletPath() throws Exception {
		Result result = service("PROPFIND", "/contacts", "/.well-known/carddav");
		assertEquals(301, result.status);
		assertEquals(CONTEXT + "/contacts/", result.location);
	}

	@Test
	public void testWellKnownWithTrailingSlash() throws Exception {
		Result result = service("GET", "/contacts", "/.well-known/carddav/");
		assertEquals(301, result.status);
		assertEquals(CONTEXT + "/contacts/", result.location);
	}

	@Test
	public void testDirectoryRedirectUnchanged() throws Exception {
		Result result = service("PROPFIND", "/contacts", null);
		assertNull(result.status);
		assertEquals(CONTEXT + "/contacts/", result.redirect);
	}

	private Result service(String method, String servletPath, String pathInfo) throws Exception {
		HttpServletRequest req = (HttpServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
			new Class<?>[] { HttpServletRequest.class }, (proxy, m, args) -> {
				switch (m.getName()) {
					case "getMethod": return method;
					case "getServletPath": return servletPath;
					case "getPathInfo": return pathInfo;
					case "getContextPath": return CONTEXT;
					default: throw new UnsupportedOperationException("Unexpected request access: " + m.getName());
				}
			});

		Result result = new Result();
		HttpServletResponse resp = (HttpServletResponse) Proxy.newProxyInstance(getClass().getClassLoader(),
			new Class<?>[] { HttpServletResponse.class }, (proxy, m, args) -> {
				switch (m.getName()) {
					case "setStatus": result.status = (Integer) args[0]; return null;
					case "setHeader":
						if ("Location".equals(args[0])) {
							result.location = (String) args[1];
						}
						return null;
					case "sendRedirect": result.redirect = (String) args[0]; return null;
					default: throw new UnsupportedOperationException("Unexpected response access: " + m.getName());
				}
			});

		new CardDavServlet().service(req, resp);
		return result;
	}

}
