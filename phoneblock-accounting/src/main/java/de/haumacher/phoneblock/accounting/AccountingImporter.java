/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.accounting;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.accounting.config.AccountingConfig;
import de.haumacher.phoneblock.accounting.db.AccountingDB;
import de.haumacher.phoneblock.accounting.db.ContributionRecord;
import de.haumacher.phoneblock.accounting.db.Contributions;
import de.haumacher.phoneblock.accounting.db.Users;

/**
 * Command-line tool for importing PhoneBlock contribution accounting data from CSV bank exports.
 *
 * <p>
 * This tool processes CSV files exported from bank accounts and imports contribution payments
 * into the PhoneBlock database for tracking and accounting purposes.
 * </p>
 *
 * @author <a href="mailto:bhu@haumacher.de">Bernhard Haumacher</a>
 */
public class AccountingImporter {

	private static final Logger LOG = LoggerFactory.getLogger(AccountingImporter.class);

	private AccountingDB _db;

	/**
	 * Column indices extracted from the header row.
	 * Encapsulates column access to handle reordered columns.
	 */
	private static class ColumnMapping {
		private final int buchung;
		private final int auftraggeber;
		private final int verwendungszweck;
		private final int betrag;

		ColumnMapping(CSVRecord headerRecord) throws IOException {
			buchung = findColumn(headerRecord, BUCHUNG_COLUMN);
			auftraggeber = findColumn(headerRecord, AUFTRAGGEBER_COLUMN);
			verwendungszweck = findColumn(headerRecord, VERWENDUNGSZWECK_COLUMN);
			betrag = findColumn(headerRecord, BETRAG_COLUMN);
		}

		private int findColumn(CSVRecord headerRecord, String columnName) throws IOException {
			for (int i = 0; i < headerRecord.size(); i++) {
				if (columnName.equals(headerRecord.get(i))) {
					return i;
				}
			}
			throw new IOException("Required column not found in header: " + columnName);
		}

		String getBuchung(CSVRecord record) {
			return record.get(buchung);
		}

		String getAuftraggeber(CSVRecord record) {
			return record.get(auftraggeber);
		}

		String getVerwendungszweck(CSVRecord record) {
			return record.get(verwendungszweck);
		}

		String getBetrag(CSVRecord record) {
			return record.get(betrag);
		}
	}

	/**
	 * Main entry point for the accounting importer.
	 *
	 * @param args Command-line arguments (see printUsage for details)
	 */
	public static void main(String[] args) {
		// Check for config file option first
		String configFile = null;
		for (int i = 0; i < args.length; i++) {
			if ((args[i].equals("-c") || args[i].equals("--config")) && i + 1 < args.length) {
				configFile = args[i + 1];
				break;
			}
		}

		// Load configuration
		AccountingConfig config;
		if (configFile != null) {
			config = AccountingConfig.loadFromFile(configFile, false);
		} else {
			config = AccountingConfig.loadDefault();
		}

		// Parse command-line arguments (these override config file values)
		if (!config.parseArguments(args)) {
			printUsage();
			System.exit(1);
		}

		// Validate configuration
		if (!config.isValid()) {
			System.err.println("Error: CSV file is required");
			System.err.println();
			printUsage();
			System.exit(1);
		}

		// Import CSV file
		try {
			AccountingImporter importer = new AccountingImporter(
				config.getDbUrl(),
				config.getDbUser(),
				config.getDbPassword()
			);
			try {
				importer.importFromCsv(config.getCsvFile(), config.getCharset(), config.isInitial());
			} finally {
				importer.close();
			}
		} catch (Exception e) {
			LOG.error("Failed to import CSV file: {}", config.getCsvFile(), e);
			System.exit(1);
		}
	}

	private static void printUsage() {
		System.out.println("PhoneBlock Accounting Importer");
		System.out.println();
		System.out.println("Usage: java -jar phoneblock-accounting.jar [options] <csv-file>");
		System.out.println();
		System.out.println("Options:");
		System.out.println("  -c, --config <file>      Configuration file (default: ~/.phoneblock-accounting)");
		System.out.println("  -f, --file <csv-file>    CSV file to import (required)");
		System.out.println("  --charset <charset>      Character encoding (default: ISO-8859-1)");
		System.out.println("                           Common values: ISO-8859-1, UTF-8, Windows-1252");
		System.out.println("  --db-url <url>           Database JDBC URL (default: jdbc:h2:./phoneblock)");
		System.out.println("  --db-user <user>         Database user (default: phone)");
		System.out.println("  --db-password <pass>     Database password (default: block)");
		System.out.println("  --initial                Skip overlap check (for initial import only)");
		System.out.println("  -h, --help               Show this help message");
		System.out.println();
		System.out.println("The CSV file can also be specified as a positional argument.");
		System.out.println();
		System.out.println("Configuration File Format (~/.phoneblock-accounting):");
		System.out.println("  charset=ISO-8859-1");
		System.out.println("  db.url=jdbc:h2:./phoneblock");
		System.out.println("  db.user=phone");
		System.out.println("  db.password=block");
		System.out.println();
		System.out.println("Examples:");
		System.out.println("  # Using defaults from ~/.phoneblock-accounting");
		System.out.println("  java -jar phoneblock-accounting.jar bank-export.csv");
		System.out.println();
		System.out.println("  # With named arguments");
		System.out.println("  java -jar phoneblock-accounting.jar --file bank-export.csv --charset UTF-8");
		System.out.println();
		System.out.println("  # With custom config file");
		System.out.println("  java -jar phoneblock-accounting.jar -c /path/to/config bank-export.csv");
		System.out.println();
		System.out.println("  # Override database settings");
		System.out.println("  java -jar phoneblock-accounting.jar --db-url jdbc:h2:/custom/path bank-export.csv");
	}

	/**
	 * Creates a new accounting importer with database connection.
	 *
	 * @param dbUrl The database JDBC URL
	 * @param dbUser The database user
	 * @param dbPassword The database password
	 * @throws SQLException If database connection fails
	 */
	public AccountingImporter(String dbUrl, String dbUser, String dbPassword) throws SQLException {
		_db = new AccountingDB(dbUrl, dbUser, dbPassword);
	}

	/**
	 * Closes the database connection.
	 */
	public void close() {
		if (_db != null) {
			_db.close();
		}
	}

	/**
	 * Required header columns to identify the data section.
	 */
	private static final Set<String> REQUIRED_HEADERS = new HashSet<>(Arrays.asList(
		"Buchung", "Wertstellungsdatum", "Auftraggeber/Empfänger",
		"Buchungstext", "Verwendungszweck", "Betrag", "Währung"
	));

	private static final String BUCHUNG_COLUMN = "Buchung";
	private static final String AUFTRAGGEBER_COLUMN = "Auftraggeber/Empfänger";
	private static final String VERWENDUNGSZWECK_COLUMN = "Verwendungszweck";
	private static final String BETRAG_COLUMN = "Betrag";
	private static final String PHONEBLOCK_KEYWORD = "phoneblock";

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

	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy");

	/**
	 * Imports contribution data from a CSV file.
	 *
	 * @param csvFilePath Path to the CSV file to import
	 * @param charset Character encoding to use for reading the file
	 * @param initial If true, skip the overlap check (for initial import)
	 * @throws IOException If the file cannot be read
	 */
	public void importFromCsv(String csvFilePath, Charset charset, boolean initial) throws IOException {
		File csvFile = new File(csvFilePath);

		if (!csvFile.exists()) {
			throw new IOException("CSV file not found: " + csvFilePath);
		}

		LOG.info("Starting import from CSV file: {} (charset: {})", csvFilePath, charset.name());

		// Parse entire file as CSV using semicolon delimiter
		CSVFormat format = CSVFormat.DEFAULT
			.builder()
			.setDelimiter(';')
			.build();

		try (InputStreamReader reader = new InputStreamReader(new FileInputStream(csvFile), charset);
		     CSVParser parser = new CSVParser(reader, format)) {

			// First pass: find header and extract all contribution records
			ColumnMapping columnMapping = null;
			List<ContributionRecord> contributions = new ArrayList<>();

			try (SqlSession session = _db.openSession()) {
				Users users = session.getMapper(Users.class);

				for (CSVRecord record : parser) {
					if (columnMapping == null) {
						// Check if this record is the header
						if (isHeaderRecord(record)) {
							LOG.info("Found header at record #{}", record.getRecordNumber());
							columnMapping = new ColumnMapping(record);
						}
						// Skip records before header
						continue;
					}

					// Extract contribution records after header
					ContributionRecord contribution = extractContribution(record, columnMapping, users);
					if (contribution != null) {
						contributions.add(contribution);
					}
				}
			}

			if (columnMapping == null) {
				String errorMessage = String.format(
					"Could not find header line with required columns in CSV file.%n" +
					"Expected header to contain: %s",
					String.join(", ", REQUIRED_HEADERS)
				);
				LOG.error(errorMessage);
				throw new IOException(errorMessage);
			}

			// Sort contributions by date (ascending order)
			LOG.info("Sorting {} contributions by date (ascending order)", contributions.size());
			contributions.sort(Comparator.comparing(ContributionRecord::getReceived));

			// Second pass: process contributions in chronological order
			try (SqlSession session = _db.openSession()) {
				Contributions contributionsMapper = session.getMapper(Contributions.class);
				Users users = session.getMapper(Users.class);

				try {
					int filteredCount = 0;
					int newCount = 0;
					int skippedCount = 0;
					boolean foundDuplicateBeforeNew = false;

					for (ContributionRecord contribution : contributions) {
						filteredCount++;

						ProcessResult result = processContribution(contribution, contributionsMapper, users);
						if (result == ProcessResult.PHONEBLOCK_NEW) {
							newCount++;
						} else if (result == ProcessResult.PHONEBLOCK_DUPLICATE) {
							skippedCount++;
							// Track if we found a duplicate before processing any new records
							if (newCount == 0) {
								foundDuplicateBeforeNew = true;
							}
						}
					}

					// Safety check: ensure data overlap unless this is the initial import
					if (!initial && newCount > 0 && !foundDuplicateBeforeNew) {
						String errorMessage = "No duplicate contributions found before new records. " +
							"This indicates a gap in import data and potential missing donations. " +
							"Use --initial flag only for the very first import.";
						LOG.error(errorMessage);
						throw new IOException(errorMessage);
					}

					// Commit all inserts (only if there were any)
					if (newCount > 0) {
						session.commit();
					}

					LOG.info("Import completed. Found {} PhoneBlock contributions ({} new, {} duplicates).",
							filteredCount, newCount, skippedCount);
				} catch (Exception e) {
					session.rollback();
					throw e;
				}
			}
		}
	}

	/**
	 * Checks if a CSV record is the header row.
	 *
	 * @param record The CSV record to check
	 * @return true if this record contains all required header columns
	 */
	private boolean isHeaderRecord(CSVRecord record) {
		// Get all values from the record as a Set for efficient lookup
		Set<String> values = new HashSet<>();
		for (int i = 0; i < record.size(); i++) {
			values.add(record.get(i));
		}

		// Check if all required headers are present
		return values.containsAll(REQUIRED_HEADERS);
	}

	/**
	 * Result of processing a record.
	 */
	private enum ProcessResult {
		NOT_PHONEBLOCK,
		PHONEBLOCK_NEW,
		PHONEBLOCK_DUPLICATE
	}

	/**
	 * Extracts a ContributionRecord from a CSV record.
	 * Returns null if the record is not a PhoneBlock contribution or cannot be parsed.
	 *
	 * @param record The CSV record to extract from
	 * @param columnMapping The column index mapping from the header
	 * @param users The users mapper for looking up user IDs
	 * @return The extracted ContributionRecord, or null if not applicable
	 */
	private ContributionRecord extractContribution(CSVRecord record, ColumnMapping columnMapping, Users users) {
		LOG.debug("Extracting record: {}", record.getRecordNumber());

		// Extract fields from CSV using column mapping
		String verwendungszweck = columnMapping.getVerwendungszweck(record);

		// Filter for PhoneBlock contributions (case insensitive)
		if (verwendungszweck == null || !verwendungszweck.toLowerCase().contains(PHONEBLOCK_KEYWORD)) {
			return null;
		}

		try {
			String buchungDate = columnMapping.getBuchung(record);
			String sender = columnMapping.getAuftraggeber(record);
			String betrag = columnMapping.getBetrag(record);

			// Parse amount (convert from EUR to cents)
			int amountCents = parseAmount(betrag);

			// Parse date to timestamp
			long receivedTimestamp = parseDate(buchungDate);

			// Create TX identifier
			String tx = createTxIdentifier(sender, buchungDate);

			// Try to find the contributing user by extracting username from message
			String username = extractUsername(verwendungszweck);
			Long userId = null;
			if (username != null) {
				userId = users.findUserIdByUsername(username);
				if (userId != null) {
					LOG.debug("Found user ID {} for username '{}'", userId, username);
				} else {
					LOG.debug("No user found for username '{}'", username);
				}
			}

			// If user not found by username, try email pattern matching
			if (userId == null) {
				String emailPattern = extractEmailPattern(verwendungszweck);
				if (emailPattern != null) {
					List<Long> matchingUsers = users.findUserIdsByEmail(emailPattern);
					if (matchingUsers.size() == 1) {
						userId = matchingUsers.get(0);
						LOG.debug("Found user ID {} by email pattern '{}'", userId, emailPattern);
					} else if (matchingUsers.size() > 1) {
						LOG.debug("Found {} users for email pattern '{}', result not unique - skipping", matchingUsers.size(), emailPattern);
					} else {
						LOG.debug("No user found for email pattern '{}'", emailPattern);
					}
				}
			}

			// Create contribution record
			return new ContributionRecord(
				userId,
				sender,
				tx,
				amountCents,
				verwendungszweck,
				receivedTimestamp
			);

		} catch (Exception e) {
			LOG.error("Failed to extract record {}: {}", record.getRecordNumber(), e.getMessage(), e);
			return null;
		}
	}

	/**
	 * Processes a single contribution record and imports it.
	 *
	 * @param contribution The contribution record to process
	 * @param contributions The contributions mapper
	 * @param users The users mapper
	 * @return ProcessResult indicating whether the record was processed
	 */
	private ProcessResult processContribution(ContributionRecord contribution, Contributions contributions, Users users) {
		String tx = contribution.getTx();

		// Check if contribution already exists
		if (contributions.exists(tx)) {
			LOG.info("Skipping duplicate contribution: {}", tx);
			printRecord("DUPLICATE", contribution);
			return ProcessResult.PHONEBLOCK_DUPLICATE;
		}

		// Insert new contribution
		contributions.insert(contribution);

		// Update user's credit if contribution is linked to a user
		if (contribution.getUserId() != null) {
			users.addCredit(contribution.getUserId(), contribution.getAmount());
			LOG.debug("Added {} cents to credit of user ID {}", contribution.getAmount(), contribution.getUserId());
		}

		LOG.info("Imported new contribution: {} ({}€)", tx, formatAmount(contribution.getAmount()));
		printRecord("NEW", contribution);

		return ProcessResult.PHONEBLOCK_NEW;
	}

	/**
	 * Extracts the username from a PhoneBlock contribution message.
	 *
	 * @param message The contribution message (Verwendungszweck)
	 * @return The extracted username, or null if no pattern found
	 */
	private String extractUsername(String message) {
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
	String extractEmailPattern(String message) {
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

	/**
	 * Creates the TX identifier from sender and date.
	 *
	 * @param sender The sender's name
	 * @param date The booking date (DD.MM.YYYY)
	 * @return The TX identifier: "Sender; DD.MM.YYYY"
	 */
	private String createTxIdentifier(String sender, String date) {
		// Normalize sender: trim and replace multiple spaces with single space
		String normalizedSender = sender.trim().replaceAll("\\s+", " ");
		return normalizedSender + "; " + date;
	}

	/**
	 * Parses an amount string from German format to cents.
	 *
	 * @param betrag The amount string (e.g., "5,00" or "-16,42")
	 * @return The amount in cents
	 */
	private int parseAmount(String betrag) {
		// Remove thousand separators (if any) and replace comma with dot
		String normalized = betrag.trim().replace(".", "").replace(',', '.');
		double euros = Double.parseDouble(normalized);
		return (int) Math.round(euros * 100);
	}

	/**
	 * Parses a date string to Unix timestamp.
	 *
	 * @param dateStr The date string (DD.MM.YYYY)
	 * @return Unix timestamp in milliseconds
	 * @throws ParseException If parsing fails
	 */
	private long parseDate(String dateStr) throws ParseException {
		Date date = DATE_FORMAT.parse(dateStr);
		return date.getTime();
	}

	/**
	 * Prints a contribution record to the console.
	 *
	 * @param status The status label (NEW, DUPLICATE, etc.)
	 * @param contribution The contribution record
	 */
	private void printRecord(String status, ContributionRecord contribution) {
		System.out.println("=".repeat(80));
		System.out.println("Contribution: " + contribution.getTx() + " [" + status + "]");
		System.out.println("-".repeat(80));

		// Extract date from TX identifier (format: "Sender; DD.MM.YYYY")
		String tx = contribution.getTx();
		int semicolonPos = tx.lastIndexOf("; ");
		String buchungDate = semicolonPos >= 0 ? tx.substring(semicolonPos + 2) : "unknown";

		// Print important fields
		System.out.printf("  %-25s: %s%n", "Buchung", buchungDate);
		System.out.printf("  %-25s: %s%n", "Auftraggeber/Empfänger", contribution.getSender());
		System.out.printf("  %-25s: %s%n", "Verwendungszweck", contribution.getMessage());
		System.out.printf("  %-25s: %s%n", "Betrag", formatAmount(contribution.getAmount()));

		// Print user information
		String username = extractUsername(contribution.getMessage());
		if (username == null) {
			username = extractEmailPattern(contribution.getMessage());
		}

		if (username != null) {
			if (contribution.getUserId() != null) {
				System.out.printf("  %-25s: %s (User ID: %d)%n", "PhoneBlock User", username, contribution.getUserId());
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
	private String formatAmount(int amountCents) {
		double euros = amountCents / 100.0;
		return String.format("%.2f", euros).replace('.', ',');
	}
}
