/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.mail;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.app.Application;
import de.haumacher.phoneblock.app.SettingsServlet;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.DBUserSettings;
import de.haumacher.phoneblock.db.settings.AnswerBotSip;
import de.haumacher.phoneblock.mail.check.EMailCheckService;
import jakarta.mail.Address;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.NoSuchProviderException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMessage.RecipientType;
import jakarta.mail.internet.MimeMultipart;

/**
 * Service for sending e-mail messages.
 */
public class MailService {

	private static final Logger LOG = LoggerFactory.getLogger(MailService.class);
	
	private static final String HOME_PAGE = "https://phoneblock.net";
	
	private static final String FACE_BOOK = "https://www.facebook.com/PhoneBlock";
	
	private static final String HELP_VIDEO = "https://www.youtube.com/@phoneblock";

	private final String _appLogoSvg;
	
	private final String _settings;
	
	private final String _app;
	
	private final String _support;
	
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
		
		String contextPath = Application.getContextPath();
		String baseUrl = HOME_PAGE + contextPath;
		
		_appLogoSvg = baseUrl + "/app-logo.svg";
		_settings = baseUrl + SettingsServlet.PATH;
		_app = baseUrl + "/ab/";
		_support = baseUrl + "/support.jsp";
	}

	public void sendActivationMail(String receiver, String code)
			throws MessagingException, IOException, AddressException {
		
		if (receiver == null || receiver.isBlank()) {
			LOG.info("Rejected emtpy e-mail address..");
			throw new AddressException("Address must not be empty.");
		}
		
    	InternetAddress address = new InternetAddress(receiver);
    	
    	if (EMailCheckService.getInstance().isDisposable(address)) {
			LOG.warn("Rejected disposable e-mail address: " + receiver);
    		throw new AddressException("Please do not use disposable e-mail addresses.");
    	}
    	
		LOG.info("Sending activation mail to '" + receiver + "'.");

		Map<String, String> variables = new HashMap<>();
		variables.put("{name}", DB.toDisplayName(address.getAddress()));
    	variables.put("{code}", code);
    	variables.put("{image}", _appLogoSvg);
    	
		sendMail("PhoneBlock Anmelde-Code", address, "mail-template", variables);
	}

	public boolean sendHelpMail(DBUserSettings userSettings) {
		String receiver = userSettings.getEmail();
		if (receiver == null || receiver.isBlank()) {
			LOG.warn("Cannot send help mail to '" + userSettings.getId() + "', no e-mail provided.");
			return true;
		}
		
		LOG.info("Sending help mail to '" + receiver + "'.");
		
		try {
			sendMail("PhoneBlock: Deine Installation", new InternetAddress(receiver), "help-mail", buildVariables(userSettings));
			return true;
		} catch (MessagingException | IOException ex) {
			LOG.error("Failed to send help mail to: " + receiver, ex);
			return false;
		}
	}
	
	public boolean sendDiableMail(DBUserSettings userSettings, AnswerBotSip answerbot) {
		String receiver = userSettings.getEmail();
		if (receiver == null || receiver.isBlank()) {
			LOG.warn("Cannot send answerbot disable mail to '" + userSettings.getId() + "', no e-mail provided.");
			return true;
		}
		
		LOG.info("Sending answerbot disable mail to '" + receiver + "'.");
		
		try {
			sendMail("PhoneBlock: Dein Anrufbeantworter", new InternetAddress(receiver), "ab-disable-mail", buildVariables(userSettings, answerbot));
			return true;
		} catch (MessagingException | IOException ex) {
			LOG.error("Failed to send help mail to: " + receiver, ex);
			return false;
		}
	}
	
	private Map<String, String> buildVariables(DBUserSettings userSettings, AnswerBotSip answerbot) {
		Map<String, String> variables = buildVariables(userSettings);
		
		variables.put("{lastSuccess}", formatDateTime(answerbot.getLastSuccess()));
		variables.put("{lastMessage}", answerbot.getRegisterMessage());
		variables.put("{botId}", answerbot.getUserName());
		
		return variables;
	}
	
	/** 
	 * Sends a welcome mail to the given user.
	 */
	public void sendWelcomeMail(DBUserSettings userSettings) {
		String receiver = userSettings.getEmail();
		if (receiver == null || receiver.isBlank()) {
			LOG.warn("Cannot send welcome mail to '" + userSettings.getId() + "', no e-mail provided.");
			return;
		}
		
		LOG.info("Sending welcome mail to '" + receiver + "'.");
		
		try {
			sendMail("Willkommen bei PhoneBlock", new InternetAddress(receiver), "welcome-mail", buildVariables(userSettings));
		} catch (MessagingException | IOException ex) {
			LOG.error("Failed to send welcome mail to: " + receiver, ex);
		}
	}

	private Map<String, String> buildVariables(DBUserSettings userSettings) {
		Map<String, String> variables = new HashMap<>();
		variables.put("{name}", userSettings.getDisplayName());
		variables.put("{userName}", userSettings.getLogin());
		variables.put("{lastAccess}", formatDateTime(userSettings.getLastAccess()));
		variables.put("{image}", _appLogoSvg);
		variables.put("{home}", HOME_PAGE);
		variables.put("{facebook}", FACE_BOOK);
		variables.put("{help}", HELP_VIDEO);
		variables.put("{mail}", MAIL);
		variables.put("{settings}", _settings);
		variables.put("{app}", _app);
		variables.put("{support}", _support);
		return variables;
	}

	protected String formatDateTime(long time) {
		DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT, Locale.GERMAN);
		String result = time == 0 ? "Noch nie." : dateFormat.format(new Date(time));
		return result;
	}
	
	private void sendMail(String subject, InternetAddress receiver, String template, Map<String, String> variables)
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

	public void sendMail(InternetAddress receiver, Message msg) throws AddressException, MessagingException {
		msg.setRecipient(RecipientType.TO, receiver);
		Address[] addresses = {receiver};
		
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
			try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
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
