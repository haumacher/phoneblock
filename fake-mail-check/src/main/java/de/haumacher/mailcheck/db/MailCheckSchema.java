/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.mailcheck.db;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.ibatis.jdbc.ScriptRunner;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central schema initialization and migration for the mail-check module.
 *
 * <p>
 * Call {@link #initialize(SqlSessionFactory)} once at startup — from
 * {@code MailCheckDB} (CLI), {@code EMailCheckService} (web), or any
 * other host application embedding the module.
 * </p>
 */
public class MailCheckSchema {

	private static final Logger LOG = LoggerFactory.getLogger(MailCheckSchema.class);

	/** Current schema version. Bump this when adding a new migration. */
	private static final int CURRENT_VERSION = 2;

	/**
	 * Initializes the mail-check schema: registers the MyBatis mapper,
	 * creates tables if needed, and applies pending migrations.
	 */
	public static void initialize(SqlSessionFactory sessionFactory) {
		Configuration cfg = sessionFactory.getConfiguration();
		if (!cfg.hasMapper(Domains.class)) {
			cfg.addMapper(Domains.class);
		}

		try (SqlSession session = sessionFactory.openSession()) {
			Connection connection = session.getConnection();

			// Create base schema (IF NOT EXISTS — safe to re-run).
			runScript(connection, "mail-check-schema.sql");

			// Check current version.
			int version = readVersion(connection);

			if (version == 0 && !hasData(connection)) {
				// Fresh install — set version to current, no migrations needed.
				setVersion(connection, CURRENT_VERSION);
				LOG.info("Mail-check schema initialized at version {}.", CURRENT_VERSION);
			} else {
				if (version == 0) {
					// Existing DB from before versioning was introduced.
					// Base schema (version 1) already exists, start migrations from 2.
					version = 1;
					setVersion(connection, version);
					LOG.info("Existing mail-check DB detected, starting migrations from version {}.", version);
				}
				// Apply pending migrations.
				while (version < CURRENT_VERSION) {
					version++;
					String scriptName = migrationScriptName(version);
					InputStream script = migrationScript(scriptName);
					if (script != null) {
						LOG.info("Running mail-check migration: {}", scriptName);
						runScript(connection, script);
					}
					setVersion(connection, version);
				}
				LOG.info("Mail-check schema up to date at version {}.", version);
			}
		}
	}

	/**
	 * Checks whether the DOMAIN_CHECK table already contains data,
	 * indicating a pre-versioning DB rather than a fresh install.
	 */
	private static boolean hasData(Connection connection) {
		try (Statement stmt = connection.createStatement()) {
			try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM DOMAIN_CHECK")) {
				return rs.next() && rs.getLong(1) > 0;
			}
		} catch (SQLException ex) {
			return false;
		}
	}

	private static int readVersion(Connection connection) {
		try (Statement stmt = connection.createStatement()) {
			try (ResultSet rs = stmt.executeQuery(
					"SELECT VAL FROM MAILCHECK_PROPERTIES WHERE NAME = 'schema.version'")) {
				if (rs.next()) {
					return Integer.parseInt(rs.getString(1));
				}
			}
		} catch (SQLException ex) {
			LOG.debug("Could not read schema version (table may not exist yet): {}", ex.getMessage());
		}
		return 0;
	}

	private static void setVersion(Connection connection, int version) {
		try (Statement stmt = connection.createStatement()) {
			int updated = stmt.executeUpdate(
				"UPDATE MAILCHECK_PROPERTIES SET VAL = '" + version + "' WHERE NAME = 'schema.version'");
			if (updated == 0) {
				stmt.executeUpdate(
					"INSERT INTO MAILCHECK_PROPERTIES (NAME, VAL) VALUES ('schema.version', '" + version + "')");
			}
			if (!connection.getAutoCommit()) {
				connection.commit();
			}
		} catch (SQLException ex) {
			LOG.error("Failed to set schema version to {}.", version, ex);
		}
	}

	private static String migrationScriptName(int version) {
		String versionId = Integer.toString(version);
		while (versionId.length() < 2) {
			versionId = "0" + versionId;
		}
		return "mailcheck-migration-" + versionId + ".sql";
	}

	private static InputStream migrationScript(String scriptName) {
		return MailCheckSchema.class.getResourceAsStream(scriptName);
	}

	private static void runScript(Connection connection, String resourceName) {
		try (InputStream in = MailCheckSchema.class.getResourceAsStream(resourceName)) {
			if (in == null) {
				LOG.error("Schema resource not found: {}", resourceName);
				return;
			}
			runScript(connection, in);
		} catch (IOException ex) {
			LOG.error("Failed to run schema script: {}", resourceName, ex);
		}
	}

	private static void runScript(Connection connection, InputStream in) {
		ScriptRunner sr = new ScriptRunner(connection);
		sr.setAutoCommit(true);
		sr.setDelimiter(";");
		sr.runScript(new InputStreamReader(in, StandardCharsets.UTF_8));
	}
}
