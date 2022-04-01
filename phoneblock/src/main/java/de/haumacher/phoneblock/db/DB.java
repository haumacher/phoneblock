/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.sql.DataSource;

import org.apache.ibatis.jdbc.ScriptRunner;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;

/**
 * The database abstraction layer.
 */
public class DB {

	private static final Collection<String> TABLE_NAMES = Arrays.asList("BLOCKLIST", "SPAMREPORTS", "USERS");
	
	private SqlSessionFactory _sessionFactory;
	private DataSource _dataSource;

	/** 
	 * Creates a {@link DB}.
	 *
	 * @param dataSource
	 */
	public DB(DataSource dataSource) throws SQLException, UnsupportedEncodingException {
		_dataSource = dataSource;
		
		TransactionFactory transactionFactory = new JdbcTransactionFactory();
		Environment environment = new Environment("phoneblock", transactionFactory, _dataSource);
		Configuration configuration = new Configuration(environment);
		configuration.setUseActualParamName(true);
		configuration.addMapper(SpamReports.class);
		_sessionFactory = new SqlSessionFactoryBuilder().build(configuration);
		
		Set<String> tableNames = new HashSet<>();
		try (SqlSession session = _sessionFactory.openSession()) {
			try (ResultSet rset = session.getConnection().getMetaData().getTables(null, "PUBLIC", "%", null)) {
				while (rset.next()) {
					String tableName = rset.getString("TABLE_NAME");
					tableNames.add(tableName);
				}
			}
			
			if (!tableNames.containsAll(TABLE_NAMES)) {
				// Set up schema.
		        ScriptRunner sr = new ScriptRunner(session.getConnection());
		        sr.setAutoCommit(true);
		        sr.setDelimiter(";");
		        sr.runScript(new InputStreamReader(getClass().getResourceAsStream("db-schema.sql"), "utf-8"));
			}
		}
	}
	
	public boolean hasSpamReportFor(String phone) {
		try (SqlSession session = _sessionFactory.openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			return reports.isKnown(phone);
		}
	}

	public void addSpam(String phone, int votes, long time) {
		if (votes == 0) {
			return;
		}
		
		try (SqlSession session = _sessionFactory.openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			processVote(reports, phone, votes, time);
			session.commit();
		}
	}

	private void processVote(SpamReports reports, String phone, int votes, long time) {
		if (votes < 0) {
			if (reports.isKnown(phone)) {
				long currentVotes = reports.getVotes(phone);
				if (currentVotes + votes <= 0) {
					reports.delete(phone);
				} else {
					reports.addVote(phone, votes, time);
				}
			}
		} else {
			if (reports.isKnown(phone)) {
				reports.addVote(phone, votes, time);
			} else {
				reports.addReport(phone, votes, time);
			}
		}
	}

	public long getLastSpamReport() {
		try (SqlSession session = _sessionFactory.openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			return reports.getLastUpdate();
		}
	}
	
	public List<SpamReport> getLatestSpamReports(long notBefore) {
		try (SqlSession session = _sessionFactory.openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			return reports.getLatestReports(notBefore);
		}
	}

	public List<SpamReport> getSpamReports(int minVotes) {
		try (SqlSession session = _sessionFactory.openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			return reports.getReports(minVotes);
		}
	}
	
	public long getSpamVotesFor(String phone) {
		try (SqlSession session = _sessionFactory.openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			return reports.isKnown(phone) ? reports.getVotes(phone) : 0;
		}
	}
	
	public void shutdown() {
		try (SqlSession session = _sessionFactory.openSession()) {
			try (Statement statement = session.getConnection().createStatement()) {
				statement.execute("SHUTDOWN");
			}
		} catch (Exception e) {
			System.err.println("Database shutdown failed.");
			e.printStackTrace();
		}
	}

}
