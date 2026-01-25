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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

	private static final Charset DEFAULT_CHARSET = Charset.forName("ISO-8859-1");

	/**
	 * Main entry point for the accounting importer.
	 *
	 * @param args Command-line arguments:
	 *             args[0] - Path to CSV file to import
	 *             args[1] - (Optional) Character encoding (default: ISO-8859-1)
	 */
	public static void main(String[] args) {
		if (args.length == 0) {
			printUsage();
			System.exit(1);
		}

		String csvFilePath = args[0];
		Charset charset = DEFAULT_CHARSET;

		if (args.length > 1) {
			try {
				charset = Charset.forName(args[1]);
			} catch (Exception e) {
				System.err.println("Error: Invalid charset '" + args[1] + "'");
				System.err.println("Using default charset: " + DEFAULT_CHARSET.name());
			}
		}

		AccountingImporter importer = new AccountingImporter();
		try {
			importer.importFromCsv(csvFilePath, charset);
		} catch (Exception e) {
			LOG.error("Failed to import CSV file: {}", csvFilePath, e);
			System.exit(1);
		}
	}

	private static void printUsage() {
		System.out.println("PhoneBlock Accounting Importer");
		System.out.println();
		System.out.println("Usage: java -jar phoneblock-accounting.jar <csv-file> [charset]");
		System.out.println();
		System.out.println("Arguments:");
		System.out.println("  <csv-file>    Path to the CSV file containing bank transactions");
		System.out.println("  [charset]     Character encoding (optional, default: ISO-8859-1)");
		System.out.println("                Common values: ISO-8859-1, UTF-8, Windows-1252");
		System.out.println();
		System.out.println("Examples:");
		System.out.println("  java -jar phoneblock-accounting.jar bank-export-2026.csv");
		System.out.println("  java -jar phoneblock-accounting.jar bank-export-2026.csv UTF-8");
	}

	/**
	 * Required header columns to identify the data section.
	 */
	private static final String[] REQUIRED_HEADERS = {
		"Buchung", "Wertstellungsdatum", "Auftraggeber/Empfänger",
		"Buchungstext", "Verwendungszweck", "Betrag", "Währung"
	};

	private static final String VERWENDUNGSZWECK_COLUMN = "Verwendungszweck";
	private static final String PHONEBLOCK_KEYWORD = "phoneblock";

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
	 * Imports contribution data from a CSV file using default charset.
	 *
	 * @param csvFilePath Path to the CSV file to import
	 * @throws IOException If the file cannot be read
	 */
	public void importFromCsv(String csvFilePath) throws IOException {
		importFromCsv(csvFilePath, DEFAULT_CHARSET);
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

		try (CSVParser parser = CSVParser.parse(csvContent.toString(), format)) {
			int recordCount = 0;
			int filteredCount = 0;

			for (CSVRecord record : parser) {
				recordCount++;

				if (processRecord(record)) {
					filteredCount++;
				}
			}

			LOG.info("Import completed. Processed {} records, found {} PhoneBlock contributions.",
					recordCount, filteredCount);
		}
	}

	/**
	 * Processes a single CSV record and imports it as a contribution if valid.
	 *
	 * @param record The CSV record to process
	 * @return true if the record contains PhoneBlock in Verwendungszweck, false otherwise
	 */
	private boolean processRecord(CSVRecord record) {
		LOG.debug("Processing record: {}", record.getRecordNumber());

		// Check if record has the Verwendungszweck column
		if (!record.isMapped(VERWENDUNGSZWECK_COLUMN)) {
			LOG.warn("Record {} does not have '{}' column", record.getRecordNumber(), VERWENDUNGSZWECK_COLUMN);
			return false;
		}

		String verwendungszweck = record.get(VERWENDUNGSZWECK_COLUMN);

		// Filter for PhoneBlock contributions (case insensitive)
		if (verwendungszweck != null && verwendungszweck.toLowerCase().contains(PHONEBLOCK_KEYWORD)) {
			printRecord(record);
			return true;
		}

		return false;
	}

	/**
	 * Prints a CSV record to the console.
	 *
	 * @param record The record to print
	 */
	private void printRecord(CSVRecord record) {
		System.out.println("=".repeat(80));
		System.out.println("Record #" + record.getRecordNumber());
		System.out.println("-".repeat(80));

		// Print all columns
		record.toMap().forEach((column, value) -> {
			System.out.printf("  %-25s: %s%n", column, value);
		});

		System.out.println("=".repeat(80));
		System.out.println();
	}
}
