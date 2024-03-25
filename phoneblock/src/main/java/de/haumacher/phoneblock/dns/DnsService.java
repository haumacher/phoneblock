package de.haumacher.phoneblock.dns;

import java.io.IOException;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.db.DBAnswerBotDynDns;
import de.haumacher.phoneblock.db.DBService;
import de.haumacher.phoneblock.db.Users;
import de.haumacher.phoneblock.scheduler.SchedulerService;

/**
 * Service module starting a DNS server for answering DNS lookups from Fritz!Box devices registered via DynDNS.
 */
public class DnsService implements ServletContextListener {
	
	private static final Logger LOG = LoggerFactory.getLogger(DnsService.class);
	
	private SchedulerService _scheduler;
	private static DnsServer _dnsServer;

	private DBService _dbService;

	private int _dnsPort;

	public DnsService(SchedulerService scheduler, DBService dbService) {
		_scheduler = scheduler;
		_dbService = dbService;
	}
	
	@Override
	public void contextInitialized(ServletContextEvent sce) {
		loadConfig();
		
		if (_dnsPort > 0) {
			LOG.info("Starting DNS on port " + _dnsPort + ".");
			try {
				_dnsServer = new DnsServer(_scheduler.executor(), _dnsPort).start();
				
				try (SqlSession tx = _dbService.db().openSession()) {
					Users users = tx.getMapper(Users.class);
					
					for (DBAnswerBotDynDns dyndns : users.getDynDnsUsers()) {
						_dnsServer.load(dyndns);
					}
				}
			} catch (IOException ex) {
				LOG.error("Failed to start name server.", ex);
			}
		} else {
			LOG.info("DNS server not configured.");
		}
	}
	
	private void loadConfig() {
		try {
			InitialContext initCtx = new InitialContext();
			Context envCtx = (Context) initCtx.lookup("java:comp/env");
			
			try {
				_dnsPort = ((Number) envCtx.lookup("dns/port")).intValue();
			} catch (NamingException ex) {
				String value = System.getProperty("dns.port");
				if (value != null) {
					_dnsPort = Integer.parseInt(value);
				}
			}
		} catch (NamingException ex) {
			LOG.info("Not using JNDI configuration: " + ex.getMessage());
		}
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		if (_dnsServer != null) {
			_dnsServer.stop();
			_dnsServer = null;
		}
	}

	public static DnsServer getDnsServer() {
		return _dnsServer;
	}
}
