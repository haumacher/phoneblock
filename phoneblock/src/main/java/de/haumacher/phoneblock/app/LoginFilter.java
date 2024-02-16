package de.haumacher.phoneblock.app;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import de.haumacher.phoneblock.db.DBService;

public abstract class LoginFilter implements Filter {

	public static final String AUTHENTICATED_USER_ATTR = "authenticated-user";
	
	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		// For backwards-compatibility.
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		HttpServletRequest req = (HttpServletRequest) request;
		HttpServletResponse resp = (HttpServletResponse) response;
		
		// Short-cut to prevent authenticating every request.
		HttpSession session = req.getSession(false);
		if (session != null) {
			String userName = getAuthenticatedUser(session);
			if (userName != null) {
				req.setAttribute(AUTHENTICATED_USER_ATTR, userName);
				chain.doFilter(request, response);
				return;
			}
		}

		String authHeader = req.getHeader("Authorization");
		if (authHeader != null) {
			String userName = DBService.getInstance().basicAuth(authHeader);
			if (userName != null) {
				setAuthenticatedUser(req, userName);
				chain.doFilter(request, response);
				return;
			}
		}
		
		requestLogin(req, resp, chain);
	}
	
	/**
	 * Handles the request, if no authentication was provided.
	 */
	protected abstract void requestLogin(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException;

	@Override
	public void destroy() {
		// For backwards-compatibility.
	}
	
	/** 
	 * The authenticated user of the given session.
	 */
	public static String getAuthenticatedUser(HttpSession session) {
		if (session == null) {
			return null;
		}
		return (String) session.getAttribute(AUTHENTICATED_USER_ATTR);
	}

	/** 
	 * Adds the given user name to the request and session.
	 */
	public static final void setAuthenticatedUser(HttpServletRequest req, String userName) {
		req.setAttribute(AUTHENTICATED_USER_ATTR, userName);
		req.getSession().setAttribute(AUTHENTICATED_USER_ATTR, userName);
	}


}
