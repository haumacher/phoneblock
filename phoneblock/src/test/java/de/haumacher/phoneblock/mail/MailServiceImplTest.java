/*
 * Copyright (c) 2024 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.mail;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Test case for {@link MailServiceImpl}.
 */
public class MailServiceImplTest {

	/** All available locales for email templates. */
	private static final String[] LOCALES = {
		"ar", "da", "de", "el", "en-US", "es", "fr", "it", "nb", "nl", "pl", "sv", "uk", "zh-Hans"
	};

	/**
	 * Test that mail-template.html expands all parameters in all locales.
	 */
	@Test
	public void testMailTemplate() {
		for (String locale : LOCALES) {
			Map<String, Object> variables = new HashMap<>();
			variables.put("name", "TEST_NAME_123");
			variables.put("code", "TEST_CODE_456");
			variables.put("image", "https://test.example.com/image.svg");

			String html = MailTemplateEngine.getInstance().processTemplate(locale, "mail-template", variables);

			assertTrue(html.contains("TEST_NAME_123"), locale + ": name should be expanded");
			assertTrue(html.contains("TEST_CODE_456"), locale + ": code should be expanded");
			assertTrue(html.contains("https://test.example.com/image.svg"), locale + ": image should be expanded");
		}
	}

	/**
	 * Test that email-change-mail.html expands all parameters in all locales.
	 */
	@Test
	public void testEmailChangeMail() {
		for (String locale : LOCALES) {
			Map<String, Object> variables = new HashMap<>();
			variables.put("name", "TEST_NAME_789");
			variables.put("code", "TEST_CODE_ABC");
			variables.put("image", "https://test.example.com/change-image.svg");

			String html = MailTemplateEngine.getInstance().processTemplate(locale, "email-change-mail", variables);

			assertTrue(html.contains("TEST_NAME_789"), locale + ": name should be expanded");
			assertTrue(html.contains("TEST_CODE_ABC"), locale + ": code should be expanded");
			assertTrue(html.contains("https://test.example.com/change-image.svg"), locale + ": image should be expanded");
		}
	}

	/**
	 * Test that welcome-mail.html expands all parameters in all locales.
	 */
	@Test
	public void testWelcomeMail() {
		for (String locale : LOCALES) {
			Map<String, Object> variables = new HashMap<>();
			variables.put("name", "TEST_WELCOME_NAME");
			variables.put("image", "https://test.example.com/welcome-image.svg");
			variables.put("home", "https://test.example.com/home");
			variables.put("support", "https://test.example.com/support");
			variables.put("settings", "https://test.example.com/settings");
			variables.put("help", "https://test.example.com/help");
			variables.put("facebook", "https://test.example.com/facebook");
			variables.put("mail", "test-welcome@example.com");

			String html = MailTemplateEngine.getInstance().processTemplate(locale, "welcome-mail", variables);

			assertTrue(html.contains("TEST_WELCOME_NAME"), locale + ": name should be expanded");
			assertTrue(html.contains("https://test.example.com/welcome-image.svg"), locale + ": image should be expanded");
			assertTrue(html.contains("https://test.example.com/home"), locale + ": home should be expanded");
			assertTrue(html.contains("https://test.example.com/support"), locale + ": support should be expanded");
			assertTrue(html.contains("https://test.example.com/settings"), locale + ": settings should be expanded");
			assertTrue(html.contains("https://test.example.com/help"), locale + ": help should be expanded");
			assertTrue(html.contains("https://test.example.com/facebook"), locale + ": facebook should be expanded");
			assertTrue(html.contains("test-welcome@example.com"), locale + ": mail should be expanded");
		}
	}

	/**
	 * Test that mobile-welcome-mail.html expands all parameters in all locales.
	 */
	@Test
	public void testMobileWelcomeMail() {
		for (String locale : LOCALES) {
			Map<String, Object> variables = new HashMap<>();
			variables.put("name", "TEST_MOBILE_NAME");
			variables.put("deviceLabel", "TEST_DEVICE_XYZ");
			variables.put("image", "https://test.example.com/mobile-image.svg");
			variables.put("home", "https://test.example.com/mobile-home");
			variables.put("support", "https://test.example.com/mobile-support");
			variables.put("settings", "https://test.example.com/mobile-settings");
			variables.put("app", "https://test.example.com/mobile-app");
			variables.put("help", "https://test.example.com/mobile-help");
			variables.put("facebook", "https://test.example.com/mobile-facebook");
			variables.put("mail", "test-mobile@example.com");

			String html = MailTemplateEngine.getInstance().processTemplate(locale, "mobile-welcome-mail", variables);

			assertTrue(html.contains("TEST_MOBILE_NAME"), locale + ": name should be expanded");
			assertTrue(html.contains("TEST_DEVICE_XYZ"), locale + ": deviceLabel should be expanded");
			assertTrue(html.contains("https://test.example.com/mobile-image.svg"), locale + ": image should be expanded");
			assertTrue(html.contains("https://test.example.com/mobile-home"), locale + ": home should be expanded");
			assertTrue(html.contains("https://test.example.com/mobile-support"), locale + ": support should be expanded");
			assertTrue(html.contains("https://test.example.com/mobile-settings"), locale + ": settings should be expanded");
			assertTrue(html.contains("https://test.example.com/mobile-app"), locale + ": app should be expanded");
			assertTrue(html.contains("https://test.example.com/mobile-help"), locale + ": help should be expanded");
			assertTrue(html.contains("https://test.example.com/mobile-facebook"), locale + ": facebook should be expanded");
			assertTrue(html.contains("test-mobile@example.com"), locale + ": mail should be expanded");
		}
	}

	/**
	 * Test that help-mail.html expands all parameters in all locales.
	 */
	@Test
	public void testHelpMail() {
		for (String locale : LOCALES) {
			Map<String, Object> variables = new HashMap<>();
			variables.put("name", "TEST_HELP_NAME");
			variables.put("userName", "TEST_USERNAME_HELP");
			variables.put("lastAccess", "TEST_LAST_ACCESS_DATE");
			variables.put("image", "https://test.example.com/help-image.svg");
			variables.put("home", "https://test.example.com/help-home");
			variables.put("support", "https://test.example.com/help-support");
			variables.put("settings", "https://test.example.com/help-settings");
			variables.put("app", "https://test.example.com/help-app");
			variables.put("help", "https://test.example.com/help-video");
			variables.put("facebook", "https://test.example.com/help-facebook");
			variables.put("mail", "test-help@example.com");

			String html = MailTemplateEngine.getInstance().processTemplate(locale, "help-mail", variables);

			assertTrue(html.contains("TEST_HELP_NAME"), locale + ": name should be expanded");
			assertTrue(html.contains("TEST_USERNAME_HELP"), locale + ": userName should be expanded");
			assertTrue(html.contains("TEST_LAST_ACCESS_DATE"), locale + ": lastAccess should be expanded");
			assertTrue(html.contains("https://test.example.com/help-image.svg"), locale + ": image should be expanded");
			assertTrue(html.contains("https://test.example.com/help-home"), locale + ": home should be expanded");
			assertTrue(html.contains("https://test.example.com/help-support"), locale + ": support should be expanded");
			assertTrue(html.contains("https://test.example.com/help-settings"), locale + ": settings should be expanded");
			assertTrue(html.contains("https://test.example.com/help-app"), locale + ": app should be expanded");
			assertTrue(html.contains("https://test.example.com/help-video"), locale + ": help should be expanded");
			assertTrue(html.contains("https://test.example.com/help-facebook"), locale + ": facebook should be expanded");
			assertTrue(html.contains("test-help@example.com"), locale + ": mail should be expanded");
		}
	}

	/**
	 * Test that thanks-mail.html expands all parameters in all locales.
	 */
	@Test
	public void testThanksMail() {
		for (String locale : LOCALES) {
			Map<String, Object> variables = new HashMap<>();
			variables.put("name", "TEST_THANKS_NAME");
			variables.put("attribute", "TEST_ATTRIBUTE_VALUE ");
			variables.put("image", "https://test.example.com/thanks-image.svg");
			variables.put("home", "https://test.example.com/thanks-home");
			variables.put("support", "https://test.example.com/thanks-support");
			variables.put("settings", "https://test.example.com/thanks-settings");
			variables.put("help", "https://test.example.com/thanks-help");
			variables.put("facebook", "https://test.example.com/thanks-facebook");
			variables.put("mail", "test-thanks@example.com");

			String html = MailTemplateEngine.getInstance().processTemplate(locale, "thanks-mail", variables);

			assertTrue(html.contains("TEST_THANKS_NAME"), locale + ": name should be expanded");
			assertTrue(html.contains("TEST_ATTRIBUTE_VALUE"), locale + ": attribute should be expanded");
			assertTrue(html.contains("https://test.example.com/thanks-image.svg"), locale + ": image should be expanded");
			assertTrue(html.contains("https://test.example.com/thanks-home"), locale + ": home should be expanded");
			assertTrue(html.contains("https://test.example.com/thanks-support"), locale + ": support should be expanded");
			assertTrue(html.contains("https://test.example.com/thanks-settings"), locale + ": settings should be expanded");
			assertTrue(html.contains("https://test.example.com/thanks-help"), locale + ": help should be expanded");
			assertTrue(html.contains("https://test.example.com/thanks-facebook"), locale + ": facebook should be expanded");
			assertTrue(html.contains("test-thanks@example.com"), locale + ": mail should be expanded");
		}
	}

	/**
	 * Test that ab-disable-mail.html expands all parameters in all locales.
	 */
	@Test
	public void testAbDisableMail() {
		for (String locale : LOCALES) {
			Map<String, Object> variables = new HashMap<>();
			variables.put("name", "TEST_DISABLE_NAME");
			variables.put("userName", "TEST_USERNAME_DISABLE");
			variables.put("botId", "TEST_BOT_ID_123");
			variables.put("lastSuccess", "TEST_LAST_SUCCESS_DATE");
			variables.put("lastMessage", "TEST_LAST_ERROR_MSG");
			variables.put("image", "https://test.example.com/disable-image.svg");
			variables.put("home", "https://test.example.com/disable-home");
			variables.put("support", "https://test.example.com/disable-support");
			variables.put("settings", "https://test.example.com/disable-settings");
			variables.put("app", "https://test.example.com/disable-app");
			variables.put("help", "https://test.example.com/disable-help");
			variables.put("facebook", "https://test.example.com/disable-facebook");
			variables.put("mail", "test-disable@example.com");

			String html = MailTemplateEngine.getInstance().processTemplate(locale, "ab-disable-mail", variables);

			assertTrue(html.contains("TEST_DISABLE_NAME"), locale + ": name should be expanded");
			assertTrue(html.contains("TEST_USERNAME_DISABLE"), locale + ": userName should be expanded");
			assertTrue(html.contains("TEST_BOT_ID_123"), locale + ": botId should be expanded");
			assertTrue(html.contains("TEST_LAST_SUCCESS_DATE"), locale + ": lastSuccess should be expanded");
			assertTrue(html.contains("TEST_LAST_ERROR_MSG"), locale + ": lastMessage should be expanded");
			assertTrue(html.contains("https://test.example.com/disable-image.svg"), locale + ": image should be expanded");
			assertTrue(html.contains("https://test.example.com/disable-home"), locale + ": home should be expanded");
			assertTrue(html.contains("https://test.example.com/disable-support"), locale + ": support should be expanded");
			assertTrue(html.contains("https://test.example.com/disable-settings"), locale + ": settings should be expanded");
			assertTrue(html.contains("https://test.example.com/disable-app"), locale + ": app should be expanded");
			assertTrue(html.contains("https://test.example.com/disable-help"), locale + ": help should be expanded");
			assertTrue(html.contains("https://test.example.com/disable-facebook"), locale + ": facebook should be expanded");
			assertTrue(html.contains("test-disable@example.com"), locale + ": mail should be expanded");
		}
	}

	/**
	 * Test HTML to plain text conversion with the mobile-welcome-mail template.
	 */
	@Test
	public void testHtmlToPlainText_MobileWelcomeMail() throws IOException {
		// Process the mobile-welcome-mail template using Thymeleaf
		Map<String, Object> variables = new HashMap<>();
		variables.put("name", "Max Mustermann");
		variables.put("deviceLabel", "Samsung-Galaxy-S23");
		variables.put("image", "https://phoneblock.net/phoneblock/assets/img/app-logo.svg");
		variables.put("home", "https://phoneblock.net/phoneblock");
		variables.put("support", "https://phoneblock.net/phoneblock/support");
		variables.put("settings", "https://phoneblock.net/phoneblock/settings");
		variables.put("app", "https://phoneblock.net/phoneblock/ab/");
		variables.put("help", "https://www.youtube.com/@phoneblock");
		variables.put("facebook", "https://www.facebook.com/PhoneBlock");
		variables.put("mail", "phoneblock@haumacher.de");

		String htmlWithVariables = MailTemplateEngine.getInstance().processTemplate("es", "mobile-welcome-mail", variables);

		// Convert to plain text using static method
		String plainText = MailServiceImpl.htmlToPlainText(htmlWithVariables);

		// Print the result for visual inspection
		System.out.println("=== HTML to Plain Text Conversion Test ===");
		System.out.println(plainText);
		System.out.println("-------------------------------------------");
		System.out.println();

		// Assertions to verify the conversion
		assertNotNull(plainText, "Plain text should not be null");
		assertFalse(plainText.isEmpty(), "Plain text should not be empty");

		// Verify that style content is NOT in the plain text
		assertFalse(plainText.contains("font-family"), "Should not contain CSS content");
		assertFalse(plainText.contains("line-height"), "Should not contain CSS content");
		assertFalse(plainText.contains("text-align"), "Should not contain CSS content");

		// Verify that actual content IS in the plain text
		assertTrue(plainText.contains("Max Mustermann"), "Should contain user name");
		assertTrue(plainText.contains("Samsung-Galaxy-S23"), "Should contain device label");
		assertTrue(plainText.contains("PhoneBlock"), "Should contain PhoneBlock reference");
		assertTrue(plainText.contains("Bernhard Haumacher"), "Should contain signature");

		// Verify that links are preserved in "(URL)" format
		assertTrue(plainText.contains("(https://phoneblock.net/phoneblock/support)"),
			"Should preserve support link URL");
		assertTrue(plainText.contains("(https://phoneblock.net/phoneblock)"),
			"Should preserve homepage link URL");
		assertTrue(plainText.contains("(https://www.facebook.com/PhoneBlock)"),
			"Should preserve Facebook link URL");

		// Verify that HTML tags are removed
		assertFalse(plainText.contains("<p"), "Should not contain opening <p> tags");
		assertFalse(plainText.contains("</p>"), "Should not contain closing </p> tags");
		assertFalse(plainText.contains("<div"), "Should not contain <div> tags");
		assertFalse(plainText.contains("<style"), "Should not contain <style> tags");
		assertFalse(plainText.contains("<br"), "Should not contain <br> tags");

		// Verify structure (should have reasonable line breaks)
		assertTrue(plainText.contains("\n"), "Should contain line breaks for readability");

		// Verify line wrapping (no line should exceed 70 characters)
		String[] lines = plainText.split("\n");
		for (int i = 0; i < lines.length; i++) {
			assertTrue(lines[i].length() <= 70,
				"Line " + (i + 1) + " exceeds 70 characters (" + lines[i].length() + " chars): " + lines[i]);
		}
	}

	/**
	 * Test with a simple HTML snippet to verify basic functionality.
	 */
	@Test
	public void testHtmlToPlainText_SimpleExample() {
		String html = "<html><head><style>.test { color: red; }</style></head>" +
			"<body><p>Hello <a href=\"https://example.com\">click here</a> for info.</p>" +
			"<p>Another paragraph with <strong>bold text</strong>.</p></body></html>";

		String plainText = MailServiceImpl.htmlToPlainText(html);

		System.out.println("\n=== Simple HTML Conversion ===");
		System.out.println(plainText);
		System.out.println();

		// Verify style is removed
		assertFalse(plainText.contains("color: red"), "Should not contain CSS");

		// Verify link is preserved with URL
		assertTrue(plainText.contains("click here (https://example.com)"),
			"Should preserve link in 'text (URL)' format");

		// Verify content is present
		assertTrue(plainText.contains("Hello"), "Should contain 'Hello'");
		assertTrue(plainText.contains("bold text"), "Should contain bold text content");
		assertTrue(plainText.contains("Another paragraph"), "Should contain second paragraph");
	}

	/**
	 * Test line wrapping functionality.
	 */
	@Test
	public void testLineWrapping() {
		// Test with a long line that needs wrapping
		String longText = "This is a very long line of text that definitely exceeds seventy characters and needs to be wrapped properly at word boundaries.";
		String wrapped = MailServiceImpl.wrapLines(longText, 70);

		// Verify all lines are within limit
		String[] lines = wrapped.split("\n");
		for (String line : lines) {
			assertTrue(line.length() <= 70,
				"Line exceeds 70 characters: " + line.length() + " - " + line);
		}

		// Verify content is preserved
		String unwrapped = wrapped.replace("\n", " ");
		assertEquals(longText, unwrapped, "Content should be preserved after wrapping");
	}

	/**
	 * Test line wrapping with existing paragraph breaks.
	 */
	@Test
	public void testLineWrappingWithParagraphs() {
		String text = "First paragraph with some text.\n\nSecond paragraph that is very long and needs to be wrapped because it exceeds seventy characters in length.\n\nThird short paragraph.";
		String wrapped = MailServiceImpl.wrapLines(text, 70);

		// Verify all lines are within limit
		String[] lines = wrapped.split("\n");
		for (String line : lines) {
			assertTrue(line.length() <= 70,
				"Line exceeds 70 characters: " + line.length() + " - " + line);
		}

		// Verify paragraph breaks are preserved (empty lines)
		assertTrue(wrapped.contains("\n\n"), "Should preserve paragraph breaks");
	}

}
