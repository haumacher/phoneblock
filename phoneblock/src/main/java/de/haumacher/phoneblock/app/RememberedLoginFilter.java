package de.haumacher.phoneblock.app;

import java.io.IOException;

import de.haumacher.phoneblock.db.settings.AuthToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Filter that performs a remembered login, if possible, but does not enforce login.
 */
@WebFilter(urlPatterns = "/*")
public class RememberedLoginFilter extends LoginFilter {

	@Override
	protected void requestLogin(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		chain.doFilter(request, response);
	}
	
	@Override
	protected boolean checkTokenAuthorization(HttpServletRequest request, AuthToken authorization) {
		return authorization.isAccessLogin();
	}

}
