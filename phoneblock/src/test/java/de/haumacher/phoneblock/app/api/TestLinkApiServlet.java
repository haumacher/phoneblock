/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.haumacher.msgbuf.json.JsonReader;
import de.haumacher.msgbuf.server.io.ReaderAdapter;
import de.haumacher.phoneblock.app.Application;
import de.haumacher.phoneblock.app.UIProperties;
import de.haumacher.phoneblock.app.api.model.LinkPollResponse;
import de.haumacher.phoneblock.app.api.model.LinkStartResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Test case for {@link LinkApiServlet} with minimal request/response stand-ins.
 */
public class TestLinkApiServlet {

	private static final String BASE = "https://pb.example.invalid";

	private String _oldBase;
	private DeviceLinks _links;
	private LinkApiServlet _servlet;

	@BeforeEach
	public void setUp() {
		_oldBase = UIProperties.APP_PROPERTIES.getProperty(UIProperties.OAUTH_CALLBACK_BASE);
		UIProperties.APP_PROPERTIES.setProperty(UIProperties.OAUTH_CALLBACK_BASE, BASE);

		_links = new DeviceLinks(new AtomicLong(1_000_000)::get, new SecureRandom());
		_servlet = new LinkApiServlet(_links);
	}

	@AfterEach
	public void tearDown() {
		if (_oldBase == null) {
			UIProperties.APP_PROPERTIES.remove(UIProperties.OAUTH_CALLBACK_BASE);
		} else {
			UIProperties.APP_PROPERTIES.setProperty(UIProperties.OAUTH_CALLBACK_BASE, _oldBase);
		}
	}

	@Test
	public void testVerifyUrlFromConfiguredBase() throws Exception {
		Result result = post(LinkApiServlet.START_PATH, "{\"appId\": \"PhoneBlockDongle\", \"label\": \"Dongle\"}", "192.0.2.20", null);
		assertEquals(200, result.status);

		LinkStartResponse response = LinkStartResponse.readLinkStartResponse(reader(result.body));
		assertEquals(BASE + Application.getContextPath() + "/link", response.getVerifyUrl());
		assertTrue(!response.getVerifyUrl().contains(response.getUserCode()));
		assertTrue(!response.getVerifyUrl().contains("internal.lan"));
		assertEquals(43, response.getDeviceCode().length());
		assertEquals(5, response.getInterval());
		assertEquals(600, response.getExpiresIn());
	}

	@Test
	public void testPerClientLimit() throws Exception {
		for (int n = 0; n < DeviceLinks.MAX_PER_CLIENT; n++) {
			assertEquals(200, post(LinkApiServlet.START_PATH, "{\"appId\": \"PhoneBlockDongle\"}", "192.0.2.20", null).status);
		}
		Result refused = post(LinkApiServlet.START_PATH, "{\"appId\": \"PhoneBlockDongle\"}", "192.0.2.20", null);
		assertEquals(429, refused.status);
		assertTrue(!refused.body.isEmpty());

		// Another client succeeds.
		assertEquals(200, post(LinkApiServlet.START_PATH, "{\"appId\": \"PhoneBlockDongle\"}", "192.0.2.21", null).status);

		// Behind the proxy, the address appended by the proxy counts, not the one the client claims.
		assertEquals(429, post(LinkApiServlet.START_PATH, "{\"appId\": \"PhoneBlockDongle\"}", "127.0.0.1", "203.0.113.1, 192.0.2.20").status);
	}

	@Test
	public void testBadRequests() throws Exception {
		assertEquals(400, post(LinkApiServlet.START_PATH, "not json", "192.0.2.20", null).status);
		assertEquals(400, post(LinkApiServlet.START_PATH, "{}", "192.0.2.20", null).status);
		assertEquals(400, post(LinkApiServlet.START_PATH, "{\"appId\": \"\"}", "192.0.2.20", null).status);
		assertEquals(400, post(LinkApiServlet.POLL_PATH, "{}", "192.0.2.20", null).status);
		assertEquals(400, post(LinkApiServlet.POLL_PATH, "[", "192.0.2.20", null).status);
		assertEquals(0, _links.size());
	}

	@Test
	public void testPollUnknown() throws Exception {
		Result result = post(LinkApiServlet.POLL_PATH, "{\"deviceCode\": \"does-not-exist\"}", "192.0.2.20", null);
		assertEquals(200, result.status);
		assertEquals("{\"status\":\"expired\"}", result.body.replace(" ", ""));
		assertEquals("expired", LinkPollResponse.readLinkPollResponse(reader(result.body)).getStatus());
	}

	@Test
	public void testPollPending() throws Exception {
		LinkStartResponse started = LinkStartResponse.readLinkStartResponse(reader(
			post(LinkApiServlet.START_PATH, "{\"appId\": \"PhoneBlockDongle\"}", "192.0.2.20", null).body));
		Result result = post(LinkApiServlet.POLL_PATH, "{\"deviceCode\": \"" + started.getDeviceCode() + "\"}", "192.0.2.20", null);
		assertEquals("{\"status\":\"pending\"}", result.body.replace(" ", ""));
	}

	private static JsonReader reader(String json) {
		return new JsonReader(new ReaderAdapter(new StringReader(json)));
	}

	private static final class Result {
		int status = 200;
		final StringWriter buffer = new StringWriter();
		String body;
	}

	private Result post(String path, String body, String remoteAddr, String forwardedFor) throws Exception {
		Map<String, String> headers = new HashMap<>();
		headers.put("host", "internal.lan:8080");
		headers.put("user-agent", "PhoneBlockDongle/test");
		if (forwardedFor != null) {
			headers.put("x-forwarded-for", forwardedFor);
		}

		HttpServletRequest req = (HttpServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
			new Class<?>[] { HttpServletRequest.class }, (proxy, method, args) -> {
				switch (method.getName()) {
					case "getServletPath": return path;
					case "getMethod": return "POST";
					case "getReader": return new BufferedReader(new StringReader(body));
					case "getHeader": return headers.get(((String) args[0]).toLowerCase());
					case "getRemoteAddr": return remoteAddr;
					case "getServerName": return "internal.lan";
					case "getServerPort": return 8080;
					case "getScheme": return "http";
					case "getContextPath": return Application.getContextPath();
					case "getRequestURL": return new StringBuffer("http://internal.lan:8080" + Application.getContextPath() + path);
					default: return defaultValue(method.getReturnType());
				}
			});

		Result result = new Result();
		PrintWriter writer = new PrintWriter(result.buffer);
		HttpServletResponse resp = (HttpServletResponse) Proxy.newProxyInstance(getClass().getClassLoader(),
			new Class<?>[] { HttpServletResponse.class }, (proxy, method, args) -> {
				switch (method.getName()) {
					case "setStatus": result.status = (Integer) args[0]; return null;
					case "getStatus": return result.status;
					case "getWriter": return writer;
					case "sendError": throw new IOException("sendError must not be used for POST.");
					default: return defaultValue(method.getReturnType());
				}
			});

		_servlet.doPost(req, resp);
		writer.flush();
		result.body = result.buffer.toString();
		return result;
	}

	private static Object defaultValue(Class<?> type) {
		if (type == boolean.class) return Boolean.FALSE;
		if (type == int.class) return 0;
		if (type == long.class) return 0L;
		return null;
	}
}
