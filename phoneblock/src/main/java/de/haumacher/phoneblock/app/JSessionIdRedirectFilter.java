package de.haumacher.phoneblock.app;

import java.io.IOException;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Redirects requests whose URI still carries a {@code ;jsessionid=...} path
 * parameter to the canonical URL without it, so search-engine crawlers
 * (which do not send cookies) stop indexing duplicate URLs.
 */
@WebFilter(urlPatterns = "/*", dispatcherTypes = DispatcherType.REQUEST)
public class JSessionIdRedirectFilter implements Filter {

	private static final String JSESSIONID_TOKEN = ";jsessionid=";

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		HttpServletRequest httpRequest = (HttpServletRequest) request;
		String uri = httpRequest.getRequestURI();

		int idx = indexOfJSessionId(uri);
		if (idx < 0) {
			chain.doFilter(request, response);
			return;
		}

		String cleanUri = stripJSessionId(uri, idx);
		String query = httpRequest.getQueryString();
		String target = (query != null && !query.isEmpty()) ? cleanUri + "?" + query : cleanUri;

		HttpServletResponse httpResponse = (HttpServletResponse) response;
		httpResponse.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
		httpResponse.setHeader("Location", target);
	}

	private static int indexOfJSessionId(String uri) {
		int semi = uri.indexOf(';');
		if (semi < 0) {
			return -1;
		}
		// Case-insensitive match for ";jsessionid=".
		if (uri.regionMatches(true, semi, JSESSIONID_TOKEN, 0, JSESSIONID_TOKEN.length())) {
			return semi;
		}
		return -1;
	}

	private static String stripJSessionId(String uri, int start) {
		// A URI may carry further path parameters after the jsessionid, separated
		// by another ';'. Drop only the jsessionid segment, keep the rest.
		int end = uri.indexOf(';', start + 1);
		if (end < 0) {
			return uri.substring(0, start);
		}
		return uri.substring(0, start) + uri.substring(end);
	}
}
