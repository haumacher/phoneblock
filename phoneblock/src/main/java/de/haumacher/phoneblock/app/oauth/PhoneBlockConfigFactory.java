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

import org.pac4j.core.client.Client;
import org.pac4j.core.client.Clients;
import org.pac4j.core.config.Config;
import org.pac4j.core.config.ConfigFactory;
import org.pac4j.core.context.FrameworkParameters;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.WebContextFactory;
import org.pac4j.core.engine.DefaultSecurityLogic;
import org.pac4j.core.engine.savedrequest.DefaultSavedRequestHandler;
import org.pac4j.core.engine.savedrequest.SavedRequestHandler;
import org.pac4j.core.util.Pac4jConstants;
import org.pac4j.jee.context.JEEContext;
import org.pac4j.jee.context.JEEFrameworkParameters;
import org.pac4j.oidc.client.GoogleOidcClient;
import org.pac4j.oidc.config.OidcConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.app.Application;
import de.haumacher.phoneblock.app.LoginServlet;
import de.haumacher.phoneblock.app.UIProperties;

/**
 * {@link ConfigFactory} for authenticating with external services.
 */
public class PhoneBlockConfigFactory implements ConfigFactory {
	
	public static final String GOOGLE_CLIENT = "Google2Client";
	
	private static final Logger LOG = LoggerFactory.getLogger(PhoneBlockConfigFactory.class);
	
    @Override
    public Config build(final Object... parameters) {
    	Properties properties = UIProperties.APP_PROPERTIES;
    	
    	String contextPath = Application.getContextPath();
    	if (contextPath == null) {
    		contextPath = properties.getProperty("phoneblock.contextpath");
    	}
    	if (contextPath == null) {
    		contextPath = "/phoneblock";
    	}
    	
    	List<Client> clientList = new ArrayList<>();
        addGoogleClient(clientList, properties);
        
        String clientNames = clientList.stream().map(client -> client.getName()).collect(Collectors.joining(","));
        LOG.info("Configured clients: " + clientNames);
		
		String callbackUrl = "https://phoneblock.net" + contextPath + "/oauth/callback";
		LOG.info("Using oauth callback URL: " + callbackUrl);
		Clients clients = new Clients(callbackUrl, clientList);
		clients.setDefaultSecurityClients(clientNames);
		
        Config result = new Config(clients);
        result.setWebContextFactory(new ProxyAwareWebContextFactory());
        
        DefaultSecurityLogic securityLogic = new DefaultSecurityLogic();
        SavedRequestHandler savedRequestHandler = new DefaultSavedRequestHandler() {
        	public void save(org.pac4j.core.context.CallContext ctx) {
        		Optional<String> locationHandle = ctx.webContext().getRequestParameter(LoginServlet.LOCATION_ATTRIBUTE);
        		if (locationHandle.isPresent()) {
        			String location = locationHandle.get();
					LOG.info("Saving requested location during OAuth authentication: " + location);
        			ctx.sessionStore().set(ctx.webContext(), LoginServlet.LOCATION_ATTRIBUTE, location);
        		}
        		
        		Optional<String> rememberHandle = ctx.webContext().getRequestParameter(LoginServlet.REMEMBER_PARAM);
        		if (rememberHandle.isPresent()) {
        			String remember = rememberHandle.get();
        			LOG.info("Saving requested remember-me status during OAuth authentication: " + remember);
        			ctx.sessionStore().set(ctx.webContext(), LoginServlet.REMEMBER_PARAM, remember);
        		}
        		
        		super.save(ctx);
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
			OidcConfiguration config = new OidcConfiguration();
			config.setClientId(googleClientId);
			config.setSecret(googleClientSecret);
			GoogleOidcClient googleClient = new GoogleOidcClient(config);
			googleClient.setName(GOOGLE_CLIENT);
			
			clientList.add(googleClient);
		}
	}

	/**
	 * {@link WebContextFactory} that does not construct a full URL with protocol and host, because the server may be
	 * behind an Apache proxy seeing only localhost as host name.
	 */
	private static final class ProxyAwareWebContextFactory
			implements WebContextFactory {
		@Override
		public WebContext newContext(FrameworkParameters parameters) {
		    var request = ((JEEFrameworkParameters) parameters).getRequest();
		    var response = ((JEEFrameworkParameters) parameters).getResponse();
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