/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import de.haumacher.phoneblock.ab.DBAnswerbotInfo;
import de.haumacher.phoneblock.db.settings.AuthToken;
import de.haumacher.phoneblock.db.settings.Contribution;

/**
 * Operations for user management.
 */
public interface Users {
	
	@Select("select VAL from PROPERTIES p where p.NAME=#{key}")
	String getProperty(String key);

	@Insert("insert into PROPERTIES (NAME, VAL) values (#{key}, #{value})")
	void addProperty(String key, String value);
	
	@Update("update PROPERTIES set VAL=#{value} where NAME=#{key}")
	int updateProperty(String key, String value);
	
	@Insert("insert into USERS (LOGIN, DISPLAYNAME, PWHASH, REGISTERED, MIN_VOTES, MAX_LENGTH) " + 
			"values (#{login}, #{displayName}, #{pwhash}, #{registered}, 4, 2000)")
	void addUser(String login, String displayName, byte[] pwhash, long registered);
	
	@Update("update USERS set PWHASH=#{pwhash} where ID=#{userId}")
	void setPassword(long userId, byte[] pwhash);
	
	@Update("update USERS set EMAIL=#{email} where LOGIN=#{login}")
	void setEmail(String login, String email);
	
	@Update("update USERS set GOOGLEID=#{googleId} where LOGIN=#{login}")
	void setGoogleId(String login, String googleId);
	
	@Update("update USERS set DISPLAYNAME=#{displayName} where LOGIN=#{login}")
	void setDisplayName(String login,  String displayName);
	
	@Delete("delete from USERS where LOGIN=#{login}")
	void deleteUser(String login);
	
	@Insert("""
			insert into CONTRIBUTIONS (USER_ID, SENDER, TX, AMOUNT, MESSAGE, RECEIVED, ACK)
			values (#{userId}, #{sender}, #{tx}, #{amount}, #{message}, #{received}, #{acknowledged})
			""")
	@Options(useGeneratedKeys = true, keyColumn = "ID", keyProperty = "id")
	void insertContribution(Contribution contribution);
	
	@Select("""
			select ID, USER_ID, SENDER, TX, AMOUNT, MESSAGE, RECEIVED, ACK
			from CONTRIBUTIONS
			where TX=#{tx}
			""")
	DBContribution getContribution(String tx);
	
	@Update("""
			update USERS
			set CREDIT=CREDIT+#{amount}
			where ID=#{userId}
			""")
	void addContribution(long userId, int amount);

	@Insert("""
			insert into TOKENS (
				USERID, LABEL, CREATED, PWHASH, 
				IMPLICIT, ACCESS_QUERY, ACCESS_DOWNLOAD, ACCESS_CARDDAV, ACCESS_RATE, ACCESS_LOGIN, 
				LASTACCESS, USERAGENT
			)
			values (
				#{userId}, #{label}, #{created}, #{pwHash}, 
				#{implicit}, #{accessQuery}, #{accessDownload}, #{accessCarddav},#{accessRate},#{accessLogin},
				#{lastAccess}, #{userAgent}
			)
			""")
	@Options(useGeneratedKeys = true, keyColumn = "ID", keyProperty = "id")
	void createAuthToken(AuthToken token);
	
	@Update("""
			update TOKENS
			set
				LASTACCESS=#{lastAccess},
				USERAGENT=#{userAgent}
			where
				ID=#{id}
			""")
	void updateAuthToken(long id, long lastAccess, String userAgent);
	
	@Update("""
			update TOKENS
			set
				PWHASH=#{pwHash}
			where
				ID=#{id}
			""")
	void updateAuthTokenSecret(long id, byte[] pwHash);

	@Delete("""
			delete from TOKENS
			where ID=#{id}
			""")
	void invalidateAuthToken(long id);
	
	/**
	 * Delete an auth token for a given user. Note: The user ID is required to prevent a user from deleting tokens of other users. This call is done from the settings servlet.
	 */
	@Delete("""
			delete from TOKENS
			where USERID=#{userId} and ID=#{id}
			""")
	void deleteAuthToken(long userId, long id);
	
	@Delete("""
			delete from TOKENS
			where USERID=#{userId}
			""")
	void invalidateAllTokens(long userId);
	
	@Select("""
			select ID 
			from TOKENS
			where
				USERID=#{userId} and
				IMPLICIT
			order by LASTACCESS desc
			offset 4 rows
			""")
	List<Long> getOutdatedLoginTokens(long userId);
	
	@Delete("""
			<script>
			delete from TOKENS
			where ID in
			    <foreach item="item" index="index" collection="ids" open="(" separator="," close=")">
			        #{item}
			    </foreach>
			</script>
			""")
	int deleteTokens(List<Long> ids);
	
	@Select("""
			select
				ID, 
				USERID, LABEL, CREATED, PWHASH, 
				IMPLICIT, ACCESS_QUERY, ACCESS_DOWNLOAD, ACCESS_CARDDAV, ACCESS_RATE, ACCESS_LOGIN, 
				LASTACCESS, USERAGENT
			from TOKENS
			where ID=#{id}
			""")
	DBAuthToken getAuthToken(long id);
	
	@Select("""
			select
				ID, 
				USERID, LABEL, CREATED, PWHASH, 
				IMPLICIT, ACCESS_QUERY, ACCESS_DOWNLOAD, ACCESS_CARDDAV, ACCESS_RATE, ACCESS_LOGIN, 
				LASTACCESS, USERAGENT
			from TOKENS
			where USERID=#{userId} and not IMPLICIT
			""")
	List<DBAuthToken> getExplicitTokens(long userId);

	@Select("select count(1) from USERS")
	int getUserCount();
	
	@Select("select count(1) from USERS where LASTACCESS < #{lastAccessLimit}")
	int getInactiveUserCount(long lastAccessLimit);
	
	@Select("select PWHASH from USERS where LOGIN=#{login}")
	java.io.InputStream getHash(String login);

	/** 
	 * Retrieves the user ID for the user with the given user name (e-mail).
	 */
	@Select("select ID from USERS where LOGIN=#{login}")
	Long getUserId(String login);
	
	@Select("select ID from USERS where LOGIN like #{loginPattern}")
	List<Long> findUser(String loginPattern);
	
	@Select("select ID from USERS where DISPLAYNAME=#{sender}")
	List<Long> usersWithDisplayName(String sender);
	
	/** 
	 * Retrieves the user ID for the user with the given Google ID.
	 */
	@Select("select LOGIN from USERS where GOOGLEID=#{googleId}")
	String getGoogleLogin(String googleId);
	
	/** 
	 * Retrieves the user ID for the user with the given e-mail.
	 */
	@Select("select LOGIN from USERS where EMAIL=#{email}")
	String getEmailLogin(String email);

	@Select("select ID, LOGIN, DISPLAYNAME, EMAIL, MIN_VOTES, MAX_LENGTH, WILDCARDS, LASTACCESS, CREDIT from USERS where LOGIN=#{login}")
	DBUserSettings getSettingsRaw(String login);
	
	@Select("select LOGIN from USERS where ID=#{userId}")
	String getUserName(long userId);
	
	@Select("select ID, LOGIN, DISPLAYNAME, EMAIL, MIN_VOTES, MAX_LENGTH, WILDCARDS, LASTACCESS, CREDIT from USERS where ID=#{userId}")
	DBUserSettings getSettingsById(long userId);
	
	@Select("select u.ID, u.LOGIN, u.DISPLAYNAME, u.EMAIL, u.MIN_VOTES, u.MAX_LENGTH, u.WILDCARDS, u.LASTACCESS, u.CREDIT from USERS u "
			+ "where u.LASTACCESS < #{lastAccessBefore} "
			+ "and (u.LASTACCESS > #{accessAfter} "
			+ "or (u.REGISTERED > #{accessAfter} "
			+ "and u.REGISTERED < #{registeredBefore})) "
			+ "and NOTIFIED = false "
			+ "and (u.USERAGENT = 'FRITZOS_CardDAV_Client/1.0' or u.USERAGENT is null) "
			+ "order by u.REGISTERED asc")
	List<DBUserSettings> getNewInactiveUsers(long lastAccessBefore, long accessAfter, long registeredBefore);
	
	@Select("select u.ID, u.LOGIN u.DISPLAYNAME, u.EMAIL, u.MIN_VOTES, u.MAX_LENGTH, u.WILDCARDS, u.LASTACCESS, u.CREDIT from USERS u "
			+ "where not u.WELCOME "
			+ "and u.LASTACCESS > #{accessAfter} "
			+ "and u.REGISTERED > #{registeredAfter} "
			+ "order by u.REGISTERED asc")
	List<DBUserSettings> getUsersWithoutWelcome(long registeredAfter, long accessAfter);
	
	@Update("update USERS set NOTIFIED=true where ID=#{id}")
	int markNotified(long id);
	
	@Update("update USERS set WELCOME=true where ID=#{id}")
	int markWelcome(long id);
	
	@Update("update USERS set MIN_VOTES=#{minVotes}, MAX_LENGTH=#{maxLength}, WILDCARDS=#{wildcards} where ID=#{id}")
	int updateSettings(long id, int minVotes, int maxLength, boolean wildcards);
	
	/**
	 * Reads the user's last access timestamp (pretend it is a new user that has never accessed the
	 * blocklist, if he was notified before that access does not work. This ensures the user get a
	 * feedback, when the error condition is resolved.
	 */
	@Select("select case when NOTIFIED then 0 else LASTACCESS end from USERS where LOGIN=#{login}")
	Long getLastAccess(String login);
	
	/**
	 * Updates the user's last access timestamp.
	 */
	@Update("update USERS set LASTACCESS=#{lastAccess}, USERAGENT=#{userAgent}, NOTIFIED=false where LOGIN=#{login}")
	void setLastAccess(String login, long lastAccess, String userAgent);
	
	@Select("select TIMESTAMP, LASTID from CALLREPORT where USERID=#{userId}")
	DBReportInfo getReportInfo(long userId);
	
	@Update("update CALLREPORT set TIMESTAMP=#{timestamp}, LASTID=#{lastId}, LASTACCESS=#{now} where USERID=#{userId}")
	int updateReportInfo(long userId, String timestamp, String lastId, long now);
	
	@Insert("insert into CALLREPORT (USERID, TIMESTAMP, LASTID, LASTACCESS) values (#{userId}, #{timestamp}, #{lastId}, #{now})")
	void createReportInfo(long userId, String timestamp, String lastId, long now);
	
	@Insert("insert into CALLERS (USERID, PHONE, CALLS, LASTUPDATE) values (#{userId}, #{phone}, 1, #{now})")
	void insertCaller(long userId, String phone, long now);
	
	@Update("update CALLERS set CALLS=CALLS + 1, LASTUPDATE=#{now} where USERID=#{userId} and PHONE=#{phone}")
	int addCall(long userId, String phone, long now);

	@Select("select ABID, USERID, CREATED, UPDATED, DYNDNS_USER, DYNDNS_PASSWD, IP4, IP6 from ANSWERBOT_DYNDNS where DYNDNS_USER=#{dynDnsUser}")
	DBAnswerBotDynDns getDynDns(String dynDnsUser);

	@Select("select ABID, USERID, CREATED, UPDATED, DYNDNS_USER, DYNDNS_PASSWD, IP4, IP6 from ANSWERBOT_DYNDNS where ABID=#{abId}")
	DBAnswerBotDynDns getDynDnsForAB(long abId);
	
	@Select("select ABID, USERID, CREATED, UPDATED, DYNDNS_USER, DYNDNS_PASSWD, IP4, IP6 from ANSWERBOT_DYNDNS")
	List<DBAnswerBotDynDns> getDynDnsUsers();
	
	@Insert("insert into ANSWERBOT_DYNDNS (ABID, USERID, CREATED, DYNDNS_USER, DYNDNS_PASSWD) values (#{abId}, #{userId}, #{now}, #{dynDnsUser}, #{dynDnsPassword})")
	int setupDynDns(long abId, long userId, long now, String dynDnsUser, String dynDnsPassword);
	
	/**
	 * Updates the user's dynamic IP address.
	 */
	@Update("update ANSWERBOT_DYNDNS set IP4=#{ip4}, IP6=#{ip6}, UPDATED=#{updated} where ABID=#{abId}")
	void updateDynDns(long abId, String ip4, String ip6, long updated);
	
	@Select("""
			select 
				s.ID, s.USERID, 
				s.UPDATED, s.LAST_SUCCESS, 
				s.REGISTERED, s.REGISTER_MSG, s.HOST, s.PREFER_V4, d.IP4, d.IP6, 
				s.REGISTRAR, s.REALM, s.USERNAME, s.PASSWD,
				s.MIN_VOTES, s.WILDCARDS 
			from ANSWERBOT_SIP s  
			left outer join ANSWERBOT_DYNDNS d on d.ABID=s.ID  
			where s.ENABLED = true
			""")
	List<DBAnswerBotSip> getEnabledAnswerBots();

	/**
	 * Marks the answerbot with the given ID as either enabled or disabled.
	 * 
	 * @param id The ID of the answerbot to update. 
	 * @param enabled Whether to enable the answerbot.
	 * @param updated The time of the change.
	 */
	@Update("update ANSWERBOT_SIP set ENABLED=#{enabled}, UPDATED=#{updated}, LAST_SUCCESS=0, REGISTERED=false, REGISTER_MSG=NULL where ID=#{id}")
	void switchAnswerBotState(long id, boolean enabled, long updated);
	
	@Select("select s.ID from ANSWERBOT_SIP s where s.USERNAME = #{sipUser}")
	long getAnswerBotId(String sipUser);
	
	@Select("select s.USERID from ANSWERBOT_SIP s where s.USERNAME = #{sipUser}")
	long getAnswerBotUserId(String sipUser);

	@Select("""
			select 
				s.ID, s.USERID, 
				s.UPDATED, s.LAST_SUCCESS, 
				s.REGISTERED, s.REGISTER_MSG, s.HOST, s.PREFER_V4, d.IP4, d.IP6, 
				s.REGISTRAR, s.REALM, s.USERNAME, s.PASSWD,
				s.MIN_VOTES, s.WILDCARDS 
			from ANSWERBOT_SIP s  
			left outer join ANSWERBOT_DYNDNS d on d.ABID=s.ID  
			where s.USERNAME = #{userName}
			""")
	DBAnswerBotSip getAnswerBotBySipUser(String userName);

	@Select("""
			select 
				s.ID, s.USERID, 
				s.ENABLED, s.PREFER_V4, s.MIN_VOTES, s.WILDCARDS, 
				s.REGISTRAR, s.HOST, d.IP4, d.IP6, s.REALM, 
				s.REGISTERED, s.REGISTER_MSG, 
				s.NEW_CALLS, s.CALLS_ACCEPTED, s.TALK_TIME, 
				s.USERNAME, s.PASSWD, 
				d.DYNDNS_USER, d.DYNDNS_PASSWD 
			from ANSWERBOT_SIP s  
			left outer join ANSWERBOT_DYNDNS d 
			on d.ABID=s.ID  
			where s.USERID= #{userId}
			""")
	List<DBAnswerbotInfo> getAnswerBots(long userId);
	
	@Select("""
			select 
				s.ID, s.USERID, 
				s.ENABLED, s.PREFER_V4, s.MIN_VOTES, s.WILDCARDS, 
				s.REGISTRAR, s.HOST, d.IP4, d.IP6, s.REALM, 
				s.REGISTERED, s.REGISTER_MSG, 
				s.NEW_CALLS, s.CALLS_ACCEPTED, s.TALK_TIME, 
				s.USERNAME, s.PASSWD, 
				d.DYNDNS_USER, d.DYNDNS_PASSWD 
			from ANSWERBOT_SIP s  
			left outer join ANSWERBOT_DYNDNS d 
			on d.ABID=s.ID  
			where s.ID= #{id}
			""")
	DBAnswerbotInfo getAnswerBot(long id);
	
	@Update("""
			update ANSWERBOT_SIP s
			set
				s.PREFER_V4=#{preferIPv4},
				s.MIN_VOTES=#{minVotes},
				s.WILDCARDS=#{wildcards}
			where
				s.ID=#{id}
			""")
	void updateAnswerbot(long id, boolean preferIPv4, int minVotes, boolean wildcards);
	
	@Update("update ANSWERBOT_SIP set LAST_SUCCESS=#{lastSuccess}, REGISTERED=#{registered}, REGISTER_MSG=#{message} where ID=#{id}")
	int updateSipRegistration(long id, boolean registered, String message, long lastSuccess);

	@Update("update ANSWERBOT_SIP set PREFER_V4=#{preferV4} where ID=#{id}")
	int updatePreferV4(long id, boolean preferV4);
	
	@Delete("delete from ANSWERBOT_DYNDNS where ABID=#{abId}")
	int answerbotDeleteDynDns(long abId);

	@Delete("delete from ANSWERBOT_SIP where ID=#{id}")
	int answerbotDelete(long id);
	
	@Update("update ANSWERBOT_SIP set HOST=#{host} where ID=#{id}")
	void answerbotEnterHostName(long id, String host);

	@Insert("insert into ANSWERBOT_CALLS (ABID, CALLER, STARTED, DURATION) values (#{abId}, #{caller}, #{started}, #{duration})")
	void recordCall(long abId, String caller, long started, long duration);
	
	@Select("select CALLER, STARTED, DURATION from ANSWERBOT_CALLS where ABID=#{abId}")
	List<DBCallInfo> listCalls(long abId);

	@Delete("delete from ANSWERBOT_CALLS where ABID=#{abId}")
	void clearCallList(long abId);

	@Delete("update ANSWERBOT_SIP set NEW_CALLS=0 where ID=#{abId}")
	void clearCallCounter(long abId);
	
	@Update("update ANSWERBOT_SIP set NEW_CALLS=NEW_CALLS + 1, CALLS_ACCEPTED=CALLS_ACCEPTED + 1, TALK_TIME=TALK_TIME + #{duration} where ID=#{id}")
	void recordCallSummary(long id, long duration);

}
