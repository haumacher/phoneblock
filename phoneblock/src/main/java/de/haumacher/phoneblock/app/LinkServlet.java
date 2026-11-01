/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import java.io.IOException;
import java.net.URLEncoder;
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
 * <b>GET /link?code=XXXX-XXXX</b> shows the device label and the code with the buttons to confirm
 * or refuse the link; without a code, or with a code that is not open, a code entry form. Login
 * is required; an anonymous request is sent through the normal login and comes back with the
 * code.
 * </p>
 *
 * <p>
 * <b>POST /link</b> with <code>code</code>, <code>action</code> (<code>approve</code> or
 * <code>deny</code>) and the session's <code>csrf</code> nonce records the decision and shows the
 * result page.
 * </p>
 */
@WebServlet(urlPatterns = LinkServlet.PATH)
public class LinkServlet extends HttpServlet {

	/** Path of the confirmation page. */
	public static final String PATH = "/link";

	/** Parameter carrying the user code. */
	public static final String CODE_PARAM = "code";

	/** Parameter selecting the decision. */
	public static final String ACTION_PARAM = "action";

	/** Parameter carrying the CSRF nonce. */
	public static final String CSRF_PARAM = "csrf";

	/** {@link #ACTION_PARAM} value confirming the link. */
	public static final String ACTION_APPROVE = "approve";

	/** {@link #ACTION_PARAM} value refusing the link. */
	public static final String ACTION_DENY = "deny";

	private static final String TEMPLATE = "/link";

	private static final String CSRF_ATTR = "linkCsrf";

	private static final String LIMIT_ATTR = "linkWrongCodes";

	/** Template variable: <code>confirm</code>, <code>enter</code> or <code>done</code>. */
	private static final String VIEW_VAR = "linkView";

	/** Template variable: the {@link LinkInfo} shown on the confirmation view. */
	private static final String LINK_VAR = "link";

	/** Template variable: the code entered by the user (re-filled into the entry form). */
	private static final String CODE_VAR = "linkCode";

	/** Template variable: error key shown on the entry view. */
	private static final String ERROR_VAR = "linkError";

	/** Template variable: <code>approved</code> or <code>denied</code> on the result view. */
	private static final String RESULT_VAR = "linkResult";

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

		String code = req.getParameter(CODE_PARAM);
		if (code == null || code.isBlank()) {
			showEntry(req, resp, null, null);
			return;
		}

		WrongCodeLimit limit = limit(req);
		long now = System.currentTimeMillis();
		if (!limit.allowed(now)) {
			showEntry(req, resp, code, "rate");
			return;
		}

		LinkInfo link = DeviceLinks.getInstance().find(code);
		if (link == null) {
			limit.failed(now);
			showEntry(req, resp, code, "unknown");
			return;
		}

		switch (link.state()) {
			case APPROVED:
				showEntry(req, resp, code, "already-approved");
				return;
			case DENIED:
				showEntry(req, resp, code, "already-denied");
				return;
			case PENDING:
			default:
				showConfirm(req, resp, link);
		}
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String code = req.getParameter(CODE_PARAM);

		String user = LoginFilter.getAuthenticatedUser(req);
		if (user == null) {
			// Back to the confirmation page after login, the decision must be made again.
			String location = PATH;
			if (code != null && !code.isBlank()) {
				location += "?" + CODE_PARAM + "=" + URLEncoder.encode(code, StandardCharsets.UTF_8);
			}
			resp.sendRedirect(req.getContextPath() + LoginServlet.PATH + LoginServlet.locationParamFirst(location));
			return;
		}

		if (!checkCsrf(req)) {
			resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
			showEntry(req, resp, code, "csrf");
			return;
		}

		String action = req.getParameter(ACTION_PARAM);
		boolean approve;
		if (ACTION_APPROVE.equals(action)) {
			approve = true;
		} else if (ACTION_DENY.equals(action)) {
			approve = false;
		} else {
			resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			showEntry(req, resp, code, "invalid");
			return;
		}

		WrongCodeLimit limit = limit(req);
		long now = System.currentTimeMillis();
		if (!limit.allowed(now)) {
			resp.setStatus(429);
			showEntry(req, resp, code, "rate");
			return;
		}

		DeviceLinks links = DeviceLinks.getInstance();
		Decision decision = approve ? links.approve(code, user) : links.deny(code, user);
		switch (decision) {
			case OK:
				req.setAttribute(VIEW_VAR, "done");
				req.setAttribute(RESULT_VAR, approve ? "approved" : "denied");
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

	private void showConfirm(HttpServletRequest req, HttpServletResponse resp, LinkInfo link) throws IOException {
		req.setAttribute(VIEW_VAR, "confirm");
		req.setAttribute(ACCOUNT_VAR, accountName(req));
		req.setAttribute(LINK_VAR, link);
		req.setAttribute(CSRF_VAR, csrfToken(req.getSession()));
		render(req, resp);
	}

	private void showEntry(HttpServletRequest req, HttpServletResponse resp, String code, String error) throws IOException {
		req.setAttribute(VIEW_VAR, "enter");
		req.setAttribute(CODE_VAR, code == null ? "" : code);
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
