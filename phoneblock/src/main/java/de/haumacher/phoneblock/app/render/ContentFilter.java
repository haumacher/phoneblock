package de.haumacher.phoneblock.app.render;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.dialect.IDialect;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.WebApplicationTemplateResolver;
import org.thymeleaf.templateresource.ITemplateResource;
import org.thymeleaf.web.IWebApplication;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import de.haumacher.phoneblock.app.AssignContributionServlet;
import de.haumacher.phoneblock.app.CreateAuthTokenServlet;
import de.haumacher.phoneblock.app.DeleteAccountServlet;
import de.haumacher.phoneblock.app.EMailVerificationServlet;
import de.haumacher.phoneblock.app.ExternalLinkServlet;
import de.haumacher.phoneblock.app.LoginServlet;
import de.haumacher.phoneblock.app.RatingServlet;
import de.haumacher.phoneblock.app.RegistrationServlet;
import de.haumacher.phoneblock.app.ResetPasswordServlet;
import de.haumacher.phoneblock.app.SearchServlet;
import de.haumacher.phoneblock.app.SettingsServlet;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebFilter(urlPatterns = {
	"/*",
})
public class ContentFilter implements Filter {
	
	private TemplateRenderer _renderer;
	
	private static final Map<String, String> LEGACY_PAGES;
	
	static {
		LEGACY_PAGES = new HashMap<>();
		LEGACY_PAGES.put("/signup.jsp", "/login.jsp");
	}

	public void init(final FilterConfig filterConfig) throws ServletException {
	    ServletContext servletContext = filterConfig.getServletContext();
	    
		JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(servletContext);
	    
	    // We will see later how the TemplateEngine object is built and configured
	    ITemplateEngine templateEngine = buildTemplateEngine(application);

	    _renderer = new TemplateRenderer(application, templateEngine);
	    _renderer.install(servletContext);
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		
		HttpServletRequest httpRequest = (HttpServletRequest)request;
		HttpServletResponse httpResponse = (HttpServletResponse) response;
		
		String path = httpRequest.getServletPath();
		
		// Forward old paths to new locations.
		String newLocation = LEGACY_PAGES.get(path);
		if (newLocation != null) {
			httpResponse.sendRedirect(httpRequest.getContextPath() + newLocation);
			return;
		}
		
		// Excluded paths.
		if (path.startsWith("/fragments") || 
			path.startsWith("/ab") || 
			path.startsWith("/assets") ||
			path.startsWith("/webjars") ||
			(path.startsWith("/api/") && !path.equals("/api/")) ||
			path.startsWith(AssignContributionServlet.PATH) ||
			path.startsWith(CreateAuthTokenServlet.CREATE_TOKEN) ||
			path.startsWith(DeleteAccountServlet.PATH) ||
			path.startsWith(EMailVerificationServlet.LOGIN_WEB) ||
			path.startsWith(EMailVerificationServlet.LOGIN_MOBILE) ||
			path.startsWith(ExternalLinkServlet.LINK_PREFIX) ||
			path.startsWith(LoginServlet.PATH) ||
			path.startsWith(SettingsServlet.ACTION_PATH) ||
			path.startsWith(RatingServlet.PATH) ||
			path.startsWith(RegistrationServlet.REGISTER_WEB) ||
			path.startsWith(RegistrationServlet.REGISTER_MOBILE) ||
			path.startsWith(ResetPasswordServlet.PATH) ||
			path.startsWith(SearchServlet.NUMS_PREFIX)  ||
			path.equals("/sitemap.jsp") ||
			path.endsWith(".js") || 
			path.endsWith(".json") 
		) {
			chain.doFilter(httpRequest, httpResponse);
			return;
		}
		
		// Canonicalize requested path.
		if (path.endsWith("index.html")) {
			String canonical = httpRequest.getContextPath() + path.substring(0, path.length() - "index.html".length());
			httpResponse.sendRedirect(canonical);
			return;
		}
		if (path.endsWith("index.jsp")) {
			String canonical = httpRequest.getContextPath() + path.substring(0, path.length() - "index.jsp".length());
			httpResponse.sendRedirect(canonical);
			return;
		}

		// Ensure that old-style JSP resources are still resolvable.
		if (path.endsWith(".jsp")) {
			String canonical = httpRequest.getContextPath() + path.substring(0, path.length() - ".jsp".length()) + "/";
			httpResponse.sendRedirect(canonical);
			return;
		}
		
		// All pages are directories.
		if (path.endsWith("/") && path.length() > 1) {
			String canonical = httpRequest.getContextPath() + path.substring(0, path.length() - 1);
			httpResponse.sendRedirect(canonical);
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
	        _renderer.process(request, response);
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
	    final WebApplicationTemplateResolver templateResolver = new WebApplicationTemplateResolver(application) {
	    	@Override
	    	protected ITemplateResource computeTemplateResource(IEngineConfiguration configuration,
	    			String ownerTemplate, String template, String resourceName, String characterEncoding,
	    			Map<String, Object> templateResolutionAttributes) {
	    		return super.computeTemplateResource(configuration, ownerTemplate, template, resourceName, characterEncoding,
	    				templateResolutionAttributes);
	    	}
	    	
	    	@Override
	    	protected String computeResourceName(IEngineConfiguration configuration, String ownerTemplate,
	    			String template, String prefix, String suffix, boolean forceSuffix,
	    			Map<String, String> templateAliases, Map<String, Object> templateResolutionAttributes) {
	    		
	    		// Allow to resolve template references relative to the calling template.
	    		if (ownerTemplate != null) {
	    			if (!template.startsWith("/")) {
	    				int dirSep = ownerTemplate.lastIndexOf('/');
	    				if (dirSep >= 0) {
	    					template = ownerTemplate.substring(0, dirSep + 1) + template;
	    				}
	    			}
	    		}
	    		
	    		return super.computeResourceName(configuration, ownerTemplate, template, prefix, suffix, forceSuffix, templateAliases,
	    				templateResolutionAttributes);
	    	}
	    };

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
	    IDialect pbDialect = new PBDialect();
		templateEngine.addDialect(pbDialect);

	    return templateEngine;

	}
}
