/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.ab;

import org.mjsip.sip.provider.SipProvider;
import org.mjsip.ua.registration.RegistrationClient;
import org.mjsip.ua.registration.RegistrationClientListener;

import de.haumacher.phoneblock.answerbot.CustomerOptions;

/**
 * {@link RegistrationClient} that holds the complete configuration for the customer.
 */
public class Registration extends RegistrationClient {

	private CustomerOptions _customer;
	private long _userId;

	/** 
	 * Creates a {@link Registration}.
	 */
	public Registration(SipProvider sipProvider, long userId, CustomerOptions customer, RegistrationClientListener sipService) {
		super(sipProvider, customer, sipService);
		_userId = userId;
		_customer = customer;
	}
	
	/**
	 * The user ID of the registered user.
	 */
	public long getUserId() {
		return _userId;
	}
	
	/**
	 * The customer configuration of this client.
	 */
	public CustomerOptions getCustomer() {
		return _customer;
	}
	
}
