/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.ab;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.mjsip.pool.PortPool;
import org.mjsip.sip.provider.SipConfig;
import org.mjsip.sip.provider.SipProvider;
import org.mjsip.time.Scheduler;
import org.mjsip.ua.registration.RegistrationClient;

import de.haumacher.phoneblock.answerbot.AnswerBot;
import de.haumacher.phoneblock.answerbot.AnswerbotConfig;
import de.haumacher.phoneblock.answerbot.CustomerConfig;
import de.haumacher.phoneblock.scheduler.SchedulerService;

/**
 * Service managing a SIP stack.
 */
public class SipService implements ServletContextListener {
	
	private SchedulerService _scheduler;
	private SipProvider _sipProvider;
	private PortPool _portPool;
	private AnswerBot _answerBot;
	
	private static SipService _instance;

	/** 
	 * Creates a {@link SipService}.
	 */
	public SipService(SchedulerService scheduler) {
		_scheduler = scheduler;
	}
	
	@Override
	public void contextInitialized(ServletContextEvent sce) {
		SipConfig sipConfig = new SipConfig();
		sipConfig.setHostPort(50060);
		sipConfig.setViaAddr("phoneblock.haumacher.de");
		_sipProvider = new SipProvider(sipConfig, Scheduler.of(_scheduler.getInstance().executor()));

		_portPool = new PortPool(50061, 50080);
		
		AnswerbotConfig botOptions = new AnswerbotConfig();
		_answerBot = new AnswerBot(_sipProvider, botOptions, configForUser, _portPool);
		
		_instance = this;
	}
	
	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		_answerBot = null;
		_portPool = null;
		
		_sipProvider.halt();
		_sipProvider = null;
		
		if (_instance == this) {
			_instance = null;
		}
	}
	
	/**
	 * The {@link SipService} singleton.
	 */
	public static SipService getInstance() {
		return _instance;
	}

	/** 
	 * TODO
	 *
	 * @param username
	 * @param passwd
	 * @param hostname
	 */
	public void register(String username, String passwd, String hostname) {
		CustomerConfig regConfig = new CustomerConfig();
		regConfig.setUser(username);
		regConfig.setUser(passwd);
		regConfig.setRealm("fritz.box");
		regConfig.setRegistrar(hostname);
		RegistrationClient client = new RegistrationClient(_sipProvider, regConfig, null);
		client.loopRegister(regConfig);
	}

}
