/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.ab;

import org.mjsip.sip.provider.SipProvider;
import org.mjsip.ua.registration.RegistrationClient;
import org.mjsip.ua.registration.RegistrationClientListener;

import de.haumacher.phoneblock.answerbot.CustomerOptions;
import de.haumacher.phoneblock.db.settings.AnswerBotSip;

/**
 * {@link RegistrationClient} that holds the complete configuration for the customer.
 */
public class Registration extends RegistrationClient {

	private CustomerOptions _customer;
	private boolean _temporary;
	private AnswerBotSip _bot;
	private int _failures;

	/** 
	 * Creates a {@link Registration}.
	 * @param temporary 
	 */
	public Registration(SipProvider sipProvider, AnswerBotSip bot, CustomerOptions customer, RegistrationClientListener sipService, boolean temporary) {
		super(sipProvider, customer, sipService);
		_bot = bot;
		_customer = customer;
		_temporary = temporary;
	}
	
	public boolean isTemporary() {
		return _temporary;
	}
	
	public void setPermanent() {
		_temporary = false;
	}
	
	public int getFailures() {
		return _failures;
	}
	
	public void incFailures() {
		_failures++;
	}
	
	public void resetFailures() {
		_failures = 0;
	}

	/**
	 * The ID of the registered answerbot.
	 */
	public long getId() {
		return _bot.getId();
	}
	
	/**
	 * The customer configuration of this client.
	 */
	public CustomerOptions getCustomer() {
		return _customer;
	}
	
}
