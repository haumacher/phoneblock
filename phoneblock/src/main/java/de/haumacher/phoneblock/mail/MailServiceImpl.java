/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.mail;

import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import org.simplejavamail.utils.mail.dkim.Canonicalization;
import org.simplejavamail.utils.mail.dkim.DkimMessage;
import org.simplejavamail.utils.mail.dkim.DkimSigner;
import org.simplejavamail.utils.mail.dkim.SigningAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import de.haumacher.phoneblock.app.Application;
import de.haumacher.phoneblock.app.SettingsServlet;
import de.haumacher.phoneblock.db.DB;
import de.haumacher.phoneblock.db.settings.AnswerBotSip;
import de.haumacher.phoneblock.db.settings.UserSettings;
import de.haumacher.phoneblock.mail.check.EMailCheckService;
import de.haumacher.phoneblock.shared.Language;
import de.haumacher.phoneblock.util.I18N;
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
		_support = baseUrl + "/support";
	}

	/**
	 * Get the user's language preference.
	 *
	 * @param userSettings The user settings
	 * @return The {@link Language}, defaults to {@link Language#getDefault()} if not set
	 */
	private Language getUserLanguage(UserSettings userSettings) {
		return Language.fromTag(userSettings.getLang());
	}


	@Override
	public void sendActivationMail(String receiver, String code, Language language)
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

		LOG.info("Sending activation mail to '{}' in language '{}'.", receiver, language.tag);

		Map<String, Object> variables = new HashMap<>();
		variables.put("name", DB.toDisplayName(address.getAddress()));
		variables.put("code", code);
		variables.put("image", _appLogoSvg);

		sendMail("mail.subject.activation", address, language, "mail-template", variables);
	}

	@Override
	public void sendEmailChangeMail(String receiver, String code, Language language)
			throws MessagingException, IOException, AddressException {

		if (receiver == null || receiver.isBlank()) {
			LOG.info("Rejected empty e-mail address.");
			throw new AddressException("Address must not be empty.");
		}

		InternetAddress address = new InternetAddress(receiver);

		if (EMailCheckService.getInstance().isDisposable(address)) {
			LOG.warn("Rejected disposable e-mail address: " + receiver);
			throw new AddressException("Please do not use disposable e-mail addresses.");
		}

		LOG.info("Sending email change verification mail to '{}' in language '{}'.", receiver, language.tag);

		Map<String, Object> variables = new HashMap<>();
		variables.put("name", DB.toDisplayName(address.getAddress()));
		variables.put("code", code);
		variables.put("image", _appLogoSvg);

		sendMail("mail.subject.email-change", address, language, "email-change-mail", variables);
	}

	public boolean sendHelpMail(UserSettings userSettings) {
		String receiver = userSettings.getEmail();
		if (receiver == null || receiver.isBlank()) {
			LOG.warn("Cannot send help mail to '{}', no e-mail provided.", userSettings.getId());
			return true;
		}

		Language language = getUserLanguage(userSettings);
		LOG.info("Sending help mail to '{}' in language '{}'.", receiver, language.tag);

		try {
			sendMail("mail.subject.help", new InternetAddress(receiver), language, "help-mail", buildVariables(userSettings));
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

		Language language = getUserLanguage(userSettings);
		LOG.info("Sending thanks mail to '{}' in language '{}'.", receiver, language.tag);

		try {
			Map<String, Object> variables = buildVariables(userSettings);
			String attribute = "";
			if (amount >= 2000) {
				attribute = "unfassbar großzügige ";
			}
			else if (amount >= 500) {
				attribute = "großzügige ";
			}
			variables.put("attribute", attribute);
			variables.put("name", donator);

			sendMail("mail.subject.thanks", new InternetAddress(receiver), language, "thanks-mail", variables);
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

		Language language = getUserLanguage(userSettings);
		LOG.info("Sending answerbot disable mail to '{}' in language '{}'.", receiver, language.tag);

		try {
			sendMail("mail.subject.ab-disable", new InternetAddress(receiver), language, "ab-disable-mail", buildVariables(userSettings, answerbot));
			return true;
		} catch (MessagingException | IOException ex) {
			LOG.error("Failed to send answerbot disable mail to: " + receiver, ex);
			return false;
		}
	}
	
	private Map<String, Object> buildVariables(UserSettings userSettings, AnswerBotSip answerbot) {
		Map<String, Object> variables = buildVariables(userSettings);

		variables.put("lastSuccess", formatDateTime(answerbot.getLastSuccess()));
		variables.put("lastMessage", answerbot.getRegisterMessage());
		variables.put("botId", answerbot.getUserName());

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

		Language language = getUserLanguage(userSettings);
		LOG.info("Sending welcome mail to '{}' in language '{}'.", receiver, language.tag);

		try {
			sendMail("mail.subject.welcome", new InternetAddress(receiver), language, "welcome-mail", buildVariables(userSettings));
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

		Language language = getUserLanguage(userSettings);

		// Use localized fallback if no device label provided
		if (deviceLabel == null || deviceLabel.isEmpty()) {
			deviceLabel = I18N.getMessage(language, "mail.defaultDeviceLabel");
		}

		LOG.info("Sending mobile welcome mail to '{}' for device '{}' in language '{}'.",
		         receiver, deviceLabel, language.tag);

		try {
			Map<String, Object> variables = buildVariables(userSettings);
			variables.put("deviceLabel", deviceLabel);

			sendMail("mail.subject.mobile-welcome",
			         new InternetAddress(receiver),
			         language,
			         "mobile-welcome-mail",
			         variables);
		} catch (MessagingException | IOException ex) {
			LOG.error("Failed to send mobile welcome mail to: " + receiver, ex);
		}
	}

	private Map<String, Object> buildVariables(UserSettings userSettings) {
		Map<String, Object> variables = new HashMap<>();
		variables.put("name", userSettings.getDisplayName());
		variables.put("userName", userSettings.getLogin());
		variables.put("lastAccess", formatDateTime(userSettings.getLastAccess()));
		variables.put("image", _appLogoSvg);
		variables.put("home", HOME_PAGE);
		variables.put("facebook", FACE_BOOK);
		variables.put("help", HELP_VIDEO);
		variables.put("mail", MAIL);
		variables.put("settings", _settings);
		variables.put("app", _app);
		variables.put("support", _support);
		return variables;
	}

	protected String formatDateTime(long time) {
		DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT, Locale.GERMAN);
		String result = time == 0 ? "Noch nie." : dateFormat.format(new Date(time));
		return result;
	}

	/**
	 * Convert HTML to plain text using jsoup.
	 *
	 * <p>
	 * This method:
	 * <ul>
	 * <li>Removes script, style, and other technical content</li>
	 * <li>Preserves links in format: "Link Text (https://url)"</li>
	 * <li>Adds spacing for block elements (paragraphs, headings)</li>
	 * <li>Handles all HTML entities automatically</li>
	 * <li>Collapses multiple empty lines</li>
	 * </ul>
	 * </p>
	 *
	 * @param html The HTML content
	 * @return Plain text version suitable for email clients that don't support HTML
	 */
	static String htmlToPlainText(String html) {
		// Parse HTML
		Document doc = Jsoup.parse(html);

		// Remove script, style, and other non-content tags
		doc.select("script, style, head").remove();

		// Process links to preserve URLs
		for (Element link : doc.select("a[href]")) {
			String href = link.attr("href");
			String text = link.text();

			// Replace link with "text (URL)" format
			// Skip if href is empty or same as text (to avoid "url (url)")
			if (!href.isEmpty() && !href.equals(text)) {
				link.text(text + " (" + href + ")");
			}
		}

		// Replace block elements with their content plus newlines
		// Process in reverse order to avoid DOM modification issues
		for (Element br : doc.select("br")) {
			br.replaceWith(new org.jsoup.nodes.TextNode("\n"));
		}

		for (Element elem : doc.select("p, div, h1, h2, h3, h4, h5, h6")) {
			// Add newlines before and after block elements
			String elemText = elem.text();
			elem.replaceWith(new org.jsoup.nodes.TextNode("\n" + elemText + "\n"));
		}

		// Get text content (jsoup handles all HTML entities automatically)
		String text = doc.body().wholeText();

		// Clean up excessive whitespace
		text = text.replaceAll("(?m)^[ \\t]+", "");  // Trim leading whitespace on each line
		text = text.replaceAll("(\r?\n){3,}", "\n\n");  // Max 2 consecutive newlines
		text = text.replaceAll("[ \\t]+", " ");  // Multiple spaces to single space

		// Apply line wrapping at 70 characters
		text = wrapLines(text.trim(), 70);

		return text;
	}

	/**
	 * Wrap lines to a maximum width, preserving existing line breaks.
	 *
	 * @param text The text to wrap
	 * @param maxWidth Maximum line width (typically 70 for email)
	 * @return Text with lines wrapped
	 */
	static String wrapLines(String text, int maxWidth) {
		StringBuilder result = new StringBuilder();
		String[] paragraphs = text.split("\n");

		for (int i = 0; i < paragraphs.length; i++) {
			String paragraph = paragraphs[i];

			if (paragraph.isEmpty()) {
				// Preserve empty lines (paragraph breaks)
				result.append("\n");
			} else if (paragraph.length() <= maxWidth) {
				// Line is already short enough
				result.append(paragraph);
				if (i < paragraphs.length - 1) {
					result.append("\n");
				}
			} else {
				// Need to wrap this line
				String wrapped = wrapSingleLine(paragraph, maxWidth);
				result.append(wrapped);
				if (i < paragraphs.length - 1) {
					result.append("\n");
				}
			}
		}

		return result.toString();
	}

	/**
	 * Wrap a single line of text at word boundaries.
	 *
	 * @param line The line to wrap
	 * @param maxWidth Maximum line width
	 * @return Wrapped text with newlines inserted
	 */
	static String wrapSingleLine(String line, int maxWidth) {
		StringBuilder result = new StringBuilder();
		String[] words = line.split(" ");
		int currentLineLength = 0;

		for (int i = 0; i < words.length; i++) {
			String word = words[i];

			if (currentLineLength == 0) {
				// First word on the line
				result.append(word);
				currentLineLength = word.length();
			} else if (currentLineLength + 1 + word.length() <= maxWidth) {
				// Word fits on current line
				result.append(" ").append(word);
				currentLineLength += 1 + word.length();
			} else {
				// Word doesn't fit, start new line
				result.append("\n").append(word);
				currentLineLength = word.length();
			}
		}

		return result.toString();
	}

	private void sendMail(String subjectKey, InternetAddress receiver, Language language, String template, Map<String, Object> variables)
			throws MessagingException, IOException {
		MimeMessage msg = createMessage();

		// Load localized subject from properties
		String subject = I18N.getMessage(language, subjectKey);
		msg.setSubject(subject);

		// Process template with Thymeleaf
		String htmlContent = MailTemplateEngine.getInstance().processTemplate(language, template, variables);

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

	private Session getSession() throws AddressException {
		if (_session == null) {
			_session = startSession();
		}
		return _session;
	}

}
