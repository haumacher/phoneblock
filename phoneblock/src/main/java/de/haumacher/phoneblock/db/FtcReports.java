/*
 * Copyright (c) 2024 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.db;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * MyBatis mapper for FTC (Federal Trade Commission) complaint provenance tables
 * ({@code FTC_SUBJECTS} and {@code FTC_REPORTS}).
 */
public interface FtcReports {

	/**
	 * Looks up the ID of a subject by its label.
	 *
	 * @return the subject ID, or {@code null} if not found.
	 */
	@Select("SELECT ID FROM FTC_SUBJECTS WHERE LABEL = #{label}")
	Integer getSubjectId(String label);

	/**
	 * Looks up the rating for a subject by its ID.
	 *
	 * @return the rating string (e.g. "G_FRAUD"), or {@code null} if no rating is assigned.
	 */
	@Select("SELECT RATING FROM FTC_SUBJECTS WHERE ID = #{id}")
	String getSubjectRating(int id);

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
