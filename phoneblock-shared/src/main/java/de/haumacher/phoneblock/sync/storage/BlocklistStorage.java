package de.haumacher.phoneblock.sync.storage;

import de.haumacher.phoneblock.app.api.model.Blocklist;

import java.io.IOException;

/**
 * Storage interface for blocklist persistence.
 */
public interface BlocklistStorage {
	/**
	 * Loads the blocklist from storage.
	 *
	 * @return The loaded blocklist, or null if no blocklist exists.
	 * @throws IOException If loading fails.
	 */
	Blocklist load() throws IOException;

	/**
	 * Saves the blocklist to storage.
	 *
	 * @param blocklist The blocklist to save.
	 * @throws IOException If saving fails.
	 */
	void save(Blocklist blocklist) throws IOException;

	/**
	 * Gets the current version from storage.
	 *
	 * @return The current version, or 0 if no blocklist exists.
	 * @throws IOException If reading the version fails.
	 */
	long getVersion() throws IOException;

	/**
	 * Checks if a blocklist exists in storage.
	 *
	 * @return true if a blocklist exists, false otherwise.
	 */
	boolean exists();

	/**
	 * Deletes the blocklist from storage.
	 *
	 * @throws IOException If deletion fails.
	 */
	void delete() throws IOException;
}
