/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.random;

import java.security.SecureRandom;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

/**
 * Service for generating secure random numbers.
 */
public class SecureRandomService implements ServletContextListener {
	
	private SecureRandom _rnd = new SecureRandom();
	private static SecureRandomService _instance;
	
	/**
	 * The secure random number generator.
	 */
	public SecureRandom getRnd() {
		return _rnd;
	}
	
	@Override
	public void contextInitialized(ServletContextEvent sce) {
		_instance = this;
	}
	
	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		if (_instance == this) {
			_instance = null;
		}
	}

	/**
	 * The {@link SecureRandomService} singleton.
	 */
	public static SecureRandomService getInstance() {
		return _instance;
	}
}
