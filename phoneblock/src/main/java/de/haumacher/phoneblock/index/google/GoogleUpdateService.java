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

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.AbstractInputStreamContent;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

import de.haumacher.msgbuf.io.StringW;
import de.haumacher.msgbuf.json.JsonWriter;
import de.haumacher.phoneblock.index.IndexUpdateService;
import de.haumacher.phoneblock.index.google.UpdateMessage.Type;
import de.haumacher.phoneblock.util.ConnectionUtil;

/**
 * {@link IndexUpdateService} updating the Google index.
 */
public class GoogleUpdateService implements IndexUpdateService {

	private String _contextPath;
	private HttpTransport _httpTransport;
	private GoogleCredential _credentials;
	private boolean _active;
	private HttpRequestFactory _requestFactory;

	@Override
	public void contextInitialized(ServletContextEvent sce) {
		_contextPath = sce.getServletContext().getContextPath();
		
		String accountFile = lookupAccountFile();
		if (accountFile == null) {
			System.out.println("No google account file found, deactivating google update.");
		} else {
			try (InputStream in = new FileInputStream(accountFile)) {
				_httpTransport = new NetHttpTransport();
				_requestFactory = _httpTransport.createRequestFactory();
				_credentials = GoogleCredential.fromStream(in, _httpTransport, new GsonFactory())
					.createScoped(Collections.singleton("https://www.googleapis.com/auth/indexing"));
				_active = true;
				System.out.println("Activated google update service.");
			} catch (IOException ex) {
				System.out.println("ERROR: Failed to activate google update service.");
				ex.printStackTrace();
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
				System.out.print("No Google account file: " + ex.getMessage());
			}
		} catch (NamingException ex) {
			System.out.print("No JNDI configuration, no Google account file: " + ex.getMessage());
		}
		return null;
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		// Ignore.
	}
	
	@Override
	public void publishUpdate(String path) {
		if (!_active) {
			return;
		}
		
		String url = "https://phoneblock.haumacher.de" + _contextPath + path;

		try {
			AbstractInputStreamContent body = new ByteArrayContent("application/json; charset=UTF-8", 
				toByteArray(UpdateMessage.create().setUrl(url).setType(Type.URL_UPDATED)));
			HttpRequest request = _requestFactory.buildPostRequest(
				new GenericUrl("https://indexing.googleapis.com/v3/urlNotifications:publish"), body);
			_credentials.initialize(request);
			HttpResponse response = request.execute();
			
			int code = response.getStatusCode();
			if (code == HttpURLConnection.HTTP_OK) {
				System.out.println("Added URL to Goolge index: " + url);
			} else {
				try (InputStream in = response.getContent()) {
					System.out.println("ERROR adding URL to Goolge index (" + code + "): " + 
						ConnectionUtil.readText(in, response.getContentEncoding()));
				}
			}
		} catch (IOException ex) {
			System.out.println("ERROR adding URL to Goolge index: " + ex.getMessage());
			ex.printStackTrace();
		}
	}

	private byte[] toByteArray(UpdateMessage message) throws IOException, UnsupportedEncodingException {
		StringW buffer = new StringW();
		message.writeTo(new JsonWriter(buffer));
		byte[] content = buffer.toString().getBytes("utf-8");
		return content;
	}

}
