package de.haumacher.phoneblock.app.render;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.web.servlet.IServletWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import de.haumacher.phoneblock.app.CreateAuthTokenServlet;
import de.haumacher.phoneblock.app.SettingsServlet;
import de.haumacher.phoneblock.app.render.controller.LoginController;
import de.haumacher.phoneblock.app.render.controller.MobileLoginController;
import de.haumacher.phoneblock.app.render.controller.PhoneInfoController;
import de.haumacher.phoneblock.app.render.controller.RequireLoginController;
import de.haumacher.phoneblock.app.render.controller.SettingsController;
import de.haumacher.phoneblock.app.render.controller.ShowApiKeyController;
import de.haumacher.phoneblock.app.render.controller.ShowCredentialsController;
import de.haumacher.phoneblock.app.render.controller.StatusController;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class TemplateRenderer {

	private static final String RENDERER = "renderer";
    private static Map<String, WebController> controllersByURL;


    static {
        controllersByURL = new HashMap<String, WebController>();
        controllersByURL.put("/login", new LoginController(SettingsServlet.PATH));
        controllersByURL.put(CreateAuthTokenServlet.MOBILE_LOGIN, new MobileLoginController());
        controllersByURL.put("/status", new StatusController());
        controllersByURL.put(SettingsServlet.PATH, new SettingsController());
        controllersByURL.put("/phone-info", new PhoneInfoController());
        controllersByURL.put("/support-banktransfer", new RequireLoginController());
        controllersByURL.put("/show-credentials", new ShowCredentialsController());
        controllersByURL.put(ShowApiKeyController.SHOW_API_KEY_PAGE, new ShowApiKeyController());
    }
    
	private JakartaServletWebApplication _application;
	private ITemplateEngine _templateEngine;

	public TemplateRenderer(JakartaServletWebApplication application, ITemplateEngine templateEngine) {
		_application = application;
		_templateEngine = templateEngine;
	}

	public boolean process(HttpServletRequest request, HttpServletResponse response) throws IOException {
		WebController controller = resolveControllerForRequest(request);
		return process(controller, request, response);
	}
	
	public boolean process(String template, HttpServletRequest request, HttpServletResponse response) throws IOException {
		WebController controller = resolveController(template);
		request.setAttribute(DefaultController.RENDER_TEMPLATE, template);
		return process(controller, request, response);
	}
	
    private WebController resolveControllerForRequest(final HttpServletRequest request) {
        final String path = request.getServletPath();
        return resolveController(path);
    }

	private WebController resolveController(String path) {
		if (path.endsWith("/")) {
			path = path.substring(0, path.length() - 1);
		}
		
		return controllersByURL.getOrDefault(path, DefaultController.INSTANCE);
	}

	public boolean process(WebController controller, HttpServletRequest request, HttpServletResponse response) throws IOException {
		return controller.process(this, request, response);
	}

	public void install(ServletContext servletContext) {
	    servletContext.setAttribute(RENDERER, this);
	}
	
	public static TemplateRenderer getInstance(ServletContext servletContext) {
		return (TemplateRenderer) servletContext.getAttribute(RENDERER);
	}

	public static TemplateRenderer getInstance(HttpServletRequest req) {
		return getInstance(req.getServletContext());
	}
	
	public JakartaServletWebApplication application() {
		return _application;
	}

	public IServletWebExchange buildExchange(HttpServletRequest request, HttpServletResponse response) {
		return _application.buildExchange(request, response);
	}

	public ITemplateEngine templateEngine() {
		return _templateEngine;
	}
	

}
