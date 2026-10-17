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
	 *
	 * <p>Numbers covered by one of the user's blocking wildcards are omitted: the wildcard is the
	 * compact representation of the block, so the concrete row stays in the table only to carry the
	 * user's vote/evidence contribution and must not be exported (CardDAV) or displayed separately.</p>
	 */
	@Select("""
			select PHONE from PERSONALIZATION p
			where USERID = #{userId} and BLOCKED and not WILDCARD
			and not exists (
				select 1 from PERSONALIZATION w
				where w.USERID = p.USERID and w.BLOCKED and w.WILDCARD
				and p.PHONE like w.PHONE || '%'
			)
			order by PHONE
			""")
	List<String> getPersonalizations(long userId);

	/**
	 * All numbers that the user with the given user ID has explicitly blocked, with creation
	 * timestamp. Numbers covered by one of the user's blocking wildcards are omitted (see
	 * {@link #getPersonalizations(long)}).
	 */
	@Select("""
			select PHONE, CREATED from PERSONALIZATION p
			where USERID = #{userId} and BLOCKED and not WILDCARD
			and not exists (
				select 1 from PERSONALIZATION w
				where w.USERID = p.USERID and w.BLOCKED and w.WILDCARD
				and p.PHONE like w.PHONE || '%'
			)
			order by PHONE
			""")
	List<DBPersonalization> getPersonalizationsWithCreated(long userId);

	/**
	 * All numbers that the user with the given user ID has explicitly allowed.
	 */
	@Select("""
			select PHONE from PERSONALIZATION
			where USERID = #{userId} and NOT BLOCKED and not WILDCARD
			order by PHONE
			""")
	Set<String> getExcluded(long userId);

	/**
	 * List of all numbers that the user with the given user ID has explicitly allowed.
	 */
	@Select("""
			select PHONE from PERSONALIZATION
			where USERID = #{userId} and NOT BLOCKED and not WILDCARD
			order by PHONE
			""")
	List<String> getWhiteList(long userId);

	/**
	 * List of all numbers that the user with the given user ID has explicitly allowed, with creation timestamp.
	 */
	@Select("""
			select PHONE, CREATED from PERSONALIZATION
			where USERID = #{userId} and NOT BLOCKED and not WILDCARD
			order by PHONE
			""")
	List<DBPersonalization> getWhiteListWithCreated(long userId);

	/**
	 * Adds a blocklist entry for the user with the given user ID. {@code LAST_ACTIVITY} is seeded to
	 * the creation time — the moment of the first spam signal — so the initial capped contribution
	 * {@code Ema.increment(1, created)} can be reconstructed (and later topped up) from the row.
	 *
	 * <p>The insert is conditional: it adds nothing if the user already has an entry for the number
	 * (#520). A concurrent submission for the same {@code (USERID, PHONE)} may have committed its row
	 * between the caller's check and this insert; a plain insert would then violate the primary key
	 * (and catching that would poison a PostgreSQL transaction).</p>
	 *
	 * <p>The existence check is correlated with the inserted row through the {@code VALUES} table on
	 * purpose: H2 caches the result of an uncorrelated {@code NOT EXISTS} within a transaction and
	 * would then miss a row inserted earlier in the same transaction.</p>
	 *
	 * @return The number of inserted rows: {@code 1} if the entry was added, {@code 0} if an entry
	 *         for the number already exists.
	 */
	@Insert("""
			insert into PERSONALIZATION (USERID, PHONE, SHA1, BLOCKED, CREATED, LAST_ACTIVITY)
			select v.USERID, v.PHONE, #{sha1}, true, #{created}, #{created}
			from (values (#{userId}, #{phone})) v (USERID, PHONE)
			where not exists (
				select 1 from PERSONALIZATION e where e.USERID = v.USERID and e.PHONE = v.PHONE
			)
			""")
	int addPersonalization(long userId, String phone, byte[] sha1, long created);

	/**
	 * Records new spam/legit activity (a topped-up call or re-rating) for an existing
	 * personalization entry by advancing its {@code LAST_ACTIVITY}.
	 */
	@Update("""
			update PERSONALIZATION set LAST_ACTIVITY = #{lastActivity}
			where USERID = #{userId} and PHONE = #{phone}
			""")
	void updateLastActivity(long userId, String phone, long lastActivity);

	/**
	 * Resolves the block state and last-activity time of a user's personalization entry, or
	 * {@code null} if the number is not personalized. Used to compute the per-user evidence cap.
	 */
	@Select("""
			select PHONE, BLOCKED, LAST_ACTIVITY as lastActivity
			from PERSONALIZATION
			where USERID = #{userId} and PHONE = #{phone}
			""")
	DBPersonalization getPersonalizationActivity(long userId, String phone);
	
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
	 * Adds an exclusion from the blocklist for the user with the given user ID. {@code LAST_ACTIVITY}
	 * is seeded to the creation time (see {@link #addPersonalization}).
	 *
	 * <p>Conditional like {@link #addPersonalization(long, String, byte[], long)} (#520).</p>
	 *
	 * @return The number of inserted rows: {@code 1} if the entry was added, {@code 0} if an entry
	 *         for the number already exists.
	 */
	@Insert("""
			insert into PERSONALIZATION (USERID, PHONE, SHA1, BLOCKED, CREATED, LAST_ACTIVITY)
			select v.USERID, v.PHONE, #{sha1}, false, #{created}, #{created}
			from (values (#{userId}, #{phone})) v (USERID, PHONE)
			where not exists (
				select 1 from PERSONALIZATION e where e.USERID = v.USERID and e.PHONE = v.PHONE
			)
			""")
	int addExclude(long userId, String phone, byte[] sha1, long created);

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

	@Select("""
			select PHONE, BLOCKED
			from PERSONALIZATION
			where USERID=#{userId} and SHA1 >= #{low} and SHA1 < #{high}
			""")
	List<DBPersonalization> getPersonalizationsByHashPrefix(long userId, byte[] low, byte[] high);

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

	/**
	 * Wildcard prefixes (phone-ID form, stored bare without a trailing {@code *}) of the given
	 * block state for the user with the given user ID (#377).
	 */
	@Select("""
			select PHONE from PERSONALIZATION
			where USERID = #{userId} and WILDCARD and BLOCKED = #{blocked}
			order by PHONE
			""")
	List<String> getWildcards(long userId, boolean blocked);

	/**
	 * Wildcard prefixes of the given block state for the user with the given user ID, with
	 * creation timestamp (#377).
	 */
	@Select("""
			select PHONE, BLOCKED, CREATED from PERSONALIZATION
			where USERID = #{userId} and WILDCARD and BLOCKED = #{blocked}
			order by PHONE
			""")
	List<DBPersonalization> getWildcardsWithCreated(long userId, boolean blocked);

	/**
	 * Adds a wildcard-prefix personalization (#377). The prefix is stored bare (no {@code *}),
	 * has no SHA1 (it is matched by prefix, not by exact hash) and is honored only by the
	 * user's own devices.
	 *
	 * <p>Conditional like {@link #addPersonalization(long, String, byte[], long)} (#520): callers
	 * remove any entry under the key first, but a concurrent request may re-insert it in between.</p>
	 *
	 * @return The number of inserted rows: {@code 1} if the wildcard was added, {@code 0} if an entry
	 *         under this key already exists.
	 */
	@Insert("""
			insert into PERSONALIZATION (USERID, PHONE, SHA1, BLOCKED, WILDCARD, CREATED)
			select v.USERID, v.PHONE, null, #{blocked}, true, #{created}
			from (values (#{userId}, #{phone})) v (USERID, PHONE)
			where not exists (
				select 1 from PERSONALIZATION e where e.USERID = v.USERID and e.PHONE = v.PHONE
			)
			""")
	int addWildcard(long userId, String phone, boolean blocked, long created);

	/**
	 * Removes a wildcard-prefix personalization (#377).
	 */
	@Delete("""
			delete from PERSONALIZATION
			where USERID = #{userId} and PHONE = #{phone} and WILDCARD
			""")
	boolean removeWildcard(long userId, String phone);

}
