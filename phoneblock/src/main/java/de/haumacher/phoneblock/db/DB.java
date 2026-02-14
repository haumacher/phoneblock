/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import java.io.ByteArrayInputStream;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.ab.DBAnswerbotInfo;
import de.haumacher.phoneblock.ab.proto.AnswerbotInfo;
import de.haumacher.phoneblock.ab.proto.RetentionPeriod;
import de.haumacher.phoneblock.analysis.NumberAnalyzer;
import de.haumacher.phoneblock.app.AuthContext;
import de.haumacher.phoneblock.app.api.model.BlockListEntry;
import de.haumacher.phoneblock.app.api.model.Blocklist;
import de.haumacher.phoneblock.app.api.model.NumberInfo;
import de.haumacher.phoneblock.app.api.model.PhoneInfo;
import de.haumacher.phoneblock.app.api.model.PhoneNumer;
import de.haumacher.phoneblock.app.api.model.Rating;
import de.haumacher.phoneblock.app.api.model.SearchInfo;
import de.haumacher.phoneblock.app.api.model.UserComment;
import de.haumacher.phoneblock.callreport.model.CallReport;
import de.haumacher.phoneblock.callreport.model.ReportInfo;
import de.haumacher.phoneblock.credits.MessageDetails;
import de.haumacher.phoneblock.db.config.DBConfig;
import de.haumacher.phoneblock.db.settings.AuthToken;
import de.haumacher.phoneblock.db.settings.Contribution;
import de.haumacher.phoneblock.db.settings.UserSettings;
import de.haumacher.phoneblock.index.IndexUpdateService;
import de.haumacher.phoneblock.mail.MailService;
import de.haumacher.phoneblock.mail.check.db.Domains;
import de.haumacher.phoneblock.scheduler.SchedulerService;
import de.haumacher.phoneblock.shared.PhoneHash;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

/**
 * The database abstraction layer.
 */
public class DB {

	static final String TOKEN_VERSION = "pbt_";

	private static final int MAX_COMMENT_LENGTH = 8192;

	private static final Logger LOG = LoggerFactory.getLogger(DB.class);

	/**
	 * Minimum number of votes to add a number to the blocklist.
	 * 
	 * <p>
	 * Note: For historic reasons, one entry to the personal blocklist by a PhoneBlock user is worth 2 votes.
	 * </p>
	 */
	public static final int MIN_VOTES = 4;

	/**
	 * Number of days a number stays on the blocklist when {@link #MIN_VOTES} are received. After that time limit,
	 * {@link #WEEK_PER_VOTE} are substracted per week.
	 */
	private static final int OLD_VOTE_DAYS = 14;

	/**
	 * Number of weeks to pass to substract a vote (after a number has not been reported active for {@link #OLD_VOTE_DAYS}). 
	 */
	private static final int WEEK_PER_VOTE = 3;

	private static final String SAVE_CHARS = "23456789qwertzuiopasdfghjkyxcvbnmQWERTZUPASDFGHJKLYXCVBNM";

	private static final Collection<String> TABLE_NAMES = Arrays.asList(
		"BLOCKLIST", "EXCLUDES", "SPAMREPORTS", "OLDREPORTS", "USERS", "CALLREPORT", "CALLERS", "RATINGS", "SEARCHES"
	);
	
	private SqlSessionFactory _sessionFactory;
	private DataSource _dataSource;

	private static final String BASIC_AUTH_PREFIX = "Basic ";

	public static final int MIN_AGGREGATE_10 = 4;
	
	public static final int MIN_AGGREGATE_100 = 3;

	/**
	 * Vote thresholds that trigger blocklist version updates.
	 * When a number crosses any of these thresholds, it's marked for version assignment.
	 *
	 * <p>
	 * These are the only valid values for the <code>minVotes</code> parameter in blocklist APIs.
	 * Using other values will result in inconsistent incremental synchronization, as version
	 * updates are only triggered when numbers cross these specific thresholds.
	 * </p>
	 */
	public static final int[] BLOCKLIST_THRESHOLDS = {2, 4, 10, 20, 50, 100};

	/**
	 * Initial version number for the blocklist.
	 *
	 * <p>
	 * Starting at 1 makes <code>since=0</code> equivalent to omitting the parameter entirely
	 * (both return the full blocklist), which is more intuitive for API consumers.
	 * </p>
	 */
	public static final long INITIAL_BLOCKLIST_VERSION = 1L;

	/**
	 * One hour in milliseconds.
	 */
	public static final long RATE_LIMIT_MS = 1000*60*60;

	private final SecureRandom _rnd;
	
	private SchedulerService _scheduler;
	
	private IndexUpdateService _indexer;

	private List<ScheduledFuture<?>> _tasks = new ArrayList<>();

	private MailService _mailService;

	private DBConfig _config;

	/**
	 * Minimum votes threshold for blocklist visibility.
	 * Only threshold crossings at or above this value trigger version updates.
	 * Default: 10 (same as {@link #DEFAULT_MIN_VISIBLE_VOTES}).
	 */
	private int _minVisibleVotes = DEFAULT_MIN_VISIBLE_VOTES;

	/**
	 * Effective thresholds for version updates, containing only thresholds >= minVisibleVotes.
	 * Precomputed for efficiency in {@link #crossesThreshold(int, int)}.
	 */
	private int[] _effectiveThresholds = computeEffectiveThresholds(DEFAULT_MIN_VISIBLE_VOTES);

	/**
	 * Default minimum votes threshold for blocklist visibility.
	 */
	public static final int DEFAULT_MIN_VISIBLE_VOTES = 10;

	public DB(DataSource dataSource, SchedulerService scheduler) throws SQLException {
		this(new SecureRandom(), DBConfig.create(), dataSource, IndexUpdateService.NONE, scheduler, null);
	}
	
	/** 
	 * Creates a {@link DB}.
	 * @param sendHelpMails 
	 *
	 * @param dataSource
	 */
	public DB(SecureRandom rnd, DBConfig config, DataSource dataSource, IndexUpdateService indexer, SchedulerService scheduler, MailService mailService) throws SQLException {
		_rnd = rnd;
		_config = config;
		_dataSource = dataSource;
		_indexer = indexer;
		_scheduler = scheduler;
		_mailService = mailService;
		
		TransactionFactory transactionFactory = new JdbcTransactionFactory();
		Environment environment = new Environment("phoneblock", transactionFactory, _dataSource);
		Configuration configuration = new Configuration(environment);
		configuration.setUseActualParamName(true);
		configuration.addMapper(SpamReports.class);
		configuration.addMapper(BlockList.class);
		configuration.addMapper(Users.class);
		configuration.addMapper(Domains.class);
		_sessionFactory = new SqlSessionFactoryBuilder().build(configuration);
		
		setupSchema();
		
		 cleanup();

		 Date timeCleanup = schedule(20, this::cleanup);
		 LOG.info("Scheduled next DB cleanup: " + timeCleanup);
		 
		 Date timeHistory = schedule(0, this::runUpdateHistory);
		 LOG.info("Scheduled search history cleanup: " + timeHistory);
		 
		 if (_config.isSendHelpMails() && _mailService != null) {
			 Date supportMails = schedule(18, this::sendSupportMails);
			 LOG.info("Scheduled support mails: " + supportMails);
		 } else {
			 LOG.info("Support mails are disabled.");
		 }
	}

	/**
	 * Sets the minimum votes threshold for blocklist visibility.
	 *
	 * <p>
	 * Only threshold crossings at or above this value will trigger blocklist version updates.
	 * This should be called during initialization, before any votes are processed.
	 * </p>
	 *
	 * @param minVisibleVotes The minimum votes threshold. Must be one of {@link #BLOCKLIST_THRESHOLDS}.
	 */
	public void setMinVisibleVotes(int minVisibleVotes) {
		if (!isValidBlocklistThreshold(minVisibleVotes)) {
			LOG.warn("Invalid minVisibleVotes {}, must be one of: {}. Using default {}",
				minVisibleVotes, getBlocklistThresholdsString(), DEFAULT_MIN_VISIBLE_VOTES);
			minVisibleVotes = DEFAULT_MIN_VISIBLE_VOTES;
		}
		_minVisibleVotes = minVisibleVotes;
		_effectiveThresholds = computeEffectiveThresholds(minVisibleVotes);
		LOG.info("Blocklist minVisibleVotes set to: {}", _minVisibleVotes);
	}

	/**
	 * Computes the effective thresholds for version updates.
	 * Returns an array containing only thresholds >= minVisibleVotes.
	 */
	private static int[] computeEffectiveThresholds(int minVisibleVotes) {
		int count = 0;
		for (int threshold : BLOCKLIST_THRESHOLDS) {
			if (threshold >= minVisibleVotes) {
				count++;
			}
		}
		int[] result = new int[count];
		int index = 0;
		for (int threshold : BLOCKLIST_THRESHOLDS) {
			if (threshold >= minVisibleVotes) {
				result[index++] = threshold;
			}
		}
		return result;
	}

	/**
	 * Gets the minimum votes threshold for blocklist visibility.
	 */
	public int getMinVisibleVotes() {
		return _minVisibleVotes;
	}

	private void setupSchema() throws SQLException {
		Set<String> tableNames = new HashSet<>();
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			
			Connection connection = session.getConnection();
			try (ResultSet rset = connection.getMetaData().getTables(null, "PUBLIC", "%", null)) {
				while (rset.next()) {
					String tableName = rset.getString("TABLE_NAME");
					tableNames.add(tableName);
				}
			}
			
			if (!tableNames.containsAll(TABLE_NAMES)) {
				runScript(connection, "db-schema.sql");
			}
			
			else if (!tableNames.contains("NUMBERS")) {
    			LOG.info("Migrating schema to consolidated numbers.");
    			
    			runScript(connection, "db-migration-02.sql");

    			LOG.info("Building revision ranges in numbers history.");

		        try (PreparedStatement stmt = connection.prepareStatement("""
		        	select h.RMIN, h.RMAX, h.PHONE from NUMBERS_HISTORY h
		        	order by h.PHONE asc, h.RMIN desc
        		""", ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE)) {
		        	
		        	int rows = 0;
		        	String lastPhone = "";
		        	int lastRmin = 0;
		        	try (ResultSet cursor = stmt.executeQuery()) {
		        		while (cursor.next()) {
		        			int rmin = cursor.getInt(1);
		        			String phone = cursor.getString(3);
		        			
		        			if (lastPhone.equals(phone)) {
		        				cursor.updateInt(2, lastRmin - 1);
		        			} else {
		        				cursor.updateInt(2, Integer.MAX_VALUE);
		        			}
		        			cursor.updateRow();
		        			rows++;
		        			
		        			if (rows % 1000 == 0) {
		        				LOG.info("Updated rmax of row {}.", rows);
		        			}
		        			
		        			lastPhone = phone;
		        			lastRmin = rmin;
		        		}
		        	}
		        }

		        connection.commit();

		        LOG.info("Loading ratings today.");
		        
		        Map<String, Map<String, Integer>> ratingsToday = new HashMap<>();
		        try (PreparedStatement stmt = connection.prepareStatement("""
			        	SELECT r.PHONE, r.RATING, r.COUNT - r.BACKUP FROM RATINGS r WHERE r.COUNT > r.BACKUP;
	        		""")) {
		        	try (ResultSet result = stmt.executeQuery()) {
		        		while (result.next()) {
		        			ratingsToday.computeIfAbsent(result.getString(1), x -> new HashMap<>()).put(result.getString(2), Integer.valueOf(result.getInt(3)));
		        		}
		        	}
		        }
		        
		        LOG.info("Loading searches today.");
		        
		        Map<String, Integer> searchesToday = new HashMap<>();
		        try (PreparedStatement stmt = connection.prepareStatement("""
			        	SELECT s.PHONE, s.COUNT - s.BACKUP FROM SEARCHES s WHERE s.COUNT - s.BACKUP > 0
	        		""")) {
		        	try (ResultSet result = stmt.executeQuery()) {
		        		while (result.next()) {
		        			searchesToday.put(result.getString(1), Integer.valueOf(result.getInt(2)));
		        		}
		        	}
		        }
		        
		        LOG.info("Aggregating numbers history.");

		        try (PreparedStatement stmt = connection.prepareStatement("""
		        	select h.RMIN, h.RMAX, h.PHONE, h.CALLS, h.VOTES, h.LEGITIMATE, h.PING, h.POLL, h.ADVERTISING, h.GAMBLE, h.FRAUD, h.SEARCHES from NUMBERS_HISTORY h
		        	order by h.PHONE asc, h.RMIN desc
        		""", ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE)) {
		        	
		        	int rows = 0;
		        	String lastPhone = "";
		        	
		        	int calls = 0;
		        	int votes = 0; 
		        	int legitimate = 0;
		        	int ping = 0;
		        	int poll = 0;
		        	int advertising = 0;
		        	int gamble = 0;
		        	int fraud = 0;
		        	int searches = 0;
		        	
		        	try (ResultSet cursor = stmt.executeQuery()) {
		        		while (cursor.next()) {
		        			String phone = cursor.getString(3);
		        			
		        			if (!lastPhone.equals(phone)) {
		        				NumberInfo info = getPhoneInfo(reports, phone);
		        				calls = info.getCalls();
		        				votes = info.getVotes();
		        				legitimate = info.getRatingLegitimate();
		        				ping = info.getRatingPing();
		        				poll = info.getRatingPoll();
		        				advertising = info.getRatingAdvertising();
		        				gamble = info.getRatingGamble();
		        				fraud = info.getRatingFraud();
		        				searches = info.getSearches();
		        				
		        				Integer todaySearches = searchesToday.get(phone);
		        				if (todaySearches != null) {
		        					searches -= todaySearches.intValue();
		        				}
		        				
		        				Map<String, Integer> todayRatings = ratingsToday.get(phone);
		        				if (todayRatings != null) {
		        					for (Entry<String, Integer> entry : todayRatings.entrySet()) {
		        						switch (entry.getKey()) {
		        						case "A_LEGITIMATE": legitimate -= entry.getValue().intValue(); break;
		        						case "C_PING": ping -= entry.getValue().intValue(); break;
		        						case "D_POLL": poll -= entry.getValue().intValue(); break;
		        						case "E_ADVERTISING": advertising -= entry.getValue().intValue(); break;
		        						case "F_GAMBLE": gamble -= entry.getValue().intValue(); break;
		        						case "G_FRAUD": fraud -= entry.getValue().intValue(); break;
		        						}
		        					}
		        				}
		        			}
		        			
	        				int deltaCalls = cursor.getInt(4);
	        				int deltaVotes = cursor.getInt(5);
	        				int deltaLegitimate = cursor.getInt(6);
	        				int deltaPing = cursor.getInt(7);
	        				int deltaPoll = cursor.getInt(8);
	        				int deltaAdvertising = cursor.getInt(9);
	        				int deltaGamble = cursor.getInt(10);
	        				int deltaFraud = cursor.getInt(11);
	        				int deltaSearches = cursor.getInt(12);
	        				
	        				cursor.updateInt(4, Math.max(0, calls));
	        				cursor.updateInt(5, Math.max(0, votes));
	        				cursor.updateInt(6, Math.max(0, legitimate));
	        				cursor.updateInt(7, Math.max(0, ping));
	        				cursor.updateInt(8, Math.max(0, poll));
	        				cursor.updateInt(9, Math.max(0, advertising));
	        				cursor.updateInt(10, Math.max(0, gamble));
	        				cursor.updateInt(11, Math.max(0, fraud));
	        				cursor.updateInt(12, Math.max(0, searches));
	        				
	        				cursor.updateRow();
		        			rows++;
		        			
		        			if (rows % 1000 == 0) {
		        				LOG.info("Aggregated row {}.", rows);
		        			}

							calls -=       deltaCalls;
							votes -=       deltaVotes; 
							legitimate -=  deltaLegitimate;
							ping -=        deltaPing;
							poll -=        deltaPoll;
							advertising -= deltaAdvertising;
							gamble -=      deltaGamble;
							fraud -=       deltaFraud;
							searches -=    deltaSearches;
		        			
		        			lastPhone = phone;
		        		}
		        	}
		        }
		        
		        connection.commit();

		        LOG.info("Computing aggregate lastPing for blocks of 10.");
		        
		        for (AggregationInfo a : reports.getAllAggregation10().stream().filter(a -> a.getCnt() >= DB.MIN_AGGREGATE_10).toList()) {
		        	String prefix = a.getPrefix();
					int prefixLength = prefix.length();
					Long lastPing = reports.getLastPingPrefix(prefix, prefixLength + 1);
		        	if (lastPing != null) {
		        		reports.sendPing(prefix, prefixLength + 1, lastPing.longValue());
		        	} else {
		        		LOG.warn("Did not find a last ping value for prefix 10: " + prefix);
		        	}
		        }

		        connection.commit();

		        LOG.info("Computing aggregate lastPing for blocks of 100.");

		        for (AggregationInfo a : reports.getAllAggregation100().stream().filter(a -> a.getCnt() >= DB.MIN_AGGREGATE_100).toList()) {
		        	String prefix = a.getPrefix();
					int prefixLength = prefix.length();
					Long lastPing = reports.getLastPingPrefix(prefix, prefixLength + 2);
		        	if (lastPing != null) {
		        		reports.sendPing(prefix, prefixLength + 2, lastPing.longValue());
		        	} else {
		        		LOG.warn("Did not find a last ping value for prefix 100: " + prefix);
		        	}
		        }
		        
		        connection.commit();
			} else if (!tableNames.contains("PROPERTIES")) {
				runScript(connection, "db-migration-04.sql");
				
				try {
					MessageDigest digest = PhoneHash.createPhoneDigest();
					
					LOG.info("Computing phone hashes.");
					try (PreparedStatement stmt = connection.prepareStatement("""
			        	select PHONE, SHA1 from NUMBERS
	        		""", ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE)) {
						try (ResultSet result = stmt.executeQuery()) {
							while (result.next()) {
								String phoneId = result.getString(1);
								
								PhoneNumer analyzed = NumberAnalyzer.analyzePhoneID(phoneId);
								if (analyzed == null) {
									LOG.error("Invalid phone number in DB: " + phoneId);
									continue;
								}

								byte[] hash = NumberAnalyzer.getPhoneHash(digest, analyzed);
								
								result.updateBinaryStream(2, new ByteArrayInputStream(hash));
								result.updateRow();
								
								digest.reset();
							}
						}
					}
			        connection.commit();

			        LOG.info("Done computing phone hashes.");
				} catch (Exception ex) {
					LOG.error("Failed to compute phone hashes.", ex);
				}
			} else {
				Users users = session.getMapper(Users.class);
				
				int version = Integer.parseInt(users.getProperty("db.version"));
				while (true) {
					version++;
					
					String versionId = Integer.toString(version);
					while (versionId.length() < 2) {
						versionId = "0" + versionId;
					}

					String scriptName = "db-migration-" + versionId + ".sql";
					InputStream script = migrationScript(scriptName);
					if (script == null) {
						break;
					}
					
					runScript(connection, scriptName);
					users.updateProperty("db.version", Integer.toString(version));
					session.commit();
				}
			}
		}
	}

	private void runScript(Connection connection, String scriptName) {
		try (InputStream in = migrationScript(scriptName)) {
			LOG.info("Running DB script: {}", scriptName);
			
			ScriptRunner sr = new ScriptRunner(connection);
			sr.setAutoCommit(true);
			sr.setDelimiter(";");
			sr.runScript(new InputStreamReader(in, StandardCharsets.UTF_8));
			
			LOG.info("Finished DB script: {}", scriptName);
		} catch (IOException ex) {
			LOG.error("Problem while running DB script '{}': {}", scriptName, ex.getMessage(), ex);
		}
	}

	private InputStream migrationScript(String scriptName) {
		return getClass().getResourceAsStream(scriptName);
	}

	private Date schedule(int atHour, Runnable command) {
		Calendar cal = GregorianCalendar.getInstance();
		 long millisNow = cal.getTimeInMillis();
		 int hourNow = cal.get(Calendar.HOUR_OF_DAY);
		 cal.set(Calendar.HOUR_OF_DAY, atHour);
		 cal.set(Calendar.MINUTE, 0);
		 cal.set(Calendar.SECOND, 0);
		 cal.set(Calendar.MILLISECOND, 0);
		 if (hourNow >= atHour) {
			 cal.add(Calendar.DAY_OF_MONTH, 1);
		 }
		 long millisFirst = cal.getTimeInMillis();
		 long initialDelay = millisFirst - millisNow;
		 
		_tasks.add(_scheduler.scheduler().scheduleAtFixedRate(command, initialDelay, 24 * 60 * 60 * 1000L, TimeUnit.MILLISECONDS));
		return cal.getTime();
	}
	
	/**
	 * Generates a random number that is sent to a e-mail address for verification.
	 */
	public String generateVerificationCode() {
		StringBuilder codeBuffer = new StringBuilder();
		for (int n = 0; n < 8; n++) {
			codeBuffer.append(_rnd.nextInt(10));
		}
		String code = codeBuffer.toString();
		return code;
	}

	/**
	 * Creates a new PhoneBlock user account.
	 * @param login The user name (e.g. e-mail address) of the new account.
	 * @param lang The preferred language (locale tag) of the user.
	 * @return The randomly generated password for the account.
	 */
	public String createUser(String login, String displayName, String lang, String dialPrefix) {
		String passwd = createPassword(20);
		addUser(login, displayName, lang, dialPrefix, passwd);
		return passwd;
	}

	/** 
	 * Deletes the user with the given login.
	 */
	public void deleteUser(String login) {
		try (SqlSession session = openSession()) {
			Users users = session.getMapper(Users.class);
			users.deleteUser(login);
			session.commit();
		}
	}

	/** 
	 * Creates a secure password with the given length.
	 */
	public String createPassword(int length) {
		StringBuilder buffer = new StringBuilder();
		for (int n = 0; n < length; n++) {
			buffer.append(SAVE_CHARS.charAt(_rnd.nextInt(SAVE_CHARS.length())));
		}
		
		return buffer.toString();
	}
	
	/**
	 * Creates a persistent authorization token.
	 * 
	 * <p>
	 * Note: The given template is completed with user ID, password hash, and token.
	 * </p>
	 */
	public void createAuthToken(AuthToken template) {
		byte[] secret = createSecret();
		byte[] digest = sha256().digest(secret);
		try (SqlSession session = openSession()) {
			Users users = session.getMapper(Users.class);

			long userId = users.getUserId(template.getUserName());
			
			template
				.setUserId(userId)
				.setPwHash(digest);
			
			List<Long> outdatedIds = users.getOutdatedLoginTokens(userId);
			if (outdatedIds != null && !outdatedIds.isEmpty()) {
				int tokens = users.deleteTokens(outdatedIds);
				
				LOG.info("Deleted {} outdated login tokens for user {}.", tokens, userId);
			}
			
			users.createAuthToken(template);
			session.commit();
		}
		
		String token = createToken(template, secret);
		template.setToken(token);
	}

	private String createToken(AuthToken template, byte[] secret) throws IOError {
		return TokenInfo.createToken(template.getId(), secret);
	}

	private byte[] createSecret() {
		byte[] secret = new byte[16];
		_rnd.nextBytes(secret);
		return secret;
	}
	
	public AuthToken createLoginToken(String login, long now, String userAgent) {
		AuthToken authorization = createAuthorizationTemplate(login, now, userAgent)
			.setImplicit(true)
			
			// See required ContentFilter authorizations.
			.setAccessQuery(true)
			.setAccessRate(true)
			.setAccessLogin(true);
		createAuthToken(authorization);
		return authorization;
	}

	public AuthToken createAPIToken(String login, long now, String userAgent, String label) {
		AuthToken authorization = createAuthorizationTemplate(login, now, userAgent)
				.setAccessDownload(true)
				.setAccessQuery(true)
				.setAccessRate(true);
		return createToken(label, authorization);
	}

	/**
	 * Creates a token with only CardDAV access permission.
	 * This is intended for Fritz!Box installations.
	 */
	public AuthToken createCardDavToken(String login, long now, String userAgent, String label) {
		AuthToken authorization = createAuthorizationTemplate(login, now, userAgent)
			.setAccessCarddav(true);  // ONLY CardDAV permission
		return createToken(label, authorization);
	}

	private AuthToken createToken(String label, AuthToken authorization) {
		if (label != null && !label.trim().isEmpty()) {
			authorization.setLabel(label.trim());
		}
		createAuthToken(authorization);
		return authorization;
	}

	public static AuthToken createAuthorizationTemplate(String login, long now, String userAgent) {
		return AuthToken.create()
			.setUserName(login)
			.setCreated(now)
			.setLastAccess(0)
			.setUserAgent(nonNullUA(userAgent));
	}
	
	private static String nonNullUA(String userAgent) {
		return userAgent == null ? "-" : userAgent;
	}

	public AuthContext checkAuthToken(String token, long now, String userAgent, boolean renew) {
		if (!token.startsWith(TOKEN_VERSION)) {
			return null;
		}

		try {
			TokenInfo tokenInfo = TokenInfo.parse(token);

			try (SqlSession session = openSession()) {
				Users users = session.getMapper(Users.class);

				DBAuthToken result = users.getAuthToken(tokenInfo.id);
				if (result == null) {
					LOG.info("Outdated authorization token received: {}", token);
					return null;
				}

				byte[] expectedHash = result.getPwHash();
				byte[] digest = sha256().digest(tokenInfo.secret);

				if (!Arrays.equals(digest, expectedHash)) {
					LOG.info("Invalid authorization token received for user {}: {}", result.getUserId(), token);
					return null;
				}

				result.setUserName(users.getUserName(result.getUserId()));

				// Remember token, since an update is sent to the client.
				result.setToken(token);

				// Update DB if rate limit exceeded or user agent changed.
				boolean userAgentChanged = !Objects.equals(result.getUserAgent(), userAgent);

				// Detect first token usage (before any updates)
				boolean isFirstAccess = result.getLastAccess() == 0;

				if (now - result.getLastAccess() > RATE_LIMIT_MS || userAgentChanged) {
					users.updateAuthToken(result.getId(), now, userAgent);
					result.setLastAccess(now);
					result.setUserAgent(userAgent);

					if (renew) {
						LOG.info("Renewing autorization token for user {}.", result.getUserName());
						renewToken(users, result);
					}

					session.commit();
				}

				// Load user settings in the same transaction
				DBUserSettings userSettings = getUserSettings(users, result.getUserName());

				// Send welcome mail on first token usage (independent of rate limit)
				if (isFirstAccess && !result.isImplicit() && _config.isSendWelcomeMails() && _mailService != null) {
					LOG.info("First token usage detected for token {}, sending welcome mail.",
					         result.getId());

					String deviceLabel = result.getLabel();
					_scheduler.executor().submit(() ->
					    _mailService.sendMobileWelcomeMail(userSettings, deviceLabel));
				}

				return new AuthContext(result, userSettings);
			}
		} catch (IOException | RuntimeException e) {
			LOG.info("Failed to parse authorization token '{}': {}", token, e.getMessage());
			return null;
		}
	}

	/** Changes the secret to invalidate the old token and exchange it with a new token. */
	private void renewToken(Users users, DBAuthToken authorization) throws IOError {
		byte[] newSecret = createSecret();
		byte[] newHash = sha256().digest(newSecret);
		
		String newToken = createToken(authorization, newSecret);
		authorization.setPwHash(newHash);
		authorization.setToken(newToken);

		users.updateAuthTokenSecret(authorization.getId(), newHash);
	}

	public void invalidateAuthToken(long id) {
		try (SqlSession session = openSession()) {
			Users users = session.getMapper(Users.class);
			users.invalidateAuthToken(id);
			session.commit();
		}
	}
	
	public void logoutAll(String login) {
		try (SqlSession session = openSession()) {
			Users users = session.getMapper(Users.class);
			
			Long userId = users.getUserId(login);
			if (userId != null) {
				users.invalidateAllTokens(userId.longValue());
				session.commit();
			}
		}
	}
	
	/** 
	 * Creates a unique random (numeric) ID with the given length.
	 */
	public String createId(int length) {
		StringBuilder buffer = new StringBuilder();
		for (int n = 0; n < length; n++) {
			char ch = (char) ('0' + _rnd.nextInt(10));
			buffer.append(ch);
		}
		return buffer.toString();
	}
	
	/** 
	 * Sets the user's e-mail address.
	 */
	public void setEmail(String login, String email) throws AddressException {
		try (SqlSession session = openSession()) {
			Users users = session.getMapper(Users.class);
			users.setEmail(login, canonicalEMail(email));
			session.commit();
		}
	}
	
	/** 
	 * Sets the user's external ID in its OAuth authorization scope.
	 * @param displayName 
	 */
	public void setGoogleId(String login, String googleId, String displayName) {
		try (SqlSession session = openSession()) {
			Users users = session.getMapper(Users.class);
			users.setGoogleId(login, googleId);
			if (displayName != null && !displayName.isBlank()) {
				users.setDisplayName(login, displayName);
			}
			session.commit();
		}
	}
	
	/**
	 * Whether ther is an entry for the given phone numner in the database.
	 */
	public boolean hasSpamReportFor(String phone) {
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			return reports.isKnown(phone);
		}
	}

	/**
	 * Adds/removes votes for the given phone number.
	 *
	 * @param phone The phone number to vote.
	 * @param votes The votes to add/remove for the given phone number.
	 * @param time The current time to update the last update time to.
	 */
	public void processVotes(PhoneNumer phone, String dialPrefix, int votes, long time) {
		if (votes == 0) {
			return;
		}
		
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			processVotesAndPublish(reports, phone, dialPrefix, votes, time);
			session.commit();
		}
	}

	/**
	 * Implementation of {@link #processVotes(String, int, long)} when there is already a database session.
	 * 
	 * @return Whether an index update should be performed.
	 */
	public boolean processVotesAndPublish(SpamReports reports, PhoneNumer phone, String dialPrefix, int votes, long time) {
		boolean updateRequired = processVotes(reports, phone, dialPrefix, votes, time);
		if (updateRequired) {
			_indexer.publishUpdate(phone);
		}
		return updateRequired;
	}

	/**
	 * Updates the votes for a certain number.
	 */
	public boolean processVotes(SpamReports reports, PhoneNumer number, String dialPrefix, int votes, long time) {
		String phone = NumberAnalyzer.getPhoneId(number);
		final int oldVotes = nonNull(reports.getVotes(phone));
		final int newVotes = oldVotes + votes;
		
		int rows = reports.addVote(phone, votes, time);
		if (rows == 0) {
			byte[] hash = NumberAnalyzer.getPhoneHash(number);
			
			// Number was not yet present, must be added.
			reports.addReport(phone, hash, votes, time);
			
			if (votes > 0) {
				updateAggregation10(reports, phone, 1, votes);
			}
		} else {
			// Add new votes to aggregation.
			updateAggregation10(reports, phone, delta(oldVotes, newVotes), votes);
		}
		
		pingRelatedNumbers(reports, phone, time);
		
		if (votes > 0) {
			updateLocalization(reports, phone, dialPrefix, 0, votes, 0, time);
		}

		boolean classifyChanged = classify(oldVotes) != classify(newVotes);
		boolean thresholdCrossed = crossesThreshold(oldVotes, newVotes);

		if (thresholdCrossed) {
			reports.markPendingUpdate(phone);
		}

		// Clear SHA1 hash when votes fall below 1 to protect privacy for legitimate numbers.
		// This prevents identifying legitimate callers in privacy-aware lookups.
		if (newVotes < 1) {
			reports.clearPhoneHash(phone);
		}

		return classifyChanged;
	}

	public void updateLocalization(SpamReports reports, String phone, String dialPrefix, int searches, int votes, int calls, long time) {
		if (dialPrefix == null) {
			return;
		}

		int cnt = reports.updateNumberLocalization(phone, dialPrefix, searches, votes, calls, time);
		if (cnt == 0) {
			reports.insertNumberLocalization(phone, dialPrefix, searches, votes, calls, time);
		}
	}

	/**
	 * Updates the "last-ping" of all related numbers in the same block.
	 * 
	 * <p>
	 * This ensures that numbers of mass spammers are only archived, if none of their numbers is active anymore.
	 * </p>
	 */
	private void pingRelatedNumbers(SpamReports reports, String phone, long now) {
		AggregationInfo aggregation10 = getAggregation10(reports, phone);
		if (aggregation10.getCnt() >= MIN_AGGREGATE_10) {
			String prefix = aggregation10.getPrefix();
			
			AggregationInfo aggregation100 = getAggregation100(reports, phone);
			if (aggregation100.getCnt() >= MIN_AGGREGATE_100) {
				prefix = aggregation100.getPrefix();
			}
			reports.sendPing(prefix, phone.length(), now);
		}
	}

	private static int delta(int oldVotes, int newVotes) {
		boolean wasSpam = oldVotes > 0;
		boolean isSpam = newVotes > 0;
		return wasSpam ? (isSpam ? 0 : -1) : (isSpam ? 1 : 0);
	}

	private void updateAggregation10(SpamReports reports, String phone, int deltaCnt, int deltaVotes) {
		String prefix = prefix10(phone);
		
		int rows = reports.updateAggregation10(prefix, deltaCnt, deltaVotes);
		if (rows == 0) {
			if (deltaCnt > 0) {
				reports.insertAggregation10(prefix, deltaCnt, deltaVotes);
			}
			
			// The newly inserted count is at most 1, therefore there is no update to the next aggregation level necessary. 
		} else {
			if (deltaCnt != 0) {
				// Check, whether an update to the next aggregation level is necessary.
				AggregationInfo info = reports.getAggregation10(prefix);
				if (info != null) {
					int cnt = info.getCnt();
					int votes = info.getVotes();

					int cntBefore = cnt - deltaCnt;
					if (cntBefore < MIN_AGGREGATE_10 && cnt >= MIN_AGGREGATE_10) {
						updateAggregation100(reports, phone, 1, votes);
					}
					else if (cntBefore >= MIN_AGGREGATE_10 && cnt < MIN_AGGREGATE_10) {
						int votesBefore = votes - deltaVotes;
						
						updateAggregation100(reports, phone, -1, -votesBefore);
					}
				}
			}
		}
	}

	private void updateAggregation100(SpamReports reports, String phone, int deltaCnt, int deltaVotes) {
		String prefix = prefix100(phone);

		int rows = reports.updateAggregation100(prefix, deltaCnt, deltaVotes);
		if (rows == 0) {
			if (deltaCnt > 0) {
				reports.insertAggregation100(prefix, deltaCnt, deltaVotes);
			}
		}
	}

	/** 
	 * Adds a rating for a phone number.
	 *
	 * @param userName The login name of the user creating the rating, or <code>null</code> if the rating is anonymous.
	 * @param phone The phone number to rate.
	 * @param rating The user rating.
	 * @param comment A user comment for this number.
	 * @param now The current time in milliseconds since epoch.
	 */
	public void addRating(String userName, PhoneNumer number, String dialPrefix, Rating rating, String comment, String lang, long now) {
		String phone = NumberAnalyzer.getPhoneId(number);
		
		boolean updateRequired;
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			
			boolean recordVote = true;
			Long userIdOptional = null;
			if (userName != null) {
				Users users = session.getMapper(Users.class);
				userIdOptional = users.getUserId(userName);
				if (userIdOptional == null) {
					LOG.error("User ID not found for: {}", userName);
					return;
				}
				
				BlockList blocklist = session.getMapper(BlockList.class);
				long userId = userIdOptional.longValue();
				
				Boolean state = blocklist.getPersonalizationState(userId, phone);
				
				boolean block = rating != Rating.A_LEGITIMATE;
				
				if (state != null && block == state.booleanValue()) {
					LOG.info("Ignored repeated rating for number {} ({}) by {}.", phone, rating, userName);
					recordVote = false;
				} else {
					if (state != null) {
						blocklist.removePersonalization(userId, phone);
					}
					if (block) {
						blocklist.addPersonalization(userId, phone);
					} else {
						blocklist.addExclude(userId, phone);
					}
				}
				
				DBUserSettings settings = users.getSettingsById(userId);
				if (lang == null) {
					lang = settings.getLang();
				}
				dialPrefix = settings.getDialPrefix();
			}
			
			UserComment userComment = UserComment.create()
				.setId(UUID.randomUUID().toString())
				.setUserId(userIdOptional)
				.setPhone(phone)
				.setRating(rating)
				.setComment(comment)
				.setLang(lang)
				.setCreated(now);
			
			updateRequired = addRating(reports, number, dialPrefix, userComment, recordVote);
			
			session.commit();
		}
		
		if (updateRequired) {
			_indexer.publishUpdate(number);
		}
	}
	
	/** 
	 * Adds a rating for a phone number without DB commit.
	 * @param recordVote 
	 * 
	 * @return Whether an index update is required.
	 */
	public boolean addRating(SpamReports reports, PhoneNumer number, String dialPrefix, UserComment comment, boolean recordVote) {
		boolean updateRequired = false;
		
		String phone = NumberAnalyzer.getPhoneId(number);
		Rating rating = comment.getRating();
		String commentText = comment.getComment();
		long created = comment.getCreated();
		if (commentText != null && !commentText.isBlank()) {
			if (commentText.length() > MAX_COMMENT_LENGTH) {
				// Limit to DB constraint.
				commentText = commentText.substring(0, MAX_COMMENT_LENGTH);
			}

			// Delete existing comment by this user for this phone number (ensure only one comment per user per number)
			Long userId = comment.getUserId();
			if (userId != null) {
				int deleted = reports.deleteUserComments(userId, phone);
				if (deleted > 0) {
					LOG.info("Replaced existing comment for phone {} by user ID {}.", phone, userId);
					updateRequired = true;
				}
			}

			reports.addComment(comment.getId(), phone, rating, commentText, comment.getLang(), comment.getService(), created, userId);
			updateRequired = true;
		}

		if (recordVote) {
			updateRequired |= processVotes(reports, number, dialPrefix, Ratings.getVotes(rating), created);
			if (rating != Rating.B_MISSED) {
				Rating oldRating = rating(reports, phone);

				// Record rating.
				reports.updateRating(phone, rating, 1, created);

				Rating newRating = rating(reports, phone);
				updateRequired |= oldRating != newRating;
			}

			pingRelatedNumbers(reports, phone, created);
		}
		
		return updateRequired;
	}

	private Rating rating(SpamReports reports, String phone) {
		return rating(getPhoneInfo(reports, phone));
	}

	public NumberInfo getPhoneInfo(SpamReports reports, String phone) {
		DBNumberInfo result = reports.getPhoneInfo(phone);
		if (result != null) {
			return result;
		}
		return NumberInfo.create().setPhone(phone);
	}

	/** 
	 * Retrieve the {@link Rating} for the given phone number with the maximum number of votes.
	 */
	public Rating getRating(String phone) {
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			
			return rating(reports, phone);
		}
	}

	private int classify(int newVotes) {
		if (newVotes <= 0) {
			return 0;
		}
		else if (newVotes < MIN_VOTES) {
			return 1;
		}
		else {
			return 2;
		}
	}

	/**
	 * Checks if the vote count crosses any visible blocklist threshold.
	 * Returns true if the number should be marked for version update.
	 *
	 * <p>
	 * Only thresholds at or above {@link #_minVisibleVotes} are considered,
	 * since changes below this threshold are not visible to API clients.
	 * Uses precomputed {@link #_effectiveThresholds} for efficiency.
	 * </p>
	 */
	private boolean crossesThreshold(int oldVotes, int newVotes) {
		for (int threshold : _effectiveThresholds) {
			boolean wasBelowThreshold = oldVotes < threshold;
			boolean isNowAtOrAbove = newVotes >= threshold;
			// Crossed upward: was below and is now at-or-above
			// Crossed downward: was at-or-above and is now below
			if (wasBelowThreshold == isNowAtOrAbove) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks if the given minVotes value is a valid blocklist threshold.
	 *
	 * <p>
	 * Only the predefined threshold values ({@link #BLOCKLIST_THRESHOLDS}) can be used
	 * for consistent incremental synchronization, as version updates are only triggered
	 * when numbers cross these specific thresholds.
	 * </p>
	 *
	 * @param minVotes The minimum votes value to validate
	 * @return true if the value is a valid threshold, false otherwise
	 */
	public static boolean isValidBlocklistThreshold(int minVotes) {
		for (int threshold : BLOCKLIST_THRESHOLDS) {
			if (minVotes == threshold) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns a formatted string of valid blocklist thresholds for error messages.
	 *
	 * @return A comma-separated string of all values from {@link #BLOCKLIST_THRESHOLDS}
	 */
	public static String getBlocklistThresholdsString() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < BLOCKLIST_THRESHOLDS.length; i++) {
			if (i > 0) {
				sb.append(", ");
			}
			sb.append(BLOCKLIST_THRESHOLDS[i]);
		}
		return sb.toString();
	}

	/**
	 * The time in milliseconds since epoch when the last update to the spam report table was done.
	 */
	public Long getLastSpamReport() {
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			return reports.getLastUpdate();
		}
	}

	/**
	 * Opens a session to query/update the database.
	 */
	public SqlSession openSession() {
		return _sessionFactory.openSession();
	}
	
	/**
	 * Looks up all spam reports that were done after the given time in milliseconds since epoch.
	 */
	public List<? extends NumberInfo> getLatestSpamReports(long notBefore) {
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			return reports.getLatestReports(notBefore);
		}
	}

	/**
	 * Looks up the top searches overall.
	 */
	public List<? extends NumberInfo> getTopSearchesOverall(int limit) {
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);

			return reports.getTopSearchesOverall(limit);
		}
	}
	
	/**
	 * Looks up the latest searches.
	 */
	public List<? extends SearchInfo> getTopSearches() {
		return getTopSearches(6);
	}
	
	public List<? extends SearchInfo> getTopSearches(int limit) {
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
		
			List<DBSearchInfo> searches = reports.getTopSearchesCurrent(limit);
			
			searches.sort(Comparator.<DBSearchInfo>comparingLong(s -> s.getLastSearch()).reversed());
			return searches;
		}
	}
	
	public long midnightYesterday() {
		GregorianCalendar calendar = new GregorianCalendar();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		calendar.add(Calendar.DAY_OF_MONTH, -1);
		return calendar.getTimeInMillis();
	}

	/**
	 * Looks all spam reports.
	 */
	public List<DBNumberInfo> getAll(int limit) {
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			return reports.getAll(limit);
		}
	}
	
	/**
	 * Looks up spam reports with the most votes in the last month.
	 */
	public List<DBNumberInfo> getTopSpamReports(int cnt) {
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			return reports.getTopSpammers(cnt);
		}
	}
	
	/**
	 * Looks up the newest entries in the blocklist.
	 */
	public List<DBNumberInfo> getLatestBlocklistEntries(int minVotes) {
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			return reports.getLatestBlocklistEntries(minVotes);
		}
	}

	/**
	 * Looks up all entries in the blocklist.
	 *
	 * <p>
	 * Returns all active numbers (votes > 0) without any filtering.
	 * No user-specific filtering (whitelist/blacklist) is applied.
	 * No vote threshold filtering is applied - clients must filter by their preferred threshold.
	 * This allows the response to be cached and served identically to all users, improving efficiency,
	 * and ensures clients can detect when numbers drop below their threshold.
	 * </p>
	 */
	/**
	 * Gets the full blocklist for API download.
	 *
	 * <p>
	 * Numbers with fewer votes than {@link #getMinVisibleVotes()} are excluded from the result.
	 * </p>
	 *
	 * @return The blocklist containing all numbers meeting the minimum threshold.
	 */
	public Blocklist getBlockListAPI() {
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			Users users = session.getMapper(Users.class);

			List<BlockListEntry> numbers = reports.getBlocklist()
					.stream()
					.map(DB::toBlocklistEntry)
					.filter(Objects::nonNull)
					.filter(entry -> entry.getVotes() >= _minVisibleVotes)
					.collect(Collectors.toList());

			String versionStr = users.getProperty("blocklist.version");
			long version = (versionStr != null) ? Long.parseLong(versionStr) : INITIAL_BLOCKLIST_VERSION;

			return Blocklist.create()
					.setNumbers(numbers)
					.setVersion(version);
		}
	}

	/**
	 * Gets blocklist changes since the given version (incremental sync).
	 * Returns entries with VERSION > sinceVersion.
	 *
	 * <p>
	 * Entries with votes below {@link #getMinVisibleVotes()} are returned with votes=0,
	 * indicating they should be removed from the client's local blocklist.
	 * No user-specific filtering (whitelist/blacklist) is applied.
	 * This allows the response to be cached and served identically to all users, improving efficiency.
	 * </p>
	 *
	 * @param sinceVersion Return only changes since this version number.
	 * @return The blocklist changes since the specified version.
	 */
	public Blocklist getBlocklistUpdateAPI(long sinceVersion) {
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			Users users = session.getMapper(Users.class);

			List<BlockListEntry> numbers = reports.getBlocklistChangesSince(sinceVersion)
					.stream()
					.map(DB::toBlocklistEntry)
					.filter(Objects::nonNull)
					.map(entry -> {
						// Entries below the visible threshold are returned as deletions (votes=0)
						if (entry.getVotes() < _minVisibleVotes) {
							return entry.setVotes(0);
						}
						return entry;
					})
					.collect(Collectors.toList());

			String versionStr = users.getProperty("blocklist.version");
			long version = (versionStr != null) ? Long.parseLong(versionStr) : INITIAL_BLOCKLIST_VERSION;

			return Blocklist.create()
					.setNumbers(numbers)
					.setVersion(version);
		}
	}
	
	private static BlockListEntry toBlocklistEntry(DBNumberInfo n) {
		PhoneNumer number = NumberAnalyzer.analyzePhoneID(n.getPhone());
		if (number == null) {
			// Invalid number in DB, filter out.
			return null;
		}
		return BlockListEntry.create()
				.setPhone(number.getPlus())
				.setVotes(normalizeVotesToThreshold(n.getVotes()))
				.setRating(rating(n));
	}

	/**
	 * Normalizes a vote count to the nearest threshold value.
	 *
	 * <p>
	 * This ensures consistency between when updates are triggered (at threshold crossings)
	 * and the vote counts transmitted to clients. A number with 5 votes (between thresholds
	 * 4 and 10) is normalized to 4, since no update would be sent until it reaches 10 votes.
	 * </p>
	 *
	 * @param votes The actual vote count.
	 * @return The normalized threshold value (0 if below the lowest threshold).
	 */
	private static int normalizeVotesToThreshold(int votes) {
		// Find the highest threshold that votes meets or exceeds
		// Search from high to low for efficiency (most numbers have higher votes)
		for (int i = BLOCKLIST_THRESHOLDS.length - 1; i >= 0; i--) {
			if (votes >= BLOCKLIST_THRESHOLDS[i]) {
				return BLOCKLIST_THRESHOLDS[i];
			}
		}
		// Below all thresholds
		return 0;
	}

	public static Rating rating(NumberInfo n) {
		if (n.getVotes() <= 0) {
			return Rating.A_LEGITIMATE;
		}
		
		Rating result = Rating.B_MISSED;
		int max = 0;

		{
			int votes = n.getRatingFraud();
			if (votes > max) {
				result = Rating.G_FRAUD;
				max = votes;
			}
		}
		{
			int votes = n.getRatingGamble();
			if (votes > max) {
				result = Rating.F_GAMBLE;
				max = votes;
			}
		}
		{
			int votes = n.getRatingAdvertising();
			if (votes > max) {
				result = Rating.E_ADVERTISING;
				max = votes;
			}
		}
		{
			int votes = n.getRatingPoll();
			if (votes > max) {
				result = Rating.D_POLL;
				max = votes;
			}
		}
		{
			int votes = n.getRatingPing();
			if (votes > max) {
				result = Rating.C_PING;
				max = votes;
			}
		}
	
		return result;
	}

	private DBUserSettings getUserSettings(Users users, String login) {
		DBUserSettings result = users.getSettingsRaw(login);

		// For legacy compatibility (e-mail addresses as display name).
		result.setDisplayName(DB.toDisplayName(result.getDisplayName()));
		
		return result;
	}

	/**
	 * Guesses a display name from an e-mail address.
	 */
	public static String toDisplayName(String email) {
		int atIndex = email.indexOf('@');
		if (atIndex > 0) {
			email = email.substring(0, atIndex);
		}
		
		StringBuilder result = new StringBuilder();
		boolean first = true;
		for (String part : email.replace('.', ' ').replace('_', ' ').split("\\s+")) {
			if (part.length() == 0) {
				continue;
			}
			
			if (first) {
				first = false;
			} else {
				result.append(' ');
			}
			
			boolean firstPart = true;
			for (String subPart : part.split("-")) {
				if (subPart.length() == 0) {
					continue;
				}
		
				if (firstPart) {
					firstPart = false;
				} else {
					result.append('-');
				}
				result.append(Character.toUpperCase(subPart.charAt(0)));
				result.append(subPart.substring(1));
			}
		}
		
		return result.toString();
	}
	
	/**
	 * The current DB status.
	 */
	public Status getStatus(int minVotes) {
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			return new Status(
					reports.getStatistics(minVotes),
					nonNull(reports.getTotalVotes()),
					nonNull(reports.getArchivedReportCount()));
		}
	}

	/**
	 * The total number of archived reports.
	 */
	public int getArchivedReportCount() {
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			return nonNull(reports.getArchivedReportCount());
		}
	}
	
	/**
	 * The total number of active reports.
	 */
	public int getActiveReportCount() {
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			return nonNull(reports.getActiveReportCount());
		}
	}
	
	private static int nonNull(Integer n) {
		return n == null ? 0 : n.intValue();
	}

	/**
	 * The number of votes that are stored for the given phone number.
	 */
	public int getVotesFor(String phone) {
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			return nonNull(reports.getVotes(phone));
		}
	}

	public PhoneInfo getPhoneApiInfo(String phone) {
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			return getPhoneApiInfo(reports, phone);
		}
	}

	public PhoneInfo getPhoneApiInfo(SpamReports reports, String phoneId) {
		if (reports.isWhiteListed(phoneId)) {
			return NumberAnalyzer.phoneInfoFromId(phoneId)
				.setWhiteListed(true)
				.setRating(Rating.A_LEGITIMATE);
		}

		NumberInfo info = getPhoneInfo(reports, phoneId);
		AggregationInfo aggregation10 = getAggregation10(reports, phoneId);
		AggregationInfo aggregation100 = getAggregation100(reports, phoneId);
		
		return getPhoneInfo(info, aggregation10, aggregation100);
	}

	public PhoneInfo getPhoneInfo(NumberInfo info, AggregationInfo aggregation10, AggregationInfo aggregation100) {
		PhoneInfo result = NumberAnalyzer.phoneInfoFromId(info.getPhone())
			.setDateAdded(info.getAdded())
			.setLastUpdate(info.getUpdated());
		
		Rating rating = rating(info);
		int votesWildcard;
		if (aggregation100.getCnt() >= MIN_AGGREGATE_100) {
			votesWildcard = aggregation100.getVotes();
			if (!info.isActive()) {
				// Direct votes did not count yet.
				votesWildcard += info.getVotes();
			}
			
			if (aggregation10.getCnt() < MIN_AGGREGATE_10) {
				// The votes of this number did not yet count to the aggregation of the block.
				votesWildcard += aggregation10.getVotes();
			}
		} else if (aggregation10.getCnt() >= MIN_AGGREGATE_10) {
			votesWildcard = aggregation10.getVotes();
			if (!info.isActive()) {
				// Direct votes did not count yet.
				votesWildcard += info.getVotes();
			}
		} else {
			votesWildcard = info.getVotes();
			result.setArchived(!info.isActive());
		}
		
		result.setVotes(info.getVotes());
		result.setVotesWildcard(votesWildcard);
		result.setRating(rating);
		
		return result;
	}

	public AggregationInfo getAggregation100(SpamReports reports, String phone) {
		String prefix = prefix100(phone);
		AggregationInfo result = reports.getAggregation100(prefix);
		return notNull(prefix, result);
	}

	public AggregationInfo getAggregation10(SpamReports reports, String phone) {
		String prefix = prefix10(phone);
		AggregationInfo result = reports.getAggregation10(prefix);
		return notNull(prefix, result);
	}

	public AggregationInfo notNull(String prefix, AggregationInfo result) {
		return result == null ? new AggregationInfo(prefix, 0, 0) : result;
	}

	private String prefix10(String phone) {
		int length = phone.length() - 1;
		return length < 0 ? "" : phone.substring(0, length);
	}

	private String prefix100(String phone) {
		int length = phone.length() - 2;
		return length < 0 ? "" : phone.substring(0, length);
	}
	
	/**
	 * Records a search hit for the given phone number.
	 */
	public void addSearchHit(PhoneNumer phone, String dialPrefix) {
		long now = System.currentTimeMillis();
		addSearchHit(phone, dialPrefix, now);
	}

	void addSearchHit(PhoneNumer phone, String dialPrefix, long now) {
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			
			addSearchHit(reports, phone, dialPrefix, now);
			
			session.commit();
		}
	}

	/**
	 * Records a search hit for the given phone number.
	 */
	public void addSearchHit(SpamReports reports, PhoneNumer number, String dialPrefix, long now) {
		String phone = NumberAnalyzer.getPhoneId(number);
		
		int rows = reports.incSearchCount(phone, now);
		if (rows == 0) {
			byte[] hash = NumberAnalyzer.getPhoneHash(number);
			reports.addReport(phone, hash, 0, now);
			reports.incSearchCount(phone, now);
		}
		
		pingRelatedNumbers(reports, phone, now);
		
		updateLocalization(reports, phone, dialPrefix, 1, 0, 0, now);
	}
	
	/**
	 * Shuts down the database layer.
	 */
	public void shutdown() {
		for (ScheduledFuture<?> task : _tasks) {
			task.cancel(false);
		}
		
		if (_dataSource != null) {
			try (Connection connection = _dataSource.getConnection()) {
				try (Statement statement = connection.createStatement()) {
					statement.execute("SHUTDOWN COMPACT");
				}
			} catch (Exception ex) {
				LOG.error("Database shutdown failed.", ex);
			}
		}
	}

	/** 
	 * Adds the given user with the given password.
	 */
	public void addUser(String login, String displayName, String lang, String dialPrefix, String passwd) {
		try (SqlSession session = openSession()) {
			Users users = session.getMapper(Users.class);
			users.addUser(login, displayName, lang, dialPrefix, pwhash(passwd), System.currentTimeMillis());
			session.commit();
		}
	}

	/** 
	 * Sets a new password for the given user.
	 * 
	 * @return The new password, or <code>null</code>, if the given user does not exist.
	 */
	public String resetPassword(String login) {
		try (SqlSession session = openSession()) {
			Users users = session.getMapper(Users.class);
			
			Long userId = users.getUserId(login);
			if (userId != null) {
				String passwd = createPassword(20);
				
				users.setPassword(userId.longValue(), pwhash(passwd));
				session.commit();
				return passwd;
			}
		}
		return null;
	}
	
	/** 
	 * The local user ID, or <code>null</code>, if the user with the given login does not yet exist.
	 * 
	 * @param googleId the user's ID in the Google OpenID provider.
	 */
	public String getGoogleLogin(String googleId) {
		try (SqlSession session = openSession()) {
			Users users = session.getMapper(Users.class);
			
			return users.getGoogleLogin(googleId);
		}
	}

	/** 
	 * The local user ID, or <code>null</code>, if the user with the given login does not yet exist.
	 * 
	 * @param email the user's e-mail address.
	 */
	public String getEmailLogin(String email) throws AddressException {
		try (SqlSession session = openSession()) {
			Users users = session.getMapper(Users.class);
			
			return users.getEmailLogin(canonicalEMail(email));
		}
	}

	private String canonicalEMail(String email) throws AddressException {
		InternetAddress address = new InternetAddress(email);
		return address.getAddress().strip().toLowerCase();
	}
	
	/**
	 * Sets the last access time for the given user to the given timestamp.
	 *
	 * @param userAgent The user agent header string.
	 * @param settings The user settings (from request attribute).
	 */
	public void updateLastAccess(String login, long timestamp, String userAgent, UserSettings settings) {
		try (SqlSession session = openSession()) {
			Users users = session.getMapper(Users.class);

			Long before = users.getLastAccess(login);
			users.setLastAccess(login, timestamp, userAgent);
			session.commit();

			if (before == null || before.longValue() == 0) {
				// This was the first access, send welcome message;
				MailService mailService = _mailService;
				if (mailService != null && _config.isSendWelcomeMails()) {
					users.markWelcome(settings.getId());
					session.commit();

					_scheduler.executor().submit(() -> mailService.sendWelcomeMail(settings));
				} else {
					LOG.info("Not sending welcome mail to '{}': {}", login,
						mailService == null ? "No mail service." : "Welcome mails are disabled.");
				}
			}
		}
	}
	
	/** 
	 * Retrieves the settings for a user.
	 */
	public UserSettings getSettings(String login) {
		try (SqlSession session = openSession()) {
			Users users = session.getMapper(Users.class);
			
			return getUserSettings(users, login);
		}
	}
	
	/** 
	 * Updates the settings for a user.
	 */
	public void updateSettings(UserSettings settings) {
		try (SqlSession session = openSession()) {
			Users users = session.getMapper(Users.class);
			
			users.updateSettings(settings.getId(), settings.getMinVotes(), settings.getMaxLength(), settings.isWildcards(), settings.getDialPrefix(), settings.isNationalOnly());
			session.commit();
		}
	}
	
	/**
	 * Checks credentials in the given authorization header.
	 * Ignores whitespaces surrounding username or password.
	 * @param userAgent
	 *
	 * @return The authentication context if authorization was successful, <code>null</code> otherwise.
	 */
	public AuthContext basicAuth(String authHeader, String userAgent) throws IOException {
		if (authHeader.startsWith(BASIC_AUTH_PREFIX)) {
			String credentials = authHeader.substring(BASIC_AUTH_PREFIX.length());
			byte[] decodedBytes = Base64.getDecoder().decode(credentials);
			String decoded = new String(decodedBytes, StandardCharsets.UTF_8);
			int sepIndex = decoded.indexOf(':');
			if (sepIndex >= 0) {
				String login = decoded.substring(0, sepIndex).trim();
				String passwd = decoded.substring(sepIndex + 1).trim();

				if (passwd.startsWith(TOKEN_VERSION)) {
					// Compatibility - FRITZ!Box cannot send bearer tokens.
					AuthContext authContext = checkAuthToken(passwd, System.currentTimeMillis(), userAgent, false);
					if (authContext == null) {
						LOG.warn("Invalid token received from {}.", login);
						return null;
					}
					if (!login.equals(authContext.getUserName())) {
						LOG.warn("User name mismatch in token authorization: {} vs. {}", login, authContext.getUserName());
						return null;
					}
					return authContext;
				} else {
					AuthContext result = login(login, passwd);
					if (result != null) {
						result.getAuthorization().setUserAgent(userAgent);
					}
					return result;
				}
			}
		}
		LOG.warn("Invalid authentication received: {}", authHeader);
		return null;
	}

	/**
	 * Checks the given credentials.
	 *
	 * @return The authentication context if authorization was successful, <code>null</code> otherwise.
	 */
	public AuthContext login(String login, String passwd) throws IOException {
		byte[] pwhash = pwhash(passwd);

		try (SqlSession session = openSession()) {
			Users users = session.getMapper(Users.class);

			InputStream hashIn = users.getHash(login);
			if (hashIn != null) {
				byte[] expectedHash = hashIn.readAllBytes();
				if (Arrays.equals(pwhash, expectedHash)) {
					long userId = users.getUserId(login).longValue();
					AuthToken authorization = createMasterLoginToken(login, userId);
					DBUserSettings settings = getUserSettings(users, login);
					return new AuthContext(authorization, settings);
				} else {
					LOG.warn("Invalid password (length " + passwd.length() + ") for user: " + login);
				}
			} else {
				LOG.warn("Invalid user name supplied: '" + saveChars(login) + "'");
			}
		}
		return null;
	}

	public AuthContext createMasterLoginToken(String login) {
		try (SqlSession session = openSession()) {
			Users users = session.getMapper(Users.class);

			Long userId = users.getUserId(login);
			if (userId == null) {
				return null;
			}
			AuthToken token = createMasterLoginToken(login, userId.longValue());
			UserSettings settings = getUserSettings(users, login);
			return new AuthContext(token, settings);
		}
	}

	private static AuthToken createMasterLoginToken(String login, long userId) {
		return AuthToken.create()
			.setUserName(login)
			.setUserId(userId)
			.setAccessCarddav(true)
			.setAccessDownload(true)
			.setAccessLogin(true)
			.setAccessQuery(true)
			.setAccessRate(true);
	}

	public static String saveChars(String login) {
		for (int n = 0, cnt = login.length(); n < cnt; n++) {
			char ch = login.charAt(n);
			if (!(ch > 32 && ch < 128)) {
				return quote(login);
			}
		}
		return login;
	}

	private static String quote(String login) {
		StringBuilder result = new StringBuilder();
		result.append('"');
		for (int n = 0, cnt = login.length(); n < cnt; n++) {
			char ch = login.charAt(n);
			if (!(ch > 32 && ch < 128)) {
				result.append('"');
				result.append(' ');
				result.append("0x");
				result.append(Integer.toHexString(ch).toUpperCase());
				result.append(' ');
				result.append('"');
			} else {
				result.append(ch);
			}
		}
		result.append('"');
		return result.toString();
	}

	private byte[] pwhash(String passwd) {
		return sha256().digest(passwd.getBytes(StandardCharsets.UTF_8));
	}

	private void cleanup() {
		LOG.info("Starting DB cleanup.");
		
		Calendar cal = GregorianCalendar.getInstance();
		cal.add(Calendar.DAY_OF_MONTH, -OLD_VOTE_DAYS);
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.MILLISECOND, 0);
		long before = cal.getTimeInMillis();
		
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			
			int archived = reports.archiveReportsWithLowVotes(before, MIN_VOTES, WEEK_PER_VOTE);
			
			LOG.info("Archived " + archived + " reports.");
			
			session.commit();
		} catch (Exception ex) {
			LOG.error("Failed to cleanup DB.", ex);
		}
		
		LOG.info("Finished DB cleanup.");
	}

	/**
	 * Checks for inactive users and sends support mail to them.
	 */
	public void sendWelcomeMails() {
		try {
			trySendWelcomeMails();
		} catch (Exception ex) {
			LOG.error("Failed to send welcome mails.", ex);
		}
	}
	
	private void trySendWelcomeMails() {
		LOG.info("Processing welcome mails.");

		GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("GMT"));
		calendar.set(Calendar.MILLISECOND, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.HOUR, 0);
		calendar.add(Calendar.HOUR, -1);
		calendar.add(Calendar.DAY_OF_MONTH, -2);
		
		long accessAfter = calendar.getTimeInMillis();

		calendar.add(Calendar.DAY_OF_MONTH, -30);
		long registeredAfter = calendar.getTimeInMillis();
		
		try (SqlSession session = openSession()) {
			Users users = session.getMapper(Users.class);
			
			// Users that did not update the address book for three days.
			List<DBUserSettings> inactiveUsers = users.getUsersWithoutWelcome(registeredAfter, accessAfter);
			for (DBUserSettings user : inactiveUsers) {
				// Mark mail as send to prevent mail-bombing a user under all circumstances. Sending
				// a support mail is not tried again, if the first attempt fails.
				users.markWelcome(user.getId());
				session.commit();
				
				_mailService.sendWelcomeMail(user);
			}
		}
	}

	/**
	 * Checks for inactive users and sends support mail to them.
	 */
	public void sendSupportMails() {
		try {
			trySendSupportMails();
		} catch (Exception ex) {
			LOG.error("Failed to send support mails.", ex);
		}
	}
	
	private void trySendSupportMails() {
		LOG.info("Processing support mails.");

		GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("GMT"));
		calendar.set(Calendar.MILLISECOND, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.HOUR, 0);
		calendar.add(Calendar.HOUR, -1);
		calendar.add(Calendar.DAY_OF_MONTH, -3);
		
		long twoDaysBefore = calendar.getTimeInMillis();

		calendar.add(Calendar.DAY_OF_MONTH, -30);
		long oneMonthBefore = calendar.getTimeInMillis();
		
		try (SqlSession session = openSession()) {
			Users users = session.getMapper(Users.class);
			
			// Users that did not update the address book for three days.
			List<DBUserSettings> inactiveUsers = users.getNewInactiveUsers(twoDaysBefore, oneMonthBefore, twoDaysBefore);
			if (inactiveUsers.size() > 50) {
				LOG.warn("Excessive amount of inactive users found ({}), not sending help mails.", inactiveUsers.size());
				return;
			}
			for (DBUserSettings user : inactiveUsers) {
				// Mark mail as send to prevent mail-bombing a user under all circumstances. Sending
				// a support mail is not tried again, if the first attempt fails.
				users.markNotified(user.getId());
				session.commit();

				// Do not bother users who have answerbots with missing blocklist update. Those may not even 
				// have a blocklist installed. This check must be reconsidered, if an explicit blocklist 
				// account can be created.
				List<DBAnswerbotInfo> answerBots = users.getAnswerBots(user.getId());
				if (answerBots.size() == 0) {
					_mailService.sendHelpMail(user);
				} 
			}
		}
	}
	
	private void runUpdateHistory() {
		LOG.info("Processing history.");
		try {
			updateHistory(365);
		} catch (Exception ex) {
			LOG.error("Failed to process history.", ex);
		}
		LOG.info("Finished procssing history.");	}

	/** 
	 * Creates a new snapshot of search history.
	 */
	public void updateHistory(int maxHistory) {
		long now = System.currentTimeMillis();
		updateHistory(maxHistory, now);
	}

	public void updateHistory(int maxHistory, long now) {
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			
			Rev newRev = new Rev(now);
			reports.storeRevision(newRev);
			
			int newRevId = newRev.getId();
			
			Long lastRevDate = reports.getRevisionDate(newRevId - 1);
			long lastSnapshot = lastRevDate == null ? 0 : lastRevDate.longValue();
			int updateCnt = reports.outdateHistorySnapshot(newRevId, lastSnapshot);
			int insertCnt = reports.createHistorySnapshot(newRevId, lastSnapshot);
			reports.updateSearches(lastSnapshot);
			
			LOG.info("Created revision {} with {} new and {} updated search entries.", newRevId, insertCnt - updateCnt, updateCnt);
			
			int oldestRev = reports.getOldestRevision();
			if (newRevId - oldestRev >= maxHistory) {
				int removedCnt = reports.cleanRevision(oldestRev);
				reports.removeRevision(oldestRev);
				
				LOG.info("Dropped revision {} with {} search entries.", oldestRev, removedCnt);
			}
			
			session.commit();
		}
	}
	
	/** 
	 * Retrieves the number of search hits for the given number in the past days.
	 * 
	 * <p>
	 * The entry last in the list represents the searches recorded today.
	 * </p>
	 */
	public List<Integer> getSearchHistory(String phone, int size) {
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			
			return getSearchHistory(reports, phone, size);
		}
	}

	public List<Integer> getSearchHistory(SpamReports reports, String phone, int size) {
		List<Integer> result = new ArrayList<>();

		int latestRev;
		int rev;
		List<DBNumberHistory> entries;
		
		Integer lastRevision = reports.getLastRevision();
		if (lastRevision != null) {
			latestRev = lastRevision.intValue();
			rev = latestRev  - (size - 1);
			entries = reports.getSearchHistory(rev, phone);
		} else {
			latestRev = (size - 1);
			rev = 0;
			entries = Collections.emptyList();
		}
		
		int lastCnt = 0;
		for (DBNumberHistory entry : entries) {
			while (entry.getRMin() > rev) {
				result.add(Integer.valueOf(0));
				rev++;
			}
			int current = entry.getSearches();
			result.add(Integer.valueOf(current - lastCnt));
			lastCnt = current;
			rev++;
		}
		
		while (rev <= latestRev) {
			result.add(Integer.valueOf(0));
			rev++;
		}
		
		// Add searches today (not yet contained in the history)
		NumberInfo info = getPhoneInfo(reports, phone);
		result.add(info.getSearches() - lastCnt);
		
		// Drop first, because this entry contains no delta information.
		return result.subList(1, result.size());
	}

	/** 
	 * The call report context for the given user.
	 */
	public ReportInfo getCallReportInfo(String login) {
		try (SqlSession session = openSession()) {
			Users users = session.getMapper(Users.class);

			long userId = users.getUserId(login);
			DBReportInfo info = users.getReportInfo(userId);
			if (info == null) {
				return ReportInfo.create();
			}
			return info;
		}
	}

	/** 
	 * Update the call report for the given user.
	 */
	public void storeCallReport(String login, CallReport callReport) {
		try (SqlSession session = openSession()) {
			Users users = session.getMapper(Users.class);
			SpamReports reports = session.getMapper(SpamReports.class);
			long now = System.currentTimeMillis();
		
			long userId = users.getUserId(login);
			String dialPrefix = users.getDialPrefix(login);
			int cnt = users.updateReportInfo(userId, callReport.getTimestamp(), callReport.getLastid(), now);
			if (cnt == 0) {
				users.createReportInfo(userId, callReport.getTimestamp(), callReport.getLastid(), now);
			}
			
			for (String phoneText : callReport.getCallers()) {
				PhoneNumer number = NumberAnalyzer.parsePhoneNumber(phoneText, dialPrefix);
				if (number == null) {
					continue;
				}
				
				String phoneId = NumberAnalyzer.getPhoneId(number);
				
				int ok = users.addCall(userId, phoneId, now);
				if (ok == 0) {
					users.insertCaller(userId, phoneId, now);
				}
				
				processVotesAndPublish(reports, number, dialPrefix, 2, now);
			}
			
			session.commit();
		}
	}

	public int getUsers() {
		try (SqlSession session = openSession()) {
			Users users = session.getMapper(Users.class);
			return users.getUserCount();
		}
	}

	public int getInactiveUsers() {
		try (SqlSession session = openSession()) {
			Users users = session.getMapper(Users.class);
			return users.getInactiveUserCount(System.currentTimeMillis() - 24 * 60 * 60 * 1000);
		}
	}

	public int getVotes() {
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			return nonNull(reports.getTotalVotes());
		}
	}

	public int getRatings() {
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			return nonNull(reports.getTotalRatings());
		}
	}

	public int getSearches() {
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			return nonNull(reports.getTotalSearches());
		}
	}

	private MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException ex) {
			throw new RuntimeException("Digest algorithm not supported: " + ex.getMessage(), ex);
		}
	}

	/**
	 * Cleans up old call records for a specific bot.
	 * 
	 * @param users The database mapper
	 * @param bot The bot information including retention settings
	 * @return Number of records cleaned up
	 */
	public int removeOutdatedCalls(Users users, AnswerbotInfo bot) {
	    RetentionPeriod retentionPeriod = bot.getRetentionPeriod();
	    if (retentionPeriod == RetentionPeriod.NEVER) {
	        return 0;
	    }
	    
	    long cutoffTime = System.currentTimeMillis() - 24 * 60 * 60 * 1000 * switch (retentionPeriod) {
			case MONTH -> 30L;
			case NEVER -> throw new AssertionError("Unreachable.");
			case QUARTER -> 90L;
			case WEEK -> 7L;
			case YEAR -> 365L;
	    };
	    
	    int deleted = users.deleteCallsOlderThan(bot.getId(), cutoffTime);
	    
	    LOG.debug("Cleaned {} calls older than {} for bot {} (cutoff: {}).", 
	            deleted, retentionPeriod, bot.getId(), cutoffTime);
	    
		return deleted;
	}

	public static long getLastSearch(Users users) {
		String value = users.getProperty("imap.lastSearch");
		long lastSearch;
		if (value == null) {
			lastSearch = 0;
		} else {
			lastSearch = Long.parseLong(value);
		}
		return lastSearch;
	}

	public static void setLastSearch(Users users, long lastSearch) {
		int ok = users.updateProperty("imap.lastSearch", Long.toString(lastSearch));
		if (ok == 0) {
			users.addProperty("imap.lastSearch", Long.toString(lastSearch));
		}
	}

	public static boolean processContribution(Users users, MessageDetails messageDetails) {
		DBContribution existing = users.getContribution(messageDetails.tx);
		if (existing != null) {
			LOG.info("Skipping already recorded donation from {}.", messageDetails.sender);
			return false;
		}
		
		recordContribution(users, messageDetails);
		return true;
	}

	private static void recordContribution(Users users, MessageDetails messageDetails) {
		Long userId;
		if (messageDetails.uid == null) {
			userId = unique(messageDetails.sender, users.usersWithDisplayName(messageDetails.sender));
		} else {
			userId = unique(messageDetails.uid, users.findUser(messageDetails.uid + "%"));
		}
		
		LOG.info("Recording donation from {}/{} ({} Ct).", messageDetails.sender, messageDetails.uid, messageDetails.amount);
		
		users.insertContribution(Contribution.create()
			.setUserId(userId)
			.setSender(messageDetails.sender)
			.setAmount(messageDetails.amount)
			.setReceived(messageDetails.date.getTime())
			.setMessage(messageDetails.msg)
			.setTx(messageDetails.tx)
		);
		
		if (userId != null) {
			users.addContribution(userId.longValue(), messageDetails.amount);
		}
	}

	private static Long unique(String uid, List<Long> users) {
		Long result = users.size() == 1 ? users.get(0) : null;
		if (result == null) {
			LOG.warn("User for user {} not found ({} matches).", uid, users.size());
		}
		return result;
	}


}
