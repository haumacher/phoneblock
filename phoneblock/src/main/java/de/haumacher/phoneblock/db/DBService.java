/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

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

	private JdbcDataSource _dataSource;
	
	@Override
	public void contextInitialized(ServletContextEvent servletContextEvent) {
        try {
            org.h2.Driver.load();
        	
            String url = "jdbc:h2:/var/lib/tomcat9/work/" + appName(servletContextEvent) + "/h2";   
            String user = "phone";        
            String password = "block";

			_dataSource = new JdbcDataSource();
			_dataSource.setUrl(url);
			_dataSource.setUser(user);
			_dataSource.setPassword(password);

			_server = Server.createTcpServer("-tcpAllowOthers");
			_server.start();
			
			System.out.println("Opening DB: " + url + " at port " + _server.getPort());
			
			INSTANCE = new DB(_dataSource);
		} catch (SQLException | UnsupportedEncodingException ex) {
			ex.printStackTrace();
		}
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
    	if (_dataSource != null) {
    		try (Connection connection = _dataSource.getConnection(); Statement statement = connection.createStatement()) {
    			statement.execute("SHUTDOWN");
    		} catch (Exception e) {
    			e.printStackTrace();
    		}
    	}
        
        if (_server != null) {
            _server.stop();
            _server = null;
        }
        
        org.h2.Driver.unload();
        
        INSTANCE = null;
    }
	
}
