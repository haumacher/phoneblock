/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import java.util.List;
import java.util.Set;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

/**
 * Query interface for the block list table.
 * 
 * <p>
 * The block list table stores modifications to the global {@link SpamReports} table done by individual PhoneBlock
 * users.
 * </p>
 */
public interface BlockList {
	
	/**
	 * All numbers that the user with the given user ID has explicitly blocked.
	 */
	@Select("select PHONE from BLOCKLIST where OWNER = #{owner} order by PHONE")
	List<String> getPersonalizations(long owner);

	/**
	 * All numbers that the user with the given user ID has explicitly allowed.
	 */
	@Select("select PHONE from EXCLUDES where OWNER = #{owner}")
	Set<String> getExcluded(long owner);

	/**
	 * Adds a blocklist entry for the user with the given user ID.
	 */
	@Insert("insert into BLOCKLIST (OWNER, PHONE) values (#{owner}, #{phone})")
	void addPersonalization(long owner, String phone);
	
	/**
	 * Removes a blocklist entry for the user with the given user ID.
	 */
	@Delete("delete from BLOCKLIST where OWNER=#{owner} and PHONE=#{phone}")
	boolean removePersonalization(long owner, String phone);
	
	/**
	 * Adds an exclusion from the blocklist for the user with the given user ID.
	 */
	@Insert("insert into EXCLUDES (OWNER, PHONE) values (#{owner}, #{phone})")
	void addExclude(long owner, String phone);
	
	/**
	 * Removes an exclusion from the blocklist for the user with the given user ID.
	 */
	@Delete("delete from EXCLUDES where OWNER=#{owner} and PHONE=#{phone}")
	boolean removeExclude(long owner, String phone);
	
}
