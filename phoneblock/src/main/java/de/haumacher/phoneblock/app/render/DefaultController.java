package de.haumacher.phoneblock.app.render;

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.web.servlet.IServletWebExchange;

import de.haumacher.phoneblock.app.LoginFilter;
import de.haumacher.phoneblock.app.LoginServlet;
import de.haumacher.phoneblock.app.RegistrationServlet;
import de.haumacher.phoneblock.app.UIProperties;
import de.haumacher.phoneblock.app.api.model.Rating;
import de.haumacher.phoneblock.util.ServletUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

public class DefaultController implements WebController {
	
	static final String RENDER_TEMPLATE = "renderTemplate";

	private static final Logger LOG = LoggerFactory.getLogger(DefaultController.class);

	public static final WebController INSTANCE = new DefaultController();
	
	private static final Map<String, Object> PROPS;
	private static final Map<String, Object> DEPS;

	private static final List<RatingDisplay> RATINGS = Arrays.asList(Rating.values()).stream()
		.map(r -> new RatingDisplay(r))
		.toList();
	
	static {
		PROPS = toModel(UIProperties.APP_PROPERTIES);

		DEPS = new HashMap<>();
		DEPS.put("bulma", UIProperties.BULMA_PATH);
		DEPS.put("bulmaCalendar", UIProperties.BULMA_CALENDAR_PATH);
		DEPS.put("bulmaCollapsible", UIProperties.BULMA_COLLAPSIBLE_PATH);
		DEPS.put("chartjs", UIProperties.CHARTJS_PATH);
		DEPS.put("fontawesome", UIProperties.FA_PATH);
		DEPS.put("jquery", UIProperties.JQUERY_PATH);
		DEPS.put("swagger", UIProperties.SWAGGER_PATH);
	}

	@Override
	public void process(IServletWebExchange webExchange, ITemplateEngine templateEngine, Writer writer) throws IOException {
        WebContext ctx = new WebContext(webExchange, webExchange.getLocale());
        
        HttpServletRequest request = (HttpServletRequest) webExchange.getNativeRequestObject();
        fillContext(ctx, request);

        String template = (String) request.getAttribute(RENDER_TEMPLATE);
        if (template == null) {
        	String path = request.getServletPath();
        	String prefix = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        	String suffix = prefix.startsWith("/") ? prefix.substring(1) : prefix;
        	
        	template = suffix.isEmpty() ? "index" : suffix;

        	LOG.debug("Serving template {} for {}.", template, path);
        }
        
        templateEngine.process(template, ctx, writer);
	}

	protected void fillContext(WebContext ctx, HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		String userName = LoginFilter.getAuthenticatedUser(session);
  		String token = RegistrationServlet.getPassword(session);
		
        ctx.setVariable("userName", userName);
        ctx.setVariable("token", token);
        ctx.setVariable("supporterId", userName == null ? null : "PhoneBlock-" + userName.substring(0, 13));
        ctx.setVariable("loggedIn", Boolean.valueOf(userName != null));
        ctx.setVariable(LoginServlet.LOCATION_ATTRIBUTE, LoginServlet.location(request));
        ctx.setVariable("currentPage", ServletUtil.currentPage(request));
        
        ctx.setVariable("title", "PhoneBlock: Der Werbeblocker fürs Telefon");
        ctx.setVariable("description", "Werbeanrufe mit Deiner Fritz!Box automatisch blockieren. PhoneBlock jetzt kostenlos installieren.");
        ctx.setVariable("keywords", "");
        
		ctx.setVariable("canonical", "https://phoneblock.net" + request.getContextPath() + request.getServletPath());
        ctx.setVariable("props", PROPS);
        ctx.setVariable("deps", DEPS);
        ctx.setVariable("contextPath", request.getContextPath());
        
    	String userAgent = request.getHeader("User-Agent");
    	userAgent = userAgent == null ? "" : userAgent.toLowerCase();
    	boolean android = userAgent.contains("android");
    	boolean iphone = userAgent.contains("iPhone");
    	
    	ctx.setVariable("android", Boolean.valueOf(android));
		ctx.setVariable("iphone", Boolean.valueOf(iphone));
		ctx.setVariable("fritzbox", Boolean.valueOf(!android && !iphone));
		
		ctx.setVariable("ratings", RATINGS);
	}

	
	/**
	 * Converts {@link Properties} with '.'-structured keys to nested maps. 
	 */
	static Map<String, Object> toModel(Properties appProperties) {
		Map<String, Object> result = new HashMap<>();
		
		for (Entry<Object, Object> entry : appProperties.entrySet()) {
			String key = (String) entry.getKey();
			
			Map<String, Object> parent = result;
			String last = null;
			for (String part : key.split("\\.")) {
				if (last != null) {
					@SuppressWarnings("unchecked")
					Map<String, Object> inner = (Map<String, Object>) parent.get(last);
					if (inner == null) {
						inner = new HashMap<>();
					}
					parent.put(last, inner);
					
					parent = inner;
				}
				
				last = part;
			}
			
			parent.put(last, entry.getValue());
		}
		
		return result;
	}

}
