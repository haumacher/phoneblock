/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.accounting;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

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
		// Create CSV file with German bank format header but no data
		File csvFile = new File(tempDir, "empty.csv");
		try (OutputStreamWriter writer = new OutputStreamWriter(
				new FileOutputStream(csvFile), StandardCharsets.UTF_8)) {
			writer.write("Umsatzanzeige;Datei erstellt am: 25.01.2026 17:31\n");
			writer.write("\n");
			writer.write("Buchung;Wertstellungsdatum;Auftraggeber/Empfänger;Buchungstext;Verwendungszweck;Betrag;Währung\n");
		}

		AccountingImporter importer = new AccountingImporter();

		// Should not throw exception
		assertDoesNotThrow(() -> importer.importFromCsv(csvFile.getAbsolutePath(), StandardCharsets.UTF_8));
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
		// Create sample CSV with German bank format and PhoneBlock contributions
		File csvFile = new File(tempDir, "sample.csv");
		try (OutputStreamWriter writer = new OutputStreamWriter(
				new FileOutputStream(csvFile), StandardCharsets.UTF_8)) {
			writer.write("Umsatzanzeige;Datei erstellt am: 25.01.2026 17:31\n");
			writer.write("\n");
			writer.write("IBAN;DE00 0000 0000 0000 0000 00\n");
			writer.write("\n");
			writer.write("Buchung;Wertstellungsdatum;Auftraggeber/Empfänger;Buchungstext;Verwendungszweck;Betrag;Währung\n");
			writer.write("20.01.2026;20.01.2026;Max Mustermann;Gutschrift aus Dauerauftrag;PhoneBlock-7b2f1641-298e;5,00;EUR\n");
			writer.write("20.01.2026;20.01.2026;John Doe;Gutschrift;Some other purpose;10,00;EUR\n");
			writer.write("19.01.2026;19.01.2026;Erika Musterfrau;Gutschrift;PhoneBlock-cf4081f7-5e4b;25,00;EUR\n");
		}

		AccountingImporter importer = new AccountingImporter();

		// Should not throw exception
		assertDoesNotThrow(() -> importer.importFromCsv(csvFile.getAbsolutePath(), StandardCharsets.UTF_8));
	}
}
