package de.haumacher.phoneblock.mail;

import java.io.IOException;

import de.haumacher.phoneblock.db.settings.AnswerBotSip;
import de.haumacher.phoneblock.db.settings.UserSettings;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;

public interface MailService {

	void startUp();

	void sendActivationMail(String receiver, String code) throws MessagingException, IOException, AddressException;
	void sendWelcomeMail(UserSettings userSettings);
	boolean sendHelpMail(UserSettings userSettings);
	boolean sendDiableMail(UserSettings userSettings, AnswerBotSip answerbot);
	boolean sendThanksMail(String donator, UserSettings userSettings, int amount);

	void shutdown();
	
}
