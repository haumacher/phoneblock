package de.haumacher.phoneblock.mail;

import java.io.IOException;

import de.haumacher.phoneblock.db.settings.AnswerBotSip;
import de.haumacher.phoneblock.db.settings.UserSettings;
import de.haumacher.phoneblock.shared.Language;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;

public interface MailService {

	void startUp();

	/**
	 * Sends an activation mail to the given receiver.
	 *
	 * @param receiver     The e-mail address.
	 * @param code         The verification code.
	 * @param language     The user's language.
	 * @param existingUser Whether the receiver is an already registered user.
	 *                     If {@code true}, the disposable e-mail check is skipped
	 *                     to allow existing users to log in even if their domain
	 *                     was later classified as disposable.
	 */
	void sendActivationMail(String receiver, String code, Language language, boolean existingUser) throws MessagingException, IOException, AddressException;
	void sendEmailChangeMail(String receiver, String code, Language language) throws MessagingException, IOException, AddressException;
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
