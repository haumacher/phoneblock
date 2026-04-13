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
			GROUP BY c.PHONE
			HAVING (SELECT COUNT(1) FROM COMMENTS g WHERE g.PHONE = c.PHONE AND g.CLASSIFICATION = 1) &lt; #{goodThreshold}
			ORDER BY MAX(c.CREATED) DESC
			</script>
			""")
	List<String> candidatePhones(@Param("goodThreshold") int goodThreshold);

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
