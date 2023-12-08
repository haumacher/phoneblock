/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.mail;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.stream.Collectors;

import javax.mail.Address;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMessage.RecipientType;
import javax.mail.internet.MimeMultipart;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.db.DBUserSettings;

/**
 * Service for sending e-mail messages.
 */
public class MailService {

	private static final Logger LOG = LoggerFactory.getLogger(MailService.class);
	
	private static final String HOME_PAGE = "https://phoneblock.net/";
	
	private static final String APP_LOGO_SVG = "https://phoneblock.net/phoneblock/app-logo.svg";

	private static final String FACE_BOOK = "https://www.facebook.com/PhoneBlock";
	
	private static final String HELP_VIDEO = "https://www.youtube.com/watch?v=iV3aWhU1cMU&t=3s";

	private static final String SETTINGS = "https://phoneblock.net/phoneblock/settings";
	
	private static final String MAIL = "phoneblock@haumacher.de";

	private Session _session;
	private String _user;
	private String _password;
	private Properties _properties;
	private Transport _transport;
	private Address _from;

	/** 
	 * Creates a {@link MailService}.
	 */
	public MailService(String user, String password, Properties properties) {
		_user = user;
		_password = password;
		_properties = properties;
	}

	public void sendActivationMail(String receiver, String code)
			throws MessagingException, IOException, AddressException {
		LOG.info("Sending activation mail to '" + receiver + "'.");

		Map<String, String> variables = new HashMap<>();
    	variables.put("{code}", code);
    	variables.put("{image}", APP_LOGO_SVG);
    	
    	sendMail("PhoneBlock E-Mail Bestätigung", receiver, "mail-template", variables);
	}

	public boolean sendHelpMail(DBUserSettings userSettings) {
		String receiver = userSettings.getEmail();
		if (receiver == null || receiver.isBlank()) {
			LOG.warn("Cannot send help message to '" + userSettings.getId() + "', no e-mail provided.");
			return true;
		}
		
		LOG.info("Sending help mail to '" + receiver + "'.");
		
		try {
			sendMail("PhoneBlock: Deine Installation", receiver, "help-mail", buildVariables(userSettings));
			return true;
		} catch (MessagingException | IOException ex) {
			LOG.error("Failed to send help mail to: " + receiver, ex);
			return false;
		}
	}
	
	/** 
	 * Sends a welcome message to the given user.
	 */
	public void sendWelcomeMail(DBUserSettings userSettings) {
		String receiver = userSettings.getEmail();
		if (receiver == null || receiver.isBlank()) {
			LOG.warn("Cannot send welcome message to '" + userSettings.getId() + "', no e-mail provided.");
			return;
		}
		
		LOG.info("Sending welcome mail to '" + receiver + "'.");
		
		try {
			sendMail("Willkommen bei PhoneBlock", receiver, "welcome-mail", buildVariables(userSettings));
		} catch (MessagingException | IOException ex) {
			LOG.error("Failed to send welcome mail to: " + receiver, ex);
		}
	}

	private Map<String, String> buildVariables(DBUserSettings userSettings) {
		String name = userSettings.getDisplayName();
		int atIndex = name.indexOf('@');
		if (atIndex > 0) {
			name = name.substring(0, atIndex);
		}
		name = Arrays.stream(name.replace('.', ' ').replace('_', ' ').split("\\s+"))
			.filter(p -> p.length() > 0)
			.map(p -> Character.toUpperCase(p.charAt(0)) + p.substring(1))
			.collect(Collectors.joining(" "));
		
		Map<String, String> variables = new HashMap<>();
		variables.put("{name}", name);
		variables.put("{image}", APP_LOGO_SVG);
		variables.put("{home}", HOME_PAGE);
		variables.put("{facebook}", FACE_BOOK);
		variables.put("{help}", HELP_VIDEO);
		variables.put("{mail}", MAIL);
		variables.put("{settings}", SETTINGS);
		return variables;
	}
	
	private void sendMail(String subject, String receiver, String template, Map<String, String> variables)
			throws MessagingException, IOException {
		Message msg = createMessage();
		msg.setSubject(subject);
		
	    MimeMultipart alternativePart = new MimeMultipart("alternative");
	    {
			{
    			MimeBodyPart sourcePart = new MimeBodyPart();
    			sourcePart.setText(read(template + ".html", variables), "utf-8", "html");
	    		alternativePart.addBodyPart(sourcePart);
	    	}

	    	{
	    		MimeBodyPart text = new MimeBodyPart();
	    		text.setText(read(template + ".txt", variables), "utf-8");
	    		alternativePart.addBodyPart(text);
	    	}
	    }
		
		msg.setContent(alternativePart);
		try {
			sendMail(receiver, msg);
		} catch (MessagingException ex) {
			LOG.error("Sending activation mail to '" + receiver + "' failed.");
			throw ex;
		}
	}

	public Message createMessage() throws MessagingException {
		Message msg = new MimeMessage(getSession());
		msg.setFrom(_from);
		return msg;
	}

	public void sendMail(String receiver, Message msg) throws AddressException, MessagingException {
		InternetAddress address = new InternetAddress(receiver);
		msg.setRecipient(RecipientType.TO, address);
		Address[] addresses = {address};
		
		try {
			getTransport().sendMessage(msg, addresses);
		} catch (MessagingException | IllegalStateException ex) {
			// Re-try.
			shutdownTransport();
			getTransport().sendMessage(msg, addresses);
		}
	}

	public void startUp() {
		try {
			getTransport();
		} catch (MessagingException ex) {
			LOG.error("Cannot start mail service.", ex);
		}
	}

	private Transport getTransport() throws NoSuchProviderException, MessagingException {
		if (_transport == null || !_transport.isConnected()) {
			_transport = getSession().getTransport();
			_transport.connect(_user, _password);
		}
		return _transport;
	}

	private Session startSession() throws AddressException {
		_from = new InternetAddress(_properties.getProperty("mail.smtp.from"));
		Authenticator authenticator = new Authenticator() {
			@Override
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication(_user, _password);
			}
		};
		return Session.getDefaultInstance(_properties, authenticator);
	}
	
	/** 
	 * Shuts down the {@link MailService}.
	 */
	public void shutdown() {
		if (_transport != null) {
			shutdownTransport();
		}
		
		if (_session != null) {
			_session = null;
			LOG.info("Mail service shut down.");
		} else {
			LOG.info("Mail service was not started, skipping shutdown.");
		}
	}

	private void shutdownTransport() {
		try {
			_transport.close();
		} catch (MessagingException ex) {
			LOG.error("Shutting down mail transport failed.", ex);
		}
		_transport = null;
	}

	private String read(String resource, Map<String, String> variables) throws IOException {
		StringBuilder result = new StringBuilder();
		char[] buffer = new char[4096];
		try (InputStream in = getClass().getResourceAsStream(resource)) {
			try (Reader r = new InputStreamReader(in, "utf-8")) {
				while (true) {
					int direct = r.read(buffer);
					if (direct < 0) {
						break;
					}
					result.append(buffer, 0, direct);
				}
			}
		}
		return expandVariables(result.toString(), variables);
	}

	private String expandVariables(String text, Map<String, String> variables) {
		for (Entry<String, String> var : variables.entrySet()) {
			text = text.replace(var.getKey(), var.getValue());
		}
		return text;
	}

	private Session getSession() throws AddressException {
		if (_session == null) {
			_session = startSession();
		}
		return _session;
	}

}
