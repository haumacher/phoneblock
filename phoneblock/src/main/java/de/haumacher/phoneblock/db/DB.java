/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Base64;
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

	private static final String SAVE_CHARS = "123456789qwertzuiopasdfghjkyxcvbnmQWERTZUPASDFGHJKLYXCVBNM";

	private static final Collection<String> TABLE_NAMES = Arrays.asList("BLOCKLIST", "SPAMREPORTS", "USERS");
	
	private SqlSessionFactory _sessionFactory;
	private DataSource _dataSource;

	private static final String BASIC_PREFIX = "Basic ";
	private MessageDigest _sha256;

	private SecureRandom _rnd = new SecureRandom();
	
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
		configuration.addMapper(BlockList.class);
		configuration.addMapper(Users.class);
		_sessionFactory = new SqlSessionFactoryBuilder().build(configuration);
		
		Set<String> tableNames = new HashSet<>();
		try (SqlSession session = openSession()) {
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
		
		try {
			_sha256 = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException ex) {
			throw new RuntimeException("Digest algorithm not supported: " + ex.getMessage(), ex);
		}
	}
	
	public String generateVerificationCode() {
		StringBuilder codeBuffer = new StringBuilder();
		for (int n = 0; n < 8; n++) {
			codeBuffer.append(_rnd.nextInt(10));
		}
		String code = codeBuffer.toString();
		return code;
	}

	public String createUser(String userName) throws UnsupportedEncodingException {
		StringBuilder pwbuffer = new StringBuilder();
		for (int n = 0; n < 20; n++) {
			pwbuffer.append(SAVE_CHARS.charAt(_rnd.nextInt(SAVE_CHARS.length())));
		}
		
		String passwd = pwbuffer.toString();
		addUser(userName, passwd);
		return passwd;
	}

	public boolean hasSpamReportFor(String phone) {
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			return reports.isKnown(phone);
		}
	}

	public void addSpam(String phone, int votes, long time) {
		if (votes == 0) {
			return;
		}
		
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			processVote(reports, phone, votes, time);
			session.commit();
		}
	}

	public void processVote(SpamReports reports, String phone, int votes, long time) {
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
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			return reports.getLastUpdate();
		}
	}

	public SqlSession openSession() {
		return _sessionFactory.openSession();
	}
	
	public List<SpamReport> getLatestSpamReports(long notBefore) {
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			return reports.getLatestReports(notBefore);
		}
	}

	public long getSpamVotesFor(String phone) {
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			return reports.isKnown(phone) ? reports.getVotes(phone) : 0;
		}
	}
	
	public void shutdown() {
		try (SqlSession session = openSession()) {
			try (Statement statement = session.getConnection().createStatement()) {
				statement.execute("SHUTDOWN");
			}
		} catch (Exception e) {
			System.err.println("Database shutdown failed.");
			e.printStackTrace();
		}
	}

	/** 
	 * Adds the given user with the given password.
	 */
	public void addUser(String userName, String passwd) throws UnsupportedEncodingException {
		try (SqlSession session = openSession()) {
			Users users = session.getMapper(Users.class);
			
			users.deleteUser(userName);
			users.addUser(userName, pwhash(passwd));
			session.commit();
		}
	}

	/**
	 * Checks credentials in the given authorization header.
	 * 
	 * @return The authorized user name, if authorization was successful, <code>null</code> otherwise.
	 */
	public String basicAuth(String authHeader) throws IOException {
		if (authHeader.startsWith(BASIC_PREFIX)) {
			String credentials = authHeader.substring(BASIC_PREFIX.length());
			byte[] decodedBytes = Base64.getDecoder().decode(credentials);
			String decoded = new String(decodedBytes, "utf-8");
			int sepIndex = decoded.indexOf(':');
			if (sepIndex >= 0) {
				String userName = decoded.substring(0, sepIndex);
				String passwd = decoded.substring(sepIndex + 1);
				
				byte[] pwhash = pwhash(passwd);
				
				try (SqlSession session = openSession()) {
					Users users = session.getMapper(Users.class);
					InputStream hashIn = users.getHash(userName);
					if (hashIn != null) {
						byte[] expectedHash = hashIn.readAllBytes();
						if (Arrays.equals(pwhash, expectedHash)) {
							return userName;
						}
					}
				}
			}
		}
		System.err.println("Invalid authentication received: " + authHeader);
		return null;
	}

	private byte[] pwhash(String passwd) throws UnsupportedEncodingException {
		return _sha256.digest(passwd.getBytes("utf-8"));
	}

}
