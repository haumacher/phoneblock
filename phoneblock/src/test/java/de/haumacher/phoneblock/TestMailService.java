package de.haumacher.phoneblock;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import de.haumacher.phoneblock.db.settings.UserSettings;
import de.haumacher.phoneblock.mail.MailService;
import de.haumacher.phoneblock.mail.MailServiceImpl;

/**
 * Manual test sending a real mail.
 * 
 * <p>
 * Runs only when the git-ignored file <code>phoneblock/mail-test.properties</code> exists with
 * <code>smtp.user</code>, <code>smtp.password</code>, <code>test.email</code> and the JavaMail
 * <code>mail.smtp.*</code> properties; otherwise the test is skipped.
 * </p>
 */
public class TestMailService {

	@Test
	public void testThankYouMail() throws IOException {
		File config = new File("mail-test.properties");
		assumeTrue(config.exists(), "no mail-test.properties, skipping the manual mail test");

		Properties properties = new Properties();
		try (FileInputStream in = new FileInputStream(config)) {
			properties.load(in);
		}
		String user = properties.getProperty("smtp.user");
		String password = properties.getProperty("smtp.password");
		String email = properties.getProperty("test.email");
		assumeTrue(password != null, "no smtp.password in mail-test.properties, skipping the manual mail test");

		MailService mailService = new MailServiceImpl(user, password, properties);
		mailService.sendThanksMail("Good Guy", UserSettings.create().setDisplayName("Bad Guy").setEmail(email), 2000);
	}

}
