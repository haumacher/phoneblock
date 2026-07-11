/*
 * Copyright (c) 2026 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.diag;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
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

	// Column aliases squash snake_case to the bean property (MyBatis matches
	// case-insensitively) since underscore-to-camel mapping is not enabled.
	String SIGNATURE_COLS = "SIG_ID AS SIGID, SOURCE, SIGNATURE, TAG, CATEGORY, "
			+ "TOTAL_EVENTS AS TOTALEVENTS, FIRST_SEEN AS FIRSTSEEN, LAST_SEEN AS LASTSEEN";

	@Select("SELECT " + SIGNATURE_COLS + " FROM DIAG_SIGNATURE "
			+ "WHERE (#{source} IS NULL OR SOURCE = #{source}) "
			+ "AND (#{onlyUnmatched} = FALSE OR CATEGORY IS NULL) "
			+ "ORDER BY TOTAL_EVENTS DESC")
	List<SignatureRow> listSignatures(@Param("source") String source,
			@Param("onlyUnmatched") boolean onlyUnmatched);

	@Select("SELECT " + SIGNATURE_COLS + " FROM DIAG_SIGNATURE WHERE SIG_ID = #{sigId}")
	SignatureRow getSignature(String sigId);

	@Select("SELECT RECEIVED_MS AS RECEIVEDMS, SEVERITY, TAG, ORIGIN_ID AS ORIGINID, MESSAGE_SCRUBBED AS MESSAGESCRUBBED "
			+ "FROM DIAG_SAMPLE WHERE SIG_ID = #{sigId} ORDER BY RECEIVED_MS DESC LIMIT #{limit}")
	List<java.util.Map<String, Object>> listSamples(@Param("sigId") String sigId, @Param("limit") int limit);

	@Update("UPDATE DIAG_SIGNATURE SET CATEGORY = #{category} WHERE SIG_ID = #{sigId}")
	void setSignatureCategory(@Param("sigId") String sigId, @Param("category") String category);

	@Select("SELECT ORIGIN_ID AS ORIGINID, USER_ID AS USERID, LAST_SEEN AS LASTSEEN, "
			+ "EVENT_COUNT AS EVENTCOUNT, DISTINCT_DAYS AS DISTINCTDAYS "
			+ "FROM DIAG_ORIGIN_SIGNATURE "
			+ "WHERE SIG_ID = #{sigId} AND EVENT_COUNT >= #{minEvents} AND DISTINCT_DAYS >= #{minDays}")
	List<OriginRow> originsOverThreshold(@Param("sigId") String sigId,
			@Param("minEvents") int minEvents, @Param("minDays") int minDays);

	// ---- Rules ----

	String RULE_COLS = "ID, NAME, SOURCE, MATCH_TAG AS MATCHTAG, MATCH_REGEX AS MATCHREGEX, "
			+ "CATEGORY, ACTOR, MIN_DISTINCT_DAYS AS MINDISTINCTDAYS, MIN_EVENTS AS MINEVENTS, "
			+ "TEMPLATE_KEY AS TEMPLATEKEY, STATE, AUTHOR, NOTES, CREATED, UPDATED";

	@Select("SELECT " + RULE_COLS + " FROM DIAG_RULE WHERE STATE IN ('SHADOW', 'LIVE')")
	List<DiagRule> listActiveRules();

	@Select("SELECT " + RULE_COLS + " FROM DIAG_RULE "
			+ "WHERE (#{state} IS NULL OR STATE = #{state}) ORDER BY ID")
	List<DiagRule> listRules(@Param("state") String state);

	@Select("SELECT " + RULE_COLS + " FROM DIAG_RULE WHERE ID = #{id}")
	DiagRule getRule(long id);

	@Select("SELECT " + RULE_COLS + " FROM DIAG_RULE WHERE NAME = #{name} LIMIT 1")
	DiagRule getRuleByName(String name);

	@Insert("INSERT INTO DIAG_RULE (NAME, SOURCE, MATCH_TAG, MATCH_REGEX, CATEGORY, ACTOR, "
			+ "MIN_DISTINCT_DAYS, MIN_EVENTS, TEMPLATE_KEY, STATE, AUTHOR, NOTES, CREATED, UPDATED) "
			+ "VALUES (#{name}, #{source, jdbcType=VARCHAR}, #{matchTag, jdbcType=VARCHAR}, #{matchRegex}, "
			+ "#{category}, #{actor}, #{minDistinctDays}, #{minEvents}, #{templateKey, jdbcType=VARCHAR}, "
			+ "#{state}, #{author}, #{notes}, #{created}, #{updated})")
	@Options(useGeneratedKeys = true, keyProperty = "id")
	void insertRule(DiagRule rule);

	@Update("UPDATE DIAG_RULE SET NAME=#{name}, SOURCE=#{source, jdbcType=VARCHAR}, "
			+ "MATCH_TAG=#{matchTag, jdbcType=VARCHAR}, MATCH_REGEX=#{matchRegex}, CATEGORY=#{category}, "
			+ "ACTOR=#{actor}, MIN_DISTINCT_DAYS=#{minDistinctDays}, MIN_EVENTS=#{minEvents}, "
			+ "TEMPLATE_KEY=#{templateKey, jdbcType=VARCHAR}, NOTES=#{notes}, UPDATED=#{updated} "
			+ "WHERE ID=#{id}")
	int updateRule(DiagRule rule);

	@Update("UPDATE DIAG_RULE SET STATE=#{state}, UPDATED=#{updated} WHERE ID=#{id}")
	int setRuleState(@Param("id") long id, @Param("state") String state, @Param("updated") long updated);

	// ---- Templates ----

	@Select("SELECT ID, TEMPLATE_KEY AS TEMPLATEKEY, LANG, SUBJECT, BODY, UPDATED "
			+ "FROM DIAG_TEMPLATE WHERE TEMPLATE_KEY = #{templateKey} AND LANG = #{lang}")
	DiagTemplate getTemplate(@Param("templateKey") String templateKey, @Param("lang") String lang);

	@Insert("MERGE INTO DIAG_TEMPLATE (TEMPLATE_KEY, LANG, SUBJECT, BODY, UPDATED) "
			+ "KEY(TEMPLATE_KEY, LANG) VALUES (#{templateKey}, #{lang}, #{subject}, #{body}, #{updated})")
	void upsertTemplate(DiagTemplate template);

	// ---- Notification ledger ----

	@Select("SELECT COUNT(*) FROM DIAG_NOTIFICATION "
			+ "WHERE RULE_ID = #{ruleId} AND ORIGIN_ID = #{originId} AND STATE IN ('PENDING', 'SENT')")
	int countActiveNotifications(@Param("ruleId") long ruleId, @Param("originId") String originId);

	@Insert("INSERT INTO DIAG_NOTIFICATION (SOURCE, ORIGIN_ID, USER_ID, RULE_ID, STATE, DRY_RUN, FIRST_MATCHED, SENT_AT) "
			+ "VALUES (#{source}, #{originId}, #{userId, jdbcType=VARCHAR}, #{ruleId}, #{state}, #{dryRun}, "
			+ "#{firstMatched}, #{sentAt, jdbcType=BIGINT})")
	void insertNotification(@Param("source") String source, @Param("originId") String originId,
			@Param("userId") String userId, @Param("ruleId") long ruleId, @Param("state") String state,
			@Param("dryRun") boolean dryRun, @Param("firstMatched") long firstMatched,
			@Param("sentAt") Long sentAt);

	@Update("UPDATE DIAG_NOTIFICATION SET STATE='CLEARED', CLEARED_AT=#{clearedAt} "
			+ "WHERE RULE_ID=#{ruleId} AND ORIGIN_ID=#{originId} AND STATE IN ('PENDING', 'SENT')")
	int clearNotifications(@Param("ruleId") long ruleId, @Param("originId") String originId,
			@Param("clearedAt") long clearedAt);

	@Select("SELECT COUNT(*) FROM DIAG_NOTIFICATION WHERE STATE='SENT' AND DRY_RUN=FALSE AND SENT_AT >= #{cutoff}")
	long countSentSince(long cutoff);

	@Select("SELECT COUNT(*) FROM DIAG_NOTIFICATION "
			+ "WHERE STATE='SENT' AND DRY_RUN=FALSE AND USER_ID=#{userId} AND SENT_AT >= #{cutoff}")
	long countSentForUserSince(@Param("userId") String userId, @Param("cutoff") long cutoff);

	// ---- Dongle liveness (silence) detection over the TOKENS table ----

	// The latest token per dongle device (device id parsed from the User-Agent),
	// restricted to devices that were active (LASTACCESS > 0) but have not checked
	// in since #{cutoff}.
	String LATEST_DONGLE_TOKEN = "SELECT DEVICEID, USERID, LASTACCESS, CREATED, ID FROM ("
			+ "SELECT d.*, ROW_NUMBER() OVER (PARTITION BY DEVICEID ORDER BY CREATED DESC, ID DESC) AS RN FROM ("
			+ "SELECT t.ID, t.USERID AS USERID, t.CREATED, t.LASTACCESS AS LASTACCESS, "
			+ "REGEXP_REPLACE(t.USERAGENT, '.*\\(([0-9a-fA-F-]+)\\).*', '$1') AS DEVICEID "
			+ "FROM TOKENS t WHERE t.USERAGENT LIKE 'PhoneBlock-Dongle/%(%)') d) r WHERE r.RN = 1";

	@Select("SELECT DEVICEID, USERID, LASTACCESS FROM (" + LATEST_DONGLE_TOKEN
			+ ") x WHERE x.LASTACCESS > 0 AND x.LASTACCESS < #{cutoff}")
	List<SilentDongle> findSilentDongles(long cutoff);

	@Update("UPDATE DIAG_NOTIFICATION SET STATE='CLEARED', CLEARED_AT=#{clearedAt} "
			+ "WHERE RULE_ID=#{ruleId} AND STATE IN ('PENDING', 'SENT') AND ORIGIN_ID IN ("
			+ "SELECT DEVICEID FROM (" + LATEST_DONGLE_TOKEN + ") x WHERE x.LASTACCESS >= #{recentCutoff})")
	int clearReturnedSilentNotifications(@Param("ruleId") long ruleId,
			@Param("recentCutoff") long recentCutoff, @Param("clearedAt") long clearedAt);
}
