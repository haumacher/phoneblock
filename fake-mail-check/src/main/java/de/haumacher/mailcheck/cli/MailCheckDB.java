/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.mailcheck.cli;

import java.sql.SQLException;

import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcConnectionPool;
import org.h2.jdbcx.JdbcDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.mailcheck.db.MailCheckSchema;

/**
 * Standalone H2 database connection manager for the mail-check CLI.
 */
public class MailCheckDB implements AutoCloseable {

	private static final Logger LOG = LoggerFactory.getLogger(MailCheckDB.class);

	private final JdbcConnectionPool _pool;
	private final SqlSessionFactory _sessionFactory;

	/**
	 * Opens (or creates) an H2 database at the given path and initializes the
	 * mail-check schema.
	 *
	 * @param dbPath File path for the H2 database (without {@code .mv.db} suffix).
	 */
	public MailCheckDB(String dbPath) throws SQLException {
		String url = "jdbc:h2:" + dbPath;
		LOG.info("Opening H2 database: {}", url);

		JdbcDataSource dataSource = new JdbcDataSource();
		dataSource.setUrl(url);
		dataSource.setUser("sa");
		dataSource.setPassword("");

		_pool = JdbcConnectionPool.create(dataSource);

		Environment environment = new Environment("mailcheck", new JdbcTransactionFactory(), _pool);
		Configuration configuration = new Configuration(environment);

		_sessionFactory = new SqlSessionFactoryBuilder().build(configuration);

		MailCheckSchema.initialize(_sessionFactory);

		LOG.info("Database ready.");
	}

	/**
	 * The MyBatis session factory for this database.
	 */
	public SqlSessionFactory getSessionFactory() {
		return _sessionFactory;
	}

	@Override
	public void close() {
		if (_pool != null) {
			LOG.info("Closing database connection.");
			_pool.dispose();
		}
	}
}
