/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.tools.classifier;

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

public class ClassifierDB implements AutoCloseable {

	private static final Logger LOG = LoggerFactory.getLogger(ClassifierDB.class);

	private final JdbcConnectionPool _pool;
	private final SqlSessionFactory _sessionFactory;

	public ClassifierDB(String url, String user, String password) {
		LOG.info("Opening H2 database: {} as '{}'.", url, user);

		JdbcDataSource dataSource = new JdbcDataSource();
		dataSource.setUrl(url);
		dataSource.setUser(user);
		dataSource.setPassword(password);

		_pool = JdbcConnectionPool.create(dataSource);

		TransactionFactory transactionFactory = new JdbcTransactionFactory();
		Environment environment = new Environment("classifier", transactionFactory, _pool);

		Configuration configuration = new Configuration(environment);
		configuration.setMapUnderscoreToCamelCase(false);
		configuration.addMapper(Comments.class);

		_sessionFactory = new SqlSessionFactoryBuilder().build(configuration);
	}

	public SqlSession openSession() {
		return _sessionFactory.openSession();
	}

	@Override
	public void close() {
		if (_pool != null) {
			_pool.dispose();
		}
	}
}
