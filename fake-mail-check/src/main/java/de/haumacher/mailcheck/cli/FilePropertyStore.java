/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.mailcheck.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.mailcheck.PropertyStore;

/**
 * File-based {@link PropertyStore} that persists properties in a
 * {@code .properties} file next to the H2 database.
 */
public class FilePropertyStore implements PropertyStore {

	private static final Logger LOG = LoggerFactory.getLogger(FilePropertyStore.class);

	private final Path _file;
	private final Properties _properties = new Properties();

	/**
	 * Creates a {@link FilePropertyStore} backed by the given file.
	 */
	public FilePropertyStore(Path file) {
		_file = file;
		if (Files.exists(file)) {
			try (InputStream in = Files.newInputStream(file)) {
				_properties.load(in);
			} catch (IOException ex) {
				LOG.warn("Failed to load properties from '{}': {}", file, ex.getMessage());
			}
		}
	}

	@Override
	public String getProperty(String key) {
		return _properties.getProperty(key);
	}

	@Override
	public void setProperty(String key, String value) {
		_properties.setProperty(key, value);
		try (OutputStream out = Files.newOutputStream(_file)) {
			_properties.store(out, "mailcheck CLI properties");
		} catch (IOException ex) {
			LOG.error("Failed to save properties to '{}': {}", _file, ex.getMessage());
		}
	}
}
