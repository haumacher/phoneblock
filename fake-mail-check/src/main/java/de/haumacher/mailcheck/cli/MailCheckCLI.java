/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.mailcheck.cli;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.ibatis.session.SqlSession;

import de.haumacher.mailcheck.DisposableListService;
import de.haumacher.mailcheck.EmailNormalizer;
import de.haumacher.mailcheck.PropertyStore;
import de.haumacher.mailcheck.db.MailCheckPropertyStore;
import de.haumacher.mailcheck.cli.model.HarvestedEmail;
import de.haumacher.mailcheck.db.DBDomainCheck;
import de.haumacher.mailcheck.db.DomainInsert;
import de.haumacher.mailcheck.db.DBMxStatus;
import de.haumacher.mailcheck.db.DBMxStatus.MxStatus;
import de.haumacher.mailcheck.model.DomainStatus;
import de.haumacher.mailcheck.db.DBEmailCheck;
import de.haumacher.mailcheck.db.Domains;
import de.haumacher.mailcheck.dns.MxLookup;
import de.haumacher.mailcheck.dns.MxResult;
import de.haumacher.mailcheck.scraper.DisposableScraperService;
import de.haumacher.msgbuf.json.JsonReader;
import de.haumacher.msgbuf.server.io.ReaderAdapter;

/**
 * Command-line interface for the fake-mail-check module.
 *
 * <pre>
 * Usage: java -jar fake-mail-check.jar [options] &lt;command&gt;
 *
 * Options:
 *   --db &lt;url&gt;         JDBC URL (default: jdbc:h2:./mailcheck)
 *   --user &lt;name&gt;      Database user (default: sa)
 *   --password &lt;pw&gt;    Database password (default: empty)
 *
 * Commands:
 *   init                          Initialize/upgrade the database schema
 *   import-list                   Download and import the GitHub disposable domain list
 *   scrape                        Run all web scrapers for disposable domains
 *   import-emails &lt;file.json&gt;     Import harvested emails from browser extension JSON export
 *   resolve-mx [--all]             Resolve missing (or all) MX records in DOMAIN_CHECK via DNS
 *   rebuild-mx                    Rebuild MX_HOST_STATUS and MX_IP_STATUS from DOMAIN_CHECK
 *   check &lt;email-or-domain&gt;       Check if an email/domain is disposable
 *   stats                         Show database statistics
 * </pre>
 */
public class MailCheckCLI {

	public static void main(String[] args) throws Exception {
		String dbUrl = "jdbc:h2:./mailcheck";
		String dbUser = "sa";
		String dbPassword = "";
		String command = null;
		String checkArg = null;
		boolean allFlag = false;

		// Parse arguments (options can appear anywhere).
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			if ("--db".equals(arg)) {
				i++;
				if (i >= args.length) {
					System.err.println("Missing value for --db option.");
					printUsage();
					System.exit(1);
				}
				dbUrl = args[i];
			} else if ("--user".equals(arg)) {
				i++;
				if (i >= args.length) {
					System.err.println("Missing value for --user option.");
					printUsage();
					System.exit(1);
				}
				dbUser = args[i];
			} else if ("--password".equals(arg)) {
				i++;
				if (i >= args.length) {
					System.err.println("Missing value for --password option.");
					printUsage();
					System.exit(1);
				}
				dbPassword = args[i];
			} else if ("--all".equals(arg)) {
				allFlag = true;
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

		DbConfig dbConfig = new DbConfig(dbUrl, dbUser, dbPassword);

		switch (command) {
			case "init":
				runInit(dbConfig);
				break;
			case "import-list":
				runImportList(dbConfig);
				break;
			case "scrape":
				runScrape(dbConfig);
				break;
			case "import-emails":
				if (checkArg == null) {
					System.err.println("Missing argument for 'import-emails' command.");
					printUsage();
					System.exit(1);
				}
				runImportEmails(dbConfig, checkArg);
				break;
			case "resolve-mx":
				runResolveMx(dbConfig, allFlag);
				break;
			case "rebuild-mx":
				runRebuildMx(dbConfig);
				break;
			case "check":
				if (checkArg == null) {
					System.err.println("Missing argument for 'check' command.");
					printUsage();
					System.exit(1);
				}
				runCheck(dbConfig, checkArg);
				break;
			case "stats":
				runStats(dbConfig);
				break;
			default:
				System.err.println("Unknown command: " + command);
				printUsage();
				System.exit(1);
		}
	}

	private record DbConfig(String url, String user, String password) {
		MailCheckDB open() throws java.sql.SQLException {
			return new MailCheckDB(url, user, password);
		}
	}

	private static void runInit(DbConfig dbConfig) throws Exception {
		try (MailCheckDB db = dbConfig.open()) {
			System.out.println("Database initialized at: " + dbConfig.url());
		}
	}

	private static void runImportList(DbConfig dbConfig) throws Exception {
		try (MailCheckDB db = dbConfig.open()) {
			PropertyStore propertyStore = new MailCheckPropertyStore(db.getSessionFactory());

			ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
			try {
				DisposableListService service = new DisposableListService(() -> scheduler, db.getSessionFactory(), propertyStore);
				service.runImport();
			} finally {
				scheduler.shutdownNow();
			}

			System.out.println("Import completed.");
		}
	}

	private static void runScrape(DbConfig dbConfig) throws Exception {
		try (MailCheckDB db = dbConfig.open()) {
			ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
			try {
				DisposableScraperService service = new DisposableScraperService(() -> scheduler, db.getSessionFactory());
				service.runScrape();
			} finally {
				scheduler.shutdownNow();
			}

			System.out.println("Scraping completed.");
		}
	}

	private static final int RESOLVE_THREADS = 20;

	private record MxEntry(DBDomainCheck domain, MxResult mx) {}

	private static void runResolveMx(DbConfig dbConfig, boolean all) throws Exception {
		try (MailCheckDB db = dbConfig.open()) {
			List<DBDomainCheck> domains;
			try (SqlSession session = db.getSessionFactory().openSession()) {
				Domains mapper = session.getMapper(Domains.class);
				domains = all ? mapper.findAllDomains() : mapper.findDomainsWithoutMx();
			}

			System.out.println((all ? "All domains" : "Domains without MX data") + ": " + domains.size());
			int total = domains.size();

			// Parallel DNS resolution.
			AtomicInteger lookupCount = new AtomicInteger();
			ExecutorService executor = Executors.newFixedThreadPool(RESOLVE_THREADS);
			List<Future<MxEntry>> futures = new ArrayList<>(total);

			for (DBDomainCheck domain : domains) {
				futures.add(executor.submit(() -> {
					MxEntry entry = new MxEntry(domain, MxLookup.lookup(domain.getDomainName()));
					int done = lookupCount.incrementAndGet();
					if (done % 100 == 0) {
						System.out.printf("  DNS lookup: %d / %d%n", done, total);
					}
					return entry;
				}));
			}
			executor.shutdown();

			// Process results and write to DB.
			long now = System.currentTimeMillis();
			int resolved = 0;
			int failed = 0;

			try (SqlSession session = db.getSessionFactory().openSession()) {
				Domains mapper = session.getMapper(Domains.class);

				for (int i = 0; i < total; i++) {
					MxEntry entry = futures.get(i).get();
					String name = entry.domain().getDomainName();
					MxResult mx = entry.mx();

					if (mx.mxHost() == null && mx.mxIp() == null) {
						mapper.updateDomainMx(name, "-", null);
						failed++;
					} else {
						mapper.updateDomainMx(name, mx.mxHost(), mx.mxIp());
						updateMxStatus(mapper, mx, entry.domain().getStatus() == DomainStatus.DISPOSABLE, now);
						resolved++;
					}

					if ((i + 1) % 100 == 0) {
						session.commit();
						System.out.printf("  %d / %d — resolved: %d, failed: %d%n", i + 1, total, resolved, failed);
					}
				}

				session.commit();
			}

			System.out.println("Done: " + resolved + " resolved, " + failed + " failed (no MX record).");
		}
	}

	private static void runRebuildMx(DbConfig dbConfig) throws Exception {
		try (MailCheckDB db = dbConfig.open()) {
			try (SqlSession session = db.getSessionFactory().openSession()) {
				Domains domains = session.getMapper(Domains.class);

				String disposable = MxStatus.disposable.name();
				String safe = MxStatus.safe.name();
				String mixed = MxStatus.mixed.name();

				// Clear existing MX status tables.
				int hostCleared = domains.clearMxHostStatus();
				int ipCleared = domains.clearMxIpStatus();
				System.out.println("Cleared MX_HOST_STATUS (" + hostCleared + " rows) and MX_IP_STATUS (" + ipCleared + " rows).");

				// Re-aggregate from DOMAIN_CHECK.
				int hostRows = domains.aggregateMxHostStatus(disposable, safe, mixed);
				System.out.println("MX_HOST_STATUS: " + hostRows + " entries.");

				int ipRows = domains.aggregateMxIpStatus(disposable, safe, mixed);
				System.out.println("MX_IP_STATUS: " + ipRows + " entries.");

				session.commit();
				System.out.println("MX status tables rebuilt.");
			}
		}
	}

	private static void updateMxStatus(Domains domains, MxResult mx, boolean disposable, long now) {
		if (mx.mxHost() != null) {
			DBMxStatus existing = domains.checkMxHost(mx.mxHost());
			if (existing == null) {
				domains.insertMxHost(mx.mxHost(), mx.mxIp(), MxStatus.of(disposable).name(), now);
			} else {
				MxStatus merged = existing.getStatus().merge(disposable);
				if (merged != existing.getStatus()) {
					domains.updateMxHostStatus(mx.mxHost(), merged.name(), now);
				}
			}
		}
		if (mx.mxIp() != null) {
			DBMxStatus existing = domains.checkMxIp(mx.mxIp());
			if (existing == null) {
				domains.insertMxIp(mx.mxIp(), MxStatus.of(disposable).name(), now);
			} else {
				MxStatus merged = existing.getStatus().merge(disposable);
				if (merged != existing.getStatus()) {
					domains.updateMxIpStatus(mx.mxIp(), merged.name(), now);
				}
			}
		}
	}

	private static void runImportEmails(DbConfig dbConfig, String filePath) throws Exception {
		Path file = Paths.get(filePath);
		if (!Files.exists(file)) {
			System.err.println("File not found: " + filePath);
			System.exit(1);
		}

		List<HarvestedEmail> entries = new java.util.ArrayList<>();
		try (Reader reader = Files.newBufferedReader(file)) {
			JsonReader json = new JsonReader(new ReaderAdapter(reader));
			json.beginArray();
			while (json.hasNext()) {
				entries.add(HarvestedEmail.readHarvestedEmail(json));
			}
			json.endArray();
		}

		System.out.println("Loaded " + entries.size() + " entries from " + file.getFileName());

		long now = System.currentTimeMillis();
		int emailsAdded = 0;
		int domainsAdded = 0;
		int skipped = 0;

		try (MailCheckDB db = dbConfig.open()) {
			try (SqlSession session = db.getSessionFactory().openSession()) {
				Domains domains = session.getMapper(Domains.class);

				for (HarvestedEmail entry : entries) {
					String email = entry.getEmail();
					String domain = entry.getDomain();
					String source = entry.getSource();

					// For known public providers, insert normalized email.
					String normalized = EmailNormalizer.toCanonicalPublicAddress(email);
					if (normalized != null) {
						if (domains.checkEmailAddress(normalized) == null) {
							domains.insertEmailCheck(normalized, true, now, source);
							emailsAdded++;
						} else {
							skipped++;
						}
					} else {
						// Unknown domain — insert as disposable domain.
						if (domains.checkDomain(domain) == null) {
							DomainInsert.insertWithMxLookup(domains, domain, DomainStatus.DISPOSABLE, now, source);
							domainsAdded++;
						}
					}

					if ((emailsAdded + domainsAdded) % 100 == 0 && (emailsAdded + domainsAdded) > 0) {
						session.commit();
					}
				}

				session.commit();
			}
		}

		System.out.println("Import complete: " + emailsAdded + " emails added, "
			+ domainsAdded + " domains added, " + skipped + " duplicates skipped.");
	}

	private static void runCheck(DbConfig dbConfig, String arg) throws Exception {
		try (MailCheckDB db = dbConfig.open()) {
			try (SqlSession session = db.getSessionFactory().openSession()) {
				Domains domains = session.getMapper(Domains.class);

				if (arg.contains("@")) {
					// E-mail address check.
					String normalized = EmailNormalizer.toCanonicalPublicAddress(arg);
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
			switch (check.getStatus()) {
				case DISPOSABLE:
					System.out.println("DISPOSABLE — " + domain + " (source: " + check.getSourceSystem() + ")");
					break;
				case INVALID:
					System.out.println("INVALID — " + domain + " has no valid MX record (source: " + check.getSourceSystem() + ")");
					break;
				case SAFE:
					System.out.println("OK — " + domain + " is not disposable (source: " + check.getSourceSystem() + ")");
					break;
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

	private static void runStats(DbConfig dbConfig) throws Exception {
		try (MailCheckDB db = dbConfig.open()) {
			try (SqlSession session = db.getSessionFactory().openSession()) {
				Connection conn = session.getConnection();
				try (Statement stmt = conn.createStatement()) {
					try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM DOMAIN_CHECK")) {
						rs.next();
						System.out.println("Domains in DOMAIN_CHECK: " + rs.getLong(1));
					}
					try (ResultSet rs = stmt.executeQuery(
							"SELECT STATUS, COUNT(*) AS CNT FROM DOMAIN_CHECK GROUP BY STATUS ORDER BY CNT DESC")) {
						while (rs.next()) {
							System.out.println("  " + rs.getString(1) + ": " + rs.getLong(2));
						}
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
		System.err.println("Usage: java -jar fake-mail-check.jar [options] <command>");
		System.err.println();
		System.err.println("Options:");
		System.err.println("  --db <url>         JDBC URL (default: jdbc:h2:./mailcheck)");
		System.err.println("  --user <name>      Database user (default: sa)");
		System.err.println("  --password <pw>    Database password (default: empty)");
		System.err.println();
		System.err.println("Commands:");
		System.err.println("  init                          Initialize/upgrade the database schema");
		System.err.println("  import-list                   Download and import the GitHub disposable domain list");
		System.err.println("  scrape                        Run all web scrapers for disposable domains");
		System.err.println("  import-emails <file.json>     Import harvested emails from browser extension export");
		System.err.println("  resolve-mx [--all]             Resolve missing (or all) MX records via DNS");
		System.err.println("  rebuild-mx                    Rebuild MX status tables from DOMAIN_CHECK");
		System.err.println("  check <email-or-domain>       Check if an email/domain is disposable");
		System.err.println("  stats                         Show database statistics");
	}
}
