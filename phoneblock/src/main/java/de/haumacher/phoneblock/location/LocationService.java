package de.haumacher.phoneblock.location;

import java.io.IOException;
import java.net.InetAddress;

import javax.naming.NamingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.jndi.JNDIProperties;
import de.haumacher.phoneblock.location.model.Country;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

/**
 * Service for resolving the country from which a request was made.
 */
public class LocationService implements LocationLookup, ServletContextListener {
	
	private static final Logger LOG = LoggerFactory.getLogger(LocationService.class);
	private Country4IP _country4ip;
	
	private static LocationLookup INSTANCE = LocationLookup.NONE;
	
	public static LocationLookup getInstance() {
		return INSTANCE;
	}
	
	@Override
	public void contextInitialized(ServletContextEvent sce) {
		LOG.info("Starting location service.");
		
		try {
			JNDIProperties jndi = new JNDIProperties();
			
			String ip4db = jndi.lookupString("location.ip4db");
			if (ip4db == null) {
				LOG.warn("No IPv4 location database configured (location.ip4db).");
			}
			String ip6db = jndi.lookupString("location.ip6db");
			if (ip6db == null) {
				LOG.warn("No IPv6 location database configured (location.ip6db).");
			}
			
			if (ip4db != null && ip6db != null) {
				_country4ip = new Country4IP(ip4db, ip6db);
			} else {
				LOG.warn("Disabling location lookup.");
			}
		} catch (NamingException | IOException ex) {
			LOG.error("Failed to setup location service, disabling location lookup.", ex);
			_country4ip = null;
		}
		
		INSTANCE = this;
	}
	
	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		_country4ip = null;
		INSTANCE = LocationLookup.NONE;
		LOG.info("Location service shut down.");
	}
	
	public Country getCountry(InetAddress address) {
		if (_country4ip == null) {
			return null;
		}
		
		try {
			String countryCode = _country4ip.lookup(address);
			return Countries.get(countryCode);
		} catch (IOException ex) {
			LOG.error("Cannot resolve country for IP address '{}'.", address, ex);
			return null;
		}
	}

}
