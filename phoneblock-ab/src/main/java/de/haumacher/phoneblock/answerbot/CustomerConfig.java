/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.answerbot;

import org.kohsuke.args4j.Option;
import org.mjsip.sip.address.SipURI;
import org.mjsip.sip.config.SipURIHandler;

/**
 * Command line options to specify a {@link CustomerConfig}.
 */
public class CustomerConfig implements CustomerOptions {

	@Option(name = "--registrar", required = true, handler = SipURIHandler.class)
	private SipURI registrar;
	
	@Option(name = "--route", handler = SipURIHandler.class)
	private SipURI route;
	
	@Option(name = "--user")
	private String user;
	
	@Option(name = "--passwd")
	private String passwd;
	
	@Option(name = "--realm")
	private String realm;

	@Override
	public SipURI getRegistrar() {
		return registrar;
	}
	
	public void setRegistrar(SipURI registrar) {
		this.registrar = registrar;
	}

	@Override
	public SipURI getRoute() {
		return route;
	}
	
	public void setRoute(SipURI route) {
		this.route = route;
	}

	@Override
	public String getUser() {
		return user;
	}
	
	public void setUser(String user) {
		this.user = user;
	}

	@Override
	public String getAuthPasswd() {
		return passwd;
	}
	
	public void setPasswd(String passwd) {
		this.passwd = passwd;
	}

	@Override
	public String getAuthRealm() {
		return realm;
	}
	
	public void setRealm(String realm) {
		this.realm = realm;
	}

}
