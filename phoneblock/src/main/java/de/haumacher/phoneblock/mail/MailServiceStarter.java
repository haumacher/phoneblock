/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.mail;

import java.util.Properties;

import javax.mail.MessagingException;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

/**
 * The {@link MailService} singleton.
 */
public class MailServiceStarter implements ServletContextListener {
	
	private static MailService INSTANCE;
	
	/**
	 * The {@link MailService} instance.
	 */
	public static MailService getInstance() {
		return INSTANCE;
	}

	@Override
	public void contextInitialized(ServletContextEvent sce) {
		try {
			InitialContext initCtx = new InitialContext();
			Context envCtx = (Context) initCtx.lookup("java:comp/env");
			
			String user = (String) envCtx.lookup("smtp/user");        
			String password = (String) envCtx.lookup("smtp/password");
			if (user == null && password == null) {
				System.err.println("No mail configuration found.");
			} else {
				Properties properties = new Properties();
				Context propertyContext = (Context) envCtx.lookup("smtp/properties");
				if (propertyContext != null) {
					NamingEnumeration<NameClassPair> list = envCtx.list("smtp/properties");
					while (list.hasMore()) {
						NameClassPair pair = list.next();
						
						if ("java.lang.String".equals(pair.getClassName())) {
							String name = pair.getName();
							String value = (String) propertyContext.lookup(name);
							properties.setProperty(name, value);
						}
					}
				}
				
				MailService mailService = new MailService(user, password, properties);
				mailService.startUp();
				
				INSTANCE = mailService;
			}
		} catch (NamingException | MessagingException ex) {
			ex.printStackTrace();
		}
	}
	
	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		INSTANCE.shutdown();
		INSTANCE = null;
	}
	
}
