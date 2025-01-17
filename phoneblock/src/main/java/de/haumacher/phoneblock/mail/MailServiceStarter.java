/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.mail;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Properties;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import net.markenwerk.utils.data.fetcher.BufferedDataFetcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link MailService} singleton.
 */
public class MailServiceStarter implements ServletContextListener {
	
	private static final String SMPT_PROPERTY_PREFIX = "smtp.properties.";

	private static final Logger LOG = LoggerFactory.getLogger(MailServiceStarter.class);

	private static MailService INSTANCE;

	private MailService _mailService;
	
	/**
	 * The {@link MailService} instance.
	 */
	public static MailService getInstance() {
		return INSTANCE;
	}
	
	/**
	 * The {@link MailService} instance.
	 */
	public MailService getMailService() {
		return _mailService;
	}

	@Override
	public void contextInitialized(ServletContextEvent sce) {
		LOG.info("Starting mail service.");
		String user;
		String password;
		
		MailSignature signature;
		
		Properties properties = new Properties();
		try {
			InitialContext initCtx = new InitialContext();
			Context envCtx = (Context) initCtx.lookup("java:comp/env");
			
			user = (String) envCtx.lookup("smtp/user");        
			password = (String) envCtx.lookup("smtp/password");
			
			if (user == null && password == null) {
				LOG.warn("No mail configuration, mail service not available.");
				return;
			} else {
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
			}
			
			signature = readMailSignatureJNDI(envCtx);
		} catch (NamingException ex) {
			LOG.info("No JNDI mail configuration: " + ex.getMessage());

			user = getProperty("user");        
			password = getProperty("password");
			if (user == null && password == null) {
				LOG.warn("No mail property configuration, mail service not available.");
				return;
			}

			Properties systemProperties = System.getProperties();
			for (String property : systemProperties.stringPropertyNames()) {
				if (!property.startsWith(SMPT_PROPERTY_PREFIX)) {
					continue;
				}
				
				String smtpProperty = property.substring(SMPT_PROPERTY_PREFIX.length());
				properties.setProperty(smtpProperty, systemProperties.getProperty(property));
			}
			
			signature = readMailSignatureProperties();
		}

		_mailService = new MailService(user, password, signature, properties);
		_mailService.startUp();
		
		INSTANCE = _mailService;
	}

	private MailSignature readMailSignatureJNDI(Context envCtx) {
		try {
			String signingSelector = (String) envCtx.lookup("smtp/signingSelector");
			String signingDomain = (String) envCtx.lookup("smtp/signingDomain");
			String signingKeyFile = (String) envCtx.lookup("smtp/signingKey");
			return createMailSignature(signingSelector, signingDomain, signingKeyFile);
		} catch (Exception ex) {
			LOG.warn("No mail signature configured in JNDI.", ex);
			return null;
		}
	}

	private MailSignature readMailSignatureProperties() {
		try {
			String signingSelector = getProperty("signingSelector");
			String signingDomain = getProperty("signingDomain");
			String signingKeyFile = getProperty("signingKey");
			return createMailSignature(signingSelector, signingDomain, signingKeyFile);
		} catch (Exception ex) {
			LOG.warn("No mail signature configured in properties.", ex);
			return null;
		}
	}
	
	private MailSignature createMailSignature(String signingSelector, String signingDomain, String signingKeyFile)
			throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
		RSAPrivateKey signingKey = readPrivateKey(signingKeyFile);
		MailSignature result = new MailSignature(signingSelector, signingDomain, signingKey);
		
		LOG.info("Loaded mail signature: " + result);
		
		return result;
	}
	
	private static RSAPrivateKey readPrivateKey(String fileName)
			throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
		LOG.info("Reading dkim key from {}.", fileName);
		try (InputStream derStream = new FileInputStream(new File(fileName))) {
			byte[] privKeyBytes = new BufferedDataFetcher().fetch(derStream, true);
			KeyFactory rsaKeyFactory = KeyFactory.getInstance("RSA");
			PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privKeyBytes);
			return (RSAPrivateKey) rsaKeyFactory.generatePrivate(privateKeySpec);
		}
	}

	   private String getProperty(String property) {
		String value = System.getProperty("smtp." + property);
		if (value != null) {
			return value;
		}
		return null;
	}
	
	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		if (_mailService != null) {
			LOG.info("Shutting down mail server.");
			if (INSTANCE == _mailService) {
				INSTANCE = null;
			}
			_mailService.shutdown();
		} else {
			LOG.info("Skipping mail server shutdown, not started.");
		}
	}
	
}
