/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.answerbot;

import org.mjsip.sip.address.NameAddress;
import org.mjsip.sip.address.SipURI;
import org.mjsip.ua.UserOptions;
import org.mjsip.ua.registration.RegistrationOptions;

/**
 * Options identifying a PhoneBlock customer.
 */
public interface CustomerOptions extends RegistrationOptions, UserOptions {

	@Override
	default String getProxy() {
		return getRegistrar().getHost();
	}
	
	@Override
	default int getExpires() {
		return 60 * 60;
	}
	
	@Override
	default String getAuthUser() {
		return getUser();
	}
	
	@Override
	default NameAddress getUserURI() {
		return new NameAddress(new SipURI(getUser(),getProxy()));
	}

	/** 
	 * The minimum PhoneBlock votes to consider a call as SPAM and accept it. 
	 */
	int getMinVotes();
	
	/** 
	 * Whether to block whole number ranges, when a great density of nearby SPAM numbers is detected. 
	 */
	boolean getWildcard();
	
	/** 
	 * Whether to accept anonymous calls.
	 */
	boolean getAcceptAnonymous();
}
