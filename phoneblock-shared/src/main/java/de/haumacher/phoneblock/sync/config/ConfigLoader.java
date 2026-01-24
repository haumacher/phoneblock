package de.haumacher.phoneblock.sync.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Loads synchronization configuration from properties files.
 */
public class ConfigLoader {
	private static final Logger LOG = LoggerFactory.getLogger(ConfigLoader.class);

	// Property keys
	private static final String PROP_API_URL = "sync.api.url";
	private static final String PROP_BEARER_TOKEN = "sync.bearer.token";
	private static final String PROP_STORAGE_FILE = "sync.storage.file";
	private static final String PROP_MIN_VOTES = "sync.min.votes";
	private static final String PROP_PRETTY_PRINT = "sync.pretty.print";
	private static final String PROP_CONNECTION_TIMEOUT = "sync.connection.timeout";

	/**
	 * Loads configuration from a properties file.
	 *
	 * @param configFile Path to the properties file.
	 * @return The loaded configuration.
	 * @throws IOException If loading fails.
	 */
	public static SyncConfig loadFromFile(Path configFile) throws IOException {
		if (!Files.exists(configFile)) {
			throw new IOException("Configuration file not found: " + configFile);
		}

		LOG.debug("Loading configuration from: {}", configFile);

		Properties props = new Properties();
		try (InputStream in = Files.newInputStream(configFile)) {
			props.load(in);
		}

		return loadFromProperties(props);
	}

	/**
	 * Loads configuration from a Properties object.
	 *
	 * @param props The properties.
	 * @return The loaded configuration.
	 */
	public static SyncConfig loadFromProperties(Properties props) {
		SyncConfig config = new SyncConfig();

		if (props.containsKey(PROP_API_URL)) {
			config.setApiBaseUrl(props.getProperty(PROP_API_URL));
		}

		if (props.containsKey(PROP_BEARER_TOKEN)) {
			config.setBearerToken(props.getProperty(PROP_BEARER_TOKEN));
		}

		if (props.containsKey(PROP_STORAGE_FILE)) {
			config.setStorageFile(Paths.get(props.getProperty(PROP_STORAGE_FILE)));
		}

		if (props.containsKey(PROP_MIN_VOTES)) {
			config.setMinVotes(Integer.parseInt(props.getProperty(PROP_MIN_VOTES)));
		}

		if (props.containsKey(PROP_PRETTY_PRINT)) {
			config.setPrettyPrint(Boolean.parseBoolean(props.getProperty(PROP_PRETTY_PRINT)));
		}

		if (props.containsKey(PROP_CONNECTION_TIMEOUT)) {
			config.setConnectionTimeout(Integer.parseInt(props.getProperty(PROP_CONNECTION_TIMEOUT)));
		}

		return config;
	}

	/**
	 * Creates a default configuration (for testing or CLI defaults).
	 *
	 * @return A new configuration with default values.
	 */
	public static SyncConfig createDefault() {
		return new SyncConfig();
	}
}
