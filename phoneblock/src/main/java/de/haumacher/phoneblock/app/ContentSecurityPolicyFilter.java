package de.haumacher.phoneblock.app;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Sets the Content-Security-Policy header.
 *
 * <p>
 * Registered in {@code web.xml}, not by annotation: the order of annotated
 * filters is undefined, and {@code ContentFilter} renders the pages without
 * passing them down the chain. On Tomcat it ran first, so the pages never got
 * this header. Filter mappings in {@code web.xml} precede annotated ones.
 * </p>
 *
 * <p>
 * {@code cdn.phoneblock.net} serves the dongle's browser installer (script
 * modules, firmware manifest and images).
 * </p>
 */
public class ContentSecurityPolicyFilter implements Filter {

    /**
     * Hash of the {@code <style>} block that the installer's log viewer
     * ({@code ewt-console} in esp-web-tools, mirrored to cdn.phoneblock.net)
     * writes into its shadow root. All other installer styles go through
     * adoptedStyleSheets or the CSSOM, which the policy does not restrict.
     *
     * <p>
     * Tied to the mirrored esp-web-tools version: after a version bump,
     * {@code phoneblock-dongle/firmware/scripts/mirror-installer-deps.sh} prints
     * the new value.
     * </p>
     */
    static final String INSTALLER_CONSOLE_STYLE = "sha256-b2Omuoh2JkOIanVTAfAZHwUz9plh5wcKDvK2buVGrFQ=";


    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        if (addWebsiteHeaders((HttpServletRequest) request)) {
            httpResponse.setHeader("Content-Security-Policy", "default-src 'none'; img-src 'self' http://fritz.box/favicon.ico https://fritz.box/favicon.ico data: w3.org/svg/2000; font-src 'self'; style-src 'self' '" + INSTALLER_CONSOLE_STYLE + "'; script-src 'self' https://cdn.phoneblock.net; frame-src https://www.youtube-nocookie.com; frame-ancestors 'none'; connect-src 'self' https://cdn.phoneblock.net;");
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
