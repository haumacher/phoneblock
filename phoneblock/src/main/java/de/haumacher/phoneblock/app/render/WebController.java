package de.haumacher.phoneblock.app.render;

import java.io.IOException;
import java.io.Writer;

import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.web.servlet.IServletWebExchange;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface WebController {

	void process(IServletWebExchange webExchange, ITemplateEngine templateEngine, Writer writer) throws IOException;

	default void process(TemplateRenderer renderer, HttpServletRequest request, HttpServletResponse response) throws IOException {
		final IServletWebExchange webExchange = renderer.buildExchange(request, response);
		
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
		process(webExchange, renderer.templateEngine(), writer);
	}

}
