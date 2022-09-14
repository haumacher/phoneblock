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

	@Insert("insert into USERS (EMAIL, PWHASH, REGISTERED) values (#{email}, #{pwhash}, #{registered})")
	void addUser(String email, byte[] pwhash, long registered);
	
	@Update("update USERS set PWHASH=#{pwhash} where ID=#{userId}")
	void setPassword(long userId, byte[] pwhash);
	
	@Delete("delete from USERS where EMAIL=#{email}")
	void deleteUser(String email);
	
	@Select("select PWHASH from USERS where EMAIL=#{email}")
	java.io.InputStream getHash(String email);

	/** 
	 * Retrieves the user ID for the user with the given user name (e-mail).
	 */
	@Select("select ID from USERS where EMAIL=#{email}")
	Long getUserId(String email);

	/**
	 * Updates the user's last access timestamp.
	 */
	@Update("update USERS set LASTACCESS=#{lastAccess} where EMAIL=#{email}")
	void setLastAccess(String email, long lastAccess);
	
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
