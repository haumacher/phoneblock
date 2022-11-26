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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.apache.ibatis.jdbc.ScriptRunner;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.haumacher.phoneblock.callreport.model.CallReport;
import de.haumacher.phoneblock.callreport.model.ReportInfo;
import de.haumacher.phoneblock.db.model.BlockListEntry;
import de.haumacher.phoneblock.db.model.Blocklist;
import de.haumacher.phoneblock.db.model.PhoneInfo;
import de.haumacher.phoneblock.db.model.Rating;
import de.haumacher.phoneblock.db.model.RatingInfo;
import de.haumacher.phoneblock.db.model.SearchInfo;
import de.haumacher.phoneblock.db.settings.UserSettings;
import de.haumacher.phoneblock.index.IndexUpdateService;

/**
 * The database abstraction layer.
 */
public class DB {

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
		"BLOCKLIST", "EXCLUDES", "SPAMREPORTS", "OLDREPORTS", "USERS", "CALLREPORT", "CALLERS", "RATINGS", "SEARCHES", 
		"SEARCHCLUSTER", "SEARCHHISTORY"
	);
	
	private SqlSessionFactory _sessionFactory;
	private DataSource _dataSource;

	private static final String BASIC_PREFIX = "Basic ";

	private MessageDigest _sha256;

	private SecureRandom _rnd = new SecureRandom();
	
	private ScheduledExecutorService _scheduler;

	private IndexUpdateService _indexer;

	private List<ScheduledFuture<?>> _tasks = new ArrayList<>();
	
	public DB(DataSource dataSource) throws SQLException, UnsupportedEncodingException {
		this(dataSource, IndexUpdateService.NONE);
	}
	
	/** 
	 * Creates a {@link DB}.
	 *
	 * @param dataSource
	 */
	public DB(DataSource dataSource, IndexUpdateService indexer) throws SQLException, UnsupportedEncodingException {
		_dataSource = dataSource;
		_indexer = indexer;
		
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
		
		 _scheduler = new ScheduledThreadPoolExecutor(1);
		 
		 cleanup();

		 Date timeCleanup = schedule(20, this::cleanup);
		 LOG.info("Scheduled next DB cleanup: " + timeCleanup);
		 
		 Date timeHistory = schedule(0, this::runCleanupSearchHistory);
		 LOG.info("Scheduled search history cleanup: " + timeHistory);
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
		 
		_tasks.add(_scheduler.scheduleAtFixedRate(command, initialDelay, 24 * 60 * 60 * 1000L, TimeUnit.MILLISECONDS));
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
	 * @param clientName The authorization scope for the new user.
	 * @param extId The ID in the given authorization scope.
	 * @param login The user name (e.g. e-mail address) of the new account.
	 * @return The randomly generated password for the account.
	 */
	public String createUser(String clientName, String extId, String login, String displayName) throws UnsupportedEncodingException {
		String passwd = createPassword(20);
		addUser(clientName, extId, login, displayName, passwd);
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
	 * Sets the user's e-mail address.
	 */
	public void setEmail(String login, String email) {
		try (SqlSession session = openSession()) {
			Users users = session.getMapper(Users.class);
			users.setEmail(login, email);
			session.commit();
		}
	}
	
	/** 
	 * Sets the user's external ID in its OAuth authorization scope.
	 */
	public void setExtId(String login, String extId) {
		try (SqlSession session = openSession()) {
			Users users = session.getMapper(Users.class);
			users.setExtId(login, extId);
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
	public void processVotes(String phone, int votes, long time) {
		if (votes == 0) {
			return;
		}
		
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			processVotes(reports, phone, votes, time);
			session.commit();
		}
	}

	/**
	 * Implementation of {@link #processVotes(String, int, long)} when there is already a database session.
	 * 
	 * @return Whether an index update should be performed.
	 */
	public boolean processVotes(SpamReports reports, String phone, int votes, long time) {
		boolean updateRequired = internalProcessVotes(reports, phone, votes, time);
		if (updateRequired) {
			publishUpdate(phone);
		}
		return updateRequired;
	}

	private boolean internalProcessVotes(SpamReports reports, String phone, int votes, long time) {
		final int oldVotes = nonNull(reports.getVotes(phone));
		final int newVotes = oldVotes + votes;
		
		if (newVotes <= 0) {
			reports.delete(phone);
		} else {
			int rows = reports.addVote(phone, votes, time);
			if (rows == 0) {
				reports.addReport(phone, votes, time);
			}
		}
		
		return classify(oldVotes) != classify(newVotes);
	}

	/** 
	 * Adds a rating for a phone number.
	 *
	 * @param phone The phone number to rate.
	 * @param rating The user rating.
	 * @param now The current time in milliseconds since epoch.
	 */
	public void addRating(String phone, Rating rating, long now) {
		boolean updateRequired;
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);

			if (rating == Rating.B_MISSED) {
				final int currentVotes = nonNull(reports.getVotes(phone));
				if (currentVotes > 0) {
					// The number was already reported, a missed call makes the probability for spam higher.
					updateRequired = internalProcessVotes(reports, phone, Ratings.getVotes(rating), now);
				} else {
					updateRequired = false;
				}
			} else {
				updateRequired = internalProcessVotes(reports, phone, Ratings.getVotes(rating), now);

				Rating oldRating = reports.getRating(phone);
				
				// Record rating.
				int rows = reports.incRating(phone, rating, now);
				if (rows == 0) {
					reports.addRating(phone, rating, now);
				}
				
				Rating newRating = reports.getRating(phone);
				updateRequired = updateRequired || oldRating != newRating;
			}
			
			session.commit();
		}
		
		if (updateRequired) {
			publishUpdate(phone);
		}
	}

	/** 
	 * Retrieve the {@link Rating} for the given phone number with the maximum number of votes.
	 */
	public Rating getRating(String phone) {
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			
			return reports.getRating(phone);
		}
	}

	/** 
	 * Retrieve all {@link Rating}s for the given phone number with corresponding vote count.
	 */
	public List<? extends RatingInfo> getRatings(String phone) {
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			
			return reports.getRatings(phone);
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

	private void publishUpdate(String phone) {
		_indexer.publishUpdate("/nums/" + phone);
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
	public List<SpamReport> getLatestSpamReports(long notBefore) {
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			return reports.getLatestReports(notBefore);
		}
	}

	private static Comparator<? super SearchInfo> byDate = (s1, s2) -> -Long.compare(s1.getLastSearch(), s2.getLastSearch());
	
	/**
	 * Looks up the latest searches.
	 */
	public List<? extends SearchInfo> getTopSearches() {
		return getTopSearches(3, 3);
	}
	
	List<? extends SearchInfo> getTopSearches(int cntLatest, int cntSearches) {
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			
			int revision = nonNull(reports.getLastRevision());
			Set<String> yesterdaySearches = revision > 0 ? reports.getTopSearches(revision) : Collections.emptySet();
			
			Set<String> topNumbers = reports.getLatestSearchesToday();
			topNumbers.addAll(yesterdaySearches);
			
			if (topNumbers.isEmpty()) {
				return Collections.emptyList();
			}

			List<DBSearchInfo> topSearches = reports.getSearchesTodayAll(topNumbers);
			Map<String, DBSearchInfo> yesterdayByPhone = reports.getSearchesAtAll(revision, topNumbers).stream().collect(Collectors.toMap(i -> i.getPhone(), i -> i));
			
			for (DBSearchInfo today : topSearches) {
				DBSearchInfo yesterday = yesterdayByPhone.get(today.getPhone());
				if (yesterday == null) {
					today.setTotal(0);
				} else {
					today.setTotal(yesterday.getCount());
				}
			}

			topSearches.sort(byDate);
			
			List<SearchInfo> result = new ArrayList<>();

			// Latest 3 (most likely from today).
			int index = Math.min(cntLatest, topSearches.size());
			result.addAll(topSearches.subList(0, index));
			
			// Sort the rest by total amount of searches (from today and yesterday).
			ArrayList<DBSearchInfo> tail = new ArrayList<>(topSearches.subList(index, topSearches.size())); 
			tail.sort((s1, s2) -> -Integer.compare(s1.getCount() + s1.getTotal(), s2.getCount() + s2.getTotal()));
			
			// Top 3
			result.addAll(tail.subList(0, Math.min(cntSearches, tail.size())));
			
			// Present all in last search order.
			result.sort(byDate);
			
			return result;
		}
	}
	
	/**
	 * Looks all spam reports.
	 */
	public List<SpamReport> getAll(int limit) {
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			return reports.getAll(limit);
		}
	}
	
	/**
	 * Looks up spam reports with the most votes in the last month.
	 */
	public List<SpamReport> getTopSpamReports(int cnt) {
		Calendar cal = GregorianCalendar.getInstance();
		cal.add(Calendar.MONTH, -1);
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.MILLISECOND, 0);
		long notBefore = cal.getTimeInMillis();
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			return reports.getTopSpammers(cnt, notBefore);
		}
	}
	
	/**
	 * Looks up the newest entries in the blocklist.
	 */
	public List<SpamReport> getLatestBlocklistEntries(String login) {
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);

			int minVotes = getMinVotes(session, login);
			return reports.getLatestBlocklistEntries(minVotes);
		}
	}

	/**
	 * Looks up the newest entries in the blocklist.
	 */
	public Blocklist getBlockListAPI(int minVotes) {
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			List<PhoneInfo> numbers = reports.getBlocklist(minVotes)
					.stream()
					.collect(Collectors.toMap(s -> s.getPhone(), s -> s, DB::withMaxRating))
					.values()
					.stream()
					.map(s -> PhoneInfo.create()
							.setPhone(s.getPhone())
							.setVotes(s.getVotes())
							.setRating(s.getRating()))
					.sorted((a, b) -> a.getPhone().compareTo(b.getPhone()))
					.collect(Collectors.toList());
			return Blocklist.create().setNumbers(numbers);
		}
	}
	
	private static BlockListEntry withMaxRating(BlockListEntry a, BlockListEntry b) {
		return compare(a, b) < 0 ? b : a;
	}

	private static int compare(BlockListEntry a, BlockListEntry b) {
		int aCount = a.getCount();
		int bCount = b.getCount();
		return aCount < bCount ? -1 : aCount > bCount ? 1 : Integer.compare(a.getRating().ordinal(), b.getRating().ordinal());
	}

	private int getMinVotes(SqlSession session, String login) {
		int minVotes = (login == null) ? MIN_VOTES : getSettings(session, login).getMinVotes();
		return minVotes;
	}

	private DBUserSettings getSettings(SqlSession session, String login) {
		Users users = session.getMapper(Users.class);
		DBUserSettings settings = users.getSettings(login);
		return settings;
	}
	
	/**
	 * The current DB status.
	 */
	public Status getStatus(String login) {
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			int minVotes = getMinVotes(session, login);
			return new Status(reports.getStatistics(minVotes), nonNull(reports.getTotalVotes()), nonNull(reports.getArchivedReportCount()));
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
	
	/**
	 * Info about the given phone number, or <code>null</code>, if the given number is not a known source of spam.
	 */
	public SpamReport getPhoneInfo(String phone) {
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			SpamReport result = reports.getPhoneInfo(phone);
			if (result == null) {
				result = reports.getPhoneInfoArchived(phone);
				if (result == null) {
					result = new SpamReport(phone, 0, 0, 0);
				} else {
					result.setArchived(true);
				}
			}
			return result;
		}
	}
	
	public PhoneInfo getPhoneApiInfo(String phone) {
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			PhoneInfo result = reports.getApiPhoneInfo(phone);
			if (result == null) {
				result = reports.getApiPhoneInfoArchived(phone);
				if (result == null) {
					result = PhoneInfo.create().setPhone(phone);
				}
			}
			return result;
		}
	}
	
	/**
	 * Records a search hit for the given phone number.
	 */
	public void addSearchHit(String phone) {
		long now = System.currentTimeMillis();
		addSearchHit(phone, now);
	}

	void addSearchHit(String phone, long now) {
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			
			int rows = reports.incSearchCount(phone, now);
			if (rows == 0) {
				reports.addSearchEntry(phone, now);
			}
			
			session.commit();
		}
	}
	
	/**
	 * Shuts down the database layer.
	 */
	public void shutdown() {
		try (SqlSession session = openSession()) {
			try (Statement statement = session.getConnection().createStatement()) {
				statement.execute("SHUTDOWN");
			}
		} catch (Exception ex) {
			LOG.error("Database shutdown failed.", ex);
		}
		
		for (ScheduledFuture<?> task : _tasks) {
			task.cancel(false);
		}
		
		_scheduler.shutdown();
		
		try {
			boolean finished = _scheduler.awaitTermination(10, TimeUnit.SECONDS);
			if (!finished) {
				LOG.warn("DB scheduler did not terminate in time.");
			}
		} catch (InterruptedException ex) {
			LOG.error("Failed to shut down scheduler.", ex);
		}
	}

	/** 
	 * Adds the given user with the given password.
	 */
	public void addUser(String clientName, String extId, String login, String displayName, String passwd) throws UnsupportedEncodingException {
		try (SqlSession session = openSession()) {
			Users users = session.getMapper(Users.class);
			users.addUser(login, clientName, extId, displayName, pwhash(passwd), System.currentTimeMillis());
			session.commit();
		}
	}

	/** 
	 * Sets a new password for the given user.
	 * 
	 * @return The new password, or <code>null</code>, if the given user does not exist.
	 */
	public String resetPassword(String login) throws UnsupportedEncodingException {
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
	 * The user ID, or <code>null</code>, if the user with the given login does not yet exist.
	 */
	public String getLogin(String clientName, String extId) {
		try (SqlSession session = openSession()) {
			Users users = session.getMapper(Users.class);
			
			return users.getLogin(clientName, extId);
		}
	}

	/** 
	 * Sets the last access time for the given user to the given timestamp.
	 * 
	 * @param userAgent The user agent header string.
	 */
	public void updateLastAccess(String login, long timestamp, String userAgent) {
		try (SqlSession session = openSession()) {
			Users users = session.getMapper(Users.class);
			
			users.setLastAccess(login, timestamp, userAgent);
			session.commit();
		}
	}
	
	/** 
	 * Retrieves the settings for a user.
	 */
	public UserSettings getSettings(String login) {
		try (SqlSession session = openSession()) {
			Users users = session.getMapper(Users.class);
			
			return users.getSettings(login);
		}
	}
	
	/** 
	 * Updates the settings for a user.
	 */
	public void updateSettings(UserSettings settings) {
		try (SqlSession session = openSession()) {
			Users users = session.getMapper(Users.class);
			
			users.updateSettings(settings.getId(), settings.getMinVotes(), settings.getMaxLength());
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
				String login = decoded.substring(0, sepIndex);
				String passwd = decoded.substring(sepIndex + 1);
				return login(login, passwd);
			}
		}
		LOG.warn("Invalid authentication received: " + authHeader);
		return null;
	}

	/** 
	 * Checks the given credentials.
	 * 
	 * @return The authorized user name, if authorization was successful, <code>null</code> otherwise.
	 */
	public String login(String login, String passwd) throws UnsupportedEncodingException, IOException {
		byte[] pwhash = pwhash(passwd);
		
		try (SqlSession session = openSession()) {
			Users users = session.getMapper(Users.class);
			
			InputStream hashIn = users.getHash(login);
			if (hashIn != null) {
				byte[] expectedHash = hashIn.readAllBytes();
				if (Arrays.equals(pwhash, expectedHash)) {
					return login;
				} else {
					LOG.warn("Invalid password (length " + passwd.length() + ") for user: " + login);
				}
			} else {
				LOG.warn("Invalid user name supplied: '" + saveChars(login) + "'");
			}
		}
		return null;
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

	private byte[] pwhash(String passwd) throws UnsupportedEncodingException {
		return _sha256.digest(passwd.getBytes("utf-8"));
	}

	/** 
	 * Explicitly allows a certain number by storing an exclude to the global blocklist.
	 *
	 * @param principal The current user.
	 * @param phoneNumber The phone number to explicitly allow.
	 */
	public void deleteEntry(String principal, String phoneNumber) {
		try (SqlSession session = openSession()) {
			SpamReports spamreport = session.getMapper(SpamReports.class);
			BlockList blockList = session.getMapper(BlockList.class);
			Users users = session.getMapper(Users.class);
			
			long currentUser = users.getUserId(principal);
			
			boolean wasAddedBefore = blockList.removePersonalization(currentUser, phoneNumber);

			// For safety reasons, to prevent primary key constraint violation.
			blockList.removeExclude(currentUser, phoneNumber);
			
			blockList.addExclude(currentUser, phoneNumber);
			
			if (wasAddedBefore) {
				// Note: Only a spam reporter may revoke his vote. This prevents vandals from deleting the whole list.
				processVotes(spamreport, phoneNumber, -2, System.currentTimeMillis());
			}
			
			session.commit();
		}
	}

	private void cleanup() {
		LOG.info("Starting DB cleanup.");
		
		Calendar cal = GregorianCalendar.getInstance();
		long now = cal.getTimeInMillis();
		cal.add(Calendar.DAY_OF_MONTH, -OLD_VOTE_DAYS);
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.MILLISECOND, 0);
		long before = cal.getTimeInMillis();
		
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			int reactivated = reports.reactivateOldReportsWithNewVotes(now);
			int deletedOld = reports.deleteOldReportsWithNewVotes(now);
			int archived = reports.archiveReportsWithLowVotes(before, MIN_VOTES, WEEK_PER_VOTE);
			int deletedNew = reports.deleteArchivedReports(now);
			
			LOG.info("Reactivated " + reactivated + " reports, archived " + archived + " reports.");
			if (deletedOld != reactivated) {
				LOG.error("Reactivated " + reactivated + " records but deleted " + deletedOld + " reports from archive.");
			}
			if (deletedNew != archived) {
				LOG.error("Archived " + archived + " records but deleted " + deletedNew + " reports from database.");
			}
			
			session.commit();
		}
		
		LOG.info("Finished DB cleanup.");
	}

	private void runCleanupSearchHistory() {
		LOG.info("Processing search history.");
		cleanupSearchHistory(365);
	}

	/** 
	 * Creates a new snapshot of search history.
	 */
	public void cleanupSearchHistory(int maxHistory) {
		long now = System.currentTimeMillis();
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			
			reports.createRevision(now);
			int newest = reports.getLastRevision();
			reports.fillSearchRevision(newest);
			reports.backupSearches();
			
			reports.fillRatingRevision(newest);
			reports.backupRatings();
			
			int oldest = reports.getOldestRevision();
			if (newest - oldest >= maxHistory) {
				reports.cleanSearchCluster(oldest);
				reports.removeSearchCluster(oldest);
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
	public List<Integer> getSearchHistory(String phone) {
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			List<Integer> data = reports.getSearchCountHistory(phone);
			data.add(nonNull(reports.getCurrentSearchHits(phone)));
			return data;
		}
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
			int cnt = users.updateReportInfo(userId, callReport.getTimestamp(), callReport.getLastid(), now);
			if (cnt == 0) {
				users.createReportInfo(userId, callReport.getTimestamp(), callReport.getLastid(), now);
			}
			
			for (String phone : callReport.getCallers()) {
				int ok = users.addCall(userId, phone, now);
				if (ok == 0) {
					users.insertCaller(userId, phone, now);
				}
				
				processVotes(reports, phone, 2, now);
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

	public List<? extends SearchInfo> getSearches(String phone) {
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);

			int lastRevision = nonNull(reports.getLastRevision());
			int startRevision = Math.max(1,  lastRevision - 6);
			
			List<DBSearchInfo> dbHistory = reports.getSearchHistory(startRevision, phone);
			SearchInfo today = reports.getSearchesToday(phone);
			if (dbHistory.isEmpty() && today == null) {
				return Collections.emptyList();
			}

			List<SearchInfo> result = new ArrayList<>();
			
			int revision = startRevision;
			for (DBSearchInfo info : dbHistory) {
				while (info.getRevision() > revision) {
					result.add(SearchInfo.create().setRevision(revision++));
				}
				result.add(info);
				revision++;
			}
			while (lastRevision > revision) {
				result.add(SearchInfo.create().setRevision(revision++));
			}
			if (today == null) {
				today = SearchInfo.create();
			}
			today.setRevision(lastRevision + 1);
			result.add(today);
			return result;
		}
	}
	
}
