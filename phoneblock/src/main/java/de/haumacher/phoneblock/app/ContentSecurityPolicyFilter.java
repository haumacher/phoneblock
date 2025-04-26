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

@WebFilter(urlPatterns = "/*", dispatcherTypes = DispatcherType.REQUEST)
public class ContentSecurityPolicyFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        if (addWebsiteHeaders((HttpServletRequest) request)) {
            httpResponse.setHeader("Content-Security-Policy", "default-src 'none'; img-src 'self' http://fritz.box/favicon.ico https://fritz.box/favicon.ico data: w3.org/svg/2000; font-src 'self'; style-src 'self'; script-src 'self'; frame-ancestors 'none'; connect-src 'self';");
        }
        chain.doFilter(request, response);
    }

    private static boolean addWebsiteHeaders(HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        int webappIndex = request.getContextPath().length();
        
		if (requestURI.startsWith("/ab", webappIndex)) {
        	// The flutter UI is breaks with content security policy enabled.
        	return false;
        }
        
        return  !requestURI.contains("/contacts") &&
                (!requestURI.contains("/api/") || requestURI.lastIndexOf("/") == requestURI.length() - 1);
    }
}
