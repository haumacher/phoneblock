/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.accounting.db;

import java.sql.SQLException;

import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcConnectionPool;
import org.h2.jdbcx.JdbcDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Database connection manager for the accounting application.
 */
public class AccountingDB implements AutoCloseable {

	private static final Logger LOG = LoggerFactory.getLogger(AccountingDB.class);

	private final JdbcConnectionPool _pool;
	private final SqlSessionFactory _sessionFactory;

	/**
	 * Creates a new database connection.
	 *
	 * @param url The JDBC URL (e.g., "jdbc:h2:/path/to/database")
	 * @param user The database user
	 * @param password The database password
	 * @throws SQLException If connection fails
	 */
	public AccountingDB(String url, String user, String password) throws SQLException {
		LOG.info("Opening H2 database: {} for user '{}'", url, user);

		JdbcDataSource dataSource = new JdbcDataSource();
		dataSource.setUrl(url);
		dataSource.setUser(user);
		dataSource.setPassword(password);

		_pool = JdbcConnectionPool.create(dataSource);

		// Configure MyBatis
		TransactionFactory transactionFactory = new JdbcTransactionFactory();
		Environment environment = new Environment("accounting", transactionFactory, _pool);

		Configuration configuration = new Configuration(environment);
		configuration.addMapper(Contributions.class);
		configuration.addMapper(Users.class);

		_sessionFactory = new SqlSessionFactoryBuilder().build(configuration);

		LOG.info("Database connection established successfully");
	}

	/**
	 * Opens a new SQL session for database operations.
	 *
	 * @return A new SQL session
	 */
	public SqlSession openSession() {
		return _sessionFactory.openSession();
	}

	/**
	 * Gets the Contributions mapper for database operations.
	 *
	 * @return The Contributions mapper
	 */
	public Contributions getContributions() {
		SqlSession session = openSession();
		return session.getMapper(Contributions.class);
	}

	@Override
	public void close() {
		if (_pool != null) {
			LOG.info("Closing database connection");
			_pool.dispose();
		}
	}
}
