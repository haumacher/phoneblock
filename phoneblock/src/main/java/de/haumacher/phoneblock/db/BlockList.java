/*
 * Copyright (c) 2022 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import java.util.List;
import java.util.Set;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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
	@Select("""
			select PHONE from PERSONALIZATION
			where USERID = #{userId} and BLOCKED
			order by PHONE
			""")
	List<String> getPersonalizations(long userId);

	/**
	 * All numbers that the user with the given user ID has explicitly blocked, with creation timestamp.
	 */
	@Select("""
			select PHONE, CREATED from PERSONALIZATION
			where USERID = #{userId} and BLOCKED
			order by PHONE
			""")
	List<DBPersonalization> getPersonalizationsWithCreated(long userId);

	/**
	 * All numbers that the user with the given user ID has explicitly allowed.
	 */
	@Select("""
			select PHONE from PERSONALIZATION
			where USERID = #{userId} and NOT BLOCKED
			order by PHONE
			""")
	Set<String> getExcluded(long userId);

	/**
	 * List of all numbers that the user with the given user ID has explicitly allowed.
	 */
	@Select("""
			select PHONE from PERSONALIZATION
			where USERID = #{userId} and NOT BLOCKED
			order by PHONE
			""")
	List<String> getWhiteList(long userId);

	/**
	 * List of all numbers that the user with the given user ID has explicitly allowed, with creation timestamp.
	 */
	@Select("""
			select PHONE, CREATED from PERSONALIZATION
			where USERID = #{userId} and NOT BLOCKED
			order by PHONE
			""")
	List<DBPersonalization> getWhiteListWithCreated(long userId);

	/**
	 * Adds a blocklist entry for the user with the given user ID.
	 */
	@Insert("""
			insert into PERSONALIZATION (USERID, PHONE, SHA1, BLOCKED, CREATED)
			values (#{userId}, #{phone}, #{sha1}, true, #{created})
			""")
	void addPersonalization(long userId, String phone, byte[] sha1, long created);
	
	/**
	 * Removes a blocklist entry for the user with the given user ID.
	 */
	@Delete("""
			delete from PERSONALIZATION 
			where USERID=#{userId} and PHONE=#{phone}
			""")
	boolean removePersonalization(long userId, String phone);
	
	/**
	 * Retrieves the personalization state of a phone number. 
	 * 
	 * @return <code>true</code> if blocked, <code>false</code> if white-listed, or <code>null</code> if not personalized.
	 */
	@Select("""
			select BLOCKED 
			from PERSONALIZATION 
			where USERID=#{userId} and PHONE=#{phone}
			""")
	Boolean getPersonalizationState(long userId, String phone);
	
	/**
	 * Adds an exclusion from the blocklist for the user with the given user ID.
	 */
	@Insert("""
			insert into PERSONALIZATION (USERID, PHONE, SHA1, BLOCKED, CREATED)
			values (#{userId}, #{phone}, #{sha1}, false, #{created})
			""")
	void addExclude(long userId, String phone, byte[] sha1, long created);

	/**
	 * Resolves a personalization entry by its SHA1 hash.
	 *
	 * @return The phone ID and blocked state, or {@code null} if not personalized.
	 */
	@Select("""
			select PHONE, BLOCKED
			from PERSONALIZATION
			where USERID=#{userId} and SHA1=#{sha1}
			""")
	DBPersonalization resolvePersonalizationByHash(long userId, byte[] sha1);

	/**
	 * All phone numbers in personalizations that do not yet have a SHA1 hash.
	 */
	@Select("""
			select PHONE from PERSONALIZATION
			where SHA1 is null
			""")
	List<String> getPersonalizationsWithoutHash();

	/**
	 * Updates the SHA1 hash for all personalization rows with the given phone number.
	 */
	@Update("""
			update PERSONALIZATION set SHA1 = #{sha1}
			where PHONE = #{phone}
			""")
	int updatePersonalizationHash(String phone, byte[] sha1);

}
