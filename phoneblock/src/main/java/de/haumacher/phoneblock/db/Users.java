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

	@Select("select ID, DISPLAYNAME, EMAIL, MIN_VOTES, MAX_LENGTH, WILDCARDS from USERS where LOGIN=#{login}")
	DBUserSettings getSettings(String login);
	
	@Select("select u.ID, u.DISPLAYNAME, u.EMAIL, u.MIN_VOTES, u.MAX_LENGTH, u.WILDCARDS from USERS u "
			+ "where u.LASTACCESS < #{lastAccessBefore} "
			+ "and (u.LASTACCESS > #{accessAfter} "
			+ "or (u.REGISTERED > #{accessAfter} "
			+ "and u.REGISTERED < #{registeredBefore})) "
			+ "and NOTIFIED = false "
			+ "and (u.USERAGENT = 'FRITZOS_CardDAV_Client/1.0' or u.USERAGENT is null) "
			+ "order by u.REGISTERED asc")
	List<DBUserSettings> getNewInactiveUsers(long lastAccessBefore, long accessAfter, long registeredBefore);
	
	@Select("select u.ID, u.DISPLAYNAME, u.EMAIL, u.MIN_VOTES, u.MAX_LENGTH, u.WILDCARDS from USERS u "
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

}
