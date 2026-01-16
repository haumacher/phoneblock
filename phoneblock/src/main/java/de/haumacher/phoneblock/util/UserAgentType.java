package de.haumacher.phoneblock.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Enum representing different types of user agents accessing the PhoneBlock application.
 */
public enum UserAgentType {
	/** Android mobile device */
	ANDROID,

	/** Apple iPhone device */
	IPHONE,

	/** PhoneBlock mobile app */
	MOBILE_APP,

	/** Desktop browser or Fritz!Box (default fallback) */
	DESKTOP;

	/**
	 * Detects the user agent type from the HTTP request.
	 *
	 * @param request The HTTP servlet request containing the User-Agent header
	 * @return The detected user agent type, never null
	 */
	public static UserAgentType detect(HttpServletRequest request) {
		String userAgent = request.getHeader("User-Agent");
		return detect(userAgent);
	}

	/**
	 * Detects the user agent type from the User-Agent header string.
	 *
	 * @param userAgent The User-Agent header string (may be null)
	 * @return The detected user agent type, never null
	 */
	public static UserAgentType detect(String userAgent) {
		String ua = userAgent == null ? "" : userAgent.toLowerCase();

		// Check for PhoneBlock mobile app first (most specific)
		if (ua.startsWith("phoneblockmobile/")) {
			return MOBILE_APP;
		}

		// Check for mobile platforms
		if (ua.contains("android")) {
			return ANDROID;
		}

		if (ua.contains("iphone")) {
			return IPHONE;
		}

		// Default to desktop/Fritz!Box
		return DESKTOP;
	}

	/**
	 * @return true if this is an Android device (but not necessarily the app)
	 */
	public boolean isAndroid() {
		return this == ANDROID || this == MOBILE_APP;
	}

	/**
	 * @return true if this is an iPhone device
	 */
	public boolean isIPhone() {
		return this == IPHONE;
	}

	/**
	 * @return true if this is a mobile device (Android or iPhone)
	 */
	public boolean isMobile() {
		return this == ANDROID || this == IPHONE || this == MOBILE_APP;
	}

	/**
	 * @return true if this is the PhoneBlock mobile app
	 */
	public boolean isMobileApp() {
		return this == MOBILE_APP;
	}

	/**
	 * @return true if this is a desktop browser or Fritz!Box
	 */
	public boolean isDesktop() {
		return this == DESKTOP;
	}

	/**
	 * @return true if this is likely a Fritz!Box (desktop and not a mobile device)
	 */
	public boolean isFritzBox() {
		return this == DESKTOP;
	}
}
