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
import java.util.Date;
import java.util.List;

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
	private static final String[] REQUIRED_HEADERS = {
		"Buchung", "Wertstellungsdatum", "Auftraggeber/Empfänger",
		"Buchungstext", "Verwendungszweck", "Betrag", "Währung"
	};

	private static final String BUCHUNG_COLUMN = "Buchung";
	private static final String AUFTRAGGEBER_COLUMN = "Auftraggeber/Empfänger";
	private static final String VERWENDUNGSZWECK_COLUMN = "Verwendungszweck";
	private static final String BETRAG_COLUMN = "Betrag";
	private static final String PHONEBLOCK_KEYWORD = "phoneblock";

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

		// Read file line by line until we find the header
		try (BufferedReader bufferedReader = new BufferedReader(
				new InputStreamReader(new FileInputStream(csvFile), charset))) {
			String headerLine = findHeaderLine(bufferedReader);

			if (headerLine == null) {
				String errorMessage = String.format(
					"Could not find header line with required columns in CSV file.%n" +
					"Expected header to contain: %s",
					String.join(", ", REQUIRED_HEADERS)
				);
				LOG.error(errorMessage);
				throw new IOException(errorMessage);
			}

			LOG.info("Found header line: {}", headerLine);

			// Now parse the CSV data starting from the current position
			processDataRows(bufferedReader, headerLine);
		}
	}

	/**
	 * Reads lines until the header line is found.
	 *
	 * @param reader The buffered reader
	 * @return The header line if found, null otherwise
	 * @throws IOException If reading fails
	 */
	private String findHeaderLine(BufferedReader reader) throws IOException {
		String line;
		while ((line = reader.readLine()) != null) {
			if (isHeaderLine(line)) {
				return line;
			}
		}
		return null;
	}

	/**
	 * Checks if a line contains all required header columns.
	 *
	 * @param line The line to check
	 * @return true if the line is a valid header line
	 */
	private boolean isHeaderLine(String line) {
		for (String requiredHeader : REQUIRED_HEADERS) {
			if (!line.contains(requiredHeader)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Processes data rows after the header has been found.
	 *
	 * @param reader The buffered reader positioned after the header line
	 * @param headerLine The header line
	 * @throws IOException If reading fails
	 */
	private void processDataRows(BufferedReader reader, String headerLine) throws IOException {
		// Collect remaining lines
		List<String> lines = new ArrayList<>();
		String line;
		while ((line = reader.readLine()) != null) {
			lines.add(line);
		}

		// Create CSV content with header and data
		StringBuilder csvContent = new StringBuilder();
		csvContent.append(headerLine).append("\n");
		for (String dataLine : lines) {
			csvContent.append(dataLine).append("\n");
		}

		// Parse CSV using semicolon delimiter
		CSVFormat format = CSVFormat.DEFAULT
			.builder()
			.setDelimiter(';')
			.setHeader()
			.setSkipHeaderRecord(true)
			.build();

		try (SqlSession session = _db.openSession()) {
			Contributions contributions = session.getMapper(Contributions.class);

			try (CSVParser parser = CSVParser.parse(csvContent.toString(), format)) {
				int recordCount = 0;
				int filteredCount = 0;
				int newCount = 0;
				int skippedCount = 0;

				for (CSVRecord record : parser) {
					recordCount++;

					ProcessResult result = processRecord(record, contributions);
					if (result == ProcessResult.PHONEBLOCK_NEW) {
						filteredCount++;
						newCount++;
					} else if (result == ProcessResult.PHONEBLOCK_DUPLICATE) {
						filteredCount++;
						skippedCount++;
					}
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
	 * @param contributions The contributions mapper
	 * @return ProcessResult indicating whether the record was processed
	 */
	private ProcessResult processRecord(CSVRecord record, Contributions contributions) {
		LOG.debug("Processing record: {}", record.getRecordNumber());

		// Check if record has the required columns
		if (!record.isMapped(VERWENDUNGSZWECK_COLUMN)) {
			LOG.warn("Record {} does not have '{}' column", record.getRecordNumber(), VERWENDUNGSZWECK_COLUMN);
			return ProcessResult.NOT_PHONEBLOCK;
		}

		String verwendungszweck = record.get(VERWENDUNGSZWECK_COLUMN);

		// Filter for PhoneBlock contributions (case insensitive)
		if (verwendungszweck == null || !verwendungszweck.toLowerCase().contains(PHONEBLOCK_KEYWORD)) {
			return ProcessResult.NOT_PHONEBLOCK;
		}

		try {
			// Extract fields from CSV
			String buchungDate = record.get(BUCHUNG_COLUMN);
			String sender = record.get(AUFTRAGGEBER_COLUMN);
			String betrag = record.get(BETRAG_COLUMN);

			// Create TX identifier: "Sender DD.MM.YYYY"
			String tx = createTxIdentifier(sender, buchungDate);

			// Check if contribution already exists
			if (contributions.exists(tx)) {
				LOG.info("Skipping duplicate contribution: {}", tx);
				printRecord(record, "DUPLICATE");
				return ProcessResult.PHONEBLOCK_DUPLICATE;
			}

			// Parse amount (convert from EUR to cents)
			int amountCents = parseAmount(betrag);

			// Parse date to timestamp
			long receivedTimestamp = parseDate(buchungDate);

			// Create and insert contribution
			ContributionRecord contribution = new ContributionRecord(
				sender,
				tx,
				amountCents,
				verwendungszweck,
				receivedTimestamp
			);

			contributions.insert(contribution);

			LOG.info("Imported new contribution: {} ({}€)", tx, betrag);
			printRecord(record, "NEW");

			return ProcessResult.PHONEBLOCK_NEW;

		} catch (Exception e) {
			LOG.error("Failed to process record {}: {}", record.getRecordNumber(), e.getMessage(), e);
			return ProcessResult.NOT_PHONEBLOCK;
		}
	}

	/**
	 * Creates the TX identifier from sender and date.
	 *
	 * @param sender The sender's name
	 * @param date The booking date (DD.MM.YYYY)
	 * @return The TX identifier: "Sender DD.MM.YYYY"
	 */
	private String createTxIdentifier(String sender, String date) {
		// Normalize sender: trim and replace multiple spaces with single space
		String normalizedSender = sender.trim().replaceAll("\\s+", " ");
		return normalizedSender + " " + date;
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
	 * @param status The status label (NEW, DUPLICATE, etc.)
	 */
	private void printRecord(CSVRecord record, String status) {
		System.out.println("=".repeat(80));
		System.out.println("Record #" + record.getRecordNumber() + " [" + status + "]");
		System.out.println("-".repeat(80));

		// Print all columns
		record.toMap().forEach((column, value) -> {
			System.out.printf("  %-25s: %s%n", column, value);
		});

		System.out.println("=".repeat(80));
		System.out.println();
	}
}
