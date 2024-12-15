package de.haumacher.phoneblock.app;

import java.io.IOException;

import org.pac4j.jee.filter.LogoutFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Filter for the logout URL that removes a persistent login cookie and removes a single-signon.
 */
public class PBLogoutFilter extends LogoutFilter {

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {

		LoginFilter.removePersistentLogin((HttpServletRequest) request, (HttpServletResponse) response);
		
		super.doFilter(request, response, chain);
	}
}
