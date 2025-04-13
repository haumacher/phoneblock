/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import java.sql.SQLException;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.h2.jdbcx.JdbcConnectionPool;
import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.db.config.DBConfig;
import de.haumacher.phoneblock.index.IndexUpdateService;
import de.haumacher.phoneblock.mail.MailServiceStarter;
import de.haumacher.phoneblock.random.SecureRandomService;
import de.haumacher.phoneblock.scheduler.SchedulerService;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

/**
 * {@link ServletContextListener} starting the database.
 */
public class DBService implements ServletContextListener {

	private static final Logger LOG = LoggerFactory.getLogger(DBService.class);

	private static DB INSTANCE;
	
	private Server _server;

	private final IndexUpdateService _indexer;

	private SchedulerService _scheduler;

	private JdbcConnectionPool _pool;

	private MailServiceStarter _mail;

	private SecureRandomService _rnd;
	
	public DBService(SchedulerService scheduler) {
		this(new SecureRandomService(), IndexUpdateService.NONE, scheduler, null);
	}
	
	/** 
	 * Creates a {@link DBService}.
	 */
	public DBService(SecureRandomService rnd, IndexUpdateService indexer, SchedulerService scheduler, MailServiceStarter mail) {
		_rnd = rnd;
		_indexer = indexer;
		_scheduler = scheduler;
		_mail = mail;
	}

	@Override
	public void contextInitialized(ServletContextEvent servletContextEvent) {
		LOG.info("Starting DB service.");
        try {
            org.h2.Driver.load();
			DBConfig config = lookupConfig(servletContextEvent);
			startDB(config);
		} catch (SQLException ex) {
			LOG.error("Failed to start DB.", ex);
		}
	}

	private DBConfig lookupConfig(ServletContextEvent servletContextEvent) {
		DBConfig config = DBConfig.create()
			.setUrl(defaultDbUrl(appName(servletContextEvent)))
			.setUser("phone")
			.setPassword("block")
			.setPort(defaultDbPort());
		
		try {
			InitialContext initCtx = new InitialContext();
			Context envCtx = (Context) initCtx.lookup("java:comp/env");
			
			for (String property : config.properties()) {
				try {
					Object value = envCtx.lookup("db/" + property);
					config.set(property, value);
					LOG.info("Set property '" + property + "': " + config.get(property));
				} catch (NamingException ex) {
					String value = System.getProperty("db." + property);
					if (value != null) {
						config.set(property, value);
					} else {
						LOG.info(ex.getMessage() + ", using default for property '" + property + "': " + config.get(property));
					}
				}
			}
		} catch (NamingException ex) {
			LOG.info("Not using JNDI configuration: " + ex.getMessage());
		}
		
		return config;
	}

	private void startDB(DBConfig config) throws SQLException {
		LOG.info("Opening H2 database: " + config.getUrl() + " for user '" + config.getUser() + "'.");
		JdbcDataSource dataSource = new JdbcDataSource();
		dataSource.setUrl(config.getUrl());
		dataSource.setUser(config.getUser());
		dataSource.setPassword(config.getPassword());

		try {
			if (config.getPort() > 0) {
				LOG.info("Starting DB server: " + config.getUrl() + " at port " + config.getPort());
				_server = Server.createTcpServer("-tcpPort", Integer.toString(config.getPort()), "-tcpAllowOthers");
				_server.start();
			} else {
				LOG.info("No DB server.");
			}
		} catch (Exception ex) {
			LOG.error("Failed to start DB server. ", ex);
		}
		
		_pool = JdbcConnectionPool.create(dataSource);
		INSTANCE = new DB(_rnd.getRnd(), config.isSendHelpMails(), _pool, _indexer, _scheduler, _mail == null ? null : _mail.getMailService());
	}

	protected int defaultDbPort() {
		return 9095;
	}

	protected String defaultDbUrl(String appName) {
		return "jdbc:h2:/var/lib/tomcat10/work/" + appName + "/h2";
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

	/** 
	 * Access to the database.
	 */
	public DB db() {
		return INSTANCE;
	}

    @Override
    public void contextDestroyed(ServletContextEvent servletContextEvent) {
		LOG.info("Stopping DB service.");

        try {
        	try {
        		if (db()  != null) {
        			LOG.info("Stopping database.");
        			db().shutdown();
        			INSTANCE = null;
        		}
        	} finally {
        		if (_server != null) {
        			LOG.info("Stopping database server.");
        			_server.stop();
        			_server = null;
        		}
        	}
		} finally {
			if (_pool != null) {
				try {
					_pool.dispose();
					_pool = null;
				} catch (Exception ex) {
					LOG.error("Connection pool shutdown failed.", ex);
				}
			}
			
			LOG.info("Unloading DB driver.");
			org.h2.Driver.unload();
		}
    }
	
}
