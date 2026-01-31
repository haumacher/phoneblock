/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app;

import de.haumacher.phoneblock.db.settings.AuthToken;
import de.haumacher.phoneblock.db.settings.UserSettings;

/**
 * Authentication context holding both the authorization token and user settings.
 *
 * <p>
 * This combination is loaded during authentication in a single database transaction
 * and stored in both session (if available) and request attributes for consistent access.
 * </p>
 */
public class AuthContext {

	private final AuthToken _authorization;
	private final UserSettings _settings;

	/**
	 * Creates an {@link AuthContext}.
	 */
	public AuthContext(AuthToken authorization, UserSettings settings) {
		_authorization = authorization;
		_settings = settings;
	}

	/**
	 * The authorization token.
	 */
	public AuthToken getAuthorization() {
		return _authorization;
	}

	/**
	 * The user settings.
	 */
	public UserSettings getSettings() {
		return _settings;
	}

	/**
	 * Shortcut for {@code getAuthorization().getUserName()}.
	 */
	public String getUserName() {
		return _authorization.getUserName();
	}

}
