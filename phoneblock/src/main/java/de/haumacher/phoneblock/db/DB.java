/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
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
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.apache.ibatis.exceptions.PersistenceException;
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

import de.haumacher.phoneblock.ab.DBAnswerbotInfo;
import de.haumacher.phoneblock.analysis.NumberAnalyzer;
import de.haumacher.phoneblock.callreport.model.CallReport;
import de.haumacher.phoneblock.callreport.model.ReportInfo;
import de.haumacher.phoneblock.db.model.BlockListEntry;
import de.haumacher.phoneblock.db.model.Blocklist;
import de.haumacher.phoneblock.db.model.NumberInfo;
import de.haumacher.phoneblock.db.model.PhoneInfo;
import de.haumacher.phoneblock.db.model.Rating;
import de.haumacher.phoneblock.db.model.SearchInfo;
import de.haumacher.phoneblock.db.model.SpamReport;
import de.haumacher.phoneblock.db.model.UserComment;
import de.haumacher.phoneblock.db.settings.UserSettings;
import de.haumacher.phoneblock.index.IndexUpdateService;
import de.haumacher.phoneblock.mail.MailService;
import de.haumacher.phoneblock.mail.check.db.Domains;
import de.haumacher.phoneblock.scheduler.SchedulerService;

/**
 * The database abstraction layer.
 */
public class DB {

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
		"BLOCKLIST", "EXCLUDES", "SPAMREPORTS", "OLDREPORTS", "USERS", "CALLREPORT", "CALLERS", "RATINGS", "SEARCHES", 
		"SEARCHCLUSTER", "SEARCHHISTORY"
	);
	
	private SqlSessionFactory _sessionFactory;
	private DataSource _dataSource;

	private static final String BASIC_PREFIX = "Basic ";

	public static final int MIN_AGGREGATE = 4;

	private MessageDigest _sha256;

	private final SecureRandom _rnd;
	
	private SchedulerService _scheduler;
	
	private IndexUpdateService _indexer;

	private List<ScheduledFuture<?>> _tasks = new ArrayList<>();

	private MailService _mailService;

	private boolean _sendHelpMails;

	public DB(DataSource dataSource, SchedulerService scheduler) throws SQLException {
		this(new SecureRandom(), false, dataSource, IndexUpdateService.NONE, scheduler, null);
	}
	
	/** 
	 * Creates a {@link DB}.
	 * @param sendHelpMails 
	 *
	 * @param dataSource
	 */
	public DB(SecureRandom rnd, boolean sendHelpMails, DataSource dataSource, IndexUpdateService indexer, SchedulerService scheduler, MailService mailService) throws SQLException {
		_rnd = rnd;
		_sendHelpMails = sendHelpMails;
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
		        sr.runScript(new InputStreamReader(getClass().getResourceAsStream("db-schema.sql"), StandardCharsets.UTF_8));
			}
		}
		
		try {
			_sha256 = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException ex) {
			throw new RuntimeException("Digest algorithm not supported: " + ex.getMessage(), ex);
		}
		
		 cleanup();

		 Date timeCleanup = schedule(20, this::cleanup);
		 LOG.info("Scheduled next DB cleanup: " + timeCleanup);
		 
		 Date timeHistory = schedule(0, this::runUpdateHistory);
		 LOG.info("Scheduled search history cleanup: " + timeHistory);
		 
		 if (sendHelpMails && _mailService != null) {
			 Date supportMails = schedule(18, this::sendSupportMails);
			 LOG.info("Scheduled support mails: " + supportMails);
		 } else {
			 LOG.info("Support mails are disabled.");
		 }
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
		 
		_tasks.add(_scheduler.executor().scheduleAtFixedRate(command, initialDelay, 24 * 60 * 60 * 1000L, TimeUnit.MILLISECONDS));
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
	public String createUser(String clientName, String extId, String login, String displayName) {
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
			processVotesAndPublish(reports, phone, votes, time);
			session.commit();
		}
	}

	/**
	 * Implementation of {@link #processVotes(String, int, long)} when there is already a database session.
	 * 
	 * @return Whether an index update should be performed.
	 */
	public boolean processVotesAndPublish(SpamReports reports, String phone, int votes, long time) {
		boolean updateRequired = processVotes(reports, phone, votes, time);
		if (updateRequired) {
			_indexer.publishUpdate(phone);
		}
		return updateRequired;
	}

	/**
	 * Updates the votes for a certain number.
	 */
	public boolean processVotes(SpamReports reports, String phone, int votes, long time) {
		final int oldVotes = nonNull(reports.getVotes(phone));
		final int newVotes = oldVotes + votes;
		
		int rows = reports.addVote(phone, votes, time);
		if (rows == 0) {
			// Number was not yet present, must be added.
			reports.addReport(phone, votes, time);
			
			if (votes > 0) {
				updateAggregation10(reports, phone, 1, votes);
			}
		} else {
			// Add new votes to aggregation.
			updateAggregation10(reports, phone, delta(oldVotes, newVotes), votes);
		}
		
		return classify(oldVotes) != classify(newVotes);
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
					if (cntBefore < MIN_AGGREGATE && cnt >= MIN_AGGREGATE) {
						updateAggregation100(reports, phone, 1, votes);
					}
					else if (cntBefore >= MIN_AGGREGATE && cnt < MIN_AGGREGATE) {
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
	 * @param phone The phone number to rate.
	 * @param rating The user rating.
	 * @param comment A user comment for this number.
	 * @param now The current time in milliseconds since epoch.
	 */
	public void addRating(String phone, Rating rating, String comment, long now) {
		boolean updateRequired;
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			
			UserComment userComment = UserComment.create()
				.setId(UUID.randomUUID().toString())
				.setPhone(phone)
				.setRating(rating)
				.setComment(comment)
				.setCreated(now);
			
			updateRequired = addRating(reports, userComment);
			
			session.commit();
		}
		
		if (updateRequired) {
			_indexer.publishUpdate(phone);
		}
	}

	/** 
	 * Adds a rating for a phone number without DB commit.
	 * 
	 * @return Whether an index update is required.
	 */
	public boolean addRating(SpamReports reports, UserComment comment) {
		boolean updateRequired = false;
		
		String phone = comment.getPhone();
		Rating rating = comment.getRating();
		String commentText = comment.getComment();
		long created = comment.getCreated();
		if (commentText != null && !commentText.isBlank()) {
			if (commentText.length() > MAX_COMMENT_LENGTH) {
				// Limit to DB constraint.
				commentText = commentText.substring(0, MAX_COMMENT_LENGTH);
			}
			reports.addComment(comment.getId(), phone, rating, commentText, comment.getService(), created);
			updateRequired = true;
		}

		updateRequired |= processVotes(reports, phone, Ratings.getVotes(rating), created);
		if (rating != Rating.B_MISSED) {
			Rating oldRating = rating(reports, phone);
			
			// Record rating.
			reports.incRating(phone, rating, created);
			
			Rating newRating = rating(reports, phone);
			updateRequired |= oldRating != newRating;
		}
		return updateRequired;
	}

	private Rating rating(SpamReports reports, String phone) {
		return rating(getPhoneInfo(reports, phone));
	}

	public NumberInfo getPhoneInfo(SpamReports reports, String phone) {
		DBNumbersEntry result = reports.getPhoneInfo(phone);
		return result != null ? result : NumberInfo.create().setPhone(phone);
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
	public List<DBNumbersEntry> getLatestSpamReports(long notBefore) {
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			return reports.getLatestReports(notBefore);
		}
	}

	/**
	 * Looks up the latest searches.
	 */
	public List<? extends SearchInfo> getTopSearches() {
		return getTopSearches(6);
	}
	
	List<? extends SearchInfo> getTopSearches(int cnt) {
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			
			int lastRev = nonNull(reports.getLastRevision());
			
			int afterRev = lastRev > 0 ? lastRev - 1 : 0;
			long revisionDate = orMidnightYesterday(reports.getRevisionDate(afterRev));
			
			List<SearchInfo> topSearches = new ArrayList<>();
			for (DBNumbersEntry entry : reports.getUpdatedPhoneInfos(revisionDate)) {
				String phone = entry.getPhone();
				int searches = entry.getSearches();
				SearchInfo info = SearchInfo.create()
						.setPhone(phone)
						.setLastSearch(entry.getUpdated())
						.setTotal(searches);
				
				DBNumberHistory before = reports.getHistoryEntry(phone, afterRev);
				if (before == null) {
					info.setCount(searches);
				} else {
					info.setCount(searches - before.getSearches());
				}
				
				topSearches.add(info);
			}
			
			// Top 6 numbers.
			Comparator<SearchInfo> comparator = Comparator.comparingInt(s -> -s.getCount());
			comparator = comparator.thenComparingLong(s -> -s.getLastSearch());
			topSearches.sort(comparator);
			
			List<SearchInfo> result = new ArrayList<>(topSearches.subList(0, Math.min(topSearches.size(), cnt)));

			// Sorted by date descending.
			result.sort(Comparator.comparing(s -> -s.getLastSearch()));
			return result;
		}
	}
	
	private long orMidnightYesterday(Long revisionDate) {
		if (revisionDate != null) {
			return revisionDate.longValue();
		}
		
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
	public List<DBNumbersEntry> getAll(int limit) {
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			return reports.getAll(limit);
		}
	}
	
	/**
	 * Looks up spam reports with the most votes in the last month.
	 */
	public List<DBNumbersEntry> getTopSpamReports(int cnt) {
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
	public List<DBNumbersEntry> getLatestBlocklistEntries(String login) {
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
			Set<String> whiteList = reports.getWhiteList();
			List<BlockListEntry> numbers = reports.getBlocklist(minVotes)
					.stream()
					.filter(s -> !whiteList.contains(s.getPhone()))
					.map(DB::toBlocklistEntry)
					.collect(Collectors.toList());
			return Blocklist.create().setNumbers(numbers);
		}
	}
	
	private static BlockListEntry toBlocklistEntry(DBNumbersEntry n) {
		return BlockListEntry.create()
				.setPhone(n.getPhone())
				.setVotes(n.getVotes())
				.setRating(rating(n));
	}

	private static Rating rating(NumberInfo n) {
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
			int votes = n.getRatingPing();
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

	public PhoneInfo getPhoneApiInfo(String phone) {
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			return getPhoneApiInfo(reports, phone);
		}
	}

	public PhoneInfo getPhoneApiInfo(SpamReports reports, String phone) {
		if (reports.isWhiteListed(phone)) {
			return PhoneInfo.create().setPhone(phone).setWhiteListed(true).setRating(Rating.A_LEGITIMATE);
		}

		NumberInfo info = getPhoneInfo(reports, phone);
		AggregationInfo aggregation10 = getAggregation10(reports, phone);
		AggregationInfo aggregation100 = getAggregation100(reports, phone);
		
		return getPhoneInfo(info, aggregation10, aggregation100);
	}

	public PhoneInfo getPhoneInfo(NumberInfo info, AggregationInfo aggregation10, AggregationInfo aggregation100) {
		PhoneInfo result = PhoneInfo.create()
			.setPhone(info.getPhone())
			.setDateAdded(info.getAdded())
			.setLastUpdate(info.getUpdated());
		
		int votes;
		if (aggregation100.getVotes() >= MIN_AGGREGATE) {
			votes = aggregation100.getVotes();
			if (!info.isActive()) {
				// Direct votes did not count yet.
				votes += info.getVotes();
			}
			
			if (aggregation10.getVotes() < MIN_AGGREGATE) {
				// The votes of this number did not yet count to the aggregation of the block.
				votes += aggregation10.getVotes();
			}
		} else if (aggregation10.getVotes() >= MIN_AGGREGATE) {
			votes = aggregation10.getVotes();
			if (!info.isActive()) {
				// Direct votes did not count yet.
				votes += info.getVotes();
			}
		} else {
			votes = info.getVotes();
			result.setArchived(!info.isActive());
		}
		
		result.setVotes(votes);
		result.setRating(rating(info));
		
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
	public void addSearchHit(String phone) {
		long now = System.currentTimeMillis();
		addSearchHit(phone, now);
	}

	void addSearchHit(String phone, long now) {
		try (SqlSession session = openSession()) {
			SpamReports reports = session.getMapper(SpamReports.class);
			
			addSearchHit(reports, phone, now);
			
			session.commit();
		}
	}

	/**
	 * Records a search hit for the given phone number.
	 */
	public void addSearchHit(SpamReports reports, String phone, long now) {
		int rows = reports.incSearchCount(phone, now);
		if (rows == 0) {
			reports.addReport(phone, 0, now);
			reports.incSearchCount(phone, now);
		}
	}
	
	/**
	 * Shuts down the database layer.
	 */
	public void shutdown() {
		for (ScheduledFuture<?> task : _tasks) {
			task.cancel(false);
		}
	}

	/** 
	 * Adds the given user with the given password.
	 */
	public void addUser(String clientName, String extId, String login, String displayName, String passwd) {
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
			
			Long before = users.getLastAccess(login);
			users.setLastAccess(login, timestamp, userAgent);
			session.commit();
			
			if (before == null || before.longValue() == 0) {
				// This was the first access, send welcome message;
				MailService mailService = _mailService;
				if (mailService != null) {
					DBUserSettings userSettings = users.getSettings(login);
					users.markWelcome(userSettings.getId());
					session.commit();
					
					_scheduler.executor().submit(() -> mailService.sendWelcomeMail(userSettings));
				} else {
					LOG.info("Cannot send welcome mail to '" + login + "', since there is no mail service.");
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
			
			return users.getSettings(login);
		}
	}
	
	/** 
	 * Updates the settings for a user.
	 */
	public void updateSettings(UserSettings settings) {
		try (SqlSession session = openSession()) {
			Users users = session.getMapper(Users.class);
			
			users.updateSettings(settings.getId(), settings.getMinVotes(), settings.getMaxLength(), settings.isWildcards());
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
			String decoded = new String(decodedBytes, StandardCharsets.UTF_8);
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
	public String login(String login, String passwd) throws IOException {
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

	private byte[] pwhash(String passwd) {
		return _sha256.digest(passwd.getBytes(StandardCharsets.UTF_8));
	}

	/** 
	 * Explicitly allows a certain number by storing an exclude to the global blocklist.
	 *
	 * @param principal The current user.
	 * @param phoneText The phone number to explicitly allow.
	 */
	public void deleteEntry(String principal, String phoneText) {
		String phoneId = NumberAnalyzer.toId(phoneText);
		if (phoneId == null) {
			return;
		}
		
		try (SqlSession session = openSession()) {
			SpamReports spamreport = session.getMapper(SpamReports.class);
			BlockList blockList = session.getMapper(BlockList.class);
			Users users = session.getMapper(Users.class);
			
			long currentUser = users.getUserId(principal);
			
			boolean wasAddedBefore = blockList.removePersonalization(currentUser, phoneId);

			// For safety reasons, to prevent primary key constraint violation.
			blockList.removeExclude(currentUser, phoneId);
			
			blockList.addExclude(currentUser, phoneId);
			
			if (wasAddedBefore) {
				// Note: Only a spam reporter may revoke his vote. This prevents vandals from deleting the whole list.
				processVotesAndPublish(spamreport, phoneId, -2, System.currentTimeMillis());
			}
			
			session.commit();
		}
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
		} catch (PersistenceException ex) {
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
		calendar.add(Calendar.DAY_OF_MONTH, -2);
		
		long lastAccessBefore = calendar.getTimeInMillis();

		calendar.add(Calendar.DAY_OF_MONTH, -30);
		long accessAfter = calendar.getTimeInMillis();
		
		try (SqlSession session = openSession()) {
			Users users = session.getMapper(Users.class);
			
			// Users that did not update the address book for three days.
			List<DBUserSettings> inactiveUsers = users.getNewInactiveUsers(lastAccessBefore, accessAfter, lastAccessBefore);
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
		updateHistory(365);
	}

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
			
			reports.createRevision(now);
			int newRev = reports.getLastRevision();
			Long lastSnapshot = reports.getRevisionDate(newRev - 1);
			reports.createHistorySnapshot(newRev, lastSnapshot == null ? 0 : lastSnapshot.longValue());
			
			int oldestRev = reports.getOldestRevision();
			if (newRev - oldestRev >= maxHistory) {
				reports.cleanSearchCluster(oldestRev);
				reports.removeSearchCluster(oldestRev);
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

		int lastRev;
		int revExpected;
		List<DBNumberHistory> entries;
		
		Integer lastRevision = reports.getLastRevision();
		if (lastRevision != null) {
			lastRev = lastRevision.intValue();
			revExpected = lastRev  - (size - 1);
			entries = reports.getSearchHistory(revExpected, phone);
		} else {
			lastRev = size - 1;
			revExpected = 0;
			entries = Collections.emptyList();
		}
		
		int last = 0;
		for (DBNumberHistory entry : entries) {
			while (revExpected < entry.getRev()) {
				result.add(Integer.valueOf(0));
				revExpected++;
			}
			int current = entry.getSearches();
			result.add(Integer.valueOf(current - last));
			last = current;
			revExpected++;
		}
		
		while (revExpected <= lastRev) {
			result.add(Integer.valueOf(0));
			revExpected++;
		}
		
		
		NumberInfo info = getPhoneInfo(reports, phone);
		result.add(info.getSearches() - last);
		
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
			int cnt = users.updateReportInfo(userId, callReport.getTimestamp(), callReport.getLastid(), now);
			if (cnt == 0) {
				users.createReportInfo(userId, callReport.getTimestamp(), callReport.getLastid(), now);
			}
			
			for (String phoneText : callReport.getCallers()) {
				String phoneId = NumberAnalyzer.toId(phoneText);
				if (phoneId == null) {
					continue;
				}
				
				int ok = users.addCall(userId, phoneId, now);
				if (ok == 0) {
					users.insertCaller(userId, phoneId, now);
				}
				
				processVotesAndPublish(reports, phoneId, 2, now);
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
	
}
