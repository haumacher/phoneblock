/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * Operations for user management.
 */
public interface Users {

	@Insert("insert into USERS (LOGIN, CLIENTNAME, EXTID, DISPLAYNAME, PWHASH, REGISTERED) values (#{login}, #{clientName}, #{extId}, #{displayName}, #{pwhash}, #{registered})")
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

	@Select("select ID, DISPLAYNAME, EMAIL, MIN_VOTES, MAX_LENGTH from USERS where LOGIN=#{login}")
	DBUserSettings getSettings(String login);
	
	@Update("update USERS set MIN_VOTES=#{minVotes}, MAX_LENGTH=#{maxLength} where ID=#{id}")
	int updateSettings(long id, int minVotes, int maxLength);
	
	/**
	 * Updates the user's last access timestamp.
	 */
	@Update("update USERS set LASTACCESS=#{lastAccess}, USERAGENT=#{userAgent} where LOGIN=#{login}")
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
