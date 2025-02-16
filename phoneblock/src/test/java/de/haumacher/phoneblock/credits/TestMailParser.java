package de.haumacher.phoneblock.credits;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.GregorianCalendar;

import org.junit.jupiter.api.Test;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * Test case for {@link MailParser}.
 */
public class TestMailParser {
	
	@Test
	public void testParseWithMessage() throws FileNotFoundException, IOException, MessagingException, ParseException {
		MessageDetails details = new MailParser().parse(load(TestMailParser.class.getResourceAsStream("mail1.txt")));
		
		assertEquals("Heinz Maier", details.sender);
		assertEquals(new GregorianCalendar(2025, 0, 27).getTime(), details.date);
		assertEquals(1000, details.amount);
		assertEquals("PhoneBlock-aaaaaaaa-bbbb danke!", details.msg);
		assertEquals("00000000000000001", details.tx);
		assertEquals("aaaaaaaa-bbbb", details.uid);
	}
	
	@Test
	public void testParseWithoutMessage() throws FileNotFoundException, IOException, MessagingException, ParseException {
		MessageDetails details = new MailParser().parse(load(TestMailParser.class.getResourceAsStream("mail2.txt")));
		
		assertEquals("Heinz Maier", details.sender);
		assertEquals(new GregorianCalendar(2025, 0, 11).getTime(), details.date);
		assertEquals(500, details.amount);
		assertEquals("", details.msg);
		assertEquals("00000000000000002", details.tx);
		assertEquals(null, details.uid);
	}

	private Message load(InputStream in) throws MessagingException {
		return new MimeMessage(null, in);
	}
	
}
