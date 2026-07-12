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
import de.haumacher.phoneblock.credits.MessageDetails;
import de.haumacher.phoneblock.db.config.DBConfig;
import de.haumacher.phoneblock.db.settings.AuthToken;
import de.haumacher.phoneblock.db.settings.Contribution;
import de.haumacher.phoneblock.db.settings.UserSettings;
import de.haumacher.phoneblock.index.IndexUpdateService;
import de.haumacher.phoneblock.mail.MailService;
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
	 * Minimum number of {@link Rating#A_LEGITIMATE} ratings required before details (comments, searches, rating
	 * breakdown) are shown publicly. Numbers with fewer positive ratings are likely personal contacts, and showing
	 * details would leak private information.
	 */
	public static final int MIN_LEGITIMATE = 4;

	// Removed in #342: the soft-delete ACTIVE flag and the Heat-based archive
	// sweep that maintained it. Decay-aware visibility
	// (`(SPAM_EVIDENCE - LEGIT_EVIDENCE) >= maxRawSpam`) handles every
	// read-path decision; long-faded rows accumulate until #341 introduces
	// hard delete.

	private static final String SAVE_CHARS = "23456789qwertzuiopasdfghjkyxcvbnmQWERTZUPASDFGHJKLYXCVBNM";

	private static final Collection<String> TABLE_NAMES = Arrays.asList(
		"USERS"
	);
	
	private SqlSessionFactory _sessionFactory;
	private DataSource _dataSource;

	private static final String BASIC_AUTH_PREFIX = "Basic ";

	/** Distinct currently-spam numbers a /10 block needs to be a spam block on its own (#300). */
	public static final int MIN_AGGREGATE_10 = 4;

	/** Kept for the legacy {@code /check-prefix} CNT filter; the /100 gate is spread×mass below. */
	public static final int MIN_AGGREGATE_100 = 3;

	/**
	 * Minimum displayed net votes for a single number to count as a block "member" (#300
	 * follow-up). {@code decode(SPAM−LEGIT)} rounded must be {@code >= 2}.
	 */
	public static final int MIN_MEMBER_VOTES = 2;

	/**
	 * /100 spread×mass gate (#300 follow-up): a /100 is a spam block when it has at least
	 * {@link #SPREAD_MIN_NUMBERS} currently-spam members spread over at least
	 * {@link #SPREAD_MIN_TENS} distinct /10 sub-blocks (each contributing /10 having at least
	 * {@link #SPREAD_TEN_CONTRIB} members). Catches the thinly-spread spammer that a single dense
	 * /10 would miss, while one dense /10 alone (one sub-block) never lifts its /100.
	 */
	public static final int SPREAD_MIN_NUMBERS = 8;
	public static final int SPREAD_MIN_TENS = 4;
	public static final int SPREAD_TEN_CONTRIB = 2;

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
	 * Numbers crossing this threshold are marked for version updates.
	 * Default: 10 (same as {@link #DEFAULT_MIN_VISIBLE_VOTES}).
	 */
	private int _minVisibleVotes = DEFAULT_MIN_VISIBLE_VOTES;

	/**
	 * Default minimum votes threshold for blocklist visibility.
	 */
	public static final int DEFAULT_MIN_VISIBLE_VOTES = 10;

	public DB(DataSource dataSource, SchedulerService scheduler) throws SQLException {
		this(new SecureRandom(), DBConfig.create(), dataSource, IndexUpdateService.NONE, scheduler, null);
	}
	
	/** 
	 * Creates a {@link DB}.
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
		configuration.addMapper(MigrationStatements.class);
		configuration.addMapper(BlockList.class);
		configuration.addMapper(Users.class);
		configuration.addMapper(FtcReports.class);
		configuration.addMapper(de.haumacher.phoneblock.diag.DiagnosticsMapper.class);
		_sessionFactory = new SqlSessionFactoryBuilder().build(configuration);
		
		setupSchema();

		 Date timeHistory = schedule(0, this::runActivityRetention);
		 LOG.info("Scheduled activity-ledger retention: " + timeHistory);
		 
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
	 * Numbers crossing this threshold will be marked for blocklist version updates.
	 * This should be called during initialization, before any votes are processed.
	 * </p>
	 *
	 * @param minVisibleVotes The minimum votes threshold. Must be a positive integer.
	 */
	public void setMinVisibleVotes(int minVisibleVotes) {
		if (minVisibleVotes < 1) {
			LOG.warn("Invalid minVisibleVotes {}, must be positive. Using default {}", minVisibleVotes, DEFAULT_MIN_VISIBLE_VOTES);
			minVisibleVotes = DEFAULT_MIN_VISIBLE_VOTES;
		}
		_minVisibleVotes = minVisibleVotes;
		LOG.info("Blocklist minVisibleVotes set to: {}", _minVisibleVotes);
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
		        				votes = info.getRawVotes();
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

		        // #300 follow-up: the former "aggregate lastPing per block" computation was
		        // removed — LASTPING-based inactivity archiving is gone (a number's blocklist
		        // life is its evidence decay), so propagating a block last-ping is dead work.
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

					if (version == 13) {
						populateAggregationHashes(reports);
					}

					if (version == 17) {
						populatePersonalizationHashes(session);
					}

					if (version == 29) {
						backfillConfidenceEmas(session.getMapper(MigrationStatements.class));
					}

					if (version == 30) {
						backfillNumbersLocaleHeat(session.getMapper(MigrationStatements.class));
					}

					if (version == 31) {
						backfillVisibilityColumns(session.getMapper(MigrationStatements.class));
					}

					if (version == 32) {
						backfillSnapshotLegitEvidence(session.getMapper(MigrationStatements.class));
					}

					// migration 33 drops legacy tables CALLREPORT / CALLERS via the
					// script; no Java hook needed.

					// migration 34 drops the obsolete ACTIVE column and its indexes
					// via the script; no Java hook needed.

					// migration 35 drops the legacy SEARCHES / SPAMREPORTS /
					// BLOCKLIST / EXCLUDES / OLDREPORTS / RATINGS / RATINGHISTORY
					// tables (plus the pre-baseline SEARCHCLUSTER /
					// SEARCHHISTORY and the renamed-away SPAMREPORTS_10 /
					// SPAMREPORTS_100) via the script; no Java hook needed.

					// migration 36 adds NUMBERS_VOTES_IDX for the status page's
					// top-spammers list via the script; no Java hook needed.

					// migration 37 clears the SHA1 hash of numbers that are not
					// spam-visible (SPAM_EVIDENCE <= LEGIT_EVIDENCE), retiring the
					// stale rainbow-table entries left by the old search/meta insert
					// paths (#300); no Java hook needed.

					// migration 38 adds NUMBERS_HISTORY_PHONE_IDX (PHONE, RMIN) and
					// NUMBERS_HISTORY_RMIN_IDX (RMIN) so per-number history reads and
					// revision scans stop full-scanning the table; no Java hook needed.

					if (version == 39) {
						migrateToBlocklistTable(session.getMapper(MigrationStatements.class));
					}

					// migration 40 drops the IDENTITY from REVISION.ID (the sequence
					// advanced on rolled-back inserts and corrupted the history
					// watermark); ids are now assigned by the application as
					// max(ID) + 1 via the script; no Java hook needed.

					// migration 41 adds the WILDCARD column (+ PERSONALIZATION_WILDCARD_IDX)
					// distinguishing prefix-wildcard personalizations (#377) from exact
					// entries via the script; no Java hook needed.

					// migration 44 adds PERSONALIZATION.LAST_ACTIVITY (seeded from CREATED)
					// for the per-user spam-evidence cap via the script; no Java hook needed.

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
			// Abort on the first failed statement instead of logging and
			// continuing. Otherwise a broken migration leaves columns/tables
			// unapplied while the surrounding version loop still bumps and
			// commits db.version — silently corrupting the schema (a chained
			// ADD COLUMN rejected by H2 2.4 did exactly this). A thrown error
			// propagates out of the loop before the version is advanced.
			sr.setStopOnError(true);
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

		// The USERAGENT column is NOT NULL; clients may omit the User-Agent header.
		// Normalize here so the change detection and the DB update below never see null.
		userAgent = nonNullUA(userAgent);

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
	 * Implementation of {@link #processVotes(PhoneNumer, String, int, long)} when there is already a database session.
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
	 *
	 * <p>Issue #332: also feeds the confidence model. Every vote produces an
	 * EMA increment on {@code HEAT}, plus on either {@code SPAM_EVIDENCE} or
	 * {@code LEGIT_EVIDENCE} depending on the sign of {@code votes}. The
	 * weight scales with {@code |votes|} so a Fritz!Box-blocked call worth
	 * 2 votes contributes twice as much evidence as a single user rating.</p>
	 */
	public boolean processVotes(SpamReports reports, PhoneNumer number, String dialPrefix, int votes, long time) {
		String phone = NumberAnalyzer.getPhoneId(number);
		final int oldVotes = nonNull(reports.getVotes(phone));
		final int newVotes = oldVotes + votes;

		int absVotes = Math.abs(votes);
		double heatInc = Ema.increment(absVotes * Signals.DIRECT_VOTE_HEAT_WEIGHT,
			time, Ema.HEAT_TAU_MILLIS);
		double evidenceInc = Ema.increment(absVotes * Signals.DIRECT_VOTE_EVIDENCE_WEIGHT,
			time, Ema.CLASSIFICATION_TAU_MILLIS);
		double spamEvidenceInc = votes > 0 ? evidenceInc : 0.0;
		double legitEvidenceInc = votes < 0 ? evidenceInc : 0.0;

		byte[] hash = NumberAnalyzer.getPhoneHash(number);
		int rows = reports.addVote(phone, votes, time, heatInc, spamEvidenceInc, legitEvidenceInc);
		if (rows == 0) {
			// Number was not yet present, must be added.
			reports.addReport(phone, hash, votes, time, heatInc, spamEvidenceInc, legitEvidenceInc);
		}

		if (absVotes != 0) {
			// Per-day activity ledger: votes cast that day, counted by magnitude
			// (matching the old DOWN_VOTES + UP_VOTES activity semantics — a legit
			// vote is still a vote cast).
			reports.mergeActivity(phone, epochDay(time), 0, 0, absVotes);
		}

		// #300 follow-up: rebuild this number's /100 block aggregation now (real-time, scoped to the
		// touched /100), counting how many of its numbers are *currently* spam. The daily sweep
		// (recomputeBlockAggregation) handles only the silent decay of blocks that get no votes.
		recomputeBlockForNumber(reports, phone, time);

		if (votes > 0) {
			updateLocalization(reports, phone, dialPrefix, 0, 0, votes, heatInc, spamEvidenceInc, time);
		}

		// SEO index ping (#91 follow-up): submit the number's public page to the
		// search index only when it first crosses into the spam class — i.e. reaches
		// MIN_VOTES upward. Every other vote is churn: re-pinging an already-indexed
		// page (or a 1..MIN_VOTES-1 number that is not even publicly listed) just
		// burns the ~200/day Google indexing quota, so genuinely new spam numbers
		// never get submitted. Down-votes and later increments do not re-ping.
		boolean becameSpam = oldVotes < MIN_VOTES && newVotes >= MIN_VOTES;

		// #342: no event-driven version bump. The periodic
		// BlocklistVersionService sweep is the single source of VERSION
		// changes — it detects visibility-class flips between snapshots
		// (including the one this vote may have just triggered) and bumps
		// VERSION in the next release window.

		// Keep the SHA1 reverse-lookup entry in lock-step with spam visibility (#300):
		// the hash is present while SPAM_EVIDENCE > LEGIT_EVIDENCE and cleared once the
		// classification has decayed to legitimate, so only actual spam is identifiable
		// in privacy-aware lookups.
		updatePhoneHashVisibility(reports, phone, hash);

		return becameSpam;
	}

	/**
	 * Keeps the SHA1 reverse-lookup hash in lock-step with spam visibility (#300):
	 * present while {@code SPAM_EVIDENCE > LEGIT_EVIDENCE}, cleared otherwise. Call after
	 * any event that changed the evidence columns of the {@code NUMBERS} row.
	 */
	private static void updatePhoneHashVisibility(SpamReports reports, String phone, byte[] hash) {
		reports.setPhoneHashIfSpam(phone, hash);
		reports.clearPhoneHashIfLegit(phone);
	}

	/**
	 * Add a per-region (dial-prefix) signal to {@code NUMBERS_LOCALE}.
	 *
	 * <p>{@code heatInc} and {@code spamEvidenceInc} must already be the
	 * projected EMA increments computed via {@link Ema#increment} with the
	 * correct signal weight for the event type — callers compute them once
	 * and feed both the global {@code NUMBERS} columns (archive gate /
	 * confidence model) and this regional row with the same values, so the
	 * dial-aware blocklist (#340) and the dial-aware visibility filter (#342)
	 * see the same decay behaviour as the global view.</p>
	 */
	public void updateLocalization(SpamReports reports, String phone, String dialPrefix,
			int searches, int calls, int votes, double heatInc, double spamEvidenceInc, long time) {
		if (dialPrefix == null) {
			return;
		}

		int cnt = reports.updateNumberLocalization(phone, dialPrefix, searches, calls, votes, heatInc, spamEvidenceInc, time);
		if (cnt == 0) {
			reports.insertNumberLocalization(phone, dialPrefix, searches, calls, votes, heatInc, spamEvidenceInc, time);
		}
	}

	/**
	 * Per-user cap on the spam evidence an intercepted call may add to a number.
	 *
	 * <p>Each user contributes at most one decoded unit of {@code SPAM_EVIDENCE} to any single
	 * number, no matter how many calls they intercept from it. The contribution lives on the user's
	 * {@code PERSONALIZATION} row and is fully described by its {@code LAST_ACTIVITY}: the stored
	 * contribution is {@code Ema.increment(1, LAST_ACTIVITY)}, decoding to ≤ 1. This method ensures
	 * the user has a blocked entry for the number (auto-adding one on first contact — a wildcard
	 * catch lands here too, since it has no exact row yet), advances {@code LAST_ACTIVITY} to
	 * {@code now}, and returns the projected {@code SPAM_EVIDENCE} delta that tops the decoded
	 * contribution back up to 1 (only the part decayed since the last activity).</p>
	 *
	 * @return the projected {@code SPAM_EVIDENCE} increment to apply (0.0 if the user has explicitly
	 *         allowed the number — a call must not add spam evidence on their behalf).
	 */
	private double cappedCallSpamContribution(BlockList blocklist, long userId, String phoneId, byte[] hash, long now) {
		DBPersonalization entry = blocklist.getPersonalizationActivity(userId, phoneId);
		if (entry == null) {
			// First contact via a call: auto-add the concrete number to the user's blacklist so the
			// contribution is tracked (hidden from display when a wildcard already covers it).
			blocklist.addPersonalization(userId, phoneId, hash, now);
			return Ema.increment(1.0, now, Ema.CLASSIFICATION_TAU_MILLIS);
		}
		if (!entry.isBlocked()) {
			// The user explicitly allowed this number — do not push spam evidence for them.
			return 0.0;
		}
		double delta = Ema.increment(1.0, now, Ema.CLASSIFICATION_TAU_MILLIS)
			- Ema.increment(1.0, entry.getLastActivity(), Ema.CLASSIFICATION_TAU_MILLIS);
		blocklist.updateLastActivity(userId, phoneId, now);
		return delta;
	}

	/**
	 * Reverses a user's capped evidence contribution when their personalization entry is removed or
	 * flipped to the opposite list.
	 *
	 * <p>Subtracts the residual (not-yet-decayed) contribution {@code Ema.increment(1, lastActivity)}
	 * from the matching axis ({@code SPAM_EVIDENCE} for a former block, {@code LEGIT_EVIDENCE} for a
	 * former allow), adjusts the legacy {@code VOTES} counter by {@code voteDelta}, keeps the
	 * per-region row and SHA1 visibility in lock-step, and rebuilds the touched block. For a former
	 * block the per-region {@code NUMBERS_LOCALE.SPAM_EVIDENCE} is decremented too; the per-region
	 * table has no legit axis, so a former allow touches the global row only.</p>
	 */
	public void revertPersonalContribution(SpamReports reports, String phone, byte[] hash,
			String dialPrefix, boolean wasBlocked, long lastActivity, int voteDelta, long now) {
		double residual = Ema.increment(1.0, lastActivity, Ema.CLASSIFICATION_TAU_MILLIS);
		double spamNeg = wasBlocked ? -residual : 0.0;
		double legitNeg = wasBlocked ? 0.0 : -residual;
		reports.addVote(phone, voteDelta, now, 0.0, spamNeg, legitNeg);
		if (wasBlocked) {
			updateLocalization(reports, phone, dialPrefix, 0, 0, 0, 0.0, spamNeg, now);
		}
		updatePhoneHashVisibility(reports, phone, hash);
		recomputeBlockForNumber(reports, phone, now);
	}

	// Removed: pingRelatedNumbers (#91). Bumping LASTPING on all spam
	// siblings of a block kept mass-spammer numbers from the old
	// inactivity-timeout archiving — the confidence model retired that
	// mechanism (a number's blocklist life is its evidence decay, which
	// LASTPING does not influence), and the mass-spammer case is covered by
	// the block-level aggregation EMAs feeding wildcard blocking (#337). The
	// ping only amplified writes: up to ~100 scattered NUMBERS row updates
	// per search/vote, plus a daily NUMBERS_HISTORY row for every pinged
	// sibling via the LASTPING-based change detection of updateHistory.

	/**
	 * Rebuilds the block-aggregation tables from the current {@code NUMBERS} evidence (#300
	 * follow-up). Replaces the former incremental, independently-decaying maintenance, which
	 * collapsed the member count of old-but-still-listed blocks below the gate.
	 *
	 * <p>Counts how many numbers in each block are <em>currently</em> spam (displayed net votes
	 * {@code >= MIN_MEMBER_VOTES}) and writes a row only for blocks that qualify:</p>
	 * <ul>
	 * <li>a /10 with {@code >= MIN_AGGREGATE_10} members (concentration), and</li>
	 * <li>a /100 with {@code >= SPREAD_MIN_NUMBERS} members spread over {@code >= SPREAD_MIN_TENS}
	 *     /10 sub-blocks of {@code >= SPREAD_TEN_CONTRIB} members each (spread×mass).</li>
	 * </ul>
	 *
	 * <p>The stored {@code SPAM_EVIDENCE}/{@code LEGIT_EVIDENCE} are the summed projected evidence
	 * of the members, so the read path's magnitude {@code decode(...)} stays decay-correct between
	 * sweeps. A row's mere presence (CNT &gt; 0) means the block qualifies.</p>
	 */
	public void recomputeBlockAggregation(long now) {
		double minNetRaw = Ema.projectedThreshold(MIN_MEMBER_VOTES - 0.5, now, Ema.CLASSIFICATION_TAU_MILLIS);

		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);

			List<AggregationInfo> tens = reports.aggregateMembersByTen(minNetRaw);

			reports.clearAggregation10();
			reports.clearAggregation100();

			int[] blocks = writeBlocksFromTens(reports, tens);

			session.commit();
			LOG.info("Recomputed block aggregation: {} /10 blocks, {} /100 blocks (from {} populated /10).",
				blocks[0], blocks[1], tens.size());
		}
	}

	/**
	 * Incremental, real-time rebuild of the single /100 block touching the given number (#300
	 * follow-up). Same computation as {@link #recomputeBlockAggregation} but scoped to one /100, so
	 * a newly forming spam block is detected at vote time without waiting for the daily sweep. The
	 * sweep stays responsible only for the silent decay of blocks that get no further votes.
	 */
	void recomputeBlockForNumber(SpamReports reports, String phone, long now) {
		String p100 = prefix100(phone);
		double minNetRaw = Ema.projectedThreshold(MIN_MEMBER_VOTES - 0.5, now, Ema.CLASSIFICATION_TAU_MILLIS);

		List<AggregationInfo> tens = reports.aggregateMembersByTenForHundred(p100, minNetRaw);
		reports.clearAggregation10ForHundred(p100);
		reports.clearAggregation100ForHundred(p100);
		writeBlocksFromTens(reports, tens);
	}

	/**
	 * Writes the qualifying /10 (concentration, {@code >= MIN_AGGREGATE_10} members) and /100
	 * (spread×mass) rows for the given per-/10 member aggregates. The aggregation rows must already
	 * have been cleared for the covered range. Returns {@code [tenBlocks, hundredBlocks]} written.
	 */
	private int[] writeBlocksFromTens(SpamReports reports, List<AggregationInfo> tens) {
		Map<String, int[]> hundredCounts = new HashMap<>();      // p100 -> [members, contributing /10s]
		Map<String, double[]> hundredEvidence = new HashMap<>(); // p100 -> [heat, spam, legit]

		int tenBlocks = 0;
		for (AggregationInfo ten : tens) {
			String p10 = ten.getPrefix();
			int members = ten.getCnt();

			// /10 concentration block.
			if (members >= MIN_AGGREGATE_10) {
				byte[] hash = computePrefixHash(p10);
				if (hash != null) {
					reports.insertAggregation10Full(p10, members, hash,
						ten.getRawHeat(), ten.getSpamEvidence(), ten.getLegitEvidence());
					tenBlocks++;
				}
			}

			String p100 = p10.length() <= 1 ? "" : p10.substring(0, p10.length() - 1);
			int[] counts = hundredCounts.computeIfAbsent(p100, k -> new int[2]);
			counts[0] += members;
			if (members >= SPREAD_TEN_CONTRIB) {
				counts[1]++;
			}
			double[] ev = hundredEvidence.computeIfAbsent(p100, k -> new double[3]);
			ev[0] += ten.getRawHeat();
			ev[1] += ten.getSpamEvidence();
			ev[2] += ten.getLegitEvidence();
		}

		int hundredBlocks = 0;
		for (Map.Entry<String, int[]> e : hundredCounts.entrySet()) {
			int members = e.getValue()[0];
			int contributingTens = e.getValue()[1];
			if (members >= SPREAD_MIN_NUMBERS && contributingTens >= SPREAD_MIN_TENS) {
				String p100 = e.getKey();
				byte[] hash = computePrefixHash(p100);
				if (hash != null) {
					double[] ev = hundredEvidence.get(p100);
					reports.insertAggregation100Full(p100, members, contributingTens, hash, ev[0], ev[1], ev[2]);
					hundredBlocks++;
				}
			}
		}
		return new int[] { tenBlocks, hundredBlocks };
	}

	/** 
	 * Adds a rating for a phone number.
	 *
	 * @param userName The login name of the user creating the rating, or <code>null</code> if the rating is anonymous.
	 * @param number The phone number to rate.
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

				DBPersonalization existing = blocklist.getPersonalizationActivity(userId, phone);

				boolean block = rating != Rating.A_LEGITIMATE;

				DBPersonalization flipFrom = null;
				if (existing != null && block == existing.isBlocked()) {
					LOG.info("Ignored repeated rating for number {} ({}) by {}.", phone, rating, userName);
					recordVote = false;
				} else {
					byte[] sha1 = NumberAnalyzer.getPhoneHash(number);

					if (existing != null) {
						// Flip to the opposite list: remember the entry so its residual capped
						// contribution can be reversed once the dial prefix is resolved below.
						flipFrom = existing;
						blocklist.removePersonalization(userId, phone);
					}
					if (block) {
						blocklist.addPersonalization(userId, phone, sha1, now);
					} else {
						blocklist.addExclude(userId, phone, sha1, now);
					}
				}

				// Never record community votes for globally whitelisted numbers.
				if (reports.isWhiteListed(phone)) {
					recordVote = false;
				}

				DBUserSettings settings = users.getSettingsById(userId);
				if (lang == null) {
					lang = settings.getLang();
				}
				dialPrefix = settings.getDialPrefix();

				if (flipFrom != null) {
					// Reverse the residual evidence the user contributed under the previous
					// classification; the processVotes below adds the new axis's increment(1, now).
					// voteDelta = 0 because processVotes also moves the legacy VOTES counter for the
					// new rating, so reversing the old counter here would double-count.
					revertPersonalContribution(reports, phone, NumberAnalyzer.getPhoneHash(number),
						dialPrefix, flipFrom.isBlocked(), flipFrom.getLastActivity(), 0, now);
				}
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
	 * Adds (or updates) a personal wildcard-prefix block/allow entry for the given user (#377).
	 *
	 * <p>
	 * The prefix is normalized to phone-ID form via {@link NumberAnalyzer#toWildcardId}. Unlike an
	 * exact personalization, a wildcard casts no community vote — there is no single number to vote
	 * on; community impact comes only from the weighted call reports its catches produce.
	 * </p>
	 *
	 * @return the normalized prefix that was stored, or {@code null} if the input is not a usable
	 *         prefix.
	 */
	public String addWildcard(String userName, String prefixText, boolean blocked, long now) {
		try (SqlSession session = openSession()) {
			Users users = session.getMapper(Users.class);
			Long userId = users.getUserId(userName);
			if (userId == null) {
				LOG.error("User ID not found for: {}", userName);
				return null;
			}

			String dialPrefix = users.getDialPrefix(userName);
			String prefix = NumberAnalyzer.toWildcardId(prefixText, dialPrefix);
			if (prefix == null) {
				return null;
			}

			BlockList blocklist = session.getMapper(BlockList.class);
			// A digit string is either exact or a prefix for a given user: replace any existing
			// entry under this key (PK is USERID, PHONE) before inserting the wildcard.
			blocklist.removePersonalization(userId, prefix);
			// A blocking wildcard subsumes the user's concrete blocks under that prefix only for
			// display/export (the wildcard is the compact representation): the covered concrete
			// rows are kept so the per-user evidence they carry (#per-user-cap) is preserved, and
			// hidden via the wildcard-cover filter in BlockList.getPersonalizations*. Removing the
			// wildcard makes them visible again unchanged.
			blocklist.addWildcard(userId, prefix, blocked, now);
			session.commit();
			return prefix;
		}
	}

	/**
	 * Removes a personal wildcard-prefix entry for the given user (#377).
	 *
	 * @return {@code true} if an entry was removed.
	 */
	public boolean removeWildcard(String userName, String prefixText) {
		try (SqlSession session = openSession()) {
			Users users = session.getMapper(Users.class);
			Long userId = users.getUserId(userName);
			if (userId == null) {
				return false;
			}

			String dialPrefix = users.getDialPrefix(userName);
			String prefix = NumberAnalyzer.toWildcardId(prefixText, dialPrefix);
			if (prefix == null) {
				return false;
			}

			BlockList blocklist = session.getMapper(BlockList.class);
			boolean deleted = blocklist.removeWildcard(userId, prefix);
			session.commit();
			return deleted;
		}
	}

	/**
	 * Adds a rating for a phone number without DB commit.
	 * @param recordVote
	 *
	 * @return Whether recording this rating pushed the number across {@link #MIN_VOTES}
	 *         into the spam class — i.e. whether to submit its page to the search index.
	 */
	public boolean addRating(SpamReports reports, PhoneNumer number, String dialPrefix, UserComment comment, boolean recordVote) {
		// The returned flag drives the SEO index ping and is true only when this rating
		// pushes the number across MIN_VOTES (see processVotes). Persisting the
		// comment/rating itself is not a reason to re-submit the page to the search
		// index — that churn just burns the daily indexing quota.
		boolean becameSpam = false;

		String phone = NumberAnalyzer.getPhoneId(number);
		Rating rating = comment.getRating();
		String commentText = comment.getComment();
		long created = comment.getCreated();
		Long userId = comment.getUserId();
		boolean hasCommentText = commentText != null && !commentText.isBlank();
		if (hasCommentText && commentText.length() > MAX_COMMENT_LENGTH) {
			// Limit to DB constraint.
			commentText = commentText.substring(0, MAX_COMMENT_LENGTH);
		}

		if (userId != null) {
			if (hasCommentText) {
				// Replace any existing comment row by this user for this phone (ensure one comment per user per number).
				int deleted = reports.deleteUserComments(userId, phone);
				if (deleted > 0) {
					LOG.info("Replaced existing comment for phone {} by user ID {}.", phone, userId);
				}
				reports.addComment(comment.getId(), phone, rating, commentText, comment.getLang(), comment.getService(), created, userId);
			} else {
				// No comment text — still record the user's rating choice so it is visible on
				// the personal blacklist/whitelist view. Update the existing row's rating in
				// place (keeping any comment), or insert a rating-only row.
				int updated = reports.updateUserRating(userId, phone, rating);
				if (updated == 0) {
					reports.addComment(comment.getId(), phone, rating, null, comment.getLang(), comment.getService(), created, userId);
				}
			}
		} else if (hasCommentText) {
			// Anonymous comment (e.g. captcha-protected web rating without login).
			reports.addComment(comment.getId(), phone, rating, commentText, comment.getLang(), comment.getService(), created, userId);
		}

		if (recordVote) {
			becameSpam = processVotes(reports, number, dialPrefix, Ratings.getVotes(rating), created);
			if (rating != Rating.B_MISSED) {
				// Record the rating tally (not itself a reason to re-index).
				reports.updateRating(phone, rating, 1, created);
			}
		}

		return becameSpam;
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
	 * The factory for creating database sessions.
	 */
	public SqlSessionFactory getSessionFactory() {
		return _sessionFactory;
	}

	/**
	 * Opens a session to query/update the database.
	 */
	public SqlSession openSession() {
		return _sessionFactory.openSession();
	}

	/**
	 * The underlying {@link DataSource} for raw JDBC access outside of MyBatis transactions.
	 */
	public DataSource dataSource() {
		return _dataSource;
	}
	
	/**
	 * Looks up all spam reports that were done after the given time in milliseconds since epoch.
	 */
	public List<? extends NumberInfo> getLatestSpamReports(long notBefore) {
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			return reports.getLatestReports(notBefore, maxRawSpam(1));
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
		return getTopSearches(limit, System.currentTimeMillis());
	}

	public List<? extends SearchInfo> getTopSearches(int limit, long now) {
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);

			// "Today and yesterday": sum the per-day search counts over the last two
			// UTC days. A number that has gone quiet has no recent activity rows and
			// drops out of the ranking — unlike the old rotated SEARCHES_CURRENT
			// counter, which froze at its last busy day's value and kept stale numbers
			// pinned to the top.
			int minDay = epochDay(now) - 1;
			return reports.getTopSearches(minDay, maxRawSpam(1), limit);
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
			return reports.getLatestBlocklistEntries(maxRawSpam(minVotes));
		}
	}

	/**
	 * Raw projected-EMA cutoff that matches "displayed votes ≥ minVotes" at
	 * the given moment (#342). Single source for every read path that
	 * filters against the visibility threshold (blocklist queries, snapshot
	 * version sweep, tests) so the cutoff stays consistent.
	 *
	 * @param at        the moment at which the threshold should hold.
	 * @param minVotes  integer visibility floor (typically
	 *                  {@link #getMinVisibleVotes()}).
	 */
	public static double maxRawSpamAt(long at, int minVotes) {
		// minVotes - 0.5 is the real-valued threshold whose Math.round result
		// equals minVotes — keeps SQL inclusion in lock-step with the
		// displayed votes in toBlocklistEntry.
		return Ema.projectedThreshold(minVotes - 0.5, at, Ema.CLASSIFICATION_TAU_MILLIS);
	}

	/** {@link #maxRawSpamAt} at the current moment. */
	public double maxRawSpam(int minVotes) {
		return maxRawSpamAt(System.currentTimeMillis(), minVotes);
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

			// #342: serves the published state (BLOCKLIST table) — bucket votes
			// frozen at publication, consistent with the version counter that
			// the same sweep transaction maintains. minVisibleVotes is a pure
			// read-time filter; publication itself always starts at bucket 2.
			List<BlockListEntry> numbers = reports.getBlocklist(_minVisibleVotes)
					.stream()
					.map(DB::toBlocklistEntry)
					.filter(Objects::nonNull)
					.collect(Collectors.toList());

			String versionStr = users.getProperty("blocklist.version");
			long version = (versionStr != null) ? Long.parseLong(versionStr) : INITIAL_BLOCKLIST_VERSION;

			return Blocklist.create()
					.setNumbers(numbers)
					.setVersion(version);
		}
	}

	/**
	 * Tombstones ({@code votes = 0} rows in BLOCKLIST) are kept long enough
	 * that every incremental-sync client sees the removal — full sync is
	 * forced at least monthly, so 90 days is a comfortable margin. The
	 * watermark mechanism (see {@link #publishBlocklist}) stretches the
	 * actual lifetime to between one and two retention windows.
	 */
	private static final long TOMBSTONE_RETENTION_MILLIS = 90L * 24 * 60 * 60 * 1000;

	/**
	 * The current blocklist version — incremented by {@link #publishBlocklist}
	 * whenever the published state changed.
	 */
	public long getBlocklistVersion() {
		try (SqlSession session = openSession()) {
			Users users = session.getMapper(Users.class);
			String versionStr = users.getProperty("blocklist.version");
			return (versionStr != null) ? Long.parseLong(versionStr) : INITIAL_BLOCKLIST_VERSION;
		}
	}

	/**
	 * Publication sweep (#342): synchronizes the BLOCKLIST table with the
	 * live visibility state, quantized to vote buckets (2, 4, 10, 20, 50,
	 * 100 — the allowed {@code minVisibleVotes} steps, so the quantization
	 * never changes a client's block decision).
	 *
	 * <p>A number is written only when it crosses a bucket boundary — by new
	 * votes or by decay. Drifting inside a bucket causes no write, no version
	 * bump and no client traffic. The global version increments only when at
	 * least one bucket flip happened.</p>
	 *
	 * @param now the sweep moment; determines the projected bucket thresholds.
	 * @return the blocklist version after the sweep — incremented when
	 *         anything changed, unchanged otherwise.
	 */
	public long publishBlocklist(long now) {
		try (SqlSession session = openSession()) {
			Users users = session.getMapper(Users.class);
			SpamReports reports = session.getMapper(SpamReports.class);

			String versionStr = users.getProperty("blocklist.version");
			long currentVersion = (versionStr != null) ? Long.parseLong(versionStr) : INITIAL_BLOCKLIST_VERSION;
			long newVersion = currentVersion + 1;

			int changed = reports.publishBlocklistUpdates(newVersion,
				maxRawSpamAt(now, 2), maxRawSpamAt(now, 4), maxRawSpamAt(now, 10),
				maxRawSpamAt(now, 20), maxRawSpamAt(now, 50), maxRawSpamAt(now, 100));
			changed += reports.publishBlocklistRemovals(newVersion, maxRawSpamAt(now, 2));

			// Per-region activity classes flip independently of the vote
			// buckets and change CardDAV content — they count towards the
			// version bump so the caches get flushed. The cleanup of rows for
			// numbers that dropped off is invisible housekeeping.
			changed += reports.publishBlocklistLocale(Ema.decode(1.0, now, Ema.HEAT_TAU_MILLIS));
			reports.cleanupBlocklistLocale();

			// Tombstone pruning via watermark: the property pair states
			// "version V existed at time T". Once T is a full retention window
			// in the past, every tombstone with VERSION <= V is provably old
			// enough to delete — no per-row timestamp needed. Tombstones live
			// between one and two retention windows.
			boolean watermarkMoved = false;
			int pruned = 0;
			String pruneVersionStr = users.getProperty("blocklist.pruneVersion");
			String pruneTimeStr = users.getProperty("blocklist.pruneTime");
			if (pruneVersionStr == null || pruneTimeStr == null) {
				setProperty(users, "blocklist.pruneVersion", Long.toString(currentVersion));
				setProperty(users, "blocklist.pruneTime", Long.toString(now));
				watermarkMoved = true;
			} else if (now - Long.parseLong(pruneTimeStr) >= TOMBSTONE_RETENTION_MILLIS) {
				pruned = reports.pruneBlocklistTombstones(Long.parseLong(pruneVersionStr));
				setProperty(users, "blocklist.pruneVersion", Long.toString(currentVersion));
				setProperty(users, "blocklist.pruneTime", Long.toString(now));
				watermarkMoved = true;
			}

			if (changed > 0) {
				setProperty(users, "blocklist.version", Long.toString(newVersion));
				session.commit();
				LOG.info("Published blocklist version {}: {} class flips, {} tombstones pruned.",
					newVersion, changed, pruned);
				return newVersion;
			}

			if (watermarkMoved) {
				session.commit();
			}
			return currentVersion;
		}
	}

	private static void setProperty(Users users, String name, String value) {
		if (users.getProperty(name) == null) {
			users.addProperty(name, value);
		} else {
			users.updateProperty(name, value);
		}
	}

	/**
	 * Heat-ranked blocklist for space-constrained clients (#336, #340).
	 *
	 * <p>Returns at most {@code limit} entries — the currently-loudest spam
	 * numbers above the minimum-votes threshold. Designed for Fritz!Box
	 * phonebooks and dongle-local blocklists where storage is capped: a
	 * once-loud-now-silent number drops out so a currently-active one takes
	 * its slot.</p>
	 *
	 * <p>When {@code dialPrefix} is non-null, the ranking uses the per-region
	 * Heat from {@code NUMBERS_LOCALE} (#340): a number heating up among US
	 * victims won't push numbers off a German client's top-N. With a
	 * {@code null} dial we fall back to the global {@code NUMBERS.HEAT} —
	 * preserves the old behaviour for clients that don't carry a region.</p>
	 *
	 * <p>This view is <em>not</em> compatible with incremental sync
	 * ({@code ?since=}): the set of included numbers changes whenever Heat
	 * shifts, and there is no per-entry version stamp for "evicted from the
	 * top-N". Clients should refetch the whole list on a schedule (e.g.
	 * daily) — the same fair-use ceiling that already caps full-blocklist
	 * fetches applies.</p>
	 */
	public Blocklist getBlockListByHeatAPI(String dialPrefix, int limit) {
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			Users users = session.getMapper(Users.class);

			double maxRawSpam = maxRawSpam(_minVisibleVotes);
			List<DBNumberInfo> raw = (dialPrefix != null)
				? reports.getBlocklistByDialHeat(dialPrefix, maxRawSpam, limit)
				: reports.getBlocklistByHeat(maxRawSpam, limit);
			List<BlockListEntry> numbers = raw.stream()
					.map(DB::toBlocklistEntry)
					.filter(Objects::nonNull)
					.collect(Collectors.toList());

			String versionStr = users.getProperty("blocklist.version");
			long version = (versionStr != null) ? Long.parseLong(versionStr) : INITIAL_BLOCKLIST_VERSION;

			return Blocklist.create()
					.setNumbers(numbers)
					.setVersion(version);
		}
	}

	/**
	 * Exact community spam entries for the size-capped binary community list:
	 * region-scoped, Heat-ranked and truncated to {@code limit}.
	 *
	 * <p>
	 * Unlike {@link #getBlockListAPI()} (which returns the full exact list for
	 * the JSON/unbounded paths), this keeps only the top {@code limit} numbers
	 * by current Heat so a dongle with limited flash gets the spam it is most
	 * likely to receive. A {@code null}/empty {@code dialPrefix} falls back to
	 * the global Heat ranking (legacy clients, unmapped regions). Numbers
	 * dropped by the cap still resolve correctly on the dongle: a local miss
	 * falls back to the live {@code /num} API.
	 * </p>
	 *
	 * <p>
	 * The net-evidence gate uses {@code minDirect} (the dongle's
	 * {@code min_direct_votes}, floored at the published threshold by the
	 * servlet), so the encoded set is a Heat-ordered subset of exactly what the
	 * dongle's API-fallback path would decide SPAM. Returns an empty list
	 * without querying when {@code limit <= 0}.
	 * </p>
	 *
	 * @param dialPrefix Region scope, or {@code null}/empty for the global list.
	 * @param minDirect  Exact-entry net-vote threshold.
	 * @param limit      Maximum number of entries (the budget-derived cap).
	 * @param now        Reference time for decaying the EMA threshold.
	 */
	public List<BlockListEntry> getCommunityExactByHeat(String dialPrefix, int minDirect, int limit, long now) {
		if (limit <= 0) {
			return List.of();
		}
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			double maxRawSpam = maxRawSpamAt(now, minDirect);
			List<DBNumberInfo> raw = (dialPrefix != null && !dialPrefix.isEmpty())
					? reports.getBlocklistByDialHeat(dialPrefix, maxRawSpam, limit)
					: reports.getBlocklistByHeat(maxRawSpam, limit);
			return raw.stream()
					.map(DB::toBlocklistEntry)
					.filter(Objects::nonNull)
					.collect(Collectors.toList());
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
						// Entries below the visible threshold (including tombstones)
						// are returned as deletions (votes=0)
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
	
	/**
	 * Server-wide inputs that feed the binary community blocklist beyond the
	 * exact-entry list returned by {@link #getBlockListAPI()}: the qualifying
	 * wildcard blocks from the aggregation tables, and the explicit
	 * legitimate-number whitelist.
	 *
	 * <p>
	 * The aggregation rows here are a cheap superset: SQL has applied the
	 * structural gate ({@code CNT >= MIN_AGGREGATE_*}, already guaranteed by a
	 * row's presence) and a {@code SPAM_EVIDENCE} lower bound derived from the
	 * caller's {@code minRange}. The exact net-evidence test
	 * ({@link #blockNetVotes} {@code >= minRange}) runs in
	 * {@code CommunityEntries}, because net votes (spam &minus; legit) is not a
	 * clean indexed predicate. {@link #blockNetVotes} is the same net-evidence
	 * magnitude {@link #computeWildcardVotes} produces for the live
	 * {@code /num} API, so the downloaded wildcards match the dongle's
	 * API-fallback verdict.
	 * </p>
	 */
	public static final class CommunityBinarySources {

		private final List<AggregationInfo> _aggregation10;

		private final List<AggregationInfo> _aggregation100;

		private final Set<String> _whitelist;

		public CommunityBinarySources(List<AggregationInfo> aggregation10, List<AggregationInfo> aggregation100,
				Set<String> whitelist) {
			_aggregation10 = aggregation10;
			_aggregation100 = aggregation100;
			_whitelist = whitelist;
		}

		/** Candidate 10-blocks (structural gate + {@code SPAM_EVIDENCE} lower bound applied). */
		public List<AggregationInfo> aggregation10() {
			return _aggregation10;
		}

		/** Candidate 100-blocks (structural gate + {@code SPAM_EVIDENCE} lower bound applied). */
		public List<AggregationInfo> aggregation100() {
			return _aggregation100;
		}

		/** Phone IDs on the global legitimate-number whitelist. */
		public Set<String> whitelist() {
			return _whitelist;
		}

	}

	/**
	 * Loads the candidate wildcard blocks and the global whitelist in one DB
	 * session for the binary community download.
	 *
	 * <p>
	 * {@code minRange} is the dongle's {@code min_range_votes} setting — the
	 * net-evidence threshold the dongle's API path applies to a wildcard block
	 * ({@code range_hit = wildcard_votes >= min_range}). A {@code minRange < 1}
	 * means range-blocking is off, so no wildcards are loaded at all (matching
	 * the API path's {@code min_range >= 1} guard).
	 * </p>
	 *
	 * <p>
	 * Because net votes (spam &minus; legit) cannot be a clean indexed SQL
	 * comparison, the SQL only applies a safe lower bound — a block's net
	 * votes can never exceed its spam votes, so any block with
	 * {@code SPAM_EVIDENCE < projectedThreshold(minRange)} is guaranteed below
	 * threshold and dropped early, keeping the index seek. The exact
	 * {@link #blockNetVotes} {@code >= minRange} test runs in
	 * {@code CommunityEntries} on the survivors.
	 * </p>
	 */
	public CommunityBinarySources getCommunityBinarySources(int minRange, long now) {
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			List<AggregationInfo> agg10;
			List<AggregationInfo> agg100;
			if (minRange >= 1) {
				double minSpam = Ema.projectedThreshold(minRange, now, Ema.CLASSIFICATION_TAU_MILLIS);
				agg10 = reports.getAggregation10AboveThresholds(MIN_AGGREGATE_10, minSpam);
				agg100 = reports.getAggregation100AboveThresholds(MIN_AGGREGATE_100, minSpam);
			} else {
				agg10 = List.of();
				agg100 = List.of();
			}
			Set<String> whitelist = reports.getWhiteList();
			return new CommunityBinarySources(agg10, agg100, whitelist);
		}
	}

	/**
	 * Net-evidence vote magnitude of a single aggregation block at
	 * {@code now}: {@code round(max(0, decode(SPAM_EVIDENCE) -
	 * decode(LEGIT_EVIDENCE)))}. Legitimate votes cancel spam votes, so a
	 * contested block reads low. Shared by {@link #computeWildcardVotes} (the
	 * live {@code /num} wildcard decision) and the binary community download,
	 * so both gate wildcards on exactly the same number.
	 */
	public static int blockNetVotes(AggregationInfo block, long now) {
		double spam = Ema.decode(block.getSpamEvidence(), now, Ema.CLASSIFICATION_TAU_MILLIS);
		double legit = Ema.decode(block.getLegitEvidence(), now, Ema.CLASSIFICATION_TAU_MILLIS);
		return (int) Math.round(Math.max(0.0, spam - legit));
	}

	/** A user's personal black and white phone-ID lists, in raw DB format. */
	public static final class PersonalLists {

		private final List<String> _blacklist;

		private final List<String> _whitelist;

		PersonalLists(List<String> blacklist, List<String> whitelist) {
			_blacklist = blacklist;
			_whitelist = whitelist;
		}

		/** Phone IDs the user has explicitly blocked (BLOCKED = true). */
		public List<String> blacklist() {
			return _blacklist;
		}

		/** Phone IDs the user has explicitly allowed (BLOCKED = false). */
		public List<String> whitelist() {
			return _whitelist;
		}

	}

	/**
	 * Loads the user's personal black/white lists in raw DB phone-ID format.
	 *
	 * <p>
	 * Entries may end in {@code *} to mark a wildcard prefix; otherwise they
	 * are exact phone IDs in national or {@code 00}-international notation.
	 * </p>
	 */
	public PersonalLists getPersonalLists(String login) {
		try (SqlSession session = openSession()) {
			Users users = session.getMapper(Users.class);
			Long userId = users.getUserId(login);
			if (userId == null) {
				return new PersonalLists(List.of(), List.of());
			}
			BlockList blocklist = session.getMapper(BlockList.class);
			List<String> black = blocklist.getPersonalizations(userId.longValue());
			List<String> white = blocklist.getWhiteList(userId.longValue());
			return new PersonalLists(black, white);
		}
	}

	/**
	 * Converts a published BLOCKLIST row to the API shape (#342). Votes are
	 * the frozen bucket floor — no decoding, no decay; the same value every
	 * client sees for this blocklist version. The rating colors the entry
	 * from the live category counters.
	 */
	private static BlockListEntry toBlocklistEntry(DBBlocklistEntry e) {
		PhoneNumer number = NumberAnalyzer.analyzePhoneID(e.getPhone());
		if (number == null) {
			// Invalid number in DB, filter out.
			return null;
		}
		// Category is the dominant rating, frozen at publication and read
		// straight from BLOCKLIST (migration 43). Tombstones (votes <= 0) carry
		// no meaningful category and are reported as A_LEGITIMATE.
		Rating rating = e.getVotes() <= 0 ? Rating.A_LEGITIMATE : e.getCategory();
		return BlockListEntry.create()
				.setPhone(number.getPlus())
				.setVotes(e.getVotes())
				.setRating(rating)
				.setLastActivity(e.getLastPing());
	}

	private static BlockListEntry toBlocklistEntry(DBNumberInfo n) {
		PhoneNumer number = NumberAnalyzer.analyzePhoneID(n.getPhone());
		if (number == null) {
			// Invalid number in DB, filter out.
			return null;
		}
		// #338: `votes` is the decay-aware net vote equivalent
		// (decoded SPAM_EVIDENCE minus decoded LEGIT_EVIDENCE, floored at 0),
		// not the cumulative DB column. Same semantic as /api/check, so
		// clients filtering `votes >= minVotes AND !archived` see a smooth
		// decay through their threshold instead of a binary archive flip.
		// For archived rows the incremental-sync query forces SPAM_EVIDENCE
		// to zero, so this naturally yields the legacy `votes=0` removal
		// signal — the contract for /api/blocklist?since=N is preserved.
		long now = System.currentTimeMillis();
		double decodedSpam = Ema.decode(n.getSpamEvidence(), now, Ema.CLASSIFICATION_TAU_MILLIS);
		double decodedLegit = Ema.decode(n.getLegitEvidence(), now, Ema.CLASSIFICATION_TAU_MILLIS);
		int votes = (int) Math.round(Math.max(0.0, decodedSpam - decodedLegit));
		return BlockListEntry.create()
				.setPhone(number.getPlus())
				.setVotes(votes)
				.setRating(rating(n))
				.setLastActivity(n.getLastPing());
	}

	public static Rating rating(NumberInfo n) {
		// #342: the visibility gate consults the decay-aware net evidence,
		// matching the votes that PhoneInfo / BlockListEntry expose. A number
		// whose spam history has decayed below its legit history reads as
		// legitimate again — same view as /api/check, no separate flip.
		// Per-category rating counts remain raw cumulative; they only decide
		// which spam category dominates *once* the gate has fired.
		if (!hasNetSpamEvidence(n)) {
			return Rating.A_LEGITIMATE;
		}

		return dominantCategory(n.getRatingFraud(), n.getRatingGamble(), n.getRatingAdvertising(),
			n.getRatingPoll(), n.getRatingPing());
	}

	/**
	 * The spam category with the highest rating count, {@link Rating#B_MISSED}
	 * when no category has any. Ties resolve to the more severe category
	 * (fraud first).
	 */
	private static Rating dominantCategory(int fraud, int gamble, int advertising, int poll, int ping) {
		Rating result = Rating.B_MISSED;
		int max = 0;

		if (fraud > max) {
			result = Rating.G_FRAUD;
			max = fraud;
		}
		if (gamble > max) {
			result = Rating.F_GAMBLE;
			max = gamble;
		}
		if (advertising > max) {
			result = Rating.E_ADVERTISING;
			max = advertising;
		}
		if (poll > max) {
			result = Rating.D_POLL;
			max = poll;
		}
		if (ping > max) {
			result = Rating.C_PING;
			max = ping;
		}

		return result;
	}

	/**
	 * Decay-aware "is this number still spam?" gate (#342). Returns true when
	 * the decoded SPAM_EVIDENCE exceeds the decoded LEGIT_EVIDENCE at the
	 * current moment — same semantic as the {@code votes > 0} surface in
	 * {@code PhoneInfo} / {@code BlockListEntry}. Falls back to the raw
	 * counter when called on a plain {@link NumberInfo} that carries no EMA
	 * data (in practice rare — every read-path producer is a
	 * {@link DBNumberInfo}).
	 */
	private static boolean hasNetSpamEvidence(NumberInfo n) {
		if (n instanceof DBNumberInfo dbInfo) {
			long now = System.currentTimeMillis();
			double decodedSpam = Ema.decode(dbInfo.getSpamEvidence(), now, Ema.CLASSIFICATION_TAU_MILLIS);
			double decodedLegit = Ema.decode(dbInfo.getLegitEvidence(), now, Ema.CLASSIFICATION_TAU_MILLIS);
			return decodedSpam - decodedLegit > 0;
		}
		return n.getRawVotes() > 0;
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
					reports.getStatistics(maxRawSpam(minVotes), maxRawSpam(1)),
					nonNull(reports.getTotalVotes()));
		}
	}

	/**
	 * Number of currently visible (blocked) numbers — the size of the active
	 * blocklist. Backed by {@code NUMBERS_SPAM_EVIDENCE_IDX} so it counts only
	 * the visible set, unlike {@link #getStatus} which full-scans NUMBERS for
	 * its reported/total aggregates.
	 */
	public int getActiveBlocklistCount(int minVotes) {
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			return reports.getActiveBlocklistCount(maxRawSpam(minVotes));
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

		// Issue #338 (semantic shift for existing clients):
		// `votes` and `votesWildcard` are decay-aware: `round(decoded
		// SPAM_EVIDENCE - decoded LEGIT_EVIDENCE)`, decayed to the moment
		// of the API call. A 10-vote spam number from last week still reads
		// `votes=10`; from four months ago reads `votes=5`; from a year ago
		// reads `votes=1`; once below `minVotes` the client treats it as
		// not-on-blocklist — same effect the old `archived` flag had, only
		// smooth instead of binary.
		long now = System.currentTimeMillis();
		double rawHeat = 0.0;
		double rawSpam = 0.0;
		double rawLegit = 0.0;
		if (info instanceof DBNumberInfo dbInfo) {
			rawHeat = dbInfo.getRawHeat();
			rawSpam = dbInfo.getSpamEvidence();
			rawLegit = dbInfo.getLegitEvidence();
		}
		double decodedNumberSpam = Ema.decode(rawSpam, now, Ema.CLASSIFICATION_TAU_MILLIS);
		double decodedNumberLegit = Ema.decode(rawLegit, now, Ema.CLASSIFICATION_TAU_MILLIS);

		// votesWildcard reflects the block view, but only for a block that actually qualifies as
		// spam by the periodically-recomputed gate (#300 follow-up): the aggregation tables hold a
		// row only for a qualifying /10 (concentration) or /100 (spread×mass). A single
		// heavily-voted number is one member and so never produces a block row — it cannot lift
		// its neighbours (the #337 flat-evidence regression). The magnitude is the qualifying
		// block's decoded net evidence; 0 when no block qualifies.
		AggregationInfo block = qualifyingSpamBlock(aggregation10, aggregation100);
		double decodedBlockSpam = block == null ? 0.0
			: Ema.decode(block.getSpamEvidence(), now, Ema.CLASSIFICATION_TAU_MILLIS);
		double decodedBlockLegit = block == null ? 0.0
			: Ema.decode(block.getLegitEvidence(), now, Ema.CLASSIFICATION_TAU_MILLIS);

		// Net evidence — legitimate votes cancel out spam votes on the displayed
		// counter. Floor at 0 so a contested number cannot read negative.
		int votes = (int) Math.round(Math.max(0.0, decodedNumberSpam - decodedNumberLegit));
		int votesWildcard = (int) Math.round(Math.max(0.0, decodedBlockSpam - decodedBlockLegit));

		result.setVotes(votes);
		result.setVotesWildcard(votesWildcard);
		result.setRating(rating);

		// Confidence model surface (#334). spamConfidence is the Wilson lower
		// bound on the block-level evidence — the same view callers see for
		// the wildcard decision.
		result.setHeat(Ema.decodeRate(rawHeat, now, Ema.HEAT_TAU_MILLIS));
		result.setSpamConfidence(Confidence.spamConfidence(
			Math.max(decodedNumberSpam, decodedBlockSpam),
			Math.max(decodedNumberLegit, decodedBlockLegit)));

		// Lifetime counter of calls PhoneBlock has intercepted for this number
		// (answer bot pickups plus blocked-call reports from app/dongle, #300).
		result.setCalls(info.getCalls());

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
		return result == null ? new AggregationInfo(prefix, 0) : result;
	}

	/**
	 * Computes the SHA1 hash for an aggregation prefix.
	 *
	 * <p>
	 * Converts the DB prefix (national format) to international format and hashes it.
	 * </p>
	 */
	static byte[] computePrefixHash(String prefix) {
		if (prefix == null || prefix.isEmpty()) {
			return null;
		}
		String internationalForm = NumberAnalyzer.toInternationalFormat(prefix);
		return PhoneHash.getPhoneHash(PhoneHash.createPhoneDigest(), internationalForm);
	}

	/**
	 * The aggregation row of the block that qualifies the given number as a wildcard-block spam
	 * member, or {@code null} if neither its /100 nor its /10 qualifies (#300 follow-up).
	 *
	 * <p>{@link #recomputeBlockAggregation} writes a row only for qualifying blocks, so a present
	 * row (CNT &gt; 0) means the block qualifies. The /100 is preferred when present — its summed
	 * evidence already covers the /10. The fallback {@link #notNull} rows (CNT 0) never match.</p>
	 */
	public AggregationInfo qualifyingSpamBlock(AggregationInfo agg10, AggregationInfo agg100) {
		if (agg100 != null && agg100.getCnt() > 0) {
			return agg100;
		}
		if (agg10 != null && agg10.getCnt() > 0) {
			return agg10;
		}
		return null;
	}

	/**
	 * The wildcard-block vote magnitude for the given moment (#300 follow-up): the decoded net
	 * evidence of the {@link #qualifyingSpamBlock qualifying block}, or 0 if none qualifies. Used
	 * identically for display ({@code votesWildcard}) and the spam decision so they cannot diverge.
	 *
	 * <p>
	 * NOTE (#326 — binary blocklist consistency): three places must agree on what a wildcard block
	 * "is worth", or the dongle's local-cache verdict drifts from its API-fallback verdict:
	 * </p>
	 * <ol>
	 * <li>this method — the live {@code /num} wildcard decision (and {@code votesWildcard} display);</li>
	 * <li>{@code compute_wildcard_votes} in the dongle's {@code api.c} — its replica for the API path;</li>
	 * <li>the binary community download — {@code CommunityEntries} gates each wildcard on
	 *     {@link #blockNetVotes} {@code >= minRange}, the dongle's {@code min_range_votes}.</li>
	 * </ol>
	 * <p>
	 * All three share the <em>net-evidence</em> magnitude ({@link #blockNetVotes}): spam minus legit,
	 * which is why {@code LEGIT_EVIDENCE} matters for wildcards. Do not switch any one of them to a
	 * different gate (e.g. the display-only Wilson {@code spamConfidence}) without switching all three.
	 */
	public int computeWildcardVotes(AggregationInfo agg10, AggregationInfo agg100, long now) {
		AggregationInfo block = qualifyingSpamBlock(agg10, agg100);
		if (block == null) {
			return 0;
		}
		return blockNetVotes(block, now);
	}

	/**
	 * Populates SHA1 hashes for existing personalization rows during migration to version 17.
	 */
	private void populatePersonalizationHashes(SqlSession session) {
		BlockList blocklist = session.getMapper(BlockList.class);
		List<String> phones = blocklist.getPersonalizationsWithoutHash();
		MessageDigest digest = PhoneHash.createPhoneDigest();
		int updated = 0;
		int skipped = 0;
		for (String phone : phones) {
			if (phone.contains("*") || phone.length() < 5) {
				LOG.info("Skipping personalization with wildcard or too short: {}", phone);
				skipped++;
				continue;
			}
			String internationalForm = NumberAnalyzer.toInternationalFormat(phone);
			byte[] hash = PhoneHash.getPhoneHash(digest, internationalForm);
			digest.reset();
			blocklist.updatePersonalizationHash(phone, hash);
			updated++;
		}
		LOG.info("Backfilled SHA1 hashes for {} personalization entries, skipped {}.", updated, skipped);
		session.commit();
	}

	/**
	 * Backfill the confidence-model EMA columns from existing cumulative
	 * counters during migration to version 29 (epic #300).
	 *
	 * <p>Without this step the new API surface ({@code heat},
	 * {@code spamConfidence}, Heat-based archiving, Heat-ranked blocklist,
	 * decay-aware wildcard tracking) would start out empty on every
	 * pre-existing row — the first Heat-based archive sweep would deactivate
	 * the entire blocklist. The backfill assumes every past event happened
	 * at {@code max(LASTPING, UPDATED)} for the row, then projects to the
	 * EMA reference epoch — see {@link SpamReports#backfillNumbersEmas} for
	 * the rationale.</p>
	 */
	private void backfillConfidenceEmas(MigrationStatements migrations) {
		LOG.info("Confidence model (#300): backfilling EMA columns from existing counters.");

		int numbersUpdated = migrations.backfillNumbersEmas(
			(double) Ema.T0_MILLIS, Ema.HEAT_TAU_MILLIS, Ema.CLASSIFICATION_TAU_MILLIS,
			Signals.DIRECT_VOTE_HEAT_WEIGHT,
			Signals.DIRECT_VOTE_EVIDENCE_WEIGHT,
			Signals.CALL_HEAT_WEIGHT,
			Signals.CALL_EVIDENCE_WEIGHT,
			Signals.SEARCH_HEAT_WEIGHT);
		LOG.info("Backfilled EMAs on {} NUMBERS rows.", numbersUpdated);

		int agg10Updated = migrations.backfillAggregation10Emas();
		LOG.info("Backfilled EMAs on {} NUMBERS_AGGREGATION_10 rows.", agg10Updated);

		int agg100Updated = migrations.backfillAggregation100Emas();
		LOG.info("Backfilled EMAs on {} NUMBERS_AGGREGATION_100 rows.", agg100Updated);
	}

	/**
	 * Backfill {@code NUMBERS_LOCALE.HEAT} from the cumulative per-dial counters
	 * during migration to version 30 (epic #300 / #340).
	 *
	 * <p>The dial-aware Heat-ranked blocklist would otherwise see an empty
	 * column on every pre-existing row and return nothing for any region
	 * until enough new activity arrived. The forward path (each new event
	 * writes the EMA increment via {@code updateLocalization}) only catches
	 * post-migration activity; this step seeds the column from history.</p>
	 */
	private void backfillNumbersLocaleHeat(MigrationStatements migrations) {
		LOG.info("Region-aware Heat (#340): backfilling NUMBERS_LOCALE.HEAT from existing counters.");
		int updated = migrations.backfillNumbersLocaleHeat(
			(double) Ema.T0_MILLIS, Ema.HEAT_TAU_MILLIS,
			Signals.DIRECT_VOTE_HEAT_WEIGHT,
			Signals.CALL_HEAT_WEIGHT,
			Signals.SEARCH_HEAT_WEIGHT);
		LOG.info("Backfilled NUMBERS_LOCALE.HEAT on {} rows.", updated);
	}

	/**
	 * Backfill the visibility/snapshot columns introduced in migration 31
	 * (#342) and drop the obsolete NUMBERS.PUBLISHED_VOTES snapshot column.
	 * The order matters: the backfill runs first while the source columns
	 * still exist, then the snapshot column is dropped. NUMBERS_LOCALE.VOTES
	 * is retained (kept in {@code db-schema.sql}) and updated additively
	 * alongside the projected SPAM_EVIDENCE going forward.
	 */
	private void backfillVisibilityColumns(MigrationStatements migrations) {
		LOG.info("Decay-aware visibility (#342): backfilling NUMBERS_LOCALE.SPAM_EVIDENCE and NUMBERS.PUBLISHED_SPAM_EVIDENCE.");

		int localeUpdated = migrations.backfillNumbersLocaleSpamEvidence(
			(double) Ema.T0_MILLIS, Ema.CLASSIFICATION_TAU_MILLIS,
			Signals.DIRECT_VOTE_EVIDENCE_WEIGHT,
			Signals.CALL_EVIDENCE_WEIGHT);
		LOG.info("Backfilled NUMBERS_LOCALE.SPAM_EVIDENCE on {} rows.", localeUpdated);

		int publishedUpdated = migrations.backfillPublishedSpamEvidence();
		LOG.info("Seeded NUMBERS.PUBLISHED_SPAM_EVIDENCE on {} rows.", publishedUpdated);

		// NUMBERS_LOCALE.VOTES is intentionally retained (epic #300): it is now
		// updated additively alongside SPAM_EVIDENCE, mirroring NUMBERS.VOTES, so
		// the raw per-region counter is never dropped. Only the unused
		// NUMBERS.PUBLISHED_VOTES snapshot column goes.
		migrations.dropNumbersPublishedVotes();
		LOG.info("Dropped legacy NUMBERS.PUBLISHED_VOTES counter.");
	}

	/**
	 * Backfill {@code NUMBERS.PUBLISHED_LEGIT_EVIDENCE} from the live
	 * {@code LEGIT_EVIDENCE} (#342 / migration 32) and drop the obsolete
	 * {@code PENDING_UPDATE} column. The snapshot-driven sweep needs the
	 * legit half of the published snapshot so it can compute
	 * {@code published_net = PUBLISHED_SPAM_EVIDENCE - PUBLISHED_LEGIT_EVIDENCE}
	 * against the projected threshold the same way the live filter does.
	 */
	private void backfillSnapshotLegitEvidence(MigrationStatements migrations) {
		LOG.info("Snapshot-driven versioning (#342): seeding NUMBERS.PUBLISHED_LEGIT_EVIDENCE.");
		int seeded = migrations.backfillPublishedLegitEvidence();
		LOG.info("Seeded PUBLISHED_LEGIT_EVIDENCE on {} rows.", seeded);

		migrations.dropNumbersPendingUpdate();
		LOG.info("Dropped legacy PENDING_UPDATE column.");
	}

	/**
	 * Moves the published blocklist state from NUMBERS columns into the
	 * narrow BLOCKLIST table (#342 / migration 39). Seeds one BLOCKLIST row
	 * per ever-published number — the bucket floor of its published net
	 * evidence, or a tombstone when it already faded below the lowest bucket
	 * — then drops the obsolete NUMBERS columns and the publication index.
	 */
	private void migrateToBlocklistTable(MigrationStatements migrations) {
		LOG.info("Bucket-based publication (#342): seeding BLOCKLIST from published NUMBERS state.");

		long now = System.currentTimeMillis();
		int seeded = migrations.seedBlocklist(
			maxRawSpamAt(now, 2), maxRawSpamAt(now, 4), maxRawSpamAt(now, 10),
			maxRawSpamAt(now, 20), maxRawSpamAt(now, 50), maxRawSpamAt(now, 100));
		LOG.info("Seeded {} BLOCKLIST rows.", seeded);

		int seededLocale = migrations.seedBlocklistLocale(Ema.decode(1.0, now, Ema.HEAT_TAU_MILLIS));
		LOG.info("Seeded {} BLOCKLIST_LOCALE rows.", seededLocale);

		migrations.dropNumbersVersionIndex();
		migrations.dropNumbersVersion();
		migrations.dropNumbersPublishedLastPing();
		migrations.dropNumbersPublishedSpamEvidence();
		migrations.dropNumbersPublishedLegitEvidence();
		LOG.info("Dropped NUMBERS publication columns (VERSION, PUBLISHED_*) and NUMBERS_VERSION_IDX.");
	}

	/**
	 * Populates SHA1 hashes for all existing aggregation rows during migration to version 13.
	 */
	private void populateAggregationHashes(SpamReports reports) {
		LOG.info("Populating SHA1 hashes for aggregation tables.");

		int count = 0;
		for (AggregationInfo a : reports.getAllAggregation10()) {
			byte[] hash = computePrefixHash(a.getPrefix());
			if (hash != null) {
				reports.updateAggregation10Hash(a.getPrefix(), hash);
				count++;
			}
		}
		LOG.info("Updated {} aggregation_10 hashes.", count);

		count = 0;
		for (AggregationInfo a : reports.getAllAggregation100()) {
			byte[] hash = computePrefixHash(a.getPrefix());
			if (hash != null) {
				reports.updateAggregation100Hash(a.getPrefix(), hash);
				count++;
			}
		}
		LOG.info("Updated {} aggregation_100 hashes.", count);
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
	 * Records a reported call from any client (Fritz!Box, dongle, mobile app,
	 * answer-bot). One call, one signal: +1 Heat and +1 evidence on the
	 * {@code NUMBERS} row, on the {@code /10} and {@code /100} aggregations
	 * and on the per-region {@code NUMBERS_LOCALE} row. The row is
	 * materialised if it doesn't exist yet — the caller already decided to
	 * report this number (either after a user-mediated spam report or
	 * because the client auto-blocked it on range/specific evidence). Per-day
	 * abuse protection lives at the API gate (see
	 * {@code Users.tryConsumeCallReportQuota} in {@code ReportCallServlet}).
	 *
	 * <p>{@code recordCall} is the single entry point for any call-driven
	 * signal: same path regardless of whether the call came from a Fritz!Box,
	 * the dongle, the mobile app, or the cloud answer bot. A catch by a
	 * personal prefix wildcard reports through the same path but with a
	 * breadth-dependent weight (see {@link #recordCall(SpamReports, PhoneNumer,
	 * String, String, long, double)} and {@link #wildcardReportWeight}).</p>
	 */
	public void recordCall(SpamReports reports, BlockList blocklist, long userId, PhoneNumer number,
			String phoneId, String dialPrefix, long now) {
		recordCall(reports, blocklist, userId, number, phoneId, dialPrefix, now, 1.0);
	}

	/**
	 * Records a call with a weight factor scaling its Heat contribution (#377).
	 *
	 * <p>A normal call report uses weight {@code 1.0}. A catch by a personal prefix wildcard is
	 * weighted down by the breadth of the matched prefix (see {@link #wildcardReportWeight}): the
	 * broader the wildcard, the less a single catch moves the Heat ranking. The {@code CALLS}
	 * counter still increments by one — a call did happen.</p>
	 *
	 * <p>Spam evidence is <em>not</em> weighted here: it is capped per user at one decoded unit per
	 * number via {@link #cappedCallSpamContribution} regardless of how many times the user
	 * intercepts the number, so a single user can never run a number's spam evidence away. Heat and
	 * the {@code CALLS} counter stay uncapped — they measure genuine call activity and drive
	 * space-constrained blocklist selection.</p>
	 */
	public void recordCall(SpamReports reports, BlockList blocklist, long userId, PhoneNumer number,
			String phoneId, String dialPrefix, long now, double weight) {
		double heatInc = weight * Ema.increment(Signals.CALL_HEAT_WEIGHT, now, Ema.HEAT_TAU_MILLIS);

		byte[] hash = NumberAnalyzer.getPhoneHash(number);
		// Per-user capped spam contribution (auto-adds/updates the user's blacklist entry).
		double evidenceInc = cappedCallSpamContribution(blocklist, userId, phoneId, hash, now);

		int updated = reports.recordCall(phoneId, now, heatInc, evidenceInc);
		if (updated == 0) {
			// First report of this number — materialise the NUMBERS row with
			// the same Heat / evidence increments the existing path would
			// have applied. Initial VOTES counter is zero; rating-category
			// counters only move via explicit user ratings (addRating).
			reports.addReport(phoneId, hash, 0, now, heatInc, evidenceInc, 0.0);
			// addReport doesn't bump CALLS; do it via the same recordCall the
			// existing branch used (zero EMA delta to avoid double-counting).
			reports.recordCall(phoneId, now, 0.0, 0.0);
		}

		// Per-day activity ledger: one call recorded today.
		reports.mergeActivity(phoneId, epochDay(now), 0, 1, 0);

		// Call evidence pushes the number toward spam: keep the SHA1 reverse-lookup entry
		// consistent with spam visibility (#300). Needed because a number first seen via a
		// pure search carries no hash, and recordCall's UPDATE branch would not set it.
		updatePhoneHashVisibility(reports, phoneId, hash);

		// Per-region signal (#340) and real-time block rebuild for the touched /100 (#300
		// follow-up) — a call report can push a block over the spam gate just like a vote.
		recomputeBlockForNumber(reports, phoneId, now);
		updateLocalization(reports, phoneId, dialPrefix, 0, 1, 0, heatInc, evidenceInc, now);
	}

	/**
	 * Report weight for a catch by a personal prefix wildcard (#377), as a function of how many
	 * trailing digits the wildcard omits from the full number.
	 *
	 * <p>The weight is computed at catch time, where the real number length is known — no length
	 * estimation needed. Cutting two digits (the {@code /100} aggregation level) counts fully;
	 * every further omitted digit halves the contribution, so an over-broad rule de-values itself.</p>
	 *
	 * @param prefixLength length of the matched wildcard prefix.
	 * @param numberLength length of the caught number (same phone-ID representation as the prefix).
	 */
	static double wildcardReportWeight(int prefixLength, int numberLength) {
		int cut = numberLength - prefixLength;
		return cut <= 2 ? 1.0 : Math.pow(0.5, cut - 2);
	}

	/**
	 * Report weight for a wildcard-triggered catch (#377): the longest of the user's blocked
	 * wildcards that is a prefix of the reported number decides the weight.
	 *
	 * @return the weight in {@code (0, 1]}, or {@code 0.0} if the user has no blocked wildcard
	 *         matching the number (e.g. a stale client claim).
	 */
	public double wildcardReportWeight(BlockList blocklist, long userId, String phoneId) {
		int bestPrefixLength = -1;
		for (String prefix : blocklist.getBlockedWildcards(userId)) {
			if (phoneId.startsWith(prefix) && prefix.length() > bestPrefixLength) {
				bestPrefixLength = prefix.length();
			}
		}
		return bestPrefixLength < 0 ? 0.0 : wildcardReportWeight(bestPrefixLength, phoneId.length());
	}

	/**
	 * Records a search hit for the given phone number.
	 *
	 * <p>Issue #332: a search is a weak Heat signal (no classification impact).
	 * The increment is applied exactly once — either by {@code incSearchCount}
	 * on the existing row, or by the follow-up {@code incSearchCount} after
	 * {@code addReport} created a fresh row (which itself stores no Heat to
	 * avoid double-counting).</p>
	 */
	public void addSearchHit(SpamReports reports, PhoneNumer number, String dialPrefix, long now) {
		String phone = NumberAnalyzer.getPhoneId(number);

		double heatInc = Ema.increment(Signals.SEARCH_HEAT_WEIGHT, now, Ema.HEAT_TAU_MILLIS);
		int rows = reports.incSearchCount(phone, now, heatInc);
		if (rows == 0) {
			// No SHA1: a search is a pure Heat signal with no classification impact, so the
			// number must not enter the reverse-lookup table (#300 privacy guard). The hash
			// is populated by updatePhoneHashVisibility once a real spam signal arrives.
			// addReport does not set SEARCHES; the second incSearchCount below
			// applies both the SEARCHES bump and the Heat increment exactly once.
			reports.addReport(phone, null, 0, now, 0.0, 0.0, 0.0);
			reports.incSearchCount(phone, now, heatInc);
		}

		// Per-day activity ledger: one search recorded today.
		reports.mergeActivity(phone, epochDay(now), 1, 0, 0);

		// A search is a pure Heat signal — no classification impact, so no
		// SPAM_EVIDENCE contribution to the locale row either.
		updateLocalization(reports, phone, dialPrefix, 1, 0, 0, heatInc, 0.0, now);
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
	
	/** Days of per-day activity kept in {@code NUMBER_ACTIVITY} before retention prunes them. */
	private static final int ACTIVITY_RETENTION_DAYS = 30;

	/** Milliseconds per day; for converting timestamps to UTC epoch-days. */
	public static final long MILLIS_PER_DAY = 24L * 60 * 60 * 1000;

	/** The UTC epoch-day (days since 1970-01-01) of the given timestamp. */
	static int epochDay(long millis) {
		return (int) Math.floorDiv(millis, MILLIS_PER_DAY);
	}

	/**
	 * Daily retention sweep of the activity ledger: drops rows older than
	 * {@link #ACTIVITY_RETENTION_DAYS}. The retention window bounds the table to a
	 * small number of rows, so this is a single delete with no batching needed —
	 * and because each day's activity is stored as a self-contained delta, dropping
	 * old rows never destroys a baseline some chart still depends on (the failure
	 * mode of the old snapshot-and-diff history).
	 */
	private void runActivityRetention() {
		LOG.info("Pruning activity ledger.");
		try {
			int removed = pruneActivity(System.currentTimeMillis());
			LOG.info("Pruned {} activity rows from the ledger.", removed);
		} catch (Exception ex) {
			LOG.error("Failed to prune activity ledger.", ex);
		}
	}

	/**
	 * Drops activity rows older than {@link #ACTIVITY_RETENTION_DAYS} relative to
	 * {@code now}. Separated from the scheduled task so it can be driven with an
	 * explicit clock in tests.
	 *
	 * @return the number of pruned rows.
	 */
	public int pruneActivity(long now) {
		int minDay = epochDay(now) - ACTIVITY_RETENTION_DAYS;
		try (SqlSession session = openSession()) {
			int removed = session.getMapper(SpamReports.class).deleteActivityBefore(minDay);
			session.commit();
			return removed;
		}
	}

	/**
	 * Per-day activity (searches, calls, votes) for the given number over the last
	 * {@code days} UTC days, oldest first. The returned list always has exactly
	 * {@code days} entries: days with no activity are filled with zero. Today is
	 * included and reflects the activity recorded so far today, since the ledger is
	 * written live on every event.
	 */
	public List<DBDayActivity> getNumberActivity(String phone, int days) {
		return getNumberActivity(phone, days, System.currentTimeMillis());
	}

	public List<DBDayActivity> getNumberActivity(String phone, int days, long now) {
		try (SqlSession session = openSession()) {
			return getNumberActivity(session.getMapper(SpamReports.class), phone, days, now);
		}
	}

	public List<DBDayActivity> getNumberActivity(SpamReports reports, String phone, int days) {
		return getNumberActivity(reports, phone, days, System.currentTimeMillis());
	}

	public List<DBDayActivity> getNumberActivity(SpamReports reports, String phone, int days, long now) {
		int today = epochDay(now);
		int minDay = today - (days - 1);

		// Ascending by day; gaps for inactive days are filled below.
		List<DBDayActivity> rows = reports.getNumberActivity(phone, minDay);

		List<DBDayActivity> result = new ArrayList<>(days);
		int idx = 0;
		for (int day = minDay; day <= today; day++) {
			if (idx < rows.size() && rows.get(idx).getEpochDay() == day) {
				result.add(rows.get(idx));
				idx++;
			} else {
				result.add(new DBDayActivity(day, 0, 0, 0));
			}
		}
		return result;
	}

	/**
	 * Daily intercepted calls, votes and searches for the last {@code days} closed
	 * UTC days (today excluded), read directly from the per-day activity ledger.
	 * Each day's value is that day's activity — no baseline, no differencing — so
	 * first-appearance and sparse-number activity is counted in full. Inactive days
	 * are present with zero counts, giving a continuous series.
	 *
	 * @return Four-element array: index 0 is a list of date labels (dd.MM., UTC),
	 *         indices 1, 2 and 3 are the per-day calls, votes and searches counts.
	 */
	public Object[] getCallsVotesSearchesHistory(int days) {
		return getCallsVotesSearchesHistory(days, System.currentTimeMillis());
	}

	public Object[] getCallsVotesSearchesHistory(int days, long now) {
		int today = epochDay(now);
		// Closed days only: [today - days, today - 1].
		int minDay = today - days;

		List<DBDayActivity> activity;
		try (SqlSession session = openSession()) {
			activity = session.getMapper(SpamReports.class).getGlobalActivity(minDay);
		}
		Map<Integer, DBDayActivity> byDay = new HashMap<>();
		for (DBDayActivity a : activity) {
			byDay.put(Integer.valueOf(a.getEpochDay()), a);
		}

		java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("dd.MM.");
		fmt.setTimeZone(TimeZone.getTimeZone("UTC"));

		List<String> labels = new ArrayList<>();
		List<Integer> callsData = new ArrayList<>();
		List<Integer> votesData = new ArrayList<>();
		List<Integer> searchesData = new ArrayList<>();
		for (int day = minDay; day < today; day++) {
			labels.add(fmt.format(new Date(day * MILLIS_PER_DAY)));
			DBDayActivity a = byDay.get(Integer.valueOf(day));
			callsData.add(Integer.valueOf(a == null ? 0 : a.getCalls()));
			votesData.add(Integer.valueOf(a == null ? 0 : a.getVotes()));
			searchesData.add(Integer.valueOf(a == null ? 0 : a.getSearches()));
		}

		return new Object[] { labels, callsData, votesData, searchesData };
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

	/**
	 * Returns cumulative user counts for the last <code>days</code> days (excluding the current day).
	 *
	 * @return Three-element array: index 0 is a list of date labels (dd.MM.), index 1 is a list of cumulative user counts (total),
	 *         index 2 is a {@code LinkedHashMap<String, List<Integer>>} of per-dial-prefix cumulative counts (top 10 + "OTHER").
	 */
	public Object[] getUserRegistrationHistory(int days) {
		Calendar cal = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
		// Start of current UTC day (excludes today).
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		long before = cal.getTimeInMillis();

		cal.add(Calendar.DAY_OF_MONTH, -days);
		long since = cal.getTimeInMillis();

		List<DailyCount> counts;
		int baseCount;
		List<DailyCount> dialCounts;
		List<DailyCount> dialBaseCounts;
		try (SqlSession session = openSession()) {
			Users u = session.getMapper(Users.class);
			counts = u.getRegistrationsPerDay(since, before);
			baseCount = u.getUserCountBefore(since);
			dialCounts = u.getRegistrationsPerDayByDial(since, before);
			dialBaseCounts = u.getUserCountBeforeByDial(since);
		}

		// Build a map for quick lookup (total).
		Map<Long, Integer> countByDay = new HashMap<>();
		for (DailyCount dc : counts) {
			countByDay.put(dc.getDayEpoch(), dc.getCnt());
		}

		// Fill gaps and accumulate to get absolute user count per day (total).
		java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("dd.MM.");
		fmt.setTimeZone(TimeZone.getTimeZone("UTC"));

		List<String> labels = new ArrayList<>();
		List<Integer> data = new ArrayList<>();

		int cumulative = baseCount;
		Calendar iter = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
		iter.setTimeInMillis(since);
		for (int i = 0; i < days; i++) {
			long dayEpoch = iter.getTimeInMillis() / 86400000;
			cumulative += countByDay.getOrDefault(dayEpoch, 0);
			labels.add(fmt.format(iter.getTime()));
			data.add(cumulative);
			iter.add(Calendar.DAY_OF_MONTH, 1);
		}

		// Per-dial: compute total user count per dial prefix (base + window sum).
		Map<String, Integer> dialBaseMap = new HashMap<>();
		for (DailyCount dc : dialBaseCounts) {
			String dial = dc.getDial() == null ? "" : dc.getDial();
			dialBaseMap.merge(dial, dc.getCnt(), Integer::sum);
		}

		Map<String, Integer> dialWindowSum = new HashMap<>();
		for (DailyCount dc : dialCounts) {
			String dial = dc.getDial() == null ? "" : dc.getDial();
			dialWindowSum.merge(dial, dc.getCnt(), Integer::sum);
		}

		// Merge all dials and compute total.
		Map<String, Integer> dialTotalMap = new HashMap<>(dialBaseMap);
		for (Map.Entry<String, Integer> e : dialWindowSum.entrySet()) {
			dialTotalMap.merge(e.getKey(), e.getValue(), Integer::sum);
		}

		// Find top 10 dial prefixes by total count.
		List<String> topDials = dialTotalMap.entrySet().stream()
			.sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
			.limit(10)
			.map(Map.Entry::getKey)
			.collect(Collectors.toList());

		Set<String> topDialSet = new HashSet<>(topDials);

		// Build per-day map for each dial prefix: dial -> (dayEpoch -> count).
		Map<String, Map<Long, Integer>> dialDayMap = new HashMap<>();
		for (DailyCount dc : dialCounts) {
			String dial = dc.getDial() == null ? "" : dc.getDial();
			String key = topDialSet.contains(dial) ? dial : "OTHER";
			dialDayMap.computeIfAbsent(key, k -> new HashMap<>())
				.merge(dc.getDayEpoch(), dc.getCnt(), Integer::sum);
		}

		// Build base counts for top dials and OTHER.
		Map<String, Integer> groupedBaseMap = new HashMap<>();
		for (Map.Entry<String, Integer> e : dialBaseMap.entrySet()) {
			String key = topDialSet.contains(e.getKey()) ? e.getKey() : "OTHER";
			groupedBaseMap.merge(key, e.getValue(), Integer::sum);
		}

		// Build cumulative series for each group (top 10 + OTHER), ordered by total count desc.
		List<String> orderedKeys = new ArrayList<>(topDials);
		if (dialDayMap.containsKey("OTHER") || groupedBaseMap.containsKey("OTHER")) {
			orderedKeys.add("OTHER");
		}

		java.util.LinkedHashMap<String, List<Integer>> perDialData = new java.util.LinkedHashMap<>();
		for (String key : orderedKeys) {
			Map<Long, Integer> dayMap = dialDayMap.getOrDefault(key, Collections.emptyMap());
			int base = groupedBaseMap.getOrDefault(key, 0);
			List<Integer> series = new ArrayList<>();
			int cum = base;
			Calendar iter2 = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
			iter2.setTimeInMillis(since);
			for (int i = 0; i < days; i++) {
				long dayEpoch = iter2.getTimeInMillis() / 86400000;
				cum += dayMap.getOrDefault(dayEpoch, 0);
				series.add(cum);
				iter2.add(Calendar.DAY_OF_MONTH, 1);
			}
			perDialData.put(key, series);
		}

		return new Object[] { labels, data, perDialData };
	}

	/**
	 * Returns cumulative active installation counts for the last <code>days</code> days.
	 *
	 * @return Three-element array: index 0 is a list of date labels, index 1 is a
	 *         {@code LinkedHashMap<String, List<Integer>>} of per-UA-prefix cumulative token counts (top 10 + "OTHER"),
	 *         index 2 is a list of cumulative registered answerbot counts.
	 */
	public Object[] getActiveInstallationsHistory(int days) {
		Calendar cal = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		long before = cal.getTimeInMillis();

		cal.add(Calendar.DAY_OF_MONTH, -days);
		long since = cal.getTimeInMillis();

		List<DailyCount> tokenCounts;
		List<DailyCount> tokenBaseCounts;
		List<DailyCount> registeredBotCounts;
		int registeredBotBase;
		try (SqlSession session = openSession()) {
			Users u = session.getMapper(Users.class);
			tokenCounts = u.getTokenCreationsPerDayByAgent(since, before);
			tokenBaseCounts = u.getTokenCountBeforeByAgent(since);
			registeredBotCounts = u.getRegisteredAnswerbotCreationsPerDay(since, before);
			registeredBotBase = u.getRegisteredAnswerbotCountBefore(since);
		}

		// Build date labels.
		java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("dd.MM.");
		fmt.setTimeZone(TimeZone.getTimeZone("UTC"));

		List<String> labels = new ArrayList<>();
		Calendar iter = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
		iter.setTimeInMillis(since);
		for (int i = 0; i < days; i++) {
			labels.add(fmt.format(iter.getTime()));
			iter.add(Calendar.DAY_OF_MONTH, 1);
		}

		// Per-agent: compute total token count per UA prefix (base + window sum).
		Map<String, Integer> agentBaseMap = new HashMap<>();
		for (DailyCount dc : tokenBaseCounts) {
			String agent = dc.getDial() == null ? "" : dc.getDial();
			agentBaseMap.merge(agent, dc.getCnt(), Integer::sum);
		}

		Map<String, Integer> agentWindowSum = new HashMap<>();
		for (DailyCount dc : tokenCounts) {
			String agent = dc.getDial() == null ? "" : dc.getDial();
			agentWindowSum.merge(agent, dc.getCnt(), Integer::sum);
		}

		Map<String, Integer> agentTotalMap = new HashMap<>(agentBaseMap);
		for (Map.Entry<String, Integer> e : agentWindowSum.entrySet()) {
			agentTotalMap.merge(e.getKey(), e.getValue(), Integer::sum);
		}

		// Find top 5 UA prefixes by total count.
		List<String> topAgents = agentTotalMap.entrySet().stream()
			.sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
			.limit(5)
			.map(Map.Entry::getKey)
			.collect(Collectors.toList());

		Set<String> topAgentSet = new HashSet<>(topAgents);

		// Build per-day map for each UA prefix: agent -> (dayEpoch -> count).
		Map<String, Map<Long, Integer>> agentDayMap = new HashMap<>();
		for (DailyCount dc : tokenCounts) {
			String agent = dc.getDial() == null ? "" : dc.getDial();
			String key = topAgentSet.contains(agent) ? agent : "OTHER";
			agentDayMap.computeIfAbsent(key, k -> new HashMap<>())
				.merge(dc.getDayEpoch(), dc.getCnt(), Integer::sum);
		}

		// Build base counts for top agents and OTHER.
		Map<String, Integer> groupedBaseMap = new HashMap<>();
		for (Map.Entry<String, Integer> e : agentBaseMap.entrySet()) {
			String key = topAgentSet.contains(e.getKey()) ? e.getKey() : "OTHER";
			groupedBaseMap.merge(key, e.getValue(), Integer::sum);
		}

		// Build cumulative series for each group.
		List<String> orderedKeys = new ArrayList<>(topAgents);
		if (agentDayMap.containsKey("OTHER") || groupedBaseMap.containsKey("OTHER")) {
			orderedKeys.add("OTHER");
		}

		java.util.LinkedHashMap<String, List<Integer>> perAgentData = new java.util.LinkedHashMap<>();
		for (String key : orderedKeys) {
			Map<Long, Integer> dayMap = agentDayMap.getOrDefault(key, Collections.emptyMap());
			int base = groupedBaseMap.getOrDefault(key, 0);
			List<Integer> series = new ArrayList<>();
			int cum = base;
			Calendar iter2 = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
			iter2.setTimeInMillis(since);
			for (int i = 0; i < days; i++) {
				long dayEpoch = iter2.getTimeInMillis() / 86400000;
				cum += dayMap.getOrDefault(dayEpoch, 0);
				series.add(cum);
				iter2.add(Calendar.DAY_OF_MONTH, 1);
			}
			perAgentData.put(key, series);
		}

		// Build cumulative answerbot series (registered).
		Map<Long, Integer> registeredBotDayMap = new HashMap<>();
		for (DailyCount dc : registeredBotCounts) {
			registeredBotDayMap.put(dc.getDayEpoch(), dc.getCnt());
		}

		List<Integer> registeredBotData = new ArrayList<>();
		int cumRegistered = registeredBotBase;
		iter.setTimeInMillis(since);
		for (int i = 0; i < days; i++) {
			long dayEpoch = iter.getTimeInMillis() / 86400000;
			cumRegistered += registeredBotDayMap.getOrDefault(dayEpoch, 0);
			registeredBotData.add(cumRegistered);
			iter.add(Calendar.DAY_OF_MONTH, 1);
		}

		return new Object[] { labels, perAgentData, registeredBotData };
	}

	/**
	 * Returns blocked number counts per country (DIAL prefix) from NUMBERS_LOCALE.
	 */
	public List<DailyCount> getBlockedNumbersByCountry() {
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			double maxRawSpam = Ema.projectedThreshold(MIN_VOTES,
				System.currentTimeMillis(), Ema.CLASSIFICATION_TAU_MILLIS);
			return reports.getBlockedNumbersByCountry(maxRawSpam);
		}
	}

	public int getVotes() {
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			return nonNull(reports.getTotalVotes());
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
