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
