/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.jmx;

import java.lang.management.ManagementFactory;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.chatgpt.ChatGPTService;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.index.IndexUpdateService;
import de.haumacher.phoneblock.mail.MailServiceStarter;

/**
 * Service registering an MBean for observing application state.
 */
public class ManagementService implements ServletContextListener {
	
	private static final Logger LOG = LoggerFactory.getLogger(ManagementService.class);
	
	private boolean _started;

	private IndexUpdateService _updater;

	private MailServiceStarter _mail;

	private DBService _db;

	private ChatGPTService _gpt;
	
	/** 
	 * Creates a {@link ManagementService}.
	 */
	public ManagementService(IndexUpdateService updater, DBService db, ChatGPTService gpt) {
		super();
		_updater = updater;
		_db = db;
		_gpt = gpt;
	}

	@Override
	public void contextInitialized(ServletContextEvent servletContextEvent) {
		MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
		try {
			ObjectName name = beanName(servletContextEvent);
			mBeanServer.registerMBean(new AppState(_updater, _db.db(), _gpt), name);
			_started = true;
			LOG.info("Registered management bean: " + name);
		} catch (NotCompliantMBeanException | MalformedObjectNameException | InstanceAlreadyExistsException | MBeanRegistrationException ex) {
			LOG.error("Failed to start management bean.", ex);
		}
	}

	@Override
	public void contextDestroyed(ServletContextEvent servletContextEvent) {
		try {
			if (_started) {
				MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
				ObjectName name = beanName(servletContextEvent);
				mBeanServer.unregisterMBean(name);
				LOG.info("Unregistered management bean: " + name);
			} else {
				LOG.info("Skipped unregistering management bean, not registered.");
			}
		} catch (MBeanRegistrationException | InstanceNotFoundException | MalformedObjectNameException ex) {
			LOG.error("Failed to unregister management bean.", ex);
		}
	}

	private ObjectName beanName(ServletContextEvent servletContextEvent) throws MalformedObjectNameException {
		String appName = servletContextEvent == null ? "phoneblock" : servletContextEvent.getServletContext().getContextPath().substring(1);
		return ObjectName.getInstance(ManagementService.class.getPackageName(), "app", appName);
	}

}
