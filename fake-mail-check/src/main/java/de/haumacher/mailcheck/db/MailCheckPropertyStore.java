/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.mailcheck.db;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import de.haumacher.mailcheck.PropertyStore;

/**
 * {@link PropertyStore} backed by the {@code MAILCHECK_PROPERTIES} table.
 */
public class MailCheckPropertyStore implements PropertyStore {

	private final SqlSessionFactory _sessionFactory;

	public MailCheckPropertyStore(SqlSessionFactory sessionFactory) {
		_sessionFactory = sessionFactory;
	}

	@Override
	public String getProperty(String key) {
		try (SqlSession session = _sessionFactory.openSession()) {
			return session.getMapper(Domains.class).getProperty(key);
		}
	}

	@Override
	public void setProperty(String key, String value) {
		try (SqlSession session = _sessionFactory.openSession()) {
			Domains domains = session.getMapper(Domains.class);
			if (domains.updateProperty(key, value) == 0) {
				domains.insertProperty(key, value);
			}
			session.commit();
		}
	}

}
