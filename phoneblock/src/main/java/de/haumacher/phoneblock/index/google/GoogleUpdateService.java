/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.index.google;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.util.Collections;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletContextEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.client.http.AbstractInputStreamContent;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

import de.haumacher.msgbuf.io.StringW;
import de.haumacher.msgbuf.json.JsonWriter;
import de.haumacher.phoneblock.index.IndexUpdateService;
import de.haumacher.phoneblock.index.google.UpdateMessage.Type;
import de.haumacher.phoneblock.util.ConnectionUtil;

/**
 * {@link IndexUpdateService} updating the Google index.
 */
public class GoogleUpdateService implements IndexUpdateService {

	private static final Logger LOG = LoggerFactory.getLogger(GoogleUpdateService.class);

	private String _contextPath;
	private boolean _active;
	private HttpRequestFactory _requestFactory;

	@Override
	public void contextInitialized(ServletContextEvent sce) {
		_contextPath = sce.getServletContext().getContextPath();
		
		String accountFile = lookupAccountFile();
		if (accountFile == null) {
			LOG.warn("No google account file found, deactivating google update.");
		} else {
			try (InputStream in = new FileInputStream(accountFile)) {
				GoogleCredentials credentials = GoogleCredentials.fromStream(in)
					.createScoped(Collections.singleton("https://www.googleapis.com/auth/indexing"));
				NetHttpTransport httpTransport = new NetHttpTransport();
				_requestFactory = httpTransport.createRequestFactory(new HttpCredentialsAdapter(credentials));
				_active = true;
				LOG.info("Activated google update service.");
			} catch (IOException ex) {
				LOG.error("Failed to activate google update service.", ex);
			}
		}
	}
	
	private String lookupAccountFile() {
		try {
			InitialContext initCtx = new InitialContext();
			Context envCtx = (Context) initCtx.lookup("java:comp/env");
			
			try {
				return (String) envCtx.lookup("google/accountfile");
			} catch (NamingException ex) {
				LOG.info("No Google account file: " + ex.getMessage());
			}
		} catch (NamingException ex) {
			LOG.info("No JNDI configuration, no Google account file: " + ex.getMessage());
		}
		return null;
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		// Ignore.
	}
	
	@Override
	public void publishPathUpdate(String path) {
		if (!_active) {
			return;
		}
		
		if (!path.startsWith("/")) {
			path = "/" + path;
		}
		
		String url = "https://phoneblock.net" + _contextPath + path;

		try {
			AbstractInputStreamContent body = new ByteArrayContent("application/json; charset=UTF-8", 
				toByteArray(UpdateMessage.create().setUrl(url).setType(Type.URL_UPDATED)));
			HttpRequest request = _requestFactory.buildPostRequest(
				new GenericUrl("https://indexing.googleapis.com/v3/urlNotifications:publish"), body);
			HttpResponse response = request.execute();
			
			int code = response.getStatusCode();
			if (code == HttpURLConnection.HTTP_OK) {
				LOG.info("Added URL to Goolge index: " + url);
			} else {
				try (InputStream in = response.getContent()) {
					LOG.warn("Failed to add URL to Goolge index (" + code + "): " + url + ": " +
						ConnectionUtil.readText(in, response.getContentEncoding()));
				}
			}
		} catch (IOException ex) {
			LOG.error("Failed to add URL to Goolge index: " +  url + ": " + ex.getMessage());
		}
	}

	private byte[] toByteArray(UpdateMessage message) throws IOException, UnsupportedEncodingException {
		StringW buffer = new StringW();
		message.writeTo(new JsonWriter(buffer));
		byte[] content = buffer.toString().getBytes("utf-8");
		return content;
	}

}
