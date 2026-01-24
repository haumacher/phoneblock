package de.haumacher.phoneblock.sync.storage;

import de.haumacher.msgbuf.json.JsonReader;
import de.haumacher.msgbuf.json.JsonWriter;
import de.haumacher.msgbuf.server.io.ReaderAdapter;
import de.haumacher.msgbuf.server.io.WriterAdapter;
import de.haumacher.phoneblock.app.api.model.Blocklist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * JSON file-based storage implementation for blocklists.
 */
public class JsonBlocklistStorage implements BlocklistStorage {
	private static final Logger LOG = LoggerFactory.getLogger(JsonBlocklistStorage.class);

	private final Path storageFile;
	private final boolean prettyPrint;

	/**
	 * Creates a JSON blocklist storage.
	 *
	 * @param storageFile Path to the JSON file.
	 * @param prettyPrint Whether to format JSON with indentation.
	 */
	public JsonBlocklistStorage(Path storageFile, boolean prettyPrint) {
		this.storageFile = storageFile;
		this.prettyPrint = prettyPrint;
	}

	@Override
	public Blocklist load() throws IOException {
		if (!exists()) {
			LOG.debug("Storage file does not exist: {}", storageFile);
			return null;
		}

		LOG.debug("Loading blocklist from: {}", storageFile);
		try (InputStream in = Files.newInputStream(storageFile);
		     JsonReader reader = new JsonReader(new ReaderAdapter(new InputStreamReader(in, UTF_8)))) {
			return Blocklist.readBlocklist(reader);
		}
	}

	@Override
	public void save(Blocklist blocklist) throws IOException {
		// Create parent directories if needed
		Path parent = storageFile.getParent();
		if (parent != null && !Files.exists(parent)) {
			Files.createDirectories(parent);
		}

		// Write to temporary file first (atomic write)
		Path tempFile = storageFile.resolveSibling(storageFile.getFileName() + ".tmp");

		LOG.debug("Saving blocklist to: {}", storageFile);
		try (OutputStream out = Files.newOutputStream(tempFile);
		     JsonWriter writer = new JsonWriter(new WriterAdapter(new OutputStreamWriter(out, UTF_8)))) {
			if (prettyPrint) {
				writer.setIndent("  ");
			}
			blocklist.writeTo(writer);
		}

		// Atomic move
		Files.move(tempFile, storageFile,
			StandardCopyOption.ATOMIC_MOVE,
			StandardCopyOption.REPLACE_EXISTING);

		LOG.info("Saved blocklist version {} with {} entries", blocklist.getVersion(), blocklist.getNumbers().size());
	}

	@Override
	public long getVersion() throws IOException {
		Blocklist blocklist = load();
		return blocklist != null ? blocklist.getVersion() : 0;
	}

	@Override
	public boolean exists() {
		return Files.exists(storageFile) && Files.isRegularFile(storageFile);
	}

	@Override
	public void delete() throws IOException {
		if (exists()) {
			Files.delete(storageFile);
			LOG.info("Deleted blocklist storage: {}", storageFile);
		}
	}

	public Path getStorageFile() {
		return storageFile;
	}
}
