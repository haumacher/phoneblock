package de.haumacher.phoneblock.app.render;

import java.io.Writer;

import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.web.servlet.IServletWebExchange;

public interface WebController {

	void process(IServletWebExchange webExchange, ITemplateEngine templateEngine, Writer writer);

}
