/*
 * Copyright (c) 2025 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.util;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

import org.apache.ibatis.session.SqlSession;

import de.haumacher.phoneblock.app.LoginFilter;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.Users;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Utility for internationalization (I18N) message lookup.
 *
 * <p>
 * Provides centralized access to localized messages from the Messages resource bundle.
 * Uses ResourceBundle's built-in fallback mechanism to automatically fall back to parent
 * locales if a key is not found in the requested locale.
 * </p>
 */
public class I18N {

	private static final String BUNDLE_NAME = "Messages";

	/**
	 * Get a localized message by key for the given locale.
	 *
	 * @param locale The locale (e.g., "de", "en-US")
	 * @param key The message key
	 * @return The localized message
	 */
	public static String getMessage(String locale, String key) {
		return getMessage(Locale.forLanguageTag(locale), key);
	}

	/**
	 * Get a localized message by key for the given locale.
	 *
	 * @param locale The locale
	 * @param key The message key
	 * @return The localized message
	 */
	public static String getMessage(Locale locale, String key) {
		ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_NAME, locale);
		return bundle.getString(key);
	}

	/**
	 * Get a localized message by key for the given locale with parameters.
	 *
	 * @param locale The locale
	 * @param key The message key
	 * @param messageParameters The parameters to format into the message
	 * @return The localized and formatted message
	 */
	public static String getMessage(Locale locale, String key, Object... messageParameters) {
		String message = getMessage(locale, key);
		if (messageParameters != null && messageParameters.length > 0) {
			return new MessageFormat(message).format(messageParameters);
		}
		return message;
	}

	/**
	 * Get a localized message by key for the given locale with parameters.
	 *
	 * @param locale The locale (e.g., "de", "en-US")
	 * @param key The message key
	 * @param messageParameters The parameters to format into the message
	 * @return The localized and formatted message
	 */
	public static String getMessage(String locale, String key, Object... messageParameters) {
		return getMessage(Locale.forLanguageTag(locale), key, messageParameters);
	}

	/**
	 * Get a localized message by key for the request's user locale.
	 *
	 * @param req The HTTP request (used to determine user's locale)
	 * @param key The message key
	 * @return The localized message
	 */
	public static String getMessage(HttpServletRequest req, String key) {
		return getMessage(getUserLocale(req), key);
	}

	/**
	 * Get a localized message by key for the request's user locale with parameters.
	 *
	 * @param req The HTTP request (used to determine user's locale)
	 * @param key The message key
	 * @param messageParameters The parameters to format into the message
	 * @return The localized and formatted message
	 */
	public static String getMessage(HttpServletRequest req, String key, Object... messageParameters) {
		return getMessage(getUserLocale(req), key, messageParameters);
	}

	/**
	 * Gets the user's preferred locale from their database settings, with fallback to browser locale.
	 *
	 * @param req The HTTP request
	 * @return The user's locale language tag (e.g., "de", "en-US")
	 */
	public static String getUserLocale(HttpServletRequest req) {
		String userName = LoginFilter.getAuthenticatedUser(req);
		if (userName != null) {
			DB db = DBService.getInstance();
			try (SqlSession sqlSession = db.openSession()) {
				Users users = sqlSession.getMapper(Users.class);
				String lang = users.getLang(userName);
				if (lang != null && !lang.isEmpty()) {
					return lang;
				}
			}
		}
		// Fallback to browser locale or default
		return req.getLocale().toLanguageTag();
	}

	private I18N() {
		// Utility class, no instances
	}
}
