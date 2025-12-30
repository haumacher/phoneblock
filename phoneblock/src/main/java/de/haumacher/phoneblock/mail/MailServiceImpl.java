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
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.ResourceBundle;

import org.simplejavamail.utils.mail.dkim.Canonicalization;
import org.simplejavamail.utils.mail.dkim.DkimMessage;
import org.simplejavamail.utils.mail.dkim.DkimSigner;
import org.simplejavamail.utils.mail.dkim.SigningAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.app.Application;
import de.haumacher.phoneblock.app.SettingsServlet;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.settings.AnswerBotSip;
import de.haumacher.phoneblock.db.settings.UserSettings;
import de.haumacher.phoneblock.mail.check.EMailCheckService;
import jakarta.mail.Address;
import jakarta.mail.Authenticator;
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
public class MailServiceImpl implements MailService {

	private static final Logger LOG = LoggerFactory.getLogger(MailServiceImpl.class);
	
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
	private InternetAddress _from;

	private final MailSignature _signature;

	/** 
	 * Creates a {@link MailService}.
	 */
	public MailServiceImpl(String user, String password, MailSignature signature, Properties properties) {
		_user = user;
		_password = password;
		_signature = signature;
		_properties = properties;
		
		String contextPath = Application.getContextPath();
		String baseUrl = HOME_PAGE + contextPath;
		
		_appLogoSvg = baseUrl + "/assets/img/app-logo.svg";
		_settings = baseUrl + SettingsServlet.PATH;
		_app = baseUrl + "/ab/";
		_support = baseUrl + "/support.jsp";
	}

	/**
	 * Get the user's locale preference.
	 *
	 * @param userSettings The user settings
	 * @return The locale string (e.g., "de", "en-US"), defaults to "de" if not set
	 */
	private String getUserLocale(UserSettings userSettings) {
		String locale = userSettings.getLang();
		if (locale == null || locale.isEmpty()) {
			return "de"; // Default to German
		}
		return locale;
	}

	/**
	 * Get localized message from properties file.
	 *
	 * @param locale The locale (e.g., "de", "en-US")
	 * @param key The property key
	 * @return The localized message
	 */
	private String getMessage(String locale, String key) {
		try {
			ResourceBundle bundle = ResourceBundle.getBundle("Messages",
				Locale.forLanguageTag(locale));
			return bundle.getString(key);
		} catch (MissingResourceException ex) {
			// Fallback to German
			LOG.warn("Message key '{}' not found for locale '{}', falling back to German",
			         key, locale);
			try {
				ResourceBundle germanBundle = ResourceBundle.getBundle("Messages",
					Locale.GERMAN);
				return germanBundle.getString(key);
			} catch (MissingResourceException ex2) {
				LOG.error("Message key '{}' not found even in German", key);
				return key; // Return key itself as last resort
			}
		}
	}

	@Override
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
    	
		// For activation mails, we don't have user settings yet, so default to German
		String locale = "de";
		LOG.info("Sending activation mail to '{}' in locale '{}'.", receiver, locale);

		Map<String, String> variables = new HashMap<>();
		variables.put("{name}", DB.toDisplayName(address.getAddress()));
    	variables.put("{code}", code);
    	variables.put("{image}", _appLogoSvg);

		sendMail("mail.subject.activation", address, locale, "mail-template", variables);
	}

	public boolean sendHelpMail(UserSettings userSettings) {
		String receiver = userSettings.getEmail();
		if (receiver == null || receiver.isBlank()) {
			LOG.warn("Cannot send help mail to '{}', no e-mail provided.", userSettings.getId());
			return true;
		}

		String locale = getUserLocale(userSettings);
		LOG.info("Sending help mail to '{}' in locale '{}'.", receiver, locale);

		try {
			sendMail("mail.subject.help", new InternetAddress(receiver), locale, "help-mail", buildVariables(userSettings));
			return true;
		} catch (MessagingException | IOException ex) {
			LOG.error("Failed to send help mail to: " + receiver, ex);
			return false;
		}
	}
	
	@Override
	public boolean sendThanksMail(String donator, UserSettings userSettings, int amount) {
		String receiver = userSettings.getEmail();
		if (receiver == null || receiver.isBlank()) {
			LOG.warn("Cannot send thanks mail to '{}', no e-mail provided.", userSettings.getId());
			return true;
		}

		String locale = getUserLocale(userSettings);
		LOG.info("Sending thanks mail to '{}' in locale '{}'.", receiver, locale);

		try {
			Map<String, String> variables = buildVariables(userSettings);
			String attribute = "";
			if (amount >= 2000) {
				attribute = "unfassbar großzügige ";
			}
			else if (amount >= 500) {
				attribute = "großzügige ";
			}
			variables.put("{attribute}", attribute);
			variables.put("{name}", donator);

			sendMail("mail.subject.thanks", new InternetAddress(receiver), locale, "thanks-mail", variables);
			return true;
		} catch (Exception ex) {
			LOG.error("Failed to send thanks mail to: " + receiver, ex);
			return false;
		}
	}
	
	public boolean sendDiableMail(UserSettings userSettings, AnswerBotSip answerbot) {
		String receiver = userSettings.getEmail();
		if (receiver == null || receiver.isBlank()) {
			LOG.warn("Cannot send answerbot disable mail to '{}', no e-mail provided.", userSettings.getId());
			return true;
		}

		String locale = getUserLocale(userSettings);
		LOG.info("Sending answerbot disable mail to '{}' in locale '{}'.", receiver, locale);

		try {
			sendMail("mail.subject.ab-disable", new InternetAddress(receiver), locale, "ab-disable-mail", buildVariables(userSettings, answerbot));
			return true;
		} catch (MessagingException | IOException ex) {
			LOG.error("Failed to send answerbot disable mail to: " + receiver, ex);
			return false;
		}
	}
	
	private Map<String, String> buildVariables(UserSettings userSettings, AnswerBotSip answerbot) {
		Map<String, String> variables = buildVariables(userSettings);
		
		variables.put("{lastSuccess}", formatDateTime(answerbot.getLastSuccess()));
		variables.put("{lastMessage}", answerbot.getRegisterMessage());
		variables.put("{botId}", answerbot.getUserName());
		
		return variables;
	}
	
	/**
	 * Sends a welcome mail to the given user.
	 */
	public void sendWelcomeMail(UserSettings userSettings) {
		String receiver = userSettings.getEmail();
		if (receiver == null || receiver.isBlank()) {
			LOG.warn("Cannot send welcome mail to '{}', no e-mail provided.", userSettings.getId());
			return;
		}

		String locale = getUserLocale(userSettings);
		LOG.info("Sending welcome mail to '{}' in locale '{}'.", receiver, locale);

		try {
			sendMail("mail.subject.welcome", new InternetAddress(receiver), locale, "welcome-mail", buildVariables(userSettings));
		} catch (MessagingException | IOException ex) {
			LOG.error("Failed to send welcome mail to: " + receiver, ex);
		}
	}

	@Override
	public void sendMobileWelcomeMail(UserSettings userSettings, String deviceLabel) {
		String receiver = userSettings.getEmail();
		if (receiver == null || receiver.isBlank()) {
			LOG.warn("Cannot send mobile welcome mail to '{}', no e-mail provided.",
			         userSettings.getId());
			return;
		}

		String locale = getUserLocale(userSettings);

		// Use localized fallback if no device label provided
		if (deviceLabel == null || deviceLabel.isEmpty()) {
			deviceLabel = getMessage(locale, "mail.defaultDeviceLabel");
		}

		LOG.info("Sending mobile welcome mail to '{}' for device '{}' in locale '{}'.",
		         receiver, deviceLabel, locale);

		try {
			Map<String, String> variables = buildVariables(userSettings);
			variables.put("{deviceLabel}", deviceLabel);

			sendMail("mail.subject.mobile-welcome",
			         new InternetAddress(receiver),
			         locale,
			         "mobile-welcome-mail",
			         variables);
		} catch (MessagingException | IOException ex) {
			LOG.error("Failed to send mobile welcome mail to: " + receiver, ex);
		}
	}

	private Map<String, String> buildVariables(UserSettings userSettings) {
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

	/**
	 * Convert HTML to plain text by removing all HTML tags and collapsing multiple empty lines.
	 *
	 * @param html The HTML content
	 * @return Plain text version
	 */
	private String htmlToPlainText(String html) {
		// Remove all HTML tags (opening and closing)
		String text = html.replaceAll("<[^>]+>", "");

		// Decode common HTML entities (decode &amp; last to avoid creating new entities)
		text = text.replace("&nbsp;", " ");
		text = text.replace("&lt;", "<");
		text = text.replace("&gt;", ">");
		text = text.replace("&quot;", "\"");
		text = text.replace("&amp;", "&");  // Must be last!

		// Remove duplicate empty lines (replace multiple consecutive newlines with max 2)
		text = text.replaceAll("(\r?\n){3,}", "\n\n");

		// Trim leading/trailing whitespace
		text = text.trim();

		return text;
	}

	private void sendMail(String subjectKey, InternetAddress receiver, String locale, String template, Map<String, String> variables)
			throws MessagingException, IOException {
		MimeMessage msg = createMessage();

		// Load localized subject from properties
		String subject = getMessage(locale, subjectKey);
		msg.setSubject(subject);

		// Read HTML template
		String htmlContent = read(locale, template + ".html", variables);

		// Generate plain text version from HTML
		String plainTextContent = htmlToPlainText(htmlContent);

	    MimeMultipart alternativePart = new MimeMultipart("alternative");
	    {
			{
    			MimeBodyPart sourcePart = new MimeBodyPart();
    			sourcePart.setText(htmlContent, "utf-8", "html");
	    		alternativePart.addBodyPart(sourcePart);
	    	}

	    	{
	    		MimeBodyPart text = new MimeBodyPart();
	    		text.setText(plainTextContent, "utf-8");
	    		alternativePart.addBodyPart(text);
	    	}
	    }

		msg.setContent(alternativePart);
		try {
			sendMail(receiver, msg);
		} catch (MessagingException ex) {
			LOG.error("Sending mail to '{}' failed.", receiver);
			throw ex;
		}
	}

	public MimeMessage createMessage() throws MessagingException {
		MimeMessage msg = new MimeMessage(getSession());
		msg.setFrom(_from);
		return msg;
	}

	public void sendMail(InternetAddress receiver, MimeMessage msg) throws AddressException, MessagingException {
		msg.setRecipient(RecipientType.TO, receiver);
		Address[] addresses = {receiver};
		
		MimeMessage signedMessage = dkimSignMessage(msg);
		
		try {
			getTransport().sendMessage(signedMessage, addresses);
		} catch (MessagingException | IllegalStateException ex) {
			// Re-try.
			shutdownTransport();
			getTransport().sendMessage(signedMessage, addresses);
		}
	}

	private MimeMessage dkimSignMessage(MimeMessage message) throws MessagingException {
		if (_signature == null) {
			return message;
		}
		
		// Note: DkimSigner is not thread-safe and must therefore created for each mail being sent.
		DkimSigner dkimSigner = _signature.createSigner();
		dkimSigner.setIdentity(_from.getAddress());
		dkimSigner.setHeaderCanonicalization(Canonicalization.SIMPLE);
		dkimSigner.setBodyCanonicalization(Canonicalization.RELAXED);
		dkimSigner.setSigningAlgorithm(SigningAlgorithm.SHA256_WITH_RSA);
		dkimSigner.setLengthParam(true);
		dkimSigner.setCopyHeaderFields(false);
		return new DkimMessage(message, dkimSigner);
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

	private String read(String locale, String template, Map<String, String> variables) throws IOException {
		String resourcePath = "templates/" + locale + "/" + template;

		// Try localized template first
		InputStream in = getClass().getResourceAsStream(resourcePath);

		// Fallback to German if localized template not found
		if (in == null) {
			LOG.warn("Mail template not found: {}, falling back to German", resourcePath);
			resourcePath = "templates/de/" + template;
			in = getClass().getResourceAsStream(resourcePath);
		}

		if (in == null) {
			throw new IOException("Mail template not found: " + template);
		}

		StringBuilder result = new StringBuilder();
		char[] buffer = new char[4096];
		try (InputStream stream = in) {
			try (Reader r = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
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
