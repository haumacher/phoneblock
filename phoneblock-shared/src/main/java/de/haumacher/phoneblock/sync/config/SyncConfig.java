package de.haumacher.phoneblock.sync.config;

import java.nio.file.Path;

/**
 * Configuration for blocklist synchronization.
 */
public class SyncConfig {
	private String apiBaseUrl = "https://phoneblock.net/phoneblock";
	private String bearerToken;
	private Path storageFile;
	private int minVotes = 4;
	private boolean prettyPrint = false;
	private int connectionTimeout = 30;

	/**
	 * Gets the base API URL (without /api/blocklist suffix).
	 */
	public String getApiBaseUrl() {
		return apiBaseUrl;
	}

	/**
	 * Sets the base API URL.
	 */
	public void setApiBaseUrl(String apiBaseUrl) {
		this.apiBaseUrl = apiBaseUrl;
	}

	/**
	 * Gets the bearer token for API authentication.
	 */
	public String getBearerToken() {
		return bearerToken;
	}

	/**
	 * Sets the bearer token.
	 */
	public void setBearerToken(String bearerToken) {
		this.bearerToken = bearerToken;
	}

	/**
	 * Gets the path to the storage file.
	 */
	public Path getStorageFile() {
		return storageFile;
	}

	/**
	 * Sets the storage file path.
	 */
	public void setStorageFile(Path storageFile) {
		this.storageFile = storageFile;
	}

	/**
	 * Gets the minimum number of votes required for an entry.
	 */
	public int getMinVotes() {
		return minVotes;
	}

	/**
	 * Sets the minimum votes threshold.
	 */
	public void setMinVotes(int minVotes) {
		if (minVotes < 0) {
			throw new IllegalArgumentException("minVotes cannot be negative");
		}
		this.minVotes = minVotes;
	}

	/**
	 * Whether to format JSON output with indentation.
	 */
	public boolean isPrettyPrint() {
		return prettyPrint;
	}

	/**
	 * Sets the pretty print flag.
	 */
	public void setPrettyPrint(boolean prettyPrint) {
		this.prettyPrint = prettyPrint;
	}

	/**
	 * Gets the connection timeout in seconds.
	 */
	public int getConnectionTimeout() {
		return connectionTimeout;
	}

	/**
	 * Sets the connection timeout.
	 */
	public void setConnectionTimeout(int connectionTimeout) {
		if (connectionTimeout <= 0) {
			throw new IllegalArgumentException("connectionTimeout must be positive");
		}
		this.connectionTimeout = connectionTimeout;
	}

	/**
	 * Validates the configuration.
	 *
	 * @throws IllegalStateException If the configuration is invalid.
	 */
	public void validate() {
		if (bearerToken == null || bearerToken.trim().isEmpty()) {
			throw new IllegalStateException("Bearer token is required");
		}
		if (storageFile == null) {
			throw new IllegalStateException("Storage file path is required");
		}
	}
}
