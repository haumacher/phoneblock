/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.mailcheck.cli;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.ibatis.session.SqlSession;

import de.haumacher.mailcheck.DisposableListService;
import de.haumacher.mailcheck.EmailNormalizer;
import de.haumacher.mailcheck.PropertyStore;
import de.haumacher.mailcheck.db.DBDomainCheck;
import de.haumacher.mailcheck.db.DBEmailCheck;
import de.haumacher.mailcheck.db.Domains;
import de.haumacher.mailcheck.scraper.DisposableScraperService;

/**
 * Command-line interface for the fake-mail-check module.
 *
 * <pre>
 * Usage: java -jar fake-mail-check.jar [--db &lt;path&gt;] &lt;command&gt;
 *
 * Options:
 *   --db &lt;path&gt;    H2 database path (default: ./mailcheck)
 *
 * Commands:
 *   init                     Initialize/upgrade the database schema
 *   import-list              Download and import the GitHub disposable domain list
 *   scrape                   Run all web scrapers for disposable domains
 *   check &lt;email-or-domain&gt;  Check if an email/domain is disposable
 *   stats                    Show database statistics
 * </pre>
 */
public class MailCheckCLI {

	public static void main(String[] args) throws Exception {
		String dbPath = "./mailcheck";
		String command = null;
		String checkArg = null;

		// Parse arguments (--db can appear anywhere).
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			if ("--db".equals(arg)) {
				i++;
				if (i >= args.length) {
					System.err.println("Missing value for --db option.");
					printUsage();
					System.exit(1);
				}
				dbPath = args[i];
			} else if (arg.startsWith("-")) {
				System.err.println("Unknown option: " + arg);
				printUsage();
				System.exit(1);
			} else if (command == null) {
				command = arg;
			} else if (checkArg == null) {
				checkArg = arg;
			}
		}

		if (command == null) {
			printUsage();
			System.exit(1);
			return;
		}

		switch (command) {
			case "init":
				runInit(dbPath);
				break;
			case "import-list":
				runImportList(dbPath);
				break;
			case "scrape":
				runScrape(dbPath);
				break;
			case "check":
				if (checkArg == null) {
					System.err.println("Missing argument for 'check' command.");
					printUsage();
					System.exit(1);
				}
				runCheck(dbPath, checkArg);
				break;
			case "stats":
				runStats(dbPath);
				break;
			default:
				System.err.println("Unknown command: " + command);
				printUsage();
				System.exit(1);
		}
	}

	private static void runInit(String dbPath) throws Exception {
		try (MailCheckDB db = new MailCheckDB(dbPath)) {
			System.out.println("Database initialized at: " + dbPath);
		}
	}

	private static void runImportList(String dbPath) throws Exception {
		try (MailCheckDB db = new MailCheckDB(dbPath)) {
			Path propsFile = Paths.get(dbPath + ".properties");
			PropertyStore propertyStore = new FilePropertyStore(propsFile);

			ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
			try {
				DisposableListService service = new DisposableListService(scheduler, db.getSessionFactory(), propertyStore);
				service.runImport();
			} finally {
				scheduler.shutdownNow();
			}

			System.out.println("Import completed.");
		}
	}

	private static void runScrape(String dbPath) throws Exception {
		try (MailCheckDB db = new MailCheckDB(dbPath)) {
			ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
			try {
				DisposableScraperService service = new DisposableScraperService(scheduler, db.getSessionFactory());
				service.runScrape();
			} finally {
				scheduler.shutdownNow();
			}

			System.out.println("Scraping completed.");
		}
	}

	private static void runCheck(String dbPath, String arg) throws Exception {
		try (MailCheckDB db = new MailCheckDB(dbPath)) {
			try (SqlSession session = db.getSessionFactory().openSession()) {
				Domains domains = session.getMapper(Domains.class);

				if (arg.contains("@")) {
					// E-mail address check.
					String normalized = EmailNormalizer.normalize(arg);
					if (normalized != null) {
						// Public provider — check EMAIL_CHECK table.
						DBEmailCheck emailCheck = domains.checkEmailAddress(normalized);
						if (emailCheck != null) {
							printEmailResult(normalized, emailCheck.isDisposable(), emailCheck.getSourceSystem());
						} else {
							System.out.println("OK — " + normalized + " is on a public e-mail provider (not disposable).");
						}
					} else {
						// Custom domain — check DOMAIN_CHECK table.
						String domain = arg.substring(arg.indexOf('@') + 1).toLowerCase();
						checkDomain(domains, domain);
					}
				} else {
					// Domain check.
					checkDomain(domains, arg.toLowerCase());
				}
			}
		}
	}

	private static void checkDomain(Domains domains, String domain) {
		DBDomainCheck check = domains.checkDomain(domain);
		if (check != null) {
			if (check.isDisposable()) {
				System.out.println("DISPOSABLE — " + domain + " (source: " + check.getSourceSystem() + ")");
			} else {
				System.out.println("OK — " + domain + " is not disposable (source: " + check.getSourceSystem() + ")");
			}
			if (check.getMxHost() != null) {
				System.out.println("  MX host: " + check.getMxHost());
			}
			if (check.getMxIP() != null) {
				System.out.println("  MX IP:   " + check.getMxIP());
			}
		} else {
			System.out.println("UNKNOWN — " + domain + " is not in the database.");
		}
	}

	private static void printEmailResult(String email, boolean disposable, String source) {
		if (disposable) {
			System.out.println("DISPOSABLE — " + email + " (source: " + source + ")");
		} else {
			System.out.println("OK — " + email + " is not disposable (source: " + source + ")");
		}
	}

	private static void runStats(String dbPath) throws Exception {
		try (MailCheckDB db = new MailCheckDB(dbPath)) {
			try (SqlSession session = db.getSessionFactory().openSession()) {
				Connection conn = session.getConnection();
				try (Statement stmt = conn.createStatement()) {
					try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM DOMAIN_CHECK")) {
						rs.next();
						System.out.println("Domains in DOMAIN_CHECK: " + rs.getLong(1));
					}
					try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM DOMAIN_CHECK WHERE DISPOSABLE = TRUE")) {
						rs.next();
						System.out.println("  Disposable:           " + rs.getLong(1));
					}
					try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM DOMAIN_CHECK WHERE DISPOSABLE = FALSE")) {
						rs.next();
						System.out.println("  Not disposable:       " + rs.getLong(1));
					}
					try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM EMAIL_CHECK")) {
						rs.next();
						System.out.println("E-mails in EMAIL_CHECK: " + rs.getLong(1));
					}
					try (ResultSet rs = stmt.executeQuery(
							"SELECT SOURCE_SYSTEM, COUNT(*) AS CNT FROM DOMAIN_CHECK GROUP BY SOURCE_SYSTEM ORDER BY CNT DESC")) {
						System.out.println();
						System.out.println("Domains by source:");
						while (rs.next()) {
							System.out.println("  " + rs.getString(1) + ": " + rs.getLong(2));
						}
					}
				}
			}
		}
	}

	private static void printUsage() {
		System.err.println("Usage: java -jar fake-mail-check.jar [--db <path>] <command>");
		System.err.println();
		System.err.println("Options:");
		System.err.println("  --db <path>    H2 database path (default: ./mailcheck)");
		System.err.println();
		System.err.println("Commands:");
		System.err.println("  init                     Initialize/upgrade the database schema");
		System.err.println("  import-list              Download and import the GitHub disposable domain list");
		System.err.println("  scrape                   Run all web scrapers for disposable domains");
		System.err.println("  check <email-or-domain>  Check if an email/domain is disposable");
		System.err.println("  stats                    Show database statistics");
	}
}
