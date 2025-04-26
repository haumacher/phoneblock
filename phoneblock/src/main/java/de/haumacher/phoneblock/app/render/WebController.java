package de.haumacher.phoneblock.app.render;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface WebController {

	boolean process(TemplateRenderer renderer, HttpServletRequest request, HttpServletResponse response) throws IOException;

}
