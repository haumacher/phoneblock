/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.ab;

import java.util.Objects;

import org.mjsip.sip.provider.SipProvider;
import org.mjsip.ua.registration.RegistrationClient;
import org.mjsip.ua.registration.RegistrationClientListener;

import de.haumacher.phoneblock.answerbot.CustomerOptions;
import de.haumacher.phoneblock.db.settings.AnswerBotSip;

/**
 * {@link RegistrationClient} that holds the complete configuration for the customer.
 */
public class Registration extends RegistrationClient {

	private final CustomerOptions _customer;
	private boolean _temporary;
	private final AnswerBotSip _bot;
	private int _failures;

	/** 
	 * Creates a {@link Registration}.
	 * @param temporary 
	 * @param updated 
	 */
	public Registration(SipProvider sipProvider, AnswerBotSip bot, CustomerOptions customer, RegistrationClientListener sipService, boolean temporary) {
		super(sipProvider, customer, sipService);
		_bot = bot;
		_customer = customer;
		_temporary = temporary;
	}
	
	public AnswerBotSip getBot() {
		return _bot;
	}
	
	/**
	 * The customer configuration of this client.
	 */
	public CustomerOptions getCustomer() {
		return _customer;
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
	
	public boolean recordFailure() {
		boolean updated = !hasFailure();
		_bot.setRegistered(false);
		_failures++;
		return updated;
	}
	
	public boolean hasFailure() {
		return _failures > 0;
	}

	public boolean recordSuccess(long now) {
		boolean updated = !isRegistered();
		
		if ((now - getLastSuccess()) > SipService.UPDATE_INTERVAL) {
			updated = true;
		}
		
		if (updated) {
			setLastSuccess(now);
		}
		_bot.setRegistered(true);
		_failures = 0;
		return updated;
	}
	
	/**
	 * The time, when the current state was reached first.
	 * 
	 * @see #hasFailure()
	 * @see #isRegistered()
	 */
	public long getLastUpdate() {
		return _bot.getUpdated();
	}
	
	/**
	 * Marks a successful registration as updated now.
	 */
	public void setLastSuccess(long update) {
		_bot.setLastSuccess(update);
	}
	
	public boolean isRegistered() {
		return _bot.isRegistered();
	}

	/**
	 * The ID of the registered answerbot.
	 */
	public long getId() {
		return _bot.getId();
	}

	/**
	 * Updates the registration message.
	 * 
	 * @return Whether the message has changed.
	 */
	public boolean updateMessage(String message) {
		String oldMessage = _bot.getRegisterMessage();
		_bot.setRegisterMessage(message);
		return !Objects.equals(message, oldMessage);
	}

	public long getLastSuccess() {
		return _bot.getLastSuccess();
	}
	
}
