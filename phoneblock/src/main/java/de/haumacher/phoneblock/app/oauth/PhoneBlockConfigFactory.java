/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.app.oauth;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.pac4j.core.client.Client;
import org.pac4j.core.client.Clients;
import org.pac4j.core.config.Config;
import org.pac4j.core.config.ConfigFactory;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.WebContextFactory;
import org.pac4j.core.engine.DefaultSecurityLogic;
import org.pac4j.core.engine.savedrequest.DefaultSavedRequestHandler;
import org.pac4j.core.engine.savedrequest.SavedRequestHandler;
import org.pac4j.core.util.Pac4jConstants;
import org.pac4j.jee.context.JEEContext;
import org.pac4j.oauth.client.FacebookClient;
import org.pac4j.oauth.client.Google2Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.app.LoginServlet;

/**
 * {@link ConfigFactory} for authenticating with external services.
 */
public class PhoneBlockConfigFactory implements ConfigFactory {
	
	private static final Logger LOG = LoggerFactory.getLogger(PhoneBlockConfigFactory.class);
	
    @Override
    public Config build(final Object... parameters) {
    	Properties properties = new Properties();
    	try {
			properties.load(PhoneBlockConfigFactory.class.getResourceAsStream("/phoneblock.properties"));
		} catch (IOException ex) {
			LOG.error("Failed to read configuration properties.", ex);
		}
    	
    	String contextPath = properties.getProperty("phoneblock.contextpath");
    	if (contextPath == null) {
    		contextPath = "/phoneblock";
    	}
    	
    	List<Client> clientList = new ArrayList<>();
        addGoogleClient(clientList, properties);
        addFacebookClient(clientList, properties);
        
        String clientNames = clientList.stream().map(client -> client.getName()).collect(Collectors.joining(","));
		
		String callbackUrl = "https://phoneblock.net" + contextPath + "/oauth/callback";
		LOG.info("Using oauth callback URL: " + callbackUrl);
		Clients clients = new Clients(callbackUrl, clientList);
		clients.setDefaultSecurityClients(clientNames);
		
        Config result = new Config(clients);
        result.setWebContextFactory(new ProxyAwareWebContextFactory());
        
        DefaultSecurityLogic securityLogic = new DefaultSecurityLogic();
        SavedRequestHandler savedRequestHandler = new DefaultSavedRequestHandler() {
        	public void save(WebContext context, org.pac4j.core.context.session.SessionStore sessionStore) {
        		Optional<String> locationHandle = context.getRequestParameter(LoginServlet.LOCATION_ATTRIBUTE);
        		if (locationHandle.isPresent()) {
        			String location = locationHandle.get();
					LOG.info("Saving requested location during OAuth authentication: " + location);
        			sessionStore.set(context, LoginServlet.LOCATION_ATTRIBUTE, location);
        		}
        		
        		super.save(context, sessionStore);
        	}
        };
		securityLogic.setSavedRequestHandler(savedRequestHandler);
		result.setSecurityLogic(securityLogic);
		return result;
    }

	private void addGoogleClient(List<Client> clientList, Properties properties) {
		String googleClientId = properties.getProperty("google.id");
		String googleClientSecret = properties.getProperty("google.secret");
		if (googleClientId == null || googleClientSecret == null) {
			LOG.warn("Missing credentials for Google authentication.");
		} else {
			LOG.info("Configuring client for Google authentication: " + googleClientId);
			clientList.add(new Google2Client(googleClientId, googleClientSecret));
		}
	}

	private void addFacebookClient(List<Client> clientList, Properties properties) {
		String facebookClientId = properties.getProperty("facebook.id");
		String facebookClientSecret = properties.getProperty("facebook.secret");
		if (facebookClientId == null || facebookClientSecret == null) {
			LOG.warn("Missing credentials for Facebook authentication.");
		} else {
			LOG.info("Configuring client for Facebook authentication: " + facebookClientId);
			clientList.add(new FacebookClient(facebookClientId, facebookClientSecret));
		}
	}

	/**
	 * {@link WebContextFactory} that does not construct a full URL with protocol and host, because the server may be
	 * behind an Apache proxy seeing only localhost as host name.
	 */
	private static final class ProxyAwareWebContextFactory
			implements WebContextFactory {
		@Override
		public WebContext newContext(Object... parameters) {
		    var request = (HttpServletRequest) parameters[0];
		    var response = (HttpServletResponse) parameters[1];
		    return new JEEContext(request, response) {
		    	@Override
		    	public String getFullRequestURL() {
		    		StringBuilder result = new StringBuilder();
					result.append(request.getContextPath());
					result.append(request.getServletPath());
					String pathInfo = request.getPathInfo();
					if (pathInfo != null) {
						result.append(pathInfo);
					}
					
					var queryString = request.getQueryString();
					if (queryString != null) {
						result.append('?');
						result.append(queryString);
					}
					return result.toString();
		    	}
		    };
		}
	}
}