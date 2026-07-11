/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.diag;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * MyBatis mapper for the diagnostics aggregate tables
 * ({@code DIAG_SIGNATURE}, {@code DIAG_ORIGIN_SIGNATURE}, {@code DIAG_SAMPLE},
 * {@code DIAG_INGEST_CURSOR}).
 *
 * <p>Aggregate rows use the update-then-insert idiom (the mapper has no upsert
 * helper) so counts accumulate; the reader keys everything on the pre-computed
 * {@code sigId}. All writes of one poll run in a single transaction.</p>
 */
public interface DiagnosticsMapper {

	// ---- Ingest cursor ----

	@Select("SELECT SEGMENT_COUNT AS SEGMENTCOUNT, BYTE_OFFSET AS BYTEOFFSET, LAST_LINE_TS AS LASTLINETS "
			+ "FROM DIAG_INGEST_CURSOR WHERE STREAM_ID = #{streamId}")
	IngestCursor getCursor(String streamId);

	@Insert("MERGE INTO DIAG_INGEST_CURSOR (STREAM_ID, SEGMENT_COUNT, BYTE_OFFSET, LAST_LINE_TS, UPDATED) "
			+ "KEY(STREAM_ID) VALUES (#{streamId}, #{segmentCount}, #{byteOffset}, #{lastLineTs}, #{updated})")
	void upsertCursor(@Param("streamId") String streamId, @Param("segmentCount") long segmentCount,
			@Param("byteOffset") long byteOffset, @Param("lastLineTs") long lastLineTs,
			@Param("updated") long updated);

	// ---- Signature aggregate ----

	@Update("UPDATE DIAG_SIGNATURE SET "
			+ "FIRST_SEEN = CASE WHEN #{firstSeen} < FIRST_SEEN THEN #{firstSeen} ELSE FIRST_SEEN END, "
			+ "LAST_SEEN = CASE WHEN #{lastSeen} > LAST_SEEN THEN #{lastSeen} ELSE LAST_SEEN END, "
			+ "TOTAL_EVENTS = TOTAL_EVENTS + #{events}, "
			+ "TAG = #{tag} "
			+ "WHERE SIG_ID = #{sigId}")
	int updateSignature(@Param("sigId") String sigId, @Param("tag") String tag,
			@Param("firstSeen") long firstSeen, @Param("lastSeen") long lastSeen,
			@Param("events") long events);

	@Insert("INSERT INTO DIAG_SIGNATURE (SIG_ID, SOURCE, SIGNATURE, TAG, SAMPLE_MESSAGE, FIRST_SEEN, LAST_SEEN, TOTAL_EVENTS) "
			+ "VALUES (#{sigId}, #{source}, #{signature}, #{tag}, #{sampleMessage}, #{firstSeen}, #{lastSeen}, #{events})")
	void insertSignature(@Param("sigId") String sigId, @Param("source") String source,
			@Param("signature") String signature, @Param("tag") String tag,
			@Param("sampleMessage") String sampleMessage, @Param("firstSeen") long firstSeen,
			@Param("lastSeen") long lastSeen, @Param("events") long events);

	// ---- Per-origin aggregate (drives persistence thresholds) ----

	@Update("UPDATE DIAG_ORIGIN_SIGNATURE SET "
			+ "LAST_SEEN = CASE WHEN #{lastSeen} > LAST_SEEN THEN #{lastSeen} ELSE LAST_SEEN END, "
			+ "EVENT_COUNT = EVENT_COUNT + #{events}, "
			+ "DISTINCT_DAYS = DISTINCT_DAYS + (CASE WHEN #{epochDay} > LAST_DAY THEN 1 ELSE 0 END), "
			+ "LAST_DAY = CASE WHEN #{epochDay} > LAST_DAY THEN #{epochDay} ELSE LAST_DAY END "
			+ "WHERE SIG_ID = #{sigId} AND ORIGIN_ID = #{originId}")
	int updateOriginSignature(@Param("sigId") String sigId, @Param("originId") String originId,
			@Param("lastSeen") long lastSeen, @Param("events") long events,
			@Param("epochDay") int epochDay);

	@Insert("INSERT INTO DIAG_ORIGIN_SIGNATURE "
			+ "(SIG_ID, SOURCE, ORIGIN_ID, USER_ID, FIRST_SEEN, LAST_SEEN, EVENT_COUNT, DISTINCT_DAYS, LAST_DAY) "
			+ "VALUES (#{sigId}, #{source}, #{originId}, #{userId, jdbcType=VARCHAR}, #{firstSeen}, #{lastSeen}, #{events}, 1, #{epochDay})")
	void insertOriginSignature(@Param("sigId") String sigId, @Param("source") String source,
			@Param("originId") String originId, @Param("userId") String userId,
			@Param("firstSeen") long firstSeen, @Param("lastSeen") long lastSeen,
			@Param("events") long events, @Param("epochDay") int epochDay);

	// ---- Bounded raw samples ----

	@Select("SELECT COUNT(*) FROM DIAG_SAMPLE WHERE SIG_ID = #{sigId}")
	int countSamples(String sigId);

	@Insert("INSERT INTO DIAG_SAMPLE "
			+ "(RECEIVED_MS, SOURCE, SIG_ID, ORIGIN_ID, USER_ID, SEVERITY, UPTIME_S, TAG, MESSAGE_SCRUBBED) "
			+ "VALUES (#{receivedMs}, #{source}, #{sigId}, #{originId}, #{userId, jdbcType=VARCHAR}, "
			+ "#{severity}, #{uptimeS, jdbcType=BIGINT}, #{tag}, #{messageScrubbed})")
	void insertSample(@Param("receivedMs") long receivedMs, @Param("source") String source,
			@Param("sigId") String sigId, @Param("originId") String originId,
			@Param("userId") String userId, @Param("severity") String severity,
			@Param("uptimeS") Long uptimeS, @Param("tag") String tag,
			@Param("messageScrubbed") String messageScrubbed);

	@Delete("DELETE FROM DIAG_SAMPLE WHERE RECEIVED_MS < #{cutoff}")
	int purgeSamples(long cutoff);

	// ---- Read helpers (introspection / verification) ----

	@Select("SELECT COUNT(*) FROM DIAG_SIGNATURE")
	long countSignatures();

	@Select("SELECT COALESCE(SUM(TOTAL_EVENTS), 0) FROM DIAG_SIGNATURE")
	long totalEvents();
}
