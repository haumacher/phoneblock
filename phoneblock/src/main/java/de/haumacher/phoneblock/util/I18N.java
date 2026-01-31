/*
 * Copyright (c) 2025 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.util;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

import de.haumacher.phoneblock.app.LoginFilter;
import de.haumacher.phoneblock.app.render.DefaultController;
import de.haumacher.phoneblock.db.settings.UserSettings;
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
	 * Gets the user's preferred locale from cached session settings, with fallback to browser locale.
	 *
	 * @param req The HTTP request
	 * @return The user's locale language tag (e.g., "de", "en-US")
	 */
	public static String getUserLocale(HttpServletRequest req) {
		UserSettings settings = LoginFilter.getUserSettings(req);
		if (settings != null) {
			String lang = settings.getLang();
			if (lang != null && !lang.isEmpty()) {
				return lang;
			}
		}
		// Fallback to browser locale, normalized to available languages
		return DefaultController.selectLanguage(req.getLocale()).tag;
	}

	private I18N() {
		// Utility class, no instances
	}
}
