/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

/**
 * Operations for user management.
 */
public interface Users {

	@Insert("insert into USERS (EMAIL, PWHASH) values (#{email}, #{pwhash})")
	void addUser(String email, byte[] pwhash);
	
	@Delete("delete from USERS where EMAIL=#{email}")
	void deleteUser(String email);
	
	@Select("select PWHASH from USERS where EMAIL=#{email}")
	java.io.InputStream getHash(String email);
	
}
