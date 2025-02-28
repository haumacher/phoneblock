package de.haumacher.phoneblock.app.render;

import java.io.IOException;
import java.io.Writer;

import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.WebApplicationTemplateResolver;
import org.thymeleaf.web.IWebApplication;
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.IWebRequest;
import org.thymeleaf.web.servlet.IServletWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebFilter(urlPatterns = {
	"/content",
	"/content/*",
})
public class ContentFilter implements Filter {

	private JakartaServletWebApplication _application;
	private ITemplateEngine templateEngine;

	public void init(final FilterConfig filterConfig) throws ServletException {
	    this._application = JakartaServletWebApplication.buildApplication(filterConfig.getServletContext());
	    
	    // We will see later how the TemplateEngine object is built and configured
	    this.templateEngine = buildTemplateEngine(this._application);
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		
		HttpServletRequest httpRequest = (HttpServletRequest)request;
		HttpServletResponse httpResponse = (HttpServletResponse) response;
		
		String path = httpRequest.getServletPath();
		
		// Excluded paths.
		if (path.startsWith("/fragments") || 
			path.startsWith("/ab") || 
			path.startsWith("/assets") ||
			path.startsWith("/api")
		) {
			chain.doFilter(httpRequest, httpResponse);
			return;
		}
		
		// Canonicalize requested path.
		if (path.endsWith("index.html")) {
			String canonical = httpRequest.getContextPath() + path.substring(0, path.length() - "index.html".length());
			httpResponse.sendRedirect(canonical, HttpServletResponse.SC_MOVED_PERMANENTLY);
			return;
		}
		if (path.endsWith("index.jsp")) {
			String canonical = httpRequest.getContextPath() + path.substring(0, path.length() - "index.jsp".length());
			httpResponse.sendRedirect(canonical, HttpServletResponse.SC_MOVED_PERMANENTLY);
			return;
		}

		// Ensure that old-style JSP resources are still resolvable.
		if (path.endsWith(".jsp")) {
			String canonical = httpRequest.getContextPath() + path.substring(0, path.length() - ".jsp".length());
			httpResponse.sendRedirect(canonical, HttpServletResponse.SC_MOVED_PERMANENTLY);
			return;
		}
		
		// All pages are directories.
		if (!path.endsWith("/")) {
			String canonical = httpRequest.getContextPath() + path + "/";
			httpResponse.sendRedirect(canonical, HttpServletResponse.SC_MOVED_PERMANENTLY);
			return;
		}
	
		if (!process(httpRequest, httpResponse)) {
			chain.doFilter(request, response);
		}
	}

	/*
	 * Each request will be processed by creating an exchange object (modeling
	 * the request, its response and all the data needed for this process) and
	 * then calling the corresponding controller.
	 */
	private boolean process(HttpServletRequest request, HttpServletResponse response)
	        throws ServletException {
	    try {
	        final IServletWebExchange webExchange = _application.buildExchange(request, response);
	        final IWebRequest webRequest = webExchange.getRequest();

	        /*
	         * Query controller/URL mapping and obtain the controller
	         * that will process the request. If no controller is available,
	         * return false and let other filters/servlets process the request.
	         */
	        WebController controller = ControllerMappings.resolveControllerForRequest(webRequest);

	        /*
	         * Write the response headers
	         */
	        response.setContentType("text/html;charset=UTF-8");
	        response.setHeader("Pragma", "no-cache");
	        response.setHeader("Cache-Control", "no-cache");
	        response.setDateHeader("Expires", 0);

	        /*
	         * Obtain the response writer
	         */
	        final Writer writer = response.getWriter();

	        /*
	         * Execute the controller and process view template,
	         * writing the results to the response writer. 
	         */
	        controller.process(webExchange, this.templateEngine, writer);
	        return true;
	    } catch (Exception e) {
	        try {
	            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
	        } catch (final IOException ignored) {
	            // Just ignore this
	        }
	        throw new ServletException(e);
	    }
	    
	}
	
	private static ITemplateEngine buildTemplateEngine(final IWebApplication application) {
	    // Templates will be resolved as application (ServletContext) resources.
	    final WebApplicationTemplateResolver templateResolver = new WebApplicationTemplateResolver(application);

	    // HTML is the default mode, but we will set it anyway for better understanding of code
	    templateResolver.setTemplateMode(TemplateMode.HTML);
	    
	    // This will convert "home" to "/WEB-INF/templates/home.html"
	    templateResolver.setPrefix("/WEB-INF/templates/");
	    templateResolver.setSuffix(".html");
	    
	    // Set template cache TTL to 1 hour. If not set, entries would live in cache until expelled by LRU.
	    templateResolver.setCacheTTLMs(Long.valueOf(3600000L));

	    // Cache is set to true by default. Set to false if you want templates to
	    // be automatically updated when modified.
	    templateResolver.setCacheable(true);

	    TemplateEngine templateEngine = new TemplateEngine();
	    templateEngine.setTemplateResolver(templateResolver);

	    return templateEngine;

	}
}
