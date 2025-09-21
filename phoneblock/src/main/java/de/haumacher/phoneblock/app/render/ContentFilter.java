package de.haumacher.phoneblock.app.render;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.dialect.IDialect;
import org.thymeleaf.messageresolver.AbstractMessageResolver;
import org.thymeleaf.messageresolver.IMessageResolver;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.WebApplicationTemplateResolver;
import org.thymeleaf.templateresource.ITemplateResource;
import org.thymeleaf.web.IWebApplication;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import de.haumacher.phoneblock.app.AssignContributionServlet;
import de.haumacher.phoneblock.app.BasicLoginFilter;
import de.haumacher.phoneblock.app.CreateAuthTokenServlet;
import de.haumacher.phoneblock.app.DeleteAccountServlet;
import de.haumacher.phoneblock.app.EMailVerificationServlet;
import de.haumacher.phoneblock.app.ErrorServlet;
import de.haumacher.phoneblock.app.ExternalLinkServlet;
import de.haumacher.phoneblock.app.LoginFilter;
import de.haumacher.phoneblock.app.LoginServlet;
import de.haumacher.phoneblock.app.RatingServlet;
import de.haumacher.phoneblock.app.RegistrationServlet;
import de.haumacher.phoneblock.app.ResetPasswordServlet;
import de.haumacher.phoneblock.app.SearchServlet;
import de.haumacher.phoneblock.app.SettingsServlet;
import de.haumacher.phoneblock.app.oauth.OAuthLoginServlet;
import de.haumacher.phoneblock.carddav.CardDavServlet;
import de.haumacher.phoneblock.db.settings.AuthToken;
import de.haumacher.phoneblock.random.SecureRandomService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebFilter(urlPatterns = {
	"/*",
})
public class ContentFilter extends LoginFilter {

	private static final String POW_CHALLENGE_ATTR = "challenge";
	
	private static final String POW_SOLUTION_PARAM = "solution";

	private static final String POW_COUNTER_ATTR = "pow-counter";

	private static final int POW_VALIDITY = 10;
	
	private static final Logger LOG = LoggerFactory.getLogger(LoginFilter.class);
	
	public static final String TEMPLATES_PATH = "/WEB-INF/templates";

	public static final String TEMPLATE_SUFFIX = ".html";
	
	private TemplateRenderer _renderer;
	
	private static final Map<String, String> LEGACY_PAGES;
	
	private static final Set<String> NO_POW;
	
	static {
		LEGACY_PAGES = new HashMap<>();
		LEGACY_PAGES.put("/signup.jsp", "/login.jsp");
		
		NO_POW = new HashSet<>();
		NO_POW.add("/");
		NO_POW.add("/support");
		NO_POW.add("/login");
		NO_POW.add("/login-web");
		NO_POW.add(LoginServlet.PATH);
		NO_POW.add(RegistrationServlet.REGISTER_WEB);
		NO_POW.add(RegistrationServlet.REGISTER_MOBILE);
		NO_POW.add(RatingServlet.PATH);
		NO_POW.add(CreateAuthTokenServlet.CREATE_TOKEN);
		NO_POW.add(ErrorServlet.NOT_ALLOWED_PATH);
		NO_POW.add(ErrorServlet.NOT_AUTHENTICATED_PATH);
		NO_POW.add(ErrorServlet.NOT_FOUND_PATH);
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
		if (BasicLoginFilter.matches(((HttpServletRequest) request).getServletPath())) {
			// Prevent duplicate checking.
			chain.doFilter(request, response);
		} else {
			super.doFilter(request, response, chain);
		}
	}

	@Override
	protected boolean checkTokenAuthorization(HttpServletRequest request, AuthToken authorization) {
		return authorization.isAccessLogin();
	}

	@Override
	protected void requestLogin(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		if (!SearchServlet.isGoodBot(request)) {
			String uri = request.getRequestURI().substring(request.getContextPath().length());
			if (NO_POW.contains(uri) || uri.startsWith("/assets") || uri.startsWith("/webjars") || uri.startsWith("/oauth") || uri.startsWith("/api") || !request.getMethod().equals("GET")) {
				render(request, response, chain);
				return;
			};
			
			// Not a well-known bot and no authenticated user. This might be a problematic bulk query, request a proof of work.
			HttpSession session = request.getSession();
			Integer counter = (Integer) session.getAttribute(POW_COUNTER_ATTR);
			if (counter != null) {
				// Check still valid.
				int next = counter.intValue() - 1;
				if (next > 0) {
					session.setAttribute(POW_COUNTER_ATTR, next);
				} else {
					session.removeAttribute(POW_COUNTER_ATTR);
				}
				render(request, response, chain);
				return;
			}
			
			// Create or check prove-of-work challenge.
			String challenge = (String) session.getAttribute(POW_CHALLENGE_ATTR);
			String challengeParam = (String) request.getParameter(POW_CHALLENGE_ATTR);
			if (challenge == null || !challenge.equals(challengeParam)) {
				challenge = createChallenge();
				session.setAttribute(POW_CHALLENGE_ATTR, challenge);
				
				// Append challenge to request parameters through redirect.
			    StringBuilder redirect = new StringBuilder(request.getRequestURI());

			    boolean first = true;
			    for (Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
			    	String name = entry.getKey();
			    	
			    	// Filter out old solutions. This happens, when a page is reloaded.
					if (POW_SOLUTION_PARAM.equals(name)) {
			    		continue;
			    	}

					for (String value : entry.getValue()) {
				        redirect
				        	.append(first ? '?' : '&')
				        	.append(URLEncoder.encode(name, StandardCharsets.UTF_8))
				        	.append('=')
				        	.append(URLEncoder.encode(value, StandardCharsets.UTF_8));
				        first = false;
			    	}
			    }
			    
			    // Add challenge.
		        redirect
		        	.append(first ? '?' : '&')
		        	.append("challenge=")
		        	.append(challenge);

		        response.sendRedirect(redirect.toString());
			} else {
				String solution = request.getParameter(POW_SOLUTION_PARAM);
				if (solution != null) {
					// Challenge was used, next request creates new challenge.
					session.removeAttribute(POW_CHALLENGE_ATTR);
					
					// Check solution.
					if (solution.equals("0")) {
						LOG.warn("Prove of work computation failed.");
					} else {
						// Test validity of solution.
						try {
							MessageDigest digest = MessageDigest.getInstance("SHA-256");
							byte[] hash = digest.digest((challenge + solution).getBytes(StandardCharsets.UTF_8));
							if (hash[0] == 0 && hash[1] == 0) {
								// OK
								session.setAttribute(POW_COUNTER_ATTR, POW_VALIDITY);
								render(request, response, chain);
								return;
							} else {
								// Not OK.
							}
						} catch (NoSuchAlgorithmException ex) {
							LOG.error("No hash algorithm found.", ex);
						}
					}
					
					TemplateRenderer.getInstance(request).process("/pow-failed", request, response);
				} else {
					// No solution yet, request proof of work.
					TemplateRenderer.getInstance(request).process("/pow", request, response);
				}
			}
			
			// Request has been processed.
			return;
		}
		
		// Continue without login, login is optional.
		render(request, response, chain);
	}
	
	private String createChallenge() {
		SecureRandom rnd = SecureRandomService.getInstance().getRnd();
		StringBuilder buffer = new StringBuilder();
		for (int n = 0; n < 32; n++) {
			char ch = (char) ('A' + rnd.nextInt(26));
			buffer.append(ch);
		}
		return buffer.toString();
	}

	@Override
	protected void loggedIn(HttpServletRequest request, HttpServletResponse response, String userName, FilterChain chain)
			throws IOException, ServletException {
		render(request, response, chain);
	}
	
	private void render(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
		String path = request.getServletPath();
		
		// Redirect old paths to new locations.
		String newLocation = LEGACY_PAGES.get(path);
		if (newLocation != null) {
			response.sendRedirect(request.getContextPath() + newLocation);
			return;
		}
		
		// Excluded paths.
		if (path.startsWith("/fragments") || 
			path.startsWith("/link") || 
			path.startsWith("/ab") || 
			path.startsWith("/assets") ||
			path.startsWith("/webjars") ||
			(path.startsWith("/api/") && !path.equals("/api/")) ||
			path.startsWith(CardDavServlet.DIR_NAME) ||
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
			path.startsWith(OAuthLoginServlet.OAUTH_LOGIN_PATH)  ||
			path.equals(ErrorServlet.NOT_FOUND_PATH) ||
			path.equals(ErrorServlet.NOT_ALLOWED_PATH) ||
			path.equals(ErrorServlet.NOT_AUTHENTICATED_PATH) ||
			path.equals("/sitemap.jsp") ||
			path.endsWith(".js") || 
			path.endsWith(".json") 
		) {
			chain.doFilter(request, response);
			return;
		}
		
		// Canonicalize requested path.
		if (path.endsWith("index.html")) {
			StringBuilder canonical = canonical(request);
			canonical.append(path, 0, path.length() - "index.html".length());
			appendParams(canonical, request);
			response.sendRedirect(canonical.toString());
			return;
		}
		if (path.endsWith("index.jsp")) {
			StringBuilder canonical = canonical(request);
			canonical.append(path, 0, path.length() - "index.jsp".length());
			appendParams(canonical, request);
			response.sendRedirect(canonical.toString());
			return;
		}

		// Ensure that old-style JSP resources are still resolvable.
		if (path.endsWith(".jsp")) {
			StringBuilder canonical = canonical(request);
			canonical.append(path, 0, path.length() - ".jsp".length());
			appendParams(canonical, request);
			response.sendRedirect(canonical.toString());
			return;
		}
		
		// All pages are directories.
		if (path.endsWith("/") && path.length() > 1) {
			StringBuilder canonical = canonical(request);
			canonical.append(path, 0, path.length() - 1);
			appendParams(canonical, request);
			response.sendRedirect(canonical.toString());
			return;
		}
	
		if (!process(request, response)) {
			chain.doFilter(request, response);
		}
	}

	private static StringBuilder canonical(HttpServletRequest httpRequest) {
		StringBuilder canonical = new StringBuilder();
		canonical.append(httpRequest.getContextPath());
		return canonical;
	}

	private static void appendParams(StringBuilder canonical, ServletRequest request) {
		boolean first = true;
		for (Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
			for (String value : entry.getValue()) {
				if (first) {
					canonical.append('?');
					first = false;
				} else {
					canonical.append('&');
				}
				canonical.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
				canonical.append('=');
				canonical.append(URLEncoder.encode(value, StandardCharsets.UTF_8));
			}
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
	        return _renderer.process(request, response);
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
	    templateResolver.setPrefix(TEMPLATES_PATH);
	    templateResolver.setSuffix(TEMPLATE_SUFFIX);
	    
	    // Set template cache TTL to 1 hour. If not set, entries would live in cache until expelled by LRU.
	    templateResolver.setCacheTTLMs(Long.valueOf(3600000L));

	    // Cache is set to true by default. Set to false if you want templates to
	    // be automatically updated when modified.
	    templateResolver.setCacheable(true);

	    IMessageResolver messageResolver = new AbstractMessageResolver() {
			@Override
			public String resolveMessage(ITemplateContext context, Class<?> origin, String key, Object[] messageParameters) {
				Locale locale = context.getLocale();

				ResourceBundle bundle = ResourceBundle.getBundle("Messages", locale);
				String message = bundle.getString(key);
				
				if (messageParameters != null && messageParameters.length > 0) {
					return new MessageFormat(message).format(messageParameters);
				} else {
					return message;
				}
			}
			
			@Override
			public String createAbsentMessageRepresentation(ITemplateContext context, Class<?> origin, String key,
					Object[] messageParameters) {
				return "[" + key + messageParameters == null || messageParameters.length == 0 
						? ""
						: ("(" + Arrays.stream(messageParameters).map(Objects::toString).collect(Collectors.joining(","))+ ")") + "]";
			}
		};
	    
	    TemplateEngine templateEngine = new TemplateEngine();
	    templateEngine.setTemplateResolver(templateResolver);
		templateEngine.setMessageResolver(messageResolver);
	    IDialect pbDialect = new PBDialect();
		templateEngine.addDialect(pbDialect);

	    return templateEngine;

	}
}
