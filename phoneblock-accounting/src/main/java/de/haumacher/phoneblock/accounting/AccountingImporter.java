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
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
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
				importer.importFromCsv(config.getCsvFile(), config.getCharset());
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

	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy");

	/**
	 * Imports contribution data from a CSV file.
	 *
	 * @param csvFilePath Path to the CSV file to import
	 * @param charset Character encoding to use for reading the file
	 * @throws IOException If the file cannot be read
	 */
	public void importFromCsv(String csvFilePath, Charset charset) throws IOException {
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

			try (SqlSession session = _db.openSession()) {
				Contributions contributions = session.getMapper(Contributions.class);
				Users users = session.getMapper(Users.class);

				try {
					ColumnMapping columnMapping = null;
					int recordCount = 0;
					int filteredCount = 0;
					int newCount = 0;
					int skippedCount = 0;

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

						// Process data records after header
						recordCount++;

						ProcessResult result = processRecord(record, columnMapping, contributions, users);
						if (result == ProcessResult.PHONEBLOCK_NEW) {
							filteredCount++;
							newCount++;
						} else if (result == ProcessResult.PHONEBLOCK_DUPLICATE) {
							filteredCount++;
							skippedCount++;
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

					// Commit all inserts
					session.commit();

					LOG.info("Import completed. Processed {} records, found {} PhoneBlock contributions ({} new, {} duplicates).",
							recordCount, filteredCount, newCount, skippedCount);
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
	 * Processes a single CSV record and imports it as a contribution if valid.
	 *
	 * @param record The CSV record to process
	 * @param columnMapping The column index mapping from the header
	 * @param contributions The contributions mapper
	 * @param users The users mapper
	 * @return ProcessResult indicating whether the record was processed
	 */
	private ProcessResult processRecord(CSVRecord record, ColumnMapping columnMapping, Contributions contributions, Users users) {
		LOG.debug("Processing record: {}", record.getRecordNumber());

		// Extract fields from CSV using column mapping
		String verwendungszweck = columnMapping.getVerwendungszweck(record);

		// Filter for PhoneBlock contributions (case insensitive)
		if (verwendungszweck == null || !verwendungszweck.toLowerCase().contains(PHONEBLOCK_KEYWORD)) {
			return ProcessResult.NOT_PHONEBLOCK;
		}

		try {
			String buchungDate = columnMapping.getBuchung(record);
			String sender = columnMapping.getAuftraggeber(record);
			String betrag = columnMapping.getBetrag(record);

			// Create TX identifier: "Sender DD.MM.YYYY"
			String tx = createTxIdentifier(sender, buchungDate);

			// Check if contribution already exists
			if (contributions.exists(tx)) {
				LOG.info("Skipping duplicate contribution: {}", tx);
				// Extract username for display (but don't look up user since it's a duplicate)
				String duplicateUsername = extractUsername(verwendungszweck);
				printRecord(record, columnMapping, "DUPLICATE", duplicateUsername, null);
				return ProcessResult.PHONEBLOCK_DUPLICATE;
			}

			// Parse amount (convert from EUR to cents)
			int amountCents = parseAmount(betrag);

			// Parse date to timestamp
			long receivedTimestamp = parseDate(buchungDate);

			// Try to find the contributing user by extracting username from message
			Long userId = null;
			String username = extractUsername(verwendungszweck);
			if (username != null) {
				userId = users.findUserIdByUsername(username);
				if (userId != null) {
					LOG.debug("Found user ID {} for username '{}'", userId, username);
				} else {
					LOG.debug("No user found for username '{}'", username);
				}
			}

			// Create and insert contribution
			ContributionRecord contribution = new ContributionRecord(
				userId,
				sender,
				tx,
				amountCents,
				verwendungszweck,
				receivedTimestamp
			);

			contributions.insert(contribution);

			LOG.info("Imported new contribution: {} ({}€)", tx, betrag);
			printRecord(record, columnMapping, "NEW", username, userId);

			return ProcessResult.PHONEBLOCK_NEW;

		} catch (Exception e) {
			LOG.error("Failed to process record {}: {}", record.getRecordNumber(), e.getMessage(), e);
			return ProcessResult.NOT_PHONEBLOCK;
		}
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
	 * Prints a CSV record to the console.
	 *
	 * @param record The record to print
	 * @param columnMapping The column index mapping
	 * @param status The status label (NEW, DUPLICATE, etc.)
	 * @param username The extracted username from the message (can be null)
	 * @param userId The found user ID (can be null)
	 */
	private void printRecord(CSVRecord record, ColumnMapping columnMapping, String status, String username, Long userId) {
		System.out.println("=".repeat(80));
		System.out.println("Record #" + record.getRecordNumber() + " [" + status + "]");
		System.out.println("-".repeat(80));

		// Print important columns using column mapping
		System.out.printf("  %-25s: %s%n", "Buchung", columnMapping.getBuchung(record));
		System.out.printf("  %-25s: %s%n", "Auftraggeber/Empfänger", columnMapping.getAuftraggeber(record));
		System.out.printf("  %-25s: %s%n", "Verwendungszweck", columnMapping.getVerwendungszweck(record));
		System.out.printf("  %-25s: %s%n", "Betrag", columnMapping.getBetrag(record));

		// Print user information
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
}
