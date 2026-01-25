/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.accounting.db;

import org.apache.ibatis.annotations.Select;

/**
 * MyBatis mapper interface for accessing the USERS table.
 */
public interface Users {

	/**
	 * Finds a user whose username (LOGIN or DISPLAYNAME) contains the given substring.
	 *
	 * @param username The substring to search for in user names
	 * @return The user ID if found, null otherwise
	 */
	@Select("""
		SELECT ID FROM USERS
		WHERE LOGIN LIKE CONCAT('%', #{username}, '%')
		   OR DISPLAYNAME LIKE CONCAT('%', #{username}, '%')
		LIMIT 1
	""")
	Long findUserIdByUsername(String username);
}
