package de.haumacher.phoneblock.jndi;

import java.util.Properties;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JNDIProperties {

	private static final Logger LOG = LoggerFactory.getLogger(JNDIProperties.class);

	private InitialContext _initCtx;
	private Context _envCtx;

	public JNDIProperties() throws NamingException {
		_initCtx = new InitialContext();
		_envCtx = (Context) _initCtx.lookup("java:comp/env");
	}

	public String lookupString(String property) {
		String name = toName(property);
		try {
			String value = _envCtx.lookup(name).toString();
			LOG.info("Using '{}' from JNDI: {}", name, value);
			return value;
		} catch (NamingException e) {
			String value = System.getProperty(property);
			if (value != null) {
				LOG.info("Using '{}' from system properties: {}", property, value);
				return value;
			} else {
				LOG.info("Neither in JDNI nor system properties: '{}'", property);
				return null;
			}
		}
	}

	public Properties lookupProperties(String prefix) {
		Properties properties = new Properties();
		
		String namePrefix = toName(prefix);
		
		try {
			Context propertyContext = (Context) _envCtx.lookup(namePrefix);
			if (propertyContext != null) {
				NamingEnumeration<NameClassPair> list = _envCtx.list(namePrefix);
				while (list.hasMore()) {
					NameClassPair pair = list.next();
					
					if ("java.lang.String".equals(pair.getClassName())) {
						String name = pair.getName();
						String value = (String) propertyContext.lookup(name);
						properties.setProperty(name, value);

						LOG.info("Using '{}' from JNDI: {}", name, value);
					}
				}
			}
		} catch (NamingException e) {
			LOG.info("No properties with prefix '{}' in JNDI: {}", namePrefix, e.getMessage());
		}
		
		String propertyPrefix = prefix + ".";
		
		Properties systemProperties = System.getProperties();
		for (String property : systemProperties.stringPropertyNames()) {
			if (!property.startsWith(propertyPrefix)) {
				continue;
			}
			
			String value = property.substring(propertyPrefix.length());
			properties.setProperty(value, systemProperties.getProperty(property));
			
			LOG.info("Using '{}' from system properties: {}", property, value);
		}
		
		return properties;
	}

	private String toName(String property) {
		return property.replace('.', '/');
	}

}
