package de.haumacher.phoneblock.app.render.controller;

import java.io.IOException;

import de.haumacher.phoneblock.app.SettingsServlet;
import de.haumacher.phoneblock.app.render.DefaultController;
import de.haumacher.phoneblock.app.render.TemplateRenderer;
import de.haumacher.phoneblock.db.settings.AuthToken;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Controller for displaying a newly created API key.
 *
 * <p>
 * This page should only be accessed immediately after creating an API key.
 * If accessed directly without an API key in the session, redirects to settings.
 * </p>
 */
public class ShowApiKeyController extends RequireLoginController {

	/**
	 * Template path for the API key display page.
	 */
	public static final String SHOW_API_KEY_PAGE = "/show-api-key";

	@Override
	public boolean process(TemplateRenderer renderer, HttpServletRequest request, HttpServletResponse response)
			throws IOException {

		// Check if there's an API key to display
		HttpSession session = request.getSession(false);
		if (session == null) {
			// No session, redirect to settings
			response.sendRedirect(request.getContextPath() + SettingsServlet.PATH + "#myAPIKeys");
			return true;
		}

		AuthToken apiKey = (AuthToken) session.getAttribute(DefaultController.API_KEY_ATTR);
		if (apiKey == null) {
			// No API key to display, redirect to settings
			response.sendRedirect(request.getContextPath() + SettingsServlet.PATH + "#myAPIKeys");
			return true;
		}

		// API key exists, proceed with normal rendering
		return super.process(renderer, request, response);
	}
}
