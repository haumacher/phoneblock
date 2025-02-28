package de.haumacher.phoneblock.app.render;

import java.io.Writer;
import java.util.HashMap;
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
import de.haumacher.phoneblock.app.UIProperties;
import de.haumacher.phoneblock.util.ServletUtil;
import jakarta.servlet.http.HttpServletRequest;

public class DefaultController implements WebController {
	
	private static final Logger LOG = LoggerFactory.getLogger(DefaultController.class);

	public static final WebController INSTANCE = new DefaultController();
	
	private static final Map<String, Object> PROPS;
	private static final Map<String, Object> DEPS;
	
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
	public void process(IServletWebExchange webExchange, ITemplateEngine templateEngine, Writer writer) {
        WebContext ctx = new WebContext(webExchange, webExchange.getLocale());
        
        HttpServletRequest request = (HttpServletRequest) webExchange.getNativeRequestObject();
		String userName = LoginFilter.getAuthenticatedUser(request.getSession(false));
        ctx.setVariable("userName", userName);
        ctx.setVariable("supporterId", userName == null ? null : "PhoneBlock-" + userName.substring(0, 13));
        ctx.setVariable("loggedIn", Boolean.valueOf(userName != null));
        ctx.setVariable(LoginServlet.LOCATION_ATTRIBUTE, LoginServlet.location(request));
        ctx.setVariable("currentPage", ServletUtil.currentPage(request));
        
        ctx.setVariable("title", "PhoneBlock: Der Werbeblocker fürs Telefon");
        ctx.setVariable("description", "Werbeanrufe mit Deiner Fritz!Box automatisch blockieren. PhoneBlock jetzt kostenlos installieren.");
        ctx.setVariable("keywords", "");
        
		String path = request.getServletPath();
    		
        ctx.setVariable("canonical", "https://phoneblock.net" + request.getContextPath() + path);
        ctx.setVariable("props", PROPS);
        ctx.setVariable("deps", DEPS);
        ctx.setVariable("contextPath", request.getContextPath());

        
        String suffix = path.startsWith("/content/") ? path.substring("/content/".length()) : path;
		String template = suffix.isEmpty() ? "index" : suffix;

        LOG.info("Serving template {} for {}.", template, path);
        
        templateEngine.process(template, ctx, writer);
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
