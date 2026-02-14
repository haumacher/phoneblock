/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.accounting.db;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Record representing a contribution entry to be stored in the database.
 */
public class ContributionRecord {

	/**
	 * Pattern to extract username from "PhoneBlock-XXXXX" format.
	 */
	private static final Pattern USERNAME_PATTERN = Pattern.compile("PhoneBlock-([^\\s]+)", Pattern.CASE_INSENSITIVE);

	/**
	 * Pattern to extract email-like patterns from messages.
	 * Matches formats like "user@domain", "user at domain", or partial addresses.
	 */
	private static final Pattern EMAIL_PATTERN = Pattern.compile(
		"([a-zA-Z0-9._-]+)\\s*(?:@|at)\\s*([a-zA-Z0-9._-]+)",
		Pattern.CASE_INSENSITIVE
	);

	private long id;
	private Long userId;
	private String sender;
	private String tx;
	private int amount;
	private String message;
	private long received;

	/**
	 * Creates a new contribution record.
	 */
	public ContributionRecord() {
		// Default constructor for MyBatis
	}

	/**
	 * Creates a new contribution record with all fields.
	 *
	 * @param userId The user ID who made the contribution (can be null)
	 * @param sender The sender's name
	 * @param tx The transaction identifier (sender + date)
	 * @param amount The amount in cents
	 * @param message The purpose/message field
	 * @param received The timestamp when the contribution was received (Unix milliseconds)
	 */
	public ContributionRecord(Long userId, String sender, String tx, int amount, String message, long received) {
		this.userId = userId;
		this.sender = sender;
		this.tx = tx;
		this.amount = amount;
		this.message = message;
		this.received = received;
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public Long getUserId() {
		return userId;
	}

	public void setUserId(Long userId) {
		this.userId = userId;
	}

	public String getSender() {
		return sender;
	}

	public void setSender(String sender) {
		this.sender = sender;
	}

	public String getTx() {
		return tx;
	}

	public void setTx(String tx) {
		this.tx = tx;
	}

	public int getAmount() {
		return amount;
	}

	public void setAmount(int amount) {
		this.amount = amount;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public long getReceived() {
		return received;
	}

	public void setReceived(long received) {
		this.received = received;
	}

	/**
	 * Prints this contribution record to the console.
	 *
	 * @param status The status label (NEW, DUPLICATE, etc.)
	 */
	public void print(String status) {
		System.out.println("=".repeat(80));
		System.out.println("Contribution: " + tx + " [" + status + "]");
		System.out.println("-".repeat(80));

		// Extract date from TX identifier (format: "Sender; DD.MM.YYYY")
		int semicolonPos = tx.lastIndexOf("; ");
		String buchungDate = semicolonPos >= 0 ? tx.substring(semicolonPos + 2) : "unknown";

		// Print important fields
		System.out.printf("  %-25s: %s%n", "Buchung", buchungDate);
		System.out.printf("  %-25s: %s%n", "Auftraggeber/Empfänger", sender);
		System.out.printf("  %-25s: %s%n", "Verwendungszweck", message);
		System.out.printf("  %-25s: %s%n", "Betrag", formatAmount(amount));

		// Print user information
		String username = extractUsername(message);
		if (username == null) {
			username = extractEmailPattern(message);
		}

		if (username != null) {
			if (userId != null) {
				System.out.printf("  %-25s: %s (User ID: %d)%n", "PhoneBlock User", username, userId);
			} else {
				System.out.printf("  %-25s: %s (not found)%n", "PhoneBlock User", username);
			}
		}

		System.out.println("=".repeat(80));
		System.out.println();
	}

	/**
	 * Formats an amount in cents to Euro string.
	 *
	 * @param amountCents The amount in cents
	 * @return Formatted amount string (e.g., "5,00")
	 */
	public static String formatAmount(int amountCents) {
		double euros = amountCents / 100.0;
		return String.format("%.2f", euros).replace('.', ',');
	}

	/**
	 * Extracts the username from a PhoneBlock contribution message.
	 *
	 * @param message The contribution message (Verwendungszweck)
	 * @return The extracted username, or null if no pattern found
	 */
	public static String extractUsername(String message) {
		if (message == null) {
			return null;
		}

		Matcher matcher = USERNAME_PATTERN.matcher(message);
		if (matcher.find()) {
			return matcher.group(1);
		}

		return null;
	}

	/**
	 * Extracts email-like patterns from a message.
	 * Handles formats like "user@domain" or "user at domain".
	 *
	 * @param message The contribution message (Verwendungszweck)
	 * @return The extracted email pattern for searching, or null if no pattern found
	 */
	public static String extractEmailPattern(String message) {
		if (message == null) {
			return null;
		}

		Matcher matcher = EMAIL_PATTERN.matcher(message);
		if (matcher.find()) {
			// Normalize to standard email format: "user@domain"
			String localPart = matcher.group(1);
			String domainPart = matcher.group(2);
			return localPart + "@" + domainPart;
		}

		return null;
	}
}
