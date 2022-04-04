/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.mail;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Properties;

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

/**
 * Service for sending e-mail messages.
 */
public class MailService {

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
		String image = "https://phoneblock.haumacher.de/phoneblock/app-logo.svg";
		
		Message msg = createMessage();
		msg.setSubject("PhoneBlock E-Mail Bestätigung");
		
	    MimeMultipart alternativePart = new MimeMultipart("alternative");
	    {
	    	{
    			MimeBodyPart sourcePart = new MimeBodyPart();
    			sourcePart.setText(read("mail-template.html", code, image), "utf-8", "html");
	    		alternativePart.addBodyPart(sourcePart);
	    	}

	    	{
	    		MimeBodyPart text = new MimeBodyPart();
	    		text.setText(read("mail-template.txt", code, image), "utf-8");
	    		alternativePart.addBodyPart(text);
	    	}
	    }
		
		msg.setContent(alternativePart);
		sendMail(receiver, msg);
	}

	public Message createMessage() throws MessagingException {
		Message msg = new MimeMessage(_session);
		msg.setFrom(_from);
		return msg;
	}

	public void sendMail(String receiver, Message msg) throws AddressException, MessagingException {
		InternetAddress address = new InternetAddress(receiver);
		msg.setRecipient(RecipientType.TO, address);
		Address[] addresses = {address};
		
		if (!_transport.isConnected()) {
			connect();
		}
		try {
			_transport.sendMessage(msg, addresses);
		} catch (MessagingException | IllegalStateException ex) {
			// Re-try.
			connect();
			_transport.sendMessage(msg, addresses);
		}
	}

	public void startUp() throws NoSuchProviderException, MessagingException {
		_session = startSession();
		connect();
	}

	private void connect() throws NoSuchProviderException, MessagingException {
		_transport = _session.getTransport();
		_transport.connect(_user, _password);
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
	 * TODO
	 *
	 */
	public void shutdown() {
		if (_session != null) {
			try {
				_transport.close();
			} catch (MessagingException ex) {
				ex.printStackTrace();
			}
			
			_transport = null;
			_session = null;
		}
	}

	private String read(String resource, String code, String image) throws IOException {
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
		return result.toString().replace("{code}", code).replace("{image}", image);
	}

}
