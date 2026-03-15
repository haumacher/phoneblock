/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.mail;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import de.haumacher.phoneblock.db.Users;
import de.haumacher.phoneblock.mail.check.PropertyStore;

/**
 * {@link PropertyStore} backed by the {@link Users} MyBatis mapper.
 */
public class DBPropertyStore implements PropertyStore {

	private final SqlSessionFactory _sessionFactory;

	/**
	 * Creates a {@link DBPropertyStore}.
	 */
	public DBPropertyStore(SqlSessionFactory sessionFactory) {
		_sessionFactory = sessionFactory;
	}

	@Override
	public String getProperty(String key) {
		try (SqlSession session = _sessionFactory.openSession()) {
			return session.getMapper(Users.class).getProperty(key);
		}
	}

	@Override
	public void setProperty(String key, String value) {
		try (SqlSession session = _sessionFactory.openSession()) {
			Users users = session.getMapper(Users.class);
			if (users.updateProperty(key, value) == 0) {
				users.addProperty(key, value);
			}
			session.commit();
		}
	}

}
