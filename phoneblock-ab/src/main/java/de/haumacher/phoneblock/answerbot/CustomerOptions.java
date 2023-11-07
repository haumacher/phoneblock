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
		return getRegistrar();
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

}
