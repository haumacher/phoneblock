/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.api;

import java.io.IOException;

import de.haumacher.msgbuf.json.JsonReader;
import de.haumacher.msgbuf.server.io.ReaderAdapter;
import de.haumacher.phoneblock.app.Application;
import de.haumacher.phoneblock.app.LinkServlet;
import de.haumacher.phoneblock.app.UIProperties;
import de.haumacher.phoneblock.app.api.model.LinkPollRequest;
import de.haumacher.phoneblock.app.api.model.LinkPollResponse;
import de.haumacher.phoneblock.app.api.model.LinkStartRequest;
import de.haumacher.phoneblock.app.api.model.LinkStartResponse;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.util.ServletUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Anonymous JSON API of the device-code link (#621), see {@link DeviceLinks}.
 *
 * <p>
 * <b>POST /api/link/start</b> with <code>{"appId": "...", "label": "..."}</code> answers
 * {@link LinkStartResponse}, or <code>429</code> when a limit for open links is reached.
 * </p>
 *
 * <p>
 * <b>POST /api/link/poll</b> with <code>{"deviceCode": "..."}</code> answers
 * {@link LinkPollResponse}.
 * </p>
 *
 * <p>
 * Neither endpoint requires authentication: the device has no credential yet. The device code is
 * the secret that binds the poll to the start.
 * </p>
 */
@WebServlet(urlPatterns = { LinkApiServlet.START_PATH, LinkApiServlet.POLL_PATH })
public class LinkApiServlet extends HttpServlet {

	/** Path of the start endpoint. */
	public static final String START_PATH = "/api/link/start";

	/** Path of the poll endpoint. */
	public static final String POLL_PATH = "/api/link/poll";

	private static final String DEFAULT_BASE = "https://phoneblock.net";

	private final DeviceLinks _links;

	/**
	 * Creates the servlet working on the application's {@link DeviceLinks}.
	 */
	public LinkApiServlet() {
		this(DeviceLinks.getInstance());
	}

	/**
	 * Creates the servlet working on the given store (for tests).
	 */
	LinkApiServlet(DeviceLinks links) {
		_links = links;
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		switch (req.getServletPath()) {
			case START_PATH:
				start(req, resp);
				break;
			case POLL_PATH:
				poll(req, resp);
				break;
			default:
				ServletUtil.sendMessage(resp, HttpServletResponse.SC_NOT_FOUND, "Unbekannter Endpunkt.");
		}
	}

	private void start(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		LinkStartRequest request;
		try {
			request = LinkStartRequest.readLinkStartRequest(new JsonReader(new ReaderAdapter(req.getReader())));
		} catch (IOException | RuntimeException ex) {
			ServletUtil.sendMessage(resp, HttpServletResponse.SC_BAD_REQUEST, "Ungültige Anfrage: JSON erwartet (Content-Type: application/json).");
			return;
		}

		String appId = request.getAppId();
		if (appId == null || appId.isBlank()) {
			ServletUtil.sendMessage(resp, HttpServletResponse.SC_BAD_REQUEST, "Das Feld 'appId' fehlt.");
			return;
		}

		DeviceLinks.Started started;
		try {
			started = _links.start(appId, request.getLabel(), ServletUtil.clientAddress(req));
		} catch (DeviceLinks.LimitExceeded ex) {
			ServletUtil.sendMessage(resp, 429, ex.getMessage());
			return;
		}

		ServletUtil.sendResult(req, resp, LinkStartResponse.create()
			.setDeviceCode(started.deviceCode())
			.setUserCode(started.userCode())
			.setVerifyUrl(verifyUrl(publicBaseUrl()))
			.setInterval(started.interval())
			.setExpiresIn(started.expiresIn()));
	}

	private void poll(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		LinkPollRequest request;
		try {
			request = LinkPollRequest.readLinkPollRequest(new JsonReader(new ReaderAdapter(req.getReader())));
		} catch (IOException | RuntimeException ex) {
			ServletUtil.sendMessage(resp, HttpServletResponse.SC_BAD_REQUEST, "Ungültige Anfrage: JSON erwartet (Content-Type: application/json).");
			return;
		}

		String deviceCode = request.getDeviceCode();
		if (deviceCode == null || deviceCode.isEmpty()) {
			ServletUtil.sendMessage(resp, HttpServletResponse.SC_BAD_REQUEST, "Das Feld 'deviceCode' fehlt.");
			return;
		}

		DeviceLinks.PollResult result = _links.poll(deviceCode, req.getHeader("User-Agent"),
			(user, now, userAgent, label) -> DBService.getInstance().createAPIToken(user, now, userAgent, label).getToken());

		LinkPollResponse response = LinkPollResponse.create().setStatus(result.status());
		if (result.token() != null) {
			response.setToken(result.token());
		}
		ServletUtil.sendResult(req, resp, response);
	}

	/**
	 * The public base URL of this application (protocol, host and context path).
	 *
	 * <p>
	 * Taken from the configured <code>oauth/callbackBase</code> (the public protocol and host of
	 * this deployment, default <code>https://phoneblock.net</code>) and the context path, never
	 * from the request: the device may reach the server under an internal name that the user's
	 * browser cannot open.
	 * </p>
	 */
	public static String publicBaseUrl() {
		String base = UIProperties.APP_PROPERTIES.getProperty(UIProperties.OAUTH_CALLBACK_BASE, DEFAULT_BASE);
		if (base.endsWith("/")) {
			base = base.substring(0, base.length() - 1);
		}
		return base + Application.getContextPath();
	}

	/**
	 * The URL of the page on which the user enters the code.
	 *
	 * <p>
	 * Deliberately without the code: the user must type the code the device shows. A link with
	 * the code in it would confirm someone else's link with one click when forwarded to a victim.
	 * </p>
	 */
	static String verifyUrl(String baseUrl) {
		return baseUrl + LinkServlet.PATH;
	}

}
