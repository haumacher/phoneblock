package de.haumacher.phoneblock.credits;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.GregorianCalendar;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

/**
 * Test case for {@link MailParser}.
 */
public class TestMailParser {
	
	@Test
	public void testParseWithMessage() throws FileNotFoundException, IOException, MessagingException, ParseException {
		MessageDetails details = new MailParser().parse(load("mail1.txt"));
		
		assertEquals("XXXXXXXXXXXXX", details.sender);
		assertEquals(new GregorianCalendar(2025, 0, 27).getTime(), details.date);
		assertEquals(500, details.amount);
		assertEquals("aaaaaaaa-bbbb", details.msg);
		assertEquals("99999999999999999", details.tx);
		assertEquals("aaaaaaaa-bbbb", details.uid);
	}
	
	@Test
	public void testParseWithoutMessage() throws FileNotFoundException, IOException, MessagingException, ParseException {
		MessageDetails details = new MailParser().parse(load("mail2.txt"));
		
		assertEquals("AAAAAAAAAAAAAAAA", details.sender);
		assertEquals(new GregorianCalendar(2024, 10, 5).getTime(), details.date);
		assertEquals(100, details.amount);
		assertEquals("Spende Phoneblock", details.msg);
		assertEquals("BBBBBBBBBBBBBBBBB", details.tx);
		assertEquals(null, details.uid);
	}

	private Message load(String resource) throws MessagingException, IOException {
		Session session = Session.getInstance(new Properties());
		
		try (InputStream in = TestMailParser.class.getResourceAsStream(resource)) {
			return new MimeMessage(session, in);
		}
	}
	
}
