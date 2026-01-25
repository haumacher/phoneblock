/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.accounting;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Test cases for {@link AccountingImporter}.
 *
 * @author <a href="mailto:bhu@haumacher.de">Bernhard Haumacher</a>
 */
class AccountingImporterTest {

	@TempDir
	File tempDir;

	@Test
	void testImportEmptyCsv() throws IOException {
		// Create empty CSV file with header only
		File csvFile = new File(tempDir, "empty.csv");
		try (FileWriter writer = new FileWriter(csvFile)) {
			writer.write("Date,Amount,Reference,Description\n");
		}

		AccountingImporter importer = new AccountingImporter();

		// Should not throw exception
		assertDoesNotThrow(() -> importer.importFromCsv(csvFile.getAbsolutePath()));
	}

	@Test
	void testImportNonExistentFile() {
		AccountingImporter importer = new AccountingImporter();

		String nonExistentPath = new File(tempDir, "does-not-exist.csv").getAbsolutePath();

		IOException exception = assertThrows(IOException.class,
				() -> importer.importFromCsv(nonExistentPath));

		assertTrue(exception.getMessage().contains("not found"));
	}

	@Test
	void testImportSampleCsv() throws IOException {
		// Create sample CSV with test data
		File csvFile = new File(tempDir, "sample.csv");
		try (FileWriter writer = new FileWriter(csvFile)) {
			writer.write("Date,Amount,Reference,Description\n");
			writer.write("2026-01-15,10.00,REF123,Test contribution\n");
			writer.write("2026-01-20,25.50,REF124,Another contribution\n");
		}

		AccountingImporter importer = new AccountingImporter();

		// Should not throw exception
		assertDoesNotThrow(() -> importer.importFromCsv(csvFile.getAbsolutePath()));
	}
}
