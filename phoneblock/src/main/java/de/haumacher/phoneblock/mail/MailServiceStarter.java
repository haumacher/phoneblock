/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.mail;

import java.util.Properties;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

/**
 * The {@link MailService} singleton.
 */
public class MailServiceStarter implements ServletContextListener {
	
	private static final String SMPT_PROPERTY_PREFIX = "smtp.properties.";

	private static final Logger LOG = LoggerFactory.getLogger(MailServiceStarter.class);

	private static MailService INSTANCE;

	private MailService _mailService;
	
	/**
	 * The {@link MailService} instance.
	 */
	public static MailService getInstance() {
		return INSTANCE;
	}
	
	/**
	 * The {@link MailService} instance.
	 */
	public MailService getMailService() {
		return _mailService;
	}

	@Override
	public void contextInitialized(ServletContextEvent sce) {
		LOG.info("Starting mail service.");
		String user;
		String password;
		
		Properties properties = new Properties();
		try {
			InitialContext initCtx = new InitialContext();
			Context envCtx = (Context) initCtx.lookup("java:comp/env");
			
			user = (String) envCtx.lookup("smtp/user");        
			password = (String) envCtx.lookup("smtp/password");
			
			if (user == null && password == null) {
				LOG.warn("No mail configuration, mail service not available.");
				return;
			} else {
				Context propertyContext = (Context) envCtx.lookup("smtp/properties");
				if (propertyContext != null) {
					NamingEnumeration<NameClassPair> list = envCtx.list("smtp/properties");
					while (list.hasMore()) {
						NameClassPair pair = list.next();
						
						String name = pair.getName();
						Object value = propertyContext.lookup(name);
						properties.setProperty(name, value.toString());
					}
				}
			}
		} catch (NamingException ex) {
			LOG.info("No JNDI mail configuration: " + ex.getMessage());

			user = getProperty("smtp.user");        
			password = getProperty("smtp.password");

			Properties systemProperties = System.getProperties();
			for (String property : systemProperties.stringPropertyNames()) {
				if (!property.startsWith(SMPT_PROPERTY_PREFIX)) {
					continue;
				}
				
				String smtpProperty = property.substring(SMPT_PROPERTY_PREFIX.length());
				properties.setProperty(smtpProperty, systemProperties.getProperty(property));
			}
		}
		
		if (user == null && password == null) {
			LOG.warn("No smtp configuration, mail service not available.");
		} else if ("true".equals(properties.get("smtp.test-only"))) {
			_mailService = DummyMailService.INSTANCE;
			_mailService.startUp();
		} else {
			_mailService = new MailServiceImpl(user, password, properties);
			_mailService.startUp();
		}

		INSTANCE = _mailService;
	}
	
   private String getProperty(String property) {
		String value = System.getProperty("smtp." + property);
		if (value != null) {
			return value;
		}
		return null;
	}
	
	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		if (_mailService != null) {
			LOG.info("Shutting down mail server.");
			if (INSTANCE == _mailService) {
				INSTANCE = null;
			}
			_mailService.shutdown();
		} else {
			LOG.info("Skipping mail server shutdown, not started.");
		}
	}
	
}
