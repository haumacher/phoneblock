/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.accounting.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration for the accounting importer tool.
 */
public class AccountingConfig {

	private static final Logger LOG = LoggerFactory.getLogger(AccountingConfig.class);

	private static final String DEFAULT_CONFIG_FILE = System.getProperty("user.home") + "/.phoneblock-accounting";
	private static final String DEFAULT_CHARSET = "ISO-8859-1";
	private static final String DEFAULT_DB_URL = "jdbc:h2:./phoneblock";
	private static final String DEFAULT_DB_USER = "phone";
	private static final String DEFAULT_DB_PASSWORD = "block";

	private String csvFile;
	private Charset charset;
	private String dbUrl;
	private String dbUser;
	private String dbPassword;

	/**
	 * Creates a new configuration with default values.
	 */
	public AccountingConfig() {
		this.charset = Charset.forName(DEFAULT_CHARSET);
		this.dbUrl = DEFAULT_DB_URL;
		this.dbUser = DEFAULT_DB_USER;
		this.dbPassword = DEFAULT_DB_PASSWORD;
	}

	/**
	 * Loads configuration from the default config file if it exists.
	 *
	 * @return The configuration loaded from file or defaults
	 */
	public static AccountingConfig loadDefault() {
		return loadFromFile(DEFAULT_CONFIG_FILE, true);
	}

	/**
	 * Loads configuration from a specific file.
	 *
	 * @param configFile Path to the configuration file
	 * @param optional If true, missing file is not an error
	 * @return The configuration loaded from file or defaults
	 */
	public static AccountingConfig loadFromFile(String configFile, boolean optional) {
		AccountingConfig config = new AccountingConfig();

		File file = new File(configFile);
		if (!file.exists()) {
			if (optional) {
				LOG.debug("Config file not found (optional): {}", configFile);
			} else {
				LOG.warn("Config file not found: {}", configFile);
			}
			return config;
		}

		try (InputStream in = new FileInputStream(file)) {
			Properties props = new Properties();
			props.load(in);

			if (props.containsKey("charset")) {
				try {
					config.charset = Charset.forName(props.getProperty("charset"));
				} catch (Exception e) {
					LOG.warn("Invalid charset in config: {}", props.getProperty("charset"));
				}
			}

			if (props.containsKey("db.url")) {
				config.dbUrl = props.getProperty("db.url");
			}

			if (props.containsKey("db.user")) {
				config.dbUser = props.getProperty("db.user");
			}

			if (props.containsKey("db.password")) {
				config.dbPassword = props.getProperty("db.password");
			}

			LOG.info("Loaded configuration from: {}", configFile);
		} catch (IOException e) {
			LOG.error("Failed to load config file: {}", configFile, e);
		}

		return config;
	}

	/**
	 * Parses command-line arguments and applies them to this configuration.
	 *
	 * @param args Command-line arguments
	 * @return true if parsing was successful, false if help should be shown
	 */
	public boolean parseArguments(String[] args) {
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];

			if (arg.equals("-h") || arg.equals("--help")) {
				return false;
			}

			if (arg.equals("-f") || arg.equals("--file")) {
				if (i + 1 >= args.length) {
					LOG.error("Missing value for {}", arg);
					return false;
				}
				this.csvFile = args[++i];
			} else if (arg.equals("--charset")) {
				if (i + 1 >= args.length) {
					LOG.error("Missing value for {}", arg);
					return false;
				}
				try {
					this.charset = Charset.forName(args[++i]);
				} catch (Exception e) {
					LOG.error("Invalid charset: {}", args[i]);
					return false;
				}
			} else if (arg.equals("--db-url")) {
				if (i + 1 >= args.length) {
					LOG.error("Missing value for {}", arg);
					return false;
				}
				this.dbUrl = args[++i];
			} else if (arg.equals("--db-user")) {
				if (i + 1 >= args.length) {
					LOG.error("Missing value for {}", arg);
					return false;
				}
				this.dbUser = args[++i];
			} else if (arg.equals("--db-password")) {
				if (i + 1 >= args.length) {
					LOG.error("Missing value for {}", arg);
					return false;
				}
				this.dbPassword = args[++i];
			} else if (!arg.startsWith("-")) {
				// Positional argument - treat as CSV file
				if (this.csvFile == null) {
					this.csvFile = arg;
				} else {
					LOG.error("Multiple CSV files specified: {} and {}", this.csvFile, arg);
					return false;
				}
			} else {
				LOG.error("Unknown option: {}", arg);
				return false;
			}
		}

		return true;
	}

	public String getCsvFile() {
		return csvFile;
	}

	public void setCsvFile(String csvFile) {
		this.csvFile = csvFile;
	}

	public Charset getCharset() {
		return charset;
	}

	public void setCharset(Charset charset) {
		this.charset = charset;
	}

	public String getDbUrl() {
		return dbUrl;
	}

	public void setDbUrl(String dbUrl) {
		this.dbUrl = dbUrl;
	}

	public String getDbUser() {
		return dbUser;
	}

	public void setDbUser(String dbUser) {
		this.dbUser = dbUser;
	}

	public String getDbPassword() {
		return dbPassword;
	}

	public void setDbPassword(String dbPassword) {
		this.dbPassword = dbPassword;
	}

	/**
	 * Validates that required configuration is present.
	 *
	 * @return true if configuration is valid
	 */
	public boolean isValid() {
		return csvFile != null && !csvFile.isEmpty();
	}
}
