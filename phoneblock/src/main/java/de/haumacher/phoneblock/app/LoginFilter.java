package de.haumacher.phoneblock.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.app.render.DefaultController;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.settings.AuthToken;
import de.haumacher.phoneblock.db.settings.UserSettings;
import de.haumacher.phoneblock.shared.Language;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

public abstract class LoginFilter implements Filter {

	private static final Logger LOG = LoggerFactory.getLogger(LoginFilter.class);
	
	/**
	 * Name of the persistent cookie to identifiy a user that has enabled the "remember me" feature.
	 */
	private static final String LOGIN_COOKIE = "pb-login";
	
	private static final int ONE_YEAR_SECONDS = 60*60*24*365;

	private static final String BEARER_AUTH = "Bearer ";

	/**
	 * Attribute name for the authentication context (stored in both session and request).
	 */
	private static final String AUTH_CONTEXT_ATTR = "auth-context";
	
	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		// For backwards-compatibility.
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		HttpServletRequest req = (HttpServletRequest) request;
		HttpServletResponse resp = (HttpServletResponse) response;

		if (allowSessionAuth(req)) {
			// Short-cut to prevent authenticating every request.
			HttpSession session = req.getSession(false);
			if (session != null) {
				AuthContext authContext = getAuthContext(session);
				if (authContext != null) {
					// Copy AuthContext from session to request
					req.setAttribute(AUTH_CONTEXT_ATTR, authContext);

					loggedIn(req, resp, chain);
					return;
				}
			}
		}

		DB db = DBService.getInstance();

		if (allowCookieAuth(req)) {
			Cookie[] cookies = req.getCookies();
			if (cookies != null) {
				for (Cookie cookie : cookies) {
					if (LOGIN_COOKIE.equals(cookie.getName())) {
						String token = cookie.getValue();
						AuthContext authContext = db.checkAuthToken(token, System.currentTimeMillis(), req.getHeader("User-Agent"), true);
						if (authContext != null && checkTokenAuthorization(req, authContext.getAuthorization())) {
							String userName = authContext.getUserName();
							LOG.info("Accepted login token for user {} accessing '{}'.", userName, req.getServletPath());

							setUser(req, authContext);

							// Update cookie to extend lifetime and invalidate old version of cookie.
							// This enhances security since a token can be used only once.
							setLoginCookie(req, resp, authContext.getAuthorization());

							loggedIn(req, resp, chain);
							return;
						} else {
							if (authContext == null) {
								LOG.info("Dropping outdated login cookie accessing '{}': {}", req.getServletPath(), token);
							} else {
								LOG.info("Login not allowed with cookie accessing '{}': {}", req.getServletPath(), token);
							}
							removeLoginCookie(req, resp);
						}
						break;
					}
				}
			}
		}

		String authHeader = req.getHeader("Authorization");
		if (authHeader != null) {
			if (authHeader.startsWith(BEARER_AUTH)) {
				String token = authHeader.substring(BEARER_AUTH.length());

				AuthContext authContext = db.checkAuthToken(token, System.currentTimeMillis(), req.getHeader("User-Agent"), false);
				if (authContext != null) {
					AuthToken authorization = authContext.getAuthorization();
					String userName = authContext.getUserName();

					if (checkTokenAuthorization(req, authorization)) {
						LOG.info("Accepted bearer token {}...({}) for user {}.", token.substring(0, 16), token, authorization.getId(), userName);

						setUser(req, authContext);
						loggedIn(req, resp, chain);
						return;
					} else {
						LOG.info("Access to {} with bearer token {}... rejected due to privilege mismatch for user {}.",
								req.getServletPath(), token.substring(0, 16), userName);
					}
				}
			} else if (allowBasicAuth(req)) {
				AuthContext authContext = db.basicAuth(authHeader, req.getHeader("User-Agent"));
				if (authContext != null) {
					setUser(req, authContext);
					loggedIn(req, resp, chain);
					return;
				}
			}
		}

		// Check for token in URL parameter (for browser links from mobile app)
		String tokenParam = req.getParameter("token");
		if (tokenParam != null && !tokenParam.trim().isEmpty()) {
			AuthContext authContext = db.checkAuthToken(tokenParam, System.currentTimeMillis(), req.getHeader("User-Agent"), false);
			if (authContext != null) {
				AuthToken authorization = authContext.getAuthorization();
				String userName = authContext.getUserName();

				if (authorization.isAccessLogin() && checkTokenAuthorization(req, authorization)) {
					LOG.info("Accepted token parameter {}...({}) for user {}.", tokenParam.substring(0, 16), tokenParam, authorization.getId(), userName);

					// Create session to avoid keeping token in URL
					setUser(req, authContext);

					// Redirect to same URL without token parameter to remove it from browser history
					String redirectUrl = buildUrlWithoutTokenParameter(req);
					resp.sendRedirect(redirectUrl);
					return;
				} else {
					LOG.info("Access to {} with token parameter {}... rejected due to privilege mismatch for user {}.",
							req.getServletPath(), tokenParam.substring(0, 16), userName);
				}
			}
		}

		requestLogin(req, resp, chain);
	}

	protected void loggedIn(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		chain.doFilter(request, response);
	}

	protected boolean allowBasicAuth(HttpServletRequest req) {
		return true;
	}

	protected boolean allowSessionAuth(HttpServletRequest req) {
		return true;
	}

	protected boolean allowCookieAuth(HttpServletRequest req) {
		return true;
	}

	protected void setUser(HttpServletRequest req, AuthContext authContext) {
		setSessionUser(req, authContext);
	}

	/**
	 * Whether the given authorization token is valid for performing a login in the concrete situation.
	 */
	protected abstract boolean checkTokenAuthorization(HttpServletRequest request, AuthToken authorization);
	
	/**
	 * Handles the request, if no authentication was provided.
	 */
	protected abstract void requestLogin(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException;

	@Override
	public void destroy() {
		// For backwards-compatibility.
	}
	
	/**
	 * Sets or updates a cookie with a login token.
	 */
	public static void setLoginCookie(HttpServletRequest req, HttpServletResponse resp, AuthToken authorization) {
		Cookie loginCookie = new Cookie(LOGIN_COOKIE, authorization.getToken());
		loginCookie.setPath(req.getContextPath());
		loginCookie.setHttpOnly(true);
		loginCookie.setSecure(true);
		loginCookie.setAttribute("SameSite", "Strict");
		loginCookie.setMaxAge(ONE_YEAR_SECONDS);
		resp.addCookie(loginCookie);
	}

	/**
	 * Removes an authorization token set during login.
	 */
	public static void removePersistentLogin(HttpServletRequest request, HttpServletResponse response) {
		// Invalidate authorization token.
		HttpSession session = request.getSession(false);
		if (session != null) {
			AuthContext authContext = getAuthContext(session);
			if (authContext != null) {
				DBService.getInstance().invalidateAuthToken(authContext.getAuthorization().getId());
			}
		}

		removeLoginCookie(request, response);
	}

	private static void removeLoginCookie(HttpServletRequest request, HttpServletResponse response) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return;
		}
		for (Cookie cookie : cookies) {
			if (LOGIN_COOKIE.equals(cookie.getName())) {
				Cookie removal = new Cookie(LOGIN_COOKIE, "");
				removal.setMaxAge(0);
				response.addCookie(removal);
			}
		}
	}

	/**
	 * The user name of the authenticated user from a request attribute set by a {@link LoginFilter} for the service URI.
	 *
	 * @see BasicLoginFilter
	 */
	public static String getAuthenticatedUser(HttpServletRequest req) {
		AuthContext authContext = getAuthContext(req);
		return authContext == null ? null : authContext.getUserName();
	}

	/**
	 * The authentication context for the given request.
	 */
	public static AuthContext getAuthContext(HttpServletRequest req) {
		return (AuthContext) req.getAttribute(AUTH_CONTEXT_ATTR);
	}

	private static AuthContext getAuthContext(HttpSession session) {
		return (AuthContext) session.getAttribute(AUTH_CONTEXT_ATTR);
	}

	/**
	 * The authenticated user for the given request.
	 */
	public static AuthToken getAuthorization(HttpServletRequest req) {
		AuthContext authContext = getAuthContext(req);
		return authContext == null ? null : authContext.getAuthorization();
	}

	/**
	 * Gets the user settings from the request attribute.
	 *
	 * @return UserSettings or null if not logged in
	 */
	public static UserSettings getUserSettings(HttpServletRequest req) {
		AuthContext authContext = getAuthContext(req);
		return authContext == null ? null : authContext.getSettings();
	}

	/**
	 * Refreshes the cached user settings after an update.
	 */
	public static void refreshUserSettings(HttpServletRequest req, UserSettings settings) {
		AuthContext oldContext = getAuthContext(req);
		if (oldContext == null) {
			return;
		}

		// Create new AuthContext with updated settings
		AuthContext newContext = new AuthContext(oldContext.getAuthorization(), settings);

		// Update in request
		req.setAttribute(AUTH_CONTEXT_ATTR, newContext);

		// Update in session if available
		HttpSession session = req.getSession(false);
		if (session != null) {
			session.setAttribute(AUTH_CONTEXT_ATTR, newContext);
			Language selectedLang = Language.fromTag(settings.getLang());
			session.setAttribute(DefaultController.LANG_ATTR, selectedLang);
		}
	}

	/**
	 * Adds the given authentication context to the request and session.
	 */
	public static void setSessionUser(HttpServletRequest req, AuthContext authContext) {
		// Store in request
		req.setAttribute(AUTH_CONTEXT_ATTR, authContext);

		// Store in session
		HttpSession session = req.getSession();
		session.setAttribute(AUTH_CONTEXT_ATTR, authContext);

		Language selectedLang = Language.fromTag(authContext.getSettings().getLang());
		session.setAttribute(DefaultController.LANG_ATTR, selectedLang);

		LOG.debug("Initialized user session for '{}' in language '{}'.", authContext.getUserName(), selectedLang);
	}

	/**
	 * Adds the given authentication context to the current request (without session).
	 */
	protected static void setRequestUser(HttpServletRequest req, AuthContext authContext) {
		req.setAttribute(AUTH_CONTEXT_ATTR, authContext);
	}

	/**
	 * Builds a redirect URL from the request without the token parameter.
	 * Preserves all other query parameters and the request path.
	 */
	private static String buildUrlWithoutTokenParameter(HttpServletRequest req) {
		StringBuilder url = new StringBuilder();
		url.append(req.getContextPath());
		url.append(req.getServletPath());

		if (req.getPathInfo() != null) {
			url.append(req.getPathInfo());
		}

		// Iterate through parameters and filter out the token
		StringBuilder query = new StringBuilder();
		for (Map.Entry<String, String[]> entry : req.getParameterMap().entrySet()) {
			String name = entry.getKey();
			if (!"token".equals(name)) {
				for (String value : entry.getValue()) {
					if (query.length() > 0) {
						query.append("&");
					}
					query.append(java.net.URLEncoder.encode(name, StandardCharsets.UTF_8))
						 .append("=")
						 .append(java.net.URLEncoder.encode(value, StandardCharsets.UTF_8));
				}
			}
		}

		if (query.length() > 0) {
			url.append("?").append(query);
		}

		return url.toString();
	}

}
