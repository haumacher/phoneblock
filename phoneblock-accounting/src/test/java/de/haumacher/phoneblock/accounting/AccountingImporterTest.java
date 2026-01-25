/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.accounting;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.junit.jupiter.api.BeforeAll;
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

	private static final String TEST_DB_URL = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1";
	private static final String TEST_DB_USER = "sa";
	private static final String TEST_DB_PASSWORD = "";

	@BeforeAll
	static void setupDatabase() throws Exception {
		// Create CONTRIBUTIONS and USERS tables in the in-memory database
		try (Connection conn = DriverManager.getConnection(TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD);
			 Statement stmt = conn.createStatement()) {

			stmt.execute("""
				CREATE TABLE CONTRIBUTIONS (
				  ID BIGINT NOT NULL AUTO_INCREMENT,
				  USER_ID BIGINT,
				  SENDER CHARACTER VARYING(255),
				  TX CHARACTER VARYING(100) NOT NULL,
				  AMOUNT INTEGER NOT NULL,
				  MESSAGE CHARACTER VARYING(1024),
				  RECEIVED BIGINT DEFAULT 0 NOT NULL,
				  ACK BOOLEAN DEFAULT FALSE NOT NULL,
				  CONSTRAINT CONTRIBUTIONS_PK PRIMARY KEY (ID)
				)
			""");

			stmt.execute("CREATE UNIQUE INDEX CONTRIBUTIONS_TX_IDX ON CONTRIBUTIONS (TX)");

			stmt.execute("""
				CREATE TABLE USERS (
				  ID BIGINT NOT NULL AUTO_INCREMENT,
				  LOGIN CHARACTER VARYING(255) NOT NULL,
				  DISPLAYNAME CHARACTER VARYING(255) NOT NULL,
				  EMAIL CHARACTER VARYING(255),
				  CREDIT INTEGER DEFAULT 0 NOT NULL,
				  CONSTRAINT USERS_PK PRIMARY KEY (ID)
				)
			""");
		}
	}

	@Test
	void testImportEmptyCsv() throws Exception {
		// Create CSV file with German bank format header but no data
		File csvFile = new File(tempDir, "empty.csv");
		try (OutputStreamWriter writer = new OutputStreamWriter(
				new FileOutputStream(csvFile), StandardCharsets.UTF_8)) {
			writer.write("Umsatzanzeige;Datei erstellt am: 25.01.2026 17:31\n");
			writer.write("\n");
			writer.write("Buchung;Wertstellungsdatum;Auftraggeber/Empfänger;Buchungstext;Verwendungszweck;Betrag;Währung\n");
		}

		AccountingImporter importer = new AccountingImporter(TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD);
		try {
			// Should not throw exception
			assertDoesNotThrow(() -> importer.importFromCsv(csvFile.getAbsolutePath(), StandardCharsets.UTF_8, true));
		} finally {
			importer.close();
		}
	}

	@Test
	void testImportNonExistentFile() throws Exception {
		AccountingImporter importer = new AccountingImporter(TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD);
		try {
			String nonExistentPath = new File(tempDir, "does-not-exist.csv").getAbsolutePath();

			IOException exception = assertThrows(IOException.class,
					() -> importer.importFromCsv(nonExistentPath, StandardCharsets.UTF_8, true));

			assertTrue(exception.getMessage().contains("not found"));
		} finally {
			importer.close();
		}
	}

	@Test
	void testImportSampleCsv() throws Exception {
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

		AccountingImporter importer = new AccountingImporter(TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD);
		try {
			// Should not throw exception (initial import)
			assertDoesNotThrow(() -> importer.importFromCsv(csvFile.getAbsolutePath(), StandardCharsets.UTF_8, true));
		} finally {
			importer.close();
		}
	}

	@Test
	void testExtractEmailPattern_StandardFormat() throws Exception {
		AccountingImporter importer = new AccountingImporter(TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD);
		try {
			assertEquals("user@example.com", importer.extractEmailPattern("Contribution from user@example.com"));
			assertEquals("john.doe@mail.de", importer.extractEmailPattern("PhoneBlock john.doe@mail.de support"));
			assertEquals("test_user@domain.org", importer.extractEmailPattern("test_user@domain.org"));
		} finally {
			importer.close();
		}
	}

	@Test
	void testExtractEmailPattern_ObfuscatedWithAt() throws Exception {
		AccountingImporter importer = new AccountingImporter(TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD);
		try {
			assertEquals("user@example.com", importer.extractEmailPattern("Contribution from user at example.com"));
			assertEquals("sh-konsum@mai", importer.extractEmailPattern("sh-konsum at mai"));
			assertEquals("john.doe@mail.de", importer.extractEmailPattern("PhoneBlock john.doe at mail.de support"));
		} finally {
			importer.close();
		}
	}

	@Test
	void testExtractEmailPattern_WithSpaces() throws Exception {
		AccountingImporter importer = new AccountingImporter(TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD);
		try {
			assertEquals("user@example.com", importer.extractEmailPattern("user  at  example.com"));
			assertEquals("test@domain", importer.extractEmailPattern("test  @  domain"));
		} finally {
			importer.close();
		}
	}

	@Test
	void testExtractEmailPattern_CaseInsensitive() throws Exception {
		AccountingImporter importer = new AccountingImporter(TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD);
		try {
			assertEquals("User@Example.Com", importer.extractEmailPattern("User AT Example.Com"));
			assertEquals("TEST@DOMAIN", importer.extractEmailPattern("TEST At DOMAIN"));
		} finally {
			importer.close();
		}
	}

	@Test
	void testExtractEmailPattern_SpecialCharacters() throws Exception {
		AccountingImporter importer = new AccountingImporter(TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD);
		try {
			assertEquals("user-name@sub-domain.example.com",
					importer.extractEmailPattern("user-name@sub-domain.example.com"));
			assertEquals("user_123@test.org", importer.extractEmailPattern("user_123@test.org"));
			assertEquals("first.last@company.co.uk", importer.extractEmailPattern("first.last at company.co.uk"));
		} finally {
			importer.close();
		}
	}

	@Test
	void testExtractEmailPattern_NoMatch() throws Exception {
		AccountingImporter importer = new AccountingImporter(TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD);
		try {
			assertNull(importer.extractEmailPattern("PhoneBlock contribution"));
			assertNull(importer.extractEmailPattern("No email here"));
			assertNull(importer.extractEmailPattern("@invalid"));
			assertNull(importer.extractEmailPattern("user@"));
			assertNull(importer.extractEmailPattern("@domain"));
			assertNull(importer.extractEmailPattern(null));
			assertNull(importer.extractEmailPattern(""));
		} finally {
			importer.close();
		}
	}

	@Test
	void testExtractEmailPattern_FirstMatchOnly() throws Exception {
		AccountingImporter importer = new AccountingImporter(TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD);
		try {
			// Should extract the first email pattern found
			assertEquals("first@example.com",
					importer.extractEmailPattern("first@example.com and second@other.com"));
			assertEquals("user1@test.org",
					importer.extractEmailPattern("user1 at test.org or user2 at test.org"));
		} finally {
			importer.close();
		}
	}
}
