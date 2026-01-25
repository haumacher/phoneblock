/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.accounting.db;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

/**
 * MyBatis mapper interface for accessing the CONTRIBUTIONS table.
 */
public interface Contributions {

	/**
	 * Checks if a contribution with the given TX identifier already exists.
	 *
	 * @param tx The transaction identifier (sender + date)
	 * @return true if a contribution with this TX exists
	 */
	@Select("SELECT COUNT(*) > 0 FROM CONTRIBUTIONS WHERE TX=#{tx}")
	boolean exists(String tx);

	/**
	 * Inserts a new contribution into the database.
	 *
	 * @param contribution The contribution record to insert
	 */
	@Insert("""
		INSERT INTO CONTRIBUTIONS (SENDER, TX, AMOUNT, MESSAGE, RECEIVED, ACK)
		VALUES (#{sender}, #{tx}, #{amount}, #{message}, #{received}, FALSE)
	""")
	@Options(useGeneratedKeys = true, keyProperty = "id")
	void insert(ContributionRecord contribution);
}
