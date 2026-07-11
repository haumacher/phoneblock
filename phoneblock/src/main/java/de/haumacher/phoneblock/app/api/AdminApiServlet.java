/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import de.haumacher.phoneblock.app.UIProperties;
import de.haumacher.phoneblock.util.ServletUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * The admin console at {@code /api/admin/*}: the interactive Swagger UI and the
 * bundled OpenAPI document for PhoneBlock's internal admin/agent API. Diagnostics
 * (mapped separately at {@code /api/admin/diag/*}) is its first area; further admin
 * areas add their paths to the same spec and appear in the same UI, so the
 * individual admin servlets never carry any Swagger/OpenAPI plumbing.
 *
 * <ul>
 *   <li>{@code GET /api/admin/} — the interactive Swagger UI (assets from the
 *       {@code swagger-ui-dist} webjar, same-origin so the strict CSP's
 *       {@code script-src 'self'} is satisfied; a small
 *       {@code assets/js/admin-api.js} boots it).</li>
 *   <li>{@code GET /api/admin/openapi.json} — the OpenAPI 3.0 document. Its
 *       {@code {{CONTEXT}}} placeholder is replaced with the live context path, so
 *       the server URL and "Try it out" work on any deployment.</li>
 * </ul>
 *
 * <p>Both are served <b>unauthenticated</b> (this servlet is deliberately absent
 * from {@code BasicLoginFilter}'s guarded patterns) so the browser can load the
 * docs before a token is entered; the data endpoints they describe stay
 * token-gated. Distinct from the public {@code /api/phoneblock.json} — this is
 * internal admin tooling, not a published user contract.</p>
 */
@WebServlet(urlPatterns = AdminApiServlet.URL_PATTERN)
public class AdminApiServlet extends HttpServlet {

	/** The servlet path (the admin umbrella). */
	public static final String SERVLET_PATH = "/api/admin";

	/** The full URL pattern this servlet is mapped to. */
	public static final String URL_PATTERN = SERVLET_PATH + "/*";

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String path = req.getPathInfo() == null ? "/" : req.getPathInfo();
		if (path.equals("/openapi.json")) {
			serveOpenApiSpec(req, resp);
		} else if (path.equals("/")) {
			serveSwaggerUi(req, resp);
		} else {
			ServletUtil.sendMessage(resp, HttpServletResponse.SC_NOT_FOUND, "Unknown resource: " + path);
		}
	}

	/**
	 * Serves the bundled OpenAPI 3.0 document, replacing the {@code {{CONTEXT}}}
	 * placeholder with the live context path so the server URL is correct on any
	 * deployment (prod {@code /phoneblock}, test {@code /pb-test}, …) and Swagger
	 * "Try it out" targets the same host it was served from.
	 */
	private void serveOpenApiSpec(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		String spec;
		try (java.io.InputStream in = AdminApiServlet.class.getResourceAsStream("openapi.json")) {
			if (in == null) {
				ServletUtil.sendMessage(resp, HttpServletResponse.SC_NOT_FOUND, "OpenAPI spec not bundled.");
				return;
			}
			spec = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		spec = spec.replace("{{CONTEXT}}", req.getContextPath());
		resp.setContentType("application/json");
		resp.setCharacterEncoding("UTF-8");
		resp.getWriter().write(spec);
	}

	/**
	 * Serves the interactive Swagger UI shell. Heavy assets come from the
	 * {@code swagger-ui-dist} webjar; a small same-origin bootstrap
	 * ({@code assets/js/admin-api.js}) instantiates it against this API's spec.
	 */
	private void serveSwaggerUi(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		String ctx = req.getContextPath();
		String swagger = ctx + UIProperties.SWAGGER_PATH;
		resp.setContentType("text/html;charset=UTF-8");
		StringBuilder html = new StringBuilder(1024);
		html.append("<!DOCTYPE html>\n<html>\n<head>\n")
			.append("<meta charset=\"UTF-8\"/>\n")
			.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"/>\n")
			.append("<title>PhoneBlock Admin API</title>\n")
			.append("<link rel=\"stylesheet\" href=\"").append(swagger).append("/swagger-ui.css\"/>\n")
			.append("</head>\n<body>\n")
			.append("<section id=\"swagger-ui\"></section>\n")
			.append("<input id=\"openapi-url\" type=\"hidden\" value=\"")
			.append(ctx).append("/api/admin/openapi.json\"/>\n")
			.append("<script src=\"").append(swagger).append("/swagger-ui-bundle.js\"></script>\n")
			.append("<script src=\"").append(ctx).append("/assets/js/admin-api.js\"></script>\n")
			.append("</body>\n</html>\n");
		resp.getWriter().write(html.toString());
	}
}
