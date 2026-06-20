/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * MyBatis mapper for the {@code API_QUOTA} table: per-subject fixed-window
 * rate-limit counters for expensive API calls.
 *
 * @see DB#tryConsumeQuota(de.haumacher.phoneblock.db.settings.AuthToken, int, long, long, long)
 */
public interface Quota {

	/**
	 * Atomically books one request against the fixed window of the given
	 * {@code (kind, subjectId, bucket)} counter.
	 *
	 * <p>The single {@code UPDATE} implements a lazily-reset fixed window: when
	 * the current window has elapsed ({@code now - QUOTA_TIME > interval}) the
	 * counter restarts at {@code 1} anchored at {@code now}; otherwise, while
	 * there is room ({@code QUOTA_COUNT < limit}), the counter is incremented
	 * without moving the window anchor. The guarded {@code WHERE} clause makes
	 * the statement affect a row only in those two cases, so the affected-row
	 * count doubles as the verdict.</p>
	 *
	 * @return {@code 1} if the request is within quota and was booked, {@code 0}
	 *         if the row is missing (first request — see {@link #insertQuota})
	 *         or the window is exhausted.
	 */
	@Update("""
			update API_QUOTA
			set QUOTA_TIME  = case when #{now} - QUOTA_TIME > #{interval} then #{now} else QUOTA_TIME end,
				QUOTA_COUNT = case when #{now} - QUOTA_TIME > #{interval} then 1     else QUOTA_COUNT + 1 end
			where SUBJECT_KIND = #{kind} and SUBJECT_ID = #{subjectId} and BUCKET = #{bucket}
				and (#{now} - QUOTA_TIME > #{interval} or QUOTA_COUNT < #{limit})
			""")
	int tryConsumeQuota(int kind, long subjectId, int bucket, long now, long interval, int limit);

	/**
	 * Creates the counter for a subject's first request in a bucket. A duplicate
	 * key means the row already existed (and {@link #tryConsumeQuota} just found
	 * it exhausted), i.e. the request must be rejected.
	 */
	@Insert("""
			insert into API_QUOTA (SUBJECT_KIND, SUBJECT_ID, BUCKET, QUOTA_COUNT, QUOTA_TIME)
			values (#{kind}, #{subjectId}, #{bucket}, 1, #{now})
			""")
	void insertQuota(int kind, long subjectId, int bucket, long now);

	/**
	 * The start of the current window, used to compute the {@code Retry-After}
	 * delay when a request is rejected.
	 */
	@Select("""
			select QUOTA_TIME from API_QUOTA
			where SUBJECT_KIND = #{kind} and SUBJECT_ID = #{subjectId} and BUCKET = #{bucket}
			""")
	Long getQuotaTime(int kind, long subjectId, int bucket);

	/** Removes all counters of a single API token (token deletion). */
	@Delete("delete from API_QUOTA where SUBJECT_KIND = 1 and SUBJECT_ID = #{tokenId}")
	void deleteTokenQuota(long tokenId);

	/** Removes all counters of the given API tokens (bulk token deletion). */
	@Delete("""
			<script>
			delete from API_QUOTA
			where SUBJECT_KIND = 1 and SUBJECT_ID in
			    <foreach item="item" collection="tokenIds" open="(" separator="," close=")">
			        #{item}
			    </foreach>
			</script>
			""")
	void deleteTokenQuotas(List<Long> tokenIds);

	/** Removes the token counters of all of a user's tokens (logout-all / account deletion). */
	@Delete("""
			delete from API_QUOTA
			where SUBJECT_KIND = 1 and SUBJECT_ID in (select ID from TOKENS where USERID = #{userId})
			""")
	void deleteTokenQuotaForUser(long userId);

	/** Removes the account-level counters of a user (account deletion). */
	@Delete("delete from API_QUOTA where SUBJECT_KIND = 0 and SUBJECT_ID = #{userId}")
	void deleteAccountQuota(long userId);

}
