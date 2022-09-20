/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import java.io.UnsupportedEncodingException;
import java.sql.SQLException;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.Server;

import de.haumacher.phoneblock.db.config.DBConfig;

/**
 * {@link ServletContextListener} starting the database.
 */
public class DBService implements ServletContextListener {

	private static DB INSTANCE;
	
	private Server _server;

	@Override
	public void contextInitialized(ServletContextEvent servletContextEvent) {
		System.out.println("Starting DB service.");
        try {
            org.h2.Driver.load();
			DBConfig config = lookupConfig(servletContextEvent);
			startDB(config);
		} catch (SQLException | UnsupportedEncodingException ex) {
			ex.printStackTrace();
		}
	}

	private DBConfig lookupConfig(ServletContextEvent servletContextEvent) {
		DBConfig config = DBConfig.create();
		config.setUrl(defaultDbUrl(appName(servletContextEvent))).setUser("phone").setPassword("block").setPort(defaultDbPort());
		
		try {
			InitialContext initCtx = new InitialContext();
			Context envCtx = (Context) initCtx.lookup("java:comp/env");
			
			try {
				String url = (String) envCtx.lookup("db/url");
				config.setUrl(url);
			} catch (NamingException ex) {
				System.out.print(ex.getMessage() + ", using default DB url: " + config.getUrl());
			}
			
			try {
				String user = (String) envCtx.lookup("db/user");
				config.setUser(user);
			} catch (NamingException ex) {
				System.out.print(ex.getMessage() + ", using default DB user: " + config.getUser());
			}
			
			try {
				String password = (String) envCtx.lookup("db/password");
				config.setPassword(password);
			} catch (NamingException ex) {
				System.out.print(ex.getMessage() + ", using default DB password.");
			}

			try {
				Integer port = (Integer) envCtx.lookup("db/port");
				if (port != null) {
					config.setPort(port.intValue());
				}
			} catch (NamingException ex) {
				System.out.print(ex.getMessage() + ", using default DB port: " + config.getPort());
			}
		} catch (NamingException ex) {
			System.out.print("Not using JNDI configuration: " + ex.getMessage());
		}
		
		return config;
	}

	private void startDB(DBConfig config) throws SQLException, UnsupportedEncodingException {
		System.out.println("Opening H2 database: " + config.getUrl() + " for user '" + config.getUser() + "'.");
		JdbcDataSource dataSource = new JdbcDataSource();
		dataSource.setUrl(config.getUrl());
		dataSource.setUser(config.getUser());
		dataSource.setPassword(config.getPassword());

		try {
			if (config.getPort() > 0) {
				System.out.println("Starting DB server: " + config.getUrl() + " at port " + config.getPort());
				_server = Server.createTcpServer("-tcpPort", Integer.toString(config.getPort()), "-tcpAllowOthers");
				_server.start();
			} else {
				System.out.println("No DB server.");
			}
		} catch (Exception ex) {
			System.err.println("Failed to start DB server: " + ex.getMessage());
			ex.printStackTrace();
		}
		
		INSTANCE = new DB(dataSource);
	}

	protected int defaultDbPort() {
		return 9095;
	}

	protected String defaultDbUrl(String appName) {
		return "jdbc:h2:/var/lib/tomcat9/work/" + appName + "/h2";
	}
	
	private String appName(ServletContextEvent servletContextEvent) {
		if (servletContextEvent == null) {
			return "phoneblock";
		}
		return appName(servletContextEvent.getServletContext().getContextPath());
	}

	private String appName(String contextPath) {
		return contextPath.replace('/', ' ').trim().replace(' ', '-');
	}

	/**
	 * The {@link DB} singleton.
	 */
	public static DB getInstance() {
		return INSTANCE;
	}

    @Override
    public void contextDestroyed(ServletContextEvent servletContextEvent) {
		System.out.println("Stopping DB service.");

        try {
        	try {
        		if (INSTANCE  != null) {
        			System.out.println("Stopping database.");
        			INSTANCE.shutdown();
        			INSTANCE = null;
        		}
        	} finally {
        		if (_server != null) {
        			System.out.println("Stopping database server.");
        			_server.stop();
        			_server = null;
        		}
        	}
		} finally {
			System.out.println("Unloading DB driver.");
			org.h2.Driver.unload();
		}
    }
	
}
