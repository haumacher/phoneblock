/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.haumacher.phoneblock.util.ServletUtil;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Serves the entry page of the embedded answerbot web UI (a Flutter web build) with its
 * <code>&lt;base href&gt;</code> rewritten to the actual context path.
 * 
 * <p>
 * The Flutter build is context-independent (built with <code>--base-href /ab/</code>), so one WAR
 * works for every deployment (<code>/phoneblock</code>, <code>/pb-test</code>, ...). All other
 * files of the UI are static and served by the default servlet.
 * </p>
 */
@WebServlet(urlPatterns = {
	AnswerbotIndexServlet.DIR,
	AnswerbotIndexServlet.DIR + "/",
	AnswerbotIndexServlet.INDEX,
})
public class AnswerbotIndexServlet extends HttpServlet {

	/** Directory of the answerbot UI within the web application. */
	public static final String DIR = "/ab";

	/** The entry page of the answerbot UI. */
	public static final String INDEX = DIR + "/index.html";

	private static final Pattern BASE_HREF = Pattern.compile("<base\\s+href=\"[^\"]*\"\\s*/?>");

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		if (DIR.equals(req.getServletPath()) && req.getPathInfo() == null) {
			// Relative URLs in the UI only resolve below the directory URL.
			resp.sendRedirect(req.getContextPath() + DIR + "/");
			return;
		}

		String html;
		try (InputStream in = getServletContext().getResourceAsStream(INDEX)) {
			if (in == null) {
				ServletUtil.sendMessage(resp, HttpServletResponse.SC_NOT_FOUND, "The answerbot UI is not part of this build.");
				return;
			}
			html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}

		resp.setContentType("text/html;charset=UTF-8");
		resp.setHeader("Cache-Control", "no-cache");
		resp.getWriter().write(rewriteBaseHref(html, req.getContextPath()));
	}

	/**
	 * Replaces the <code>&lt;base href&gt;</code> of the given page with the UI directory below the
	 * given context path.
	 */
	static String rewriteBaseHref(String html, String contextPath) {
		String baseHref = "<base href=\"" + contextPath + DIR + "/\">";
		return BASE_HREF.matcher(html).replaceFirst(Matcher.quoteReplacement(baseHref));
	}

}
