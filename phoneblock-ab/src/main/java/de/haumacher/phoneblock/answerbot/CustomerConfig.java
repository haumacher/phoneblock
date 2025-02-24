/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.answerbot;

import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.BooleanOptionHandler;
import org.mjsip.config.YesNoHandler;
import org.mjsip.sip.address.SipURI;
import org.mjsip.sip.config.SipURIHandler;

/**
 * Command line options to specify a {@link CustomerConfig}.
 */
public class CustomerConfig implements CustomerOptions {

	@Option(name = "--registrar", required = true, 
			usage = "Fully qualified domain name (or address) of the registrar server. It is used as recipient for REGISTER requests.", 
			handler = SipURIHandler.class)
	private SipURI registrar;
	
	@Option(name = "--route",
			usage = "Additional routing information to reach the registrar. When connecting to a Fritz!Box router, the registrar must be 'fritz.box'. " + 
					"If this address is not reachable from your anser bot installation, you use '<IP address of your box>;lr' as routing information.",
			handler = SipURIHandler.class)
	private SipURI route;
	
	@Option(name = "--sip-user",
			usage = "User name to register at the registrar server.")
	private String sipUser;
	
	@Option(name = "--sip-passwd",
			usage = "Password for the user to register at the registrar server.")
	private String sipPasswd;
	
	@Option(name = "--realm",
			usage = "Authentication realm for registering at the registrar server.")
	private String realm;

	@Option(name = "--min-votes", handler = GreaterThanZeroIntOptionHandler.class, usage = "The minimum number of PhoneBlock votes for a number to be consideres SPAM.")
	private int _minVotes = 4;

	@Option(name = "--wildcard", handler = BooleanOptionHandler.class, usage = "Whether to block numbers that are within a range of numbers with high density of SPAM.")
	private boolean _wildcard;
	
	@Option(name = "--accept-anonymous", handler = YesNoHandler.class, usage = "Whether to let PhoneBlock accept anonymous calls. This is not recommended. Better configure a separate answering machine in you router to handle anonymous calls.")
	private boolean _acceptAnonymous = false;

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
	public String getSipUser() {
		return sipUser;
	}
	
	public void setUser(String user) {
		this.sipUser = user;
	}

	@Override
	public String getAuthPasswd() {
		return sipPasswd;
	}
	
	public void setPasswd(String passwd) {
		this.sipPasswd = passwd;
	}

	@Override
	public String getAuthRealm() {
		return realm;
	}
	
	public void setRealm(String realm) {
		this.realm = realm;
	}

	@Override
	public int getMinVotes() {
		return _minVotes;
	}
	
	/**
	 * @see #getMinVotes()
	 */
	public void setMinVotes(int minVotes) {
		_minVotes = minVotes;
	}
	
	@Override
	public boolean getWildcard() {
		return _wildcard;
	}
	
	public void setWildcard(boolean value) {
		_wildcard = value;
	}
	
	
	@Override
	public boolean getAcceptAnonymous() {
		return _acceptAnonymous;
	}
	
	/**
	 * @see #getAcceptAnonymous()
	 */
	public void setAcceptAnonymous(boolean acceptAnonymous) {
		_acceptAnonymous = acceptAnonymous;
	}
}
