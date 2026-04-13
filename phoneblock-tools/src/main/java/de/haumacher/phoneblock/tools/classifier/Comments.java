/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.tools.classifier;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * MyBatis mapper used by the classifier tool.
 * <p>
 * Only includes the COMMENTS / WHITELIST / SUMMARY operations that are needed
 * for the batch workflow.
 * </p>
 */
public interface Comments {

	/**
	 * Phone numbers that have at least one unclassified comment and fewer than
	 * {@code goodThreshold} GOOD-classified comments. Whitelisted numbers are
	 * excluded.
	 */
	@Select("""
			<script>
			SELECT c.PHONE
			FROM COMMENTS c
			WHERE c.CLASSIFICATION = 0
			  AND c.PHONE NOT IN (SELECT PHONE FROM WHITELIST)
			  AND c.PHONE NOT IN (SELECT PHONE FROM SUMMARY)
			GROUP BY c.PHONE
			HAVING (SELECT COUNT(1) FROM COMMENTS g WHERE g.PHONE = c.PHONE AND g.CLASSIFICATION = 1) &lt; #{goodThreshold}
			ORDER BY MAX(c.CREATED) DESC
			</script>
			""")
	List<String> candidatePhones(@Param("goodThreshold") int goodThreshold);

	@Select("SELECT COUNT(1) FROM SUMMARY WHERE PHONE = #{phone}")
	int hasSummary(@Param("phone") String phone);

	/**
	 * Unclassified comments for a given phone, ordered by decreasing vote score,
	 * then length, then newest.
	 */
	@Select("""
			SELECT ID, PHONE, RATING, COMMENT, UP, DOWN, CREATED
			FROM COMMENTS
			WHERE PHONE = #{phone} AND CLASSIFICATION = 0
			ORDER BY (UP - DOWN) DESC, LENGTH(COMMENT) DESC, CREATED DESC
			LIMIT #{limit}
			""")
	List<PendingComment> pendingForPhone(@Param("phone") String phone, @Param("limit") int limit);

	/**
	 * All unclassified comments belonging to non-whitelisted phones that don't yet
	 * have a SUMMARY and have fewer than {@code goodThreshold} GOODs. Used by the
	 * auto-candidate flow to load everything in one query instead of N+1 queries
	 * per phone.
	 */
	@Select("""
			SELECT c.ID, c.PHONE, c.RATING, c.COMMENT, c.UP, c.DOWN, c.CREATED
			FROM COMMENTS c
			WHERE c.CLASSIFICATION = 0
			  AND c.PHONE NOT IN (SELECT PHONE FROM WHITELIST)
			  AND c.PHONE NOT IN (SELECT PHONE FROM SUMMARY)
			  AND (SELECT COUNT(1) FROM COMMENTS g WHERE g.PHONE = c.PHONE AND g.CLASSIFICATION = 1) < #{goodThreshold}
			  AND (SELECT COUNT(1) FROM COMMENTS u WHERE u.PHONE = c.PHONE AND u.CLASSIFICATION = 0) >= #{minComments}
			ORDER BY c.PHONE, (c.UP - c.DOWN) DESC, LENGTH(c.COMMENT) DESC, c.CREATED DESC
			""")
	List<PendingComment> allPendingEligible(
			@Param("goodThreshold") int goodThreshold,
			@Param("minComments") int minComments);

	/**
	 * Initial GOOD counts for all phones that already have at least one GOOD
	 * comment. Used to seed the good-count bookkeeping in a single query.
	 */
	@Select("SELECT PHONE AS phone, COUNT(1) AS count FROM COMMENTS WHERE CLASSIFICATION = 1 GROUP BY PHONE")
	List<PhoneCount> goodCountsByPhone();

	@Select("SELECT COUNT(1) FROM COMMENTS WHERE PHONE = #{phone} AND CLASSIFICATION = 1")
	int countGood(@Param("phone") String phone);

	@Select("SELECT COUNT(1) FROM COMMENTS WHERE PHONE = #{phone}")
	int countAll(@Param("phone") String phone);

	@Update("UPDATE COMMENTS SET CLASSIFICATION = #{classification} WHERE ID = #{id}")
	int setClassification(@Param("id") String id, @Param("classification") int classification);

	@Select("SELECT COUNT(1) FROM WHITELIST WHERE PHONE = #{phone}")
	int isWhitelisted(@Param("phone") String phone);

	@Select("""
			SELECT ID, PHONE, RATING, COMMENT, UP, DOWN, CREATED
			FROM COMMENTS
			WHERE PHONE = #{phone} AND CLASSIFICATION = 1
			ORDER BY (UP - DOWN) DESC, LENGTH(COMMENT) DESC, CREATED DESC
			""")
	List<PendingComment> goodForPhone(@Param("phone") String phone);

	@Update("""
			MERGE INTO SUMMARY (PHONE, COMMENT, CREATED)
			KEY (PHONE)
			VALUES (#{phone}, #{summary}, #{created})
			""")
	int upsertSummary(@Param("phone") String phone, @Param("summary") String summary,
			@Param("created") long created);
}
