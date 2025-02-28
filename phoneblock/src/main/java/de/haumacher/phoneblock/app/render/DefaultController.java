package de.haumacher.phoneblock.app.render;

import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

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
		PROPS = new HashMap<>();
		PROPS.put("version", "PhoneBlock " + UIProperties.VERSION);
		PROPS.put("timestamp", "Version " + UIProperties.VERSION);

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
        ctx.setVariable("loggedIn", Boolean.valueOf(userName != null));
        ctx.setVariable(LoginServlet.LOCATION_ATTRIBUTE, LoginServlet.location(request));
        ctx.setVariable("currentPage", ServletUtil.currentPage(request));
        
        ctx.setVariable("title", "PhoneBlock: Der Werbeblocker fürs Telefon");
        ctx.setVariable("description", "Werbeanrufe mit Deiner Fritz!Box automatisch blockieren. PhoneBlock jetzt kostenlos installieren.");
        ctx.setVariable("keywords", "");
        
    	String path = (String) request.getAttribute("path");
    	if (path == null) {
    		path = request.getServletPath();
    		if (path.endsWith("index.html")) {
    			path = path.substring(0, path.length() - "index.html".length());
    		}
    	}
        ctx.setVariable("canonical", "https://phoneblock.net" + request.getContextPath() + path);
        ctx.setVariable("props", PROPS);
        ctx.setVariable("deps", DEPS);
        ctx.setVariable("contextPath", request.getContextPath());

        
        String suffix = path.startsWith("/content/") ? path.substring("/content/".length()) : path;
		String template = suffix.isEmpty() ? "home" : suffix;

        LOG.info("Serving template {} for {}.", template, path);
        
        templateEngine.process(template, ctx, writer);
	}

}
