/*
 * Copyright (c) 2024 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * MyBatis mapper for FTC (Federal Trade Commission) complaint data tables.
 *
 * <p>
 * Provides read queries for {@code SpamCheckServlet} integration and write queries
 * for the FTC CSV data import service.
 * </p>
 */
public interface FtcReports {

	// --- Read queries (for SpamCheckServlet) ---

	/**
	 * Looks up FTC number information by SHA-1 hash.
	 */
	@Select("SELECT PHONE, VOTES, ROBOCALLS, FIRST_REPORTED, LAST_REPORTED FROM FTC_NUMBERS WHERE SHA1 = #{hash}")
	DBFtcNumberInfo getFtcNumberByHash(byte[] hash);

	/**
	 * Retrieves the rating breakdown for a given phone number from FTC complaint subjects.
	 */
	@Select("SELECT s.RATING, SUM(r.VOTES) as VOTES FROM FTC_REPORTS r " +
			"JOIN FTC_SUBJECTS s ON r.SUBJECT_ID = s.ID " +
			"WHERE r.PHONE = #{phone} AND s.RATING IS NOT NULL " +
			"GROUP BY s.RATING ORDER BY VOTES DESC")
	List<DBFtcRatingInfo> getFtcRatingsByPhone(String phone);

	// --- Write queries (for FtcImportService) ---

	/**
	 * Looks up the ID of a subject by its label.
	 *
	 * @return the subject ID, or {@code null} if not found.
	 */
	@Select("SELECT ID FROM FTC_SUBJECTS WHERE LABEL = #{label}")
	Integer getSubjectId(String label);

	/**
	 * Inserts a new FTC subject with the given label.
	 *
	 * <p>
	 * Use {@link #getSubjectId(String)} afterward to retrieve the generated ID.
	 * </p>
	 */
	@Insert("INSERT INTO FTC_SUBJECTS (LABEL) VALUES (#{label})")
	void insertSubject(String label);

	/**
	 * Retrieves FTC number information by phone number.
	 */
	@Select("SELECT PHONE, VOTES, ROBOCALLS, FIRST_REPORTED, LAST_REPORTED FROM FTC_NUMBERS WHERE PHONE = #{phone}")
	DBFtcNumberInfo getFtcNumber(String phone);

	/**
	 * Inserts a new FTC number record.
	 */
	@Insert("INSERT INTO FTC_NUMBERS (PHONE, SHA1, VOTES, ROBOCALLS, FIRST_REPORTED, LAST_REPORTED) " +
			"VALUES (#{phone}, #{sha1}, #{votes}, #{robocalls}, #{firstReported}, #{lastReported})")
	void insertFtcNumber(@Param("phone") String phone, @Param("sha1") byte[] sha1,
						 @Param("votes") int votes, @Param("robocalls") int robocalls,
						 @Param("firstReported") long firstReported, @Param("lastReported") long lastReported);

	/**
	 * Updates an existing FTC number record, accumulating votes and robocalls and expanding the reporting range.
	 */
	@Update("UPDATE FTC_NUMBERS SET VOTES = VOTES + #{votes}, ROBOCALLS = ROBOCALLS + #{robocalls}, " +
			"FIRST_REPORTED = LEAST(FIRST_REPORTED, #{firstReported}), " +
			"LAST_REPORTED = GREATEST(LAST_REPORTED, #{lastReported}) " +
			"WHERE PHONE = #{phone}")
	void updateFtcNumber(@Param("phone") String phone, @Param("votes") int votes,
						 @Param("robocalls") int robocalls,
						 @Param("firstReported") long firstReported, @Param("lastReported") long lastReported);

	/**
	 * Inserts a new FTC report linking a phone number to a complaint subject.
	 */
	@Insert("INSERT INTO FTC_REPORTS (PHONE, SUBJECT_ID, VOTES) VALUES (#{phone}, #{subjectId}, #{votes})")
	void insertFtcReport(@Param("phone") String phone, @Param("subjectId") int subjectId, @Param("votes") int votes);

	/**
	 * Updates the vote count for an existing FTC report.
	 *
	 * @return the number of rows updated (0 if no matching report exists).
	 */
	@Update("UPDATE FTC_REPORTS SET VOTES = VOTES + #{votes} WHERE PHONE = #{phone} AND SUBJECT_ID = #{subjectId}")
	int updateFtcReport(@Param("phone") String phone, @Param("subjectId") int subjectId, @Param("votes") int votes);
}
