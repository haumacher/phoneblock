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
 *  {@link Filter} disable MIME sniffing by setting  X-Content-Type-Options header.
 */
@WebFilter(urlPatterns = "/*", dispatcherTypes = DispatcherType.REQUEST)
public class ContentTypeOptionFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        if (contentTypeOption((HttpServletRequest) request)) {
            httpResponse.setHeader("X-Content-Type-Options", "nosniff");
        }
        chain.doFilter(request, response);
    }

    private static boolean contentTypeOption(HttpServletRequest request) {
        return  !request.getRequestURI().contains("/contacts");
    }
}
