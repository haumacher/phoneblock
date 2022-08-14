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
	
	@Delete("delete from USERS where EMAIL=#{email}")
	void deleteUser(String email);
	
	@Select("select PWHASH from USERS where EMAIL=#{email}")
	java.io.InputStream getHash(String email);

	/** 
	 * Retrieves the user ID for the user with the given user name (e-mail).
	 */
	@Select("select ID from USERS where EMAIL=#{email}")
	long getUserId(String email);

	/**
	 * Updates the user's last access timestamp.
	 */
	@Update("update USERS set LASTACCESS=#{lastAccess} where EMAIL=#{email}")
	void setLastAccess(String email, long lastAccess);
	
}
