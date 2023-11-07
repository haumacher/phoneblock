/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.answerbot;

import org.kohsuke.args4j.Option;

/**
 * Command line options to specify a {@link CustomerConfig}.
 */
public class CustomerConfig implements CustomerOptions {

	@Option(name = "--registrar")
	private String registrar;
	
	@Option(name = "--route")
	private String route;
	
	@Option(name = "--user")
	private String user;
	
	@Option(name = "--passwd")
	private String passwd;
	
	@Option(name = "--realm")
	private String realm;

	@Override
	public String getRegistrar() {
		return registrar;
	}

	@Override
	public String getRoute() {
		return route;
	}

	@Override
	public String getUser() {
		return user;
	}

	@Override
	public String getAuthPasswd() {
		return passwd;
	}

	@Override
	public String getAuthRealm() {
		return realm;
	}

}
