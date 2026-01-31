package de.haumacher.phoneblock.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.app.render.DefaultController;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.Users;
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

	/**
	 * Session attribute storing the ID of the authorization token used to log in.
	 */
	public static final String AUTHORIZATION_ATTR = "tokenId";

	private static final String BEARER_AUTH = "Bearer ";

	private static final String AUTHENTICATED_USER_ATTR = "authenticated-user";

	/**
	 * Attribute name for cached user settings (used in both session and request).
	 */
	protected static final String USER_SETTINGS_ATTR = "user-settings";
	
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
				AuthToken authorization = LoginFilter.getAuthorization(session);
				if (authorization != null) {
					setRequestUser(req, authorization);

					// Copy UserSettings from session to request
					UserSettings settings = getUserSettings(session);
					if (settings != null) {
						req.setAttribute(USER_SETTINGS_ATTR, settings);
					}

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
						AuthToken authorization = db.checkAuthToken(token, System.currentTimeMillis(), req.getHeader("User-Agent"), true);
						if (authorization != null && checkTokenAuthorization(req, authorization)) {
							String userName = authorization.getUserName();
							LOG.info("Accepted login token for user {} accessing '{}'.", userName, req.getServletPath());
							
							setUser(req, authorization);
							
							// Update cookie to extend lifetime and invalidate old version of cookie. 
							// This enhances security since a token can be used only once.
							setLoginCookie(req, resp, authorization);
							
							loggedIn(req, resp, chain);
							return;
						} else {
							if (authorization == null) {
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

				AuthToken authorization = db.checkAuthToken(token, System.currentTimeMillis(), req.getHeader("User-Agent"), false);
				if (authorization != null) {
					String userName = authorization.getUserName();

					if (checkTokenAuthorization(req, authorization)) {
						LOG.info("Accepted bearer token {}...({}) for user {}.", token.substring(0, 16), token, authorization.getId(), userName);

						setUser(req, authorization);
						loggedIn(req, resp, chain);
						return;
					} else {
						LOG.info("Access to {} with bearer token {}... rejected due to privilege mismatch for user {}.",
								req.getServletPath(), token.substring(0, 16), userName);
					}
				}
			} else if (allowBasicAuth(req)) {
				AuthToken authorization = db.basicAuth(authHeader, req.getHeader("User-Agent"));
				if (authorization != null) {
					setUser(req, authorization);
					loggedIn(req, resp, chain);
					return;
				}
			}
		}

		// Check for token in URL parameter (for browser links from mobile app)
		String tokenParam = req.getParameter("token");
		if (tokenParam != null && !tokenParam.trim().isEmpty()) {
			AuthToken authorization = db.checkAuthToken(tokenParam, System.currentTimeMillis(), req.getHeader("User-Agent"), false);
			if (authorization != null) {
				String userName = authorization.getUserName();

				if (authorization.isAccessLogin() && checkTokenAuthorization(req, authorization)) {
					LOG.info("Accepted token parameter {}...({}) for user {}.", tokenParam.substring(0, 16), tokenParam, authorization.getId(), userName);

					// Create session to avoid keeping token in URL
					setUser(req, authorization);

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

	protected void setUser(HttpServletRequest req, AuthToken authorization) {
		setSessionUser(req, authorization);
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
		
		req.getSession().setAttribute(AUTHORIZATION_ATTR, authorization);
	}

	/**
	 * Removes an authorization token set during login.
	 */
	public static void removePersistentLogin(HttpServletRequest request, HttpServletResponse response) {
		// Invalidate authorization token.
		HttpSession session = request.getSession(false);
		if (session != null) {
			AuthToken token = (AuthToken) session.getAttribute(AUTHORIZATION_ATTR);
			if (token != null) {
				DBService.getInstance().invalidateAuthToken(token.getId());
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
		AuthToken authorization = getAuthorization(req);
		return authorization == null ? null : authorization.getUserName();
	}

	/** 
	 * The authenticated user for the given request.
	 */
	public static AuthToken getAuthorization(HttpServletRequest req) {
		return (AuthToken) req.getAttribute(AUTHENTICATED_USER_ATTR);
	}
	
	private static AuthToken getAuthorization(HttpSession session) {
		return (AuthToken) session.getAttribute(AUTHENTICATED_USER_ATTR);
	}

	/**
	 * Gets the cached user settings from the request or session.
	 *
	 * @return UserSettings or null if not logged in
	 */
	public static UserSettings getUserSettings(HttpServletRequest req) {
		// First try request attribute (set by filter for every request)
		UserSettings settings = (UserSettings) req.getAttribute(USER_SETTINGS_ATTR);
		if (settings != null) {
			return settings;
		}
		// Fallback to session for backwards compatibility
		HttpSession session = req.getSession(false);
		return session != null ? getUserSettings(session) : null;
	}

	private static UserSettings getUserSettings(HttpSession session) {
		return (UserSettings) session.getAttribute(USER_SETTINGS_ATTR);
	}

	/**
	 * Refreshes the cached user settings after an update.
	 */
	public static void refreshUserSettings(HttpServletRequest req, UserSettings settings) {
		// Update in request
		req.setAttribute(USER_SETTINGS_ATTR, settings);

		// Update in session if available
		HttpSession session = req.getSession(false);
		if (session != null) {
			session.setAttribute(USER_SETTINGS_ATTR, settings);
			Language selectedLang = DefaultController.selectLanguage(settings.getLang());
			session.setAttribute(DefaultController.LANG_ATTR, selectedLang);
		}
	}

	/**
	 * Adds the given user name to the request and session.
	 */
	public static void setSessionUser(HttpServletRequest req, AuthToken authorization) {
		setRequestUser(req, authorization);

		HttpSession session = req.getSession();
		session.setAttribute(AUTHENTICATED_USER_ATTR, authorization);

		DB db = DBService.getInstance();
		UserSettings settings = db.getSettings(authorization.getUserName());

		// Store in both session and request
		session.setAttribute(USER_SETTINGS_ATTR, settings);
		req.setAttribute(USER_SETTINGS_ATTR, settings);

		Language selectedLang = DefaultController.selectLanguage(settings.getLang());
		session.setAttribute(DefaultController.LANG_ATTR, selectedLang);

		LOG.debug("Initialized user session for '{}' in language '{}'.", authorization.getUserName(), selectedLang);
	}

	/**
	 * Adds the given user name to the current request (without session).
	 */
	public static void setRequestUser(HttpServletRequest req, AuthToken authorization) {
		req.setAttribute(AUTHENTICATED_USER_ATTR, authorization);
	}

	/**
	 * Adds the given user settings to the current request (without session).
	 */
	protected static void setRequestUserSettings(HttpServletRequest req, UserSettings settings) {
		req.setAttribute(USER_SETTINGS_ATTR, settings);
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
