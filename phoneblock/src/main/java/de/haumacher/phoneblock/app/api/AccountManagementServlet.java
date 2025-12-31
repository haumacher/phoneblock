/*
 * Copyright (c) 2025 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.api;

import java.io.IOException;
import java.util.regex.Pattern;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.msgbuf.json.JsonReader;
import de.haumacher.msgbuf.json.JsonWriter;
import de.haumacher.msgbuf.server.io.ReaderAdapter;
import de.haumacher.msgbuf.server.io.WriterAdapter;
import de.haumacher.phoneblock.app.LoginFilter;
import de.haumacher.phoneblock.app.api.model.AccountSettings;
import de.haumacher.phoneblock.app.api.model.UpdateAccountRequest;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.DBUserSettings;
import de.haumacher.phoneblock.db.Users;
import de.haumacher.phoneblock.location.Countries;
import de.haumacher.phoneblock.location.model.Country;
import de.haumacher.phoneblock.util.ServletUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * API servlet for managing user account settings from mobile applications.
 *
 * <p>
 * Allows mobile apps to update user preferences such as language and country dial prefix
 * during initial setup. Requires Bearer token authentication.
 * </p>
 *
 * <p>
 * <b>GET /api/account</b> - Retrieve current account settings
 * </p>
 *
 * <p>
 * <b>PUT /api/account</b> - Update account settings<br>
 * Request body (JSON): {"lang": "de-DE", "dialPrefix": "+49", "displayName": "John Doe"}
 * </p>
 */
@WebServlet(urlPatterns = AccountManagementServlet.PATH)
public class AccountManagementServlet extends HttpServlet {

	private static final Logger LOG = LoggerFactory.getLogger(AccountManagementServlet.class);

	/** Servlet path for account management API. */
	public static final String PATH = "/api/account";

	/** Pattern for validating dial prefix format (+XX or +XXX). */
	private static final Pattern DIAL_PREFIX_PATTERN = Pattern.compile("^\\+[1-9][0-9]{0,2}$");

	/** Pattern for validating language tag format (e.g., "de", "en-US", "pt-BR"). */
	private static final Pattern LANG_TAG_PATTERN = Pattern.compile("^[a-z]{2}(-[A-Z]{2})?$");

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String userName = LoginFilter.getAuthenticatedUser(req);
		if (userName == null) {
			ServletUtil.sendAuthenticationRequest(resp);
			return;
		}

		DB db = DBService.getInstance();
		try (SqlSession session = db.openSession()) {
			Users users = session.getMapper(Users.class);
			DBUserSettings settings = users.getSettingsRaw(userName);

			if (settings == null) {
				ServletUtil.sendMessage(resp, HttpServletResponse.SC_NOT_FOUND, "User settings not found");
				return;
			}

			sendAccountSettings(resp, settings);
		}
	}

	@Override
	protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String userName = LoginFilter.getAuthenticatedUser(req);
		if (userName == null) {
			ServletUtil.sendAuthenticationRequest(resp);
			return;
		}

		// Parse request using generated UpdateAccountRequest message
		UpdateAccountRequest updateRequest;
		try {
			updateRequest = UpdateAccountRequest.readUpdateAccountRequest(
					new JsonReader(new ReaderAdapter(req.getReader())));
		} catch (Exception e) {
			LOG.warn("Failed to parse update request: {}", e.getMessage());
			ServletUtil.sendError(resp, "Invalid JSON: " + e.getMessage());
			return;
		}

		// Extract and validate parameters
		String lang = updateRequest.getLang();
		String dialPrefix = updateRequest.getDialPrefix();
		String displayName = updateRequest.getDisplayName();
		String countryCode = updateRequest.getCountryCode();

		// Validate language tag format
		if (lang != null && !lang.isEmpty() && !LANG_TAG_PATTERN.matcher(lang).matches()) {
			ServletUtil.sendError(resp, "Invalid language tag format. Expected format: 'de' or 'en-US'");
			return;
		}

		// Convert country code to dial prefix if provided
		if (countryCode != null && !countryCode.isEmpty()) {
			Country country = Countries.BY_ISO_31661_ALPHA_2.get(countryCode.toUpperCase());
			if (country == null) {
				ServletUtil.sendError(resp, "Invalid country code. Expected ISO 3166-1 alpha-2 format (e.g., 'DE', 'US', 'BR')");
				return;
			}
			// Use the first dial prefix for the country
			if (!country.getDialPrefixes().isEmpty()) {
				dialPrefix = country.getDialPrefixes().get(0);
				LOG.info("Converted country code '{}' to dial prefix '{}'", countryCode, dialPrefix);
			}
		}

		// Validate dial prefix format (if provided directly, not from country code)
		if (dialPrefix != null && !dialPrefix.isEmpty() && !DIAL_PREFIX_PATTERN.matcher(dialPrefix).matches()) {
			ServletUtil.sendError(resp, "Invalid dial prefix format. Expected format: '+XX' (e.g., '+49', '+1', '+351')");
			return;
		}

		// Validate display name (basic validation - not empty if provided)
		if (displayName != null && displayName.trim().isEmpty()) {
			ServletUtil.sendError(resp, "Display name must not be empty");
			return;
		}

		// Update user settings in database
		DB db = DBService.getInstance();
		try (SqlSession session = db.openSession()) {
			Users users = session.getMapper(Users.class);

			// Update only provided fields using individual setter methods
			boolean updated = false;

			if (lang != null && !lang.isEmpty()) {
				users.setLang(userName, lang);
				updated = true;
				LOG.info("Updated language for user '{}' to '{}'", userName, lang);
			}

			if (dialPrefix != null && !dialPrefix.isEmpty()) {
				users.setDialPrefix(userName, dialPrefix);
				updated = true;
				LOG.info("Updated dial prefix for user '{}' to '{}'", userName, dialPrefix);
			}

			if (displayName != null && !displayName.isEmpty()) {
				users.setDisplayName(userName, displayName.trim());
				updated = true;
				LOG.info("Updated display name for user '{}' to '{}'", userName, displayName.trim());
			}

			if (updated) {
				session.commit();
			}

			// Fetch updated settings to return
			DBUserSettings settings = users.getSettingsRaw(userName);

			sendAccountSettings(resp, settings);
		}
	}

	/**
	 * Sends account settings as JSON response.
	 */
	private void sendAccountSettings(HttpServletResponse resp, DBUserSettings settings) throws IOException {
		AccountSettings response = AccountSettings.create()
				.setLang(settings.getLang())
				.setDialPrefix(settings.getDialPrefix())
				.setDisplayName(DB.toDisplayName(settings.getDisplayName()))
				.setEmail(settings.getEmail());

		resp.setContentType("application/json");
		resp.setCharacterEncoding("UTF-8");
		response.writeContent(new JsonWriter(new WriterAdapter(resp.getWriter())));
	}
}
