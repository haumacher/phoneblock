package de.haumacher.phoneblock.mail;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.db.settings.AnswerBotSip;
import de.haumacher.phoneblock.db.settings.UserSettings;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;

/**
 * Test-only implementation of {@link MailService}.
 */
public class DummyMailService implements MailService {
	
	private static final Logger LOG = LoggerFactory.getLogger(DummyMailService.class);
	
	public static final MailService INSTANCE = new DummyMailService();

	@Override
	public void startUp() {
		LOG.info("Starting.");
	}

	@Override
	public void sendActivationMail(String receiver, String code, String locale)
			throws MessagingException, IOException, AddressException {
		LOG.info("Send activation to {} in locale {}: {}", receiver, locale, code);
	}

	@Override
	public void sendEmailChangeMail(String receiver, String code, String locale)
			throws MessagingException, IOException, AddressException {
		LOG.info("Send email change verification to {} in locale {}: {}", receiver, locale, code);
	}

	@Override
	public void sendWelcomeMail(UserSettings userSettings) {
		LOG.info("Send welcome to {}.", userSettings.getEmail());
	}

	@Override
	public void sendMobileWelcomeMail(UserSettings userSettings, String deviceLabel) {
		LOG.info("Send mobile welcome to {} for device '{}'.", userSettings.getEmail(), deviceLabel);
	}

	@Override
	public boolean sendHelpMail(UserSettings userSettings) {
		LOG.info("Send help to {}.", userSettings.getEmail());
		return true;
	}
	
	@Override
	public boolean sendDiableMail(UserSettings userSettings, AnswerBotSip answerbot) {
		LOG.info("Send disable to {}: {}", userSettings.getEmail(), answerbot.getUserName());
		return true;
	}

	@Override
	public boolean sendThanksMail(String donator, UserSettings userSettings, int amount) {
		LOG.info("Send thanks to {}: {} ({})", userSettings.getEmail(), donator, amount);
		return true;
	}

	@Override
	public void shutdown() {
		LOG.info("Shut-down.");
	}

}
