/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.random;

import java.security.SecureRandom;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

/**
 * Service for generating secure random numbers.
 */
public class SecureRandomService implements ServletContextListener {
	
	private SecureRandom _rnd = new SecureRandom();
	private static SecureRandomService INSTANCE;
	
	/**
	 * The secure random number generator.
	 */
	public SecureRandom getRnd() {
		return _rnd;
	}
	
	@Override
	public void contextInitialized(ServletContextEvent sce) {
		INSTANCE = this;
	}
	
	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		INSTANCE = null;
	}

	/**
	 * The {@link SecureRandomService} singleton.
	 */
	public static SecureRandomService getInstance() {
		return INSTANCE;
	}
}
