package de.haumacher.phoneblock.app;

import java.io.IOException;

import de.haumacher.phoneblock.db.settings.AuthToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Filter that performs a remembered login, if possible, but does not enforce login.
 */
@WebFilter(urlPatterns = "/*")
public class RememberedLoginFilter extends LoginFilter {
	
	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		if (BasicLoginFilter.matches(((HttpServletRequest) request).getServletPath())) {
			// Prevent duplicate checking.
			chain.doFilter(request, response);
		} else {
			super.doFilter(request, response, chain);
		}
	}

	@Override
	protected void requestLogin(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		// Continue without login, login is optional.
		chain.doFilter(request, response);
	}
	
	@Override
	protected boolean checkTokenAuthorization(HttpServletRequest request, AuthToken authorization) {
		return authorization.isAccessLogin();
	}

}
