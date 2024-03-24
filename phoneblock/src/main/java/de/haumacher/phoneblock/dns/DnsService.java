package de.haumacher.phoneblock.dns;

import java.io.IOException;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.scheduler.SchedulerService;

/**
 * Service module starting a DNS server for answering DNS lookups from Fritz!Box devices registered via DynDNS.
 */
public class DnsService implements ServletContextListener {
	
	private static final Logger LOG = LoggerFactory.getLogger(DnsService.class);
	
	private SchedulerService _scheduler;
	private DnsServer _dnsServer;

	public DnsService(SchedulerService scheduler) {
		_scheduler = scheduler;
	}
	
	@Override
	public void contextInitialized(ServletContextEvent sce) {
		try {
			_dnsServer = new DnsServer(_scheduler.executor(), 5300).start();
		} catch (IOException ex) {
			LOG.error("Failed to start name server.", ex);
		}
	}
	
	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		if (_dnsServer != null) {
			_dnsServer.stop();
			_dnsServer = null;
		}
	}

	public DnsServer getDnsServer() {
		return _dnsServer;
	}
}
