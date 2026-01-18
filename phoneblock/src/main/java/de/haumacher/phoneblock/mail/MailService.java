package de.haumacher.phoneblock.mail;

import java.io.IOException;

import de.haumacher.phoneblock.db.settings.AnswerBotSip;
import de.haumacher.phoneblock.db.settings.UserSettings;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;

public interface MailService {

	void startUp();

	void sendActivationMail(String receiver, String code) throws MessagingException, IOException, AddressException;
	void sendEmailChangeMail(String receiver, String code) throws MessagingException, IOException, AddressException;
	void sendWelcomeMail(UserSettings userSettings);

	/**
	 * Sends a welcome mail for successful mobile app installation.
	 *
	 * @param userSettings The user settings
	 * @param deviceLabel The label of the device (e.g., "PhoneBlock Mobile on Pixel 6")
	 */
	void sendMobileWelcomeMail(UserSettings userSettings, String deviceLabel);

	boolean sendHelpMail(UserSettings userSettings);
	boolean sendDiableMail(UserSettings userSettings, AnswerBotSip answerbot);
	boolean sendThanksMail(String donator, UserSettings userSettings, int amount);

	void shutdown();
	
}
