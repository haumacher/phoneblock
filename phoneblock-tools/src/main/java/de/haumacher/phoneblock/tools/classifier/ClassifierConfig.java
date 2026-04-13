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

	private String dbUrl = "jdbc:h2:./phoneblock";
	private String dbUser = "phone";
	private String dbPassword = "block";

	private String anthropicApiKey;
	private String anthropicModel = "claude-haiku-4-5";

	private int maxRequests = 500;
	private int batchSize = 10;
	private int goodThreshold = 5;
	private int minGoodForSummary = 3;

	/** Explicit phone IDs to process. If empty, candidates are chosen automatically. */
	private final List<String> phones = new ArrayList<>();

	public static ClassifierConfig load(String[] args) {
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
	public List<String> getPhones() { return phones; }
}
