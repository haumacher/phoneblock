/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.tools.classifier;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration for the comment classifier / summarizer tool.
 */
public class ClassifierConfig {

	private static final Logger LOG = LoggerFactory.getLogger(ClassifierConfig.class);

	private static final String DEFAULT_CONFIG_FILE = System.getProperty("user.home") + "/.phoneblock-classifier";

	/** Single source of truth for defaults — referenced by fields and by {@link #printUsage}. */
	static final String DEFAULT_DB_URL = "jdbc:h2:./phoneblock";
	static final String DEFAULT_DB_USER = "phone";
	static final String DEFAULT_DB_PASSWORD = "block";
	static final String DEFAULT_MODEL = "claude-haiku-4-5";
	static final int DEFAULT_MAX_REQUESTS = 500;
	static final int DEFAULT_BATCH_SIZE = 10;
	static final int DEFAULT_GOOD_THRESHOLD = 5;
	static final int DEFAULT_MIN_GOOD_FOR_SUMMARY = 3;
	static final int DEFAULT_MIN_COMMENTS = 5;

	private String dbUrl = DEFAULT_DB_URL;
	private String dbUser = DEFAULT_DB_USER;
	private String dbPassword = DEFAULT_DB_PASSWORD;

	private String anthropicApiKey;
	private String anthropicModel = DEFAULT_MODEL;

	private int maxRequests = DEFAULT_MAX_REQUESTS;
	private int batchSize = DEFAULT_BATCH_SIZE;
	private int goodThreshold = DEFAULT_GOOD_THRESHOLD;
	private int minGoodForSummary = DEFAULT_MIN_GOOD_FOR_SUMMARY;
	private int minComments = DEFAULT_MIN_COMMENTS;

	/** Explicit phone IDs to process. If empty, candidates are chosen automatically. */
	private final List<String> phones = new ArrayList<>();

	public static ClassifierConfig load(String[] args) {
		for (String arg : args) {
			if ("-h".equals(arg) || "--help".equals(arg)) {
				printUsage(System.out);
				return null;
			}
		}

		ClassifierConfig config = new ClassifierConfig();

		String configFile = DEFAULT_CONFIG_FILE;
		for (int i = 0; i < args.length; i++) {
			if ("-c".equals(args[i]) || "--config".equals(args[i])) {
				if (i + 1 >= args.length) {
					throw new IllegalArgumentException("Missing value for " + args[i]);
				}
				configFile = args[++i];
			}
		}
		config.loadFile(configFile);
		config.parseArgs(args);
		return config;
	}

	public static void printUsage(java.io.PrintStream out) {
		out.println(("""
				Usage: phoneblock-classifier [options]

				Classifies unclassified COMMENTS rows via Anthropic, then summarizes phone
				numbers that now have enough GOOD comments. Whitelisted numbers are skipped.

				Options:
				  -h, --help                 Show this help and exit.
				  -c, --config <file>        Config file (default: ~/.phoneblock-classifier).

				  --db-url <jdbc-url>        JDBC URL (default: %s).
				  --db-user <user>           Database user (default: %s).
				  --db-password <pwd>        Database password (default: %s).

				  --anthropic-key <key>      Anthropic API key (required).
				  --model <id>               Model ID (default: %s).

				  --max-requests <n>         Max LLM requests per run (default: %d).
				  --batch-size <n>           Comments per classification batch (default: %d).
				  --good-threshold <n>       Stop classifying a phone after this many GOODs
				                             (default: %d).
				  --min-good <n>             Min GOOD comments required to run a summary
				                             (default: %d).
				  --min-comments <n>         Skip phones with fewer than this many
				                             unclassified comments (default: %d).

				  --phone <phoneId>          Only process this phone ID. May be repeated.
				  --phones <id1,id2,...>     Comma-separated list of phone IDs.

				Phone IDs use the internal DB format: German numbers as "0xxx...",
				international numbers as "00<country><number>".
				""").formatted(
						DEFAULT_DB_URL, DEFAULT_DB_USER, DEFAULT_DB_PASSWORD, DEFAULT_MODEL,
						DEFAULT_MAX_REQUESTS, DEFAULT_BATCH_SIZE,
						DEFAULT_GOOD_THRESHOLD, DEFAULT_MIN_GOOD_FOR_SUMMARY,
						DEFAULT_MIN_COMMENTS));
	}

	private void loadFile(String configFile) {
		File file = new File(configFile);
		if (!file.exists()) {
			LOG.warn("Config file not found: {}", configFile);
			return;
		}
		try (InputStream in = new FileInputStream(file)) {
			Properties props = new Properties();
			props.load(in);
			dbUrl = props.getProperty("db.url", dbUrl);
			dbUser = props.getProperty("db.user", dbUser);
			dbPassword = props.getProperty("db.password", dbPassword);
			anthropicApiKey = props.getProperty("anthropic.api-key", anthropicApiKey);
			anthropicModel = props.getProperty("anthropic.model", anthropicModel);
			LOG.info("Loaded configuration from: {}", configFile);
		} catch (IOException ex) {
			LOG.error("Failed to load config file: {}", configFile, ex);
		}
	}

	private void parseArgs(String[] args) {
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			switch (arg) {
				case "-c":
				case "--config":
					i++;
					break;
				case "--db-url":
					dbUrl = args[++i];
					break;
				case "--db-user":
					dbUser = args[++i];
					break;
				case "--db-password":
					dbPassword = args[++i];
					break;
				case "--anthropic-key":
					anthropicApiKey = args[++i];
					break;
				case "--model":
					anthropicModel = args[++i];
					break;
				case "--max-requests":
					maxRequests = Integer.parseInt(args[++i]);
					break;
				case "--batch-size":
					batchSize = Integer.parseInt(args[++i]);
					break;
				case "--good-threshold":
					goodThreshold = Integer.parseInt(args[++i]);
					break;
				case "--min-good":
					minGoodForSummary = Integer.parseInt(args[++i]);
					break;
				case "--min-comments":
					minComments = Integer.parseInt(args[++i]);
					break;
				case "--phone":
					phones.add(args[++i]);
					break;
				case "--phones":
					for (String p : args[++i].split(",")) {
						String trimmed = p.trim();
						if (!trimmed.isEmpty()) phones.add(trimmed);
					}
					break;
				default:
					if (arg.startsWith("-")) {
						throw new IllegalArgumentException("Unknown option: " + arg);
					}
			}
		}
	}

	public String getDbUrl() { return dbUrl; }
	public String getDbUser() { return dbUser; }
	public String getDbPassword() { return dbPassword; }
	public String getAnthropicApiKey() { return anthropicApiKey; }
	public String getAnthropicModel() { return anthropicModel; }
	public int getMaxRequests() { return maxRequests; }
	public int getBatchSize() { return batchSize; }
	public int getGoodThreshold() { return goodThreshold; }
	public int getMinGoodForSummary() { return minGoodForSummary; }
	public int getMinComments() { return minComments; }
	public List<String> getPhones() { return phones; }
}
