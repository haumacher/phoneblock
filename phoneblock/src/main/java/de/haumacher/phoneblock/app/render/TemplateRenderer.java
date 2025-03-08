package de.haumacher.phoneblock.app.render;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.web.servlet.IServletWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import de.haumacher.phoneblock.app.render.controller.LoginController;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class TemplateRenderer {

	private static final String RENDERER = "renderer";
    private static Map<String, WebController> controllersByURL;


    static {
        controllersByURL = new HashMap<String, WebController>();
        controllersByURL.put("/login/", new LoginController());
    }
    
	private JakartaServletWebApplication _application;
	private ITemplateEngine _templateEngine;

	public TemplateRenderer(JakartaServletWebApplication application, ITemplateEngine templateEngine) {
		_application = application;
		_templateEngine = templateEngine;
	}

	public void process(HttpServletRequest request, HttpServletResponse response) throws IOException {
		WebController controller = resolveControllerForRequest(request);
		process(controller, request, response);
	}
	
	public void process(String template, HttpServletRequest request, HttpServletResponse response) throws IOException {
		WebController controller = resolveController(template);
		request.setAttribute(DefaultController.RENDER_TEMPLATE, template);
		process(controller, request, response);
	}
	
    private WebController resolveControllerForRequest(final HttpServletRequest request) {
        final String path = request.getServletPath();
        return resolveController(path);
    }

	private WebController resolveController(final String path) {
		return controllersByURL.getOrDefault(path, DefaultController.INSTANCE);
	}

	public void process(WebController controller, HttpServletRequest request, HttpServletResponse response) throws IOException {
		final IServletWebExchange webExchange = _application.buildExchange(request, response);
		
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
		controller.process(webExchange, _templateEngine, writer);
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
	

}
