/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import java.io.UnsupportedEncodingException;
import java.sql.SQLException;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.Server;

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
        	
            String url = defaultDbUrl(servletContextEvent);
            String user = "phone";        
            String password = "block";

			JdbcDataSource dataSource = new JdbcDataSource();
			dataSource.setUrl(url);
			dataSource.setUser(user);
			dataSource.setPassword(password);

			try {
				_server = Server.createTcpServer("-tcpPort", Integer.toString(defaultDbPort()), "-tcpAllowOthers");
				_server.start();
				System.out.println("DB server: " + url + " at port " + _server.getPort());
			} catch (Exception ex) {
				System.err.println("Failed to start DB server: " + ex.getMessage());
				ex.printStackTrace();
			}
			
			INSTANCE = new DB(dataSource);
		} catch (SQLException | UnsupportedEncodingException ex) {
			ex.printStackTrace();
		}
	}

	protected int defaultDbPort() {
		return 9095;
	}

	protected String defaultDbUrl(ServletContextEvent servletContextEvent) {
		return "jdbc:h2:/var/lib/tomcat9/work/" + appName(servletContextEvent) + "/h2";
	}
	
	private String appName(ServletContextEvent servletContextEvent) {
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
