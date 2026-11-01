/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import de.haumacher.phoneblock.app.api.DeviceLinks;
import de.haumacher.phoneblock.app.api.DeviceLinks.Decision;
import de.haumacher.phoneblock.app.api.DeviceLinks.LinkInfo;
import de.haumacher.phoneblock.app.api.WrongCodeLimit;
import de.haumacher.phoneblock.app.render.TemplateRenderer;
import de.haumacher.phoneblock.db.settings.UserSettings;
import de.haumacher.phoneblock.random.SecureRandomService;
import de.haumacher.phoneblock.util.ServletUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Confirmation page of the device-code link (#621), see {@link DeviceLinks}.
 *
 * <p>
 * <b>GET /link</b> shows the code entry form. Login is required; an anonymous request is sent
 * through the normal login and comes back here. A <code>code</code> parameter is ignored: the
 * user must type the code the device shows, so a forwarded link cannot confirm someone else's
 * link (code phishing).
 * </p>
 *
 * <p>
 * <b>POST /link</b> with <code>code</code> and the session's <code>csrf</code> nonce approves the
 * link: typing in the code the device shows is the user's consent, there is no second
 * confirmation step. The result page names the linked device.
 * </p>
 */
@WebServlet(urlPatterns = LinkServlet.PATH)
public class LinkServlet extends HttpServlet {

	/** Path of the confirmation page. */
	public static final String PATH = "/link";

	/** Parameter carrying the user code. */
	public static final String CODE_PARAM = "code";

	/** Parameter carrying the CSRF nonce. */
	public static final String CSRF_PARAM = "csrf";

	private static final String TEMPLATE = "/link";

	private static final String CSRF_ATTR = "linkCsrf";

	private static final String LIMIT_ATTR = "linkWrongCodes";

	/** Template variable: <code>enter</code> or <code>done</code>. */
	private static final String VIEW_VAR = "linkView";

	/** Template variable: the {@link LinkInfo} of the linked device on the result view. */
	private static final String LINK_VAR = "link";

	/** Template variable: the code entered by the user (re-filled into the entry form). */
	private static final String CODE_VAR = "linkCode";

	/** Template variable: error key shown on the entry view. */
	private static final String ERROR_VAR = "linkError";

	/** Template variable: the e-mail address or display name of the account to link. */
	private static final String ACCOUNT_VAR = "linkAccount";

	/** Template variable: the CSRF nonce for the confirmation form. */
	private static final String CSRF_VAR = "linkCsrf";

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String user = LoginFilter.getAuthenticatedUser(req);
		if (user == null) {
			LoginServlet.requestLogin(req, resp);
			return;
		}

		// A code in the URL is ignored on purpose: the user must type the code the device shows.
		// A link that leads straight to the confirmation could be sent to a victim by whoever
		// started the link (code phishing).
		showEntry(req, resp, null, null);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String code = req.getParameter(CODE_PARAM);

		String user = LoginFilter.getAuthenticatedUser(req);
		if (user == null) {
			// Back to the entry page after login, the code must be entered again.
			resp.sendRedirect(req.getContextPath() + LoginServlet.PATH + LoginServlet.locationParamFirst(PATH));
			return;
		}

		if (!checkCsrf(req)) {
			resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
			showEntry(req, resp, code, "csrf");
			return;
		}

		if (code == null || code.isBlank()) {
			showEntry(req, resp, null, null);
			return;
		}

		WrongCodeLimit limit = limit(req);
		long now = System.currentTimeMillis();
		if (!limit.allowed(now)) {
			resp.setStatus(429);
			showEntry(req, resp, code, "rate");
			return;
		}

		// Typing in the code the device shows is the consent: no second confirmation step.
		DeviceLinks links = DeviceLinks.getInstance();
		LinkInfo link = links.find(code);
		Decision decision = link == null ? Decision.UNKNOWN : links.approve(code, user);
		switch (decision) {
			case OK:
				req.setAttribute(VIEW_VAR, "done");
				req.setAttribute(LINK_VAR, link);
				render(req, resp);
				return;
			case ALREADY_APPROVED:
				resp.setStatus(HttpServletResponse.SC_CONFLICT);
				showEntry(req, resp, code, "already-approved");
				return;
			case ALREADY_DENIED:
				resp.setStatus(HttpServletResponse.SC_CONFLICT);
				showEntry(req, resp, code, "already-denied");
				return;
			case UNKNOWN:
			default:
				limit.failed(now);
				resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
				showEntry(req, resp, code, "unknown");
				return;
		}
	}

	private void showEntry(HttpServletRequest req, HttpServletResponse resp, String code, String error) throws IOException {
		req.setAttribute(VIEW_VAR, "enter");
		req.setAttribute(CODE_VAR, code == null ? "" : code);
		req.setAttribute(CSRF_VAR, csrfToken(req.getSession()));
		req.setAttribute(ACCOUNT_VAR, accountName(req));
		if (error != null) {
			req.setAttribute(ERROR_VAR, error);
		}
		render(req, resp);
	}

	private void render(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		resp.setHeader("Cache-Control", "no-store");
		if (!TemplateRenderer.getInstance(req).process(TEMPLATE, req, resp)) {
			ServletUtil.sendMessage(resp, HttpServletResponse.SC_NOT_FOUND, "Seite nicht gefunden.");
		}
	}

	/**
	 * The name under which the user knows the account: the e-mail address, otherwise the display
	 * name (the login is a generated ID and shown separately).
	 */
	private static String accountName(HttpServletRequest req) {
		UserSettings settings = LoginFilter.getUserSettings(req);
		if (settings == null) {
			return null;
		}
		String email = settings.getEmail();
		if (email != null && !email.isBlank()) {
			return email;
		}
		String displayName = settings.getDisplayName();
		return displayName == null || displayName.isBlank() ? null : displayName;
	}

	private static WrongCodeLimit limit(HttpServletRequest req) {
		HttpSession session = req.getSession();
		synchronized (session) {
			WrongCodeLimit limit = (WrongCodeLimit) session.getAttribute(LIMIT_ATTR);
			if (limit == null) {
				limit = new WrongCodeLimit();
				session.setAttribute(LIMIT_ATTR, limit);
			}
			return limit;
		}
	}

	private static String csrfToken(HttpSession session) {
		synchronized (session) {
			String token = (String) session.getAttribute(CSRF_ATTR);
			if (token == null) {
				byte[] bytes = new byte[16];
				SecureRandomService.getInstance().getRnd().nextBytes(bytes);
				token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
				session.setAttribute(CSRF_ATTR, token);
			}
			return token;
		}
	}

	private static boolean checkCsrf(HttpServletRequest req) {
		HttpSession session = req.getSession(false);
		if (session == null) {
			return false;
		}
		String expected = (String) session.getAttribute(CSRF_ATTR);
		String actual = req.getParameter(CSRF_PARAM);
		if (expected == null || actual == null) {
			return false;
		}
		return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
	}

}
