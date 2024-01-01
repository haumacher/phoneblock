/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * Operations for user management.
 */
public interface Users {

	@Insert("insert into USERS (LOGIN, CLIENTNAME, EXTID, DISPLAYNAME, PWHASH, REGISTERED, MIN_VOTES, MAX_LENGTH) " + 
			"values (#{login}, #{clientName}, #{extId}, #{displayName}, #{pwhash}, #{registered}, 4, 2000)")
	void addUser(String login, String clientName, String extId, String displayName, byte[] pwhash, long registered);
	
	@Update("update USERS set PWHASH=#{pwhash} where ID=#{userId}")
	void setPassword(long userId, byte[] pwhash);
	
	@Update("update USERS set EMAIL=#{email} where LOGIN=#{login}")
	void setEmail(String login, String email);
	
	@Update("update USERS set EXTID=#{extId} where LOGIN=#{login}")
	void setExtId(String login, String extId);
	
	@Delete("delete from USERS where LOGIN=#{login}")
	void deleteUser(String login);
	
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
	
	/** 
	 * Retrieves the user ID for the user with the given user name (e-mail).
	 */
	@Select("select LOGIN from USERS where CLIENTNAME=#{clientName} and EXTID=#{extId}")
	String getLogin(String clientName, String extId);

	@Select("select ID, LOGIN, DISPLAYNAME, EMAIL, MIN_VOTES, MAX_LENGTH, WILDCARDS, LASTACCESS from USERS where LOGIN=#{login}")
	DBUserSettings getSettings(String login);
	
	@Select("select u.ID, u.LOGIN, u.DISPLAYNAME, u.EMAIL, u.MIN_VOTES, u.MAX_LENGTH, u.WILDCARDS, u.LASTACCESS from USERS u "
			+ "where u.LASTACCESS < #{lastAccessBefore} "
			+ "and (u.LASTACCESS > #{accessAfter} "
			+ "or (u.REGISTERED > #{accessAfter} "
			+ "and u.REGISTERED < #{registeredBefore})) "
			+ "and NOTIFIED = false "
			+ "and (u.USERAGENT = 'FRITZOS_CardDAV_Client/1.0' or u.USERAGENT is null) "
			+ "order by u.REGISTERED asc")
	List<DBUserSettings> getNewInactiveUsers(long lastAccessBefore, long accessAfter, long registeredBefore);
	
	@Select("select u.ID, u.LOGIN u.DISPLAYNAME, u.EMAIL, u.MIN_VOTES, u.MAX_LENGTH, u.WILDCARDS, u.LASTACCESS from USERS u "
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

	@Select("select USERID, CREATED, UPDATED, DYNDNS_USER, DYNDNS_PASSWD, IP4, IP6 from ANSWERBOT_DYNDNS where DYNDNS_USER=#{user}")
	DBAnswerBotDynDns getDynDns(String user);
	
	@Select("select USERID, CREATED, UPDATED, DYNDNS_USER, DYNDNS_PASSWD, IP4, IP6 from ANSWERBOT_DYNDNS where USERID=#{userId}")
	DBAnswerBotDynDns getDynDnsByUserId(long userId);
	
	/**
	 * Updates the user's dynamic IP address.
	 */
	@Insert("update ANSWERBOT_DYNDNS set IP4=#{ip4}, IP6=#{ip6}, UPDATED=#{updated} where USERID=#{userId}")
	void updateDynDny(long userId, String ip4, String ip6, long updated);
	
	@Select("select s.USERID, s.HOST, d.IP4, d.IP6, s.REGISTRAR, s.REALM, s.USERNAME, s.PASSWD from ANSWERBOT_SIP s " + 
			"left outer join ANSWERBOT_DYNDNS d on d.USERID=s.USERID " + 
			"where s.ENABLED = true and (s.UPDATED > #{since} or d.UPDATED > #{since})")
	List<DBAnswerBotSip> getEnabledAnswerBots(long since);

	@Update("update ANSWERBOT_SIP set ENABLED=#{enabled}, UPDATED=#{updated}, REGISTERED=false, REGISTER_MSG=NULL where USERID=#{userId}")
	void enableAnswerBot(long userId, boolean enabled, long updated);

	@Insert("insert into ANSWERBOT_SIP (USERID, USERNAME, PASSWD, CREATED, UPDATED) values (#{userId}, #{userName}, #{password}, #{now}, #{now})")
	int createAnswerBot(long userId, String userName, String password, long now);

	@Select("select s.USERID, s.HOST, d.IP4, d.IP6, s.REGISTRAR, s.REALM, s.USERNAME, s.PASSWD from ANSWERBOT_SIP s " + 
			"left outer join ANSWERBOT_DYNDNS d on d.USERID=s.USERID " + 
			"where s.USERNAME = #{userName}")
	DBAnswerBotSip getAnswerBot(String userName);

	@Select("select s.USERID, s.HOST, d.IP4, d.IP6, s.REGISTRAR, s.REALM, s.USERNAME, s.PASSWD from ANSWERBOT_SIP s " + 
			"left outer join ANSWERBOT_DYNDNS d on d.USERID=s.USERID " + 
			"where s.USERID= #{userId}")
	List<DBAnswerBotSip> getAnswerBots(long userId);
	
	
	@Update("update ANSWERBOT_SIP set REGISTERED=#{registered}, REGISTER_MSG=#{message} where USERID=#{userId} and (REGISTER_MSG is null or not REGISTER_MSG=#{message} or not REGISTERED=#{registered})")
	int updateSipRegistration(long userId, boolean registered, String message);

}
