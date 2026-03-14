package de.haumacher.phoneblock;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import de.haumacher.phoneblock.db.settings.UserSettings;
import de.haumacher.phoneblock.mail.MailService;
import de.haumacher.phoneblock.mail.MailServiceImpl;

public class TestMailService {

	@Test
	public void testThankYouMail() throws IOException {
		Properties properties = new Properties();
		try (FileInputStream in = new FileInputStream(".phoneblock")) {
			properties.load(in);
		}
		String user = properties.getProperty("smtp.user");
		String password = properties.getProperty("smtp.password");
		String email = properties.getProperty("test.email");
		
		if (password != null) {
			MailService mailService = new MailServiceImpl(user, password, properties);
			mailService.sendThanksMail("Good Guy", UserSettings.create().setDisplayName("Bad Guy").setEmail(email), 2000);
		}
	}

}
