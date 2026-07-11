-- Diagnostics framework (docs/plans/2026-07-11-diagnostics-framework-design.md):
-- source-agnostic storage + analysis of operational log lines that point at a
-- user's installation problem. First fed by the dongle error reports that
-- LogReportServlet writes to the server log; a scheduled reader tails the rolling
-- log, normalizes each relevant line to a signature and rolls up aggregates here.
-- Phase 1: persist only (no rules, no mail).

-- One row per distinct (SOURCE, SIGNATURE). SIG_ID is a stable SHA-1 hex of
-- SOURCE + '\n' + SIGNATURE, computed by the reader so upserts need no generated
-- key round trip. CATEGORY stays NULL until a (future) rule classifies the
-- signature -- CATEGORY IS NULL is the agent's "what's new" feed.
CREATE TABLE DIAG_SIGNATURE (
	SIG_ID CHARACTER(40) NOT NULL,
	SOURCE CHARACTER VARYING(32) NOT NULL,
	SIGNATURE CHARACTER VARYING(1024) NOT NULL,
	TAG CHARACTER VARYING(64) DEFAULT '' NOT NULL,
	CATEGORY CHARACTER VARYING(64),
	SAMPLE_MESSAGE CHARACTER VARYING(1024) DEFAULT '' NOT NULL,
	FIRST_SEEN BIGINT DEFAULT 0 NOT NULL,
	LAST_SEEN BIGINT DEFAULT 0 NOT NULL,
	TOTAL_EVENTS BIGINT DEFAULT 0 NOT NULL,
	CONSTRAINT DIAG_SIGNATURE_PK PRIMARY KEY (SIG_ID)
);

CREATE INDEX DIAG_SIGNATURE_SOURCE_IDX ON DIAG_SIGNATURE (SOURCE, CATEGORY);

-- Per-origin rolling aggregate; the matcher (future phase) evaluates persistence
-- thresholds against DISTINCT_DAYS / EVENT_COUNT here. LAST_DAY (UTC epoch-day of
-- the last event) drives DISTINCT_DAYS: the reader bumps DISTINCT_DAYS only when
-- an event's day exceeds LAST_DAY, which is exact as long as events are ingested
-- in log order (they are). EPOCH_DAY-style INTEGER, mirroring NUMBER_ACTIVITY.
CREATE TABLE DIAG_ORIGIN_SIGNATURE (
	SIG_ID CHARACTER(40) NOT NULL,
	SOURCE CHARACTER VARYING(32) NOT NULL,
	ORIGIN_ID CHARACTER VARYING(128) NOT NULL,
	USER_ID CHARACTER VARYING(64),
	FIRST_SEEN BIGINT DEFAULT 0 NOT NULL,
	LAST_SEEN BIGINT DEFAULT 0 NOT NULL,
	EVENT_COUNT BIGINT DEFAULT 0 NOT NULL,
	DISTINCT_DAYS INTEGER DEFAULT 0 NOT NULL,
	LAST_DAY INTEGER DEFAULT 0 NOT NULL,
	CONSTRAINT DIAG_ORIGIN_SIGNATURE_PK PRIMARY KEY (SIG_ID, ORIGIN_ID)
);

CREATE INDEX DIAG_ORIGIN_SIGNATURE_ORIGIN_IDX ON DIAG_ORIGIN_SIGNATURE (SOURCE, ORIGIN_ID);

-- Bounded raw evidence, 30-day retention (DiagnosticsService purge). Capped to N
-- rows per SIG_ID at write time; the agent reads these to investigate new
-- signatures. MESSAGE_SCRUBBED has had emails / phone numbers masked on ingest.
CREATE TABLE DIAG_SAMPLE (
	ID BIGINT NOT NULL AUTO_INCREMENT,
	RECEIVED_MS BIGINT DEFAULT 0 NOT NULL,
	SOURCE CHARACTER VARYING(32) NOT NULL,
	SIG_ID CHARACTER(40) NOT NULL,
	ORIGIN_ID CHARACTER VARYING(128) DEFAULT '' NOT NULL,
	USER_ID CHARACTER VARYING(64),
	SEVERITY CHARACTER(1) DEFAULT 'E' NOT NULL,
	UPTIME_S BIGINT,
	TAG CHARACTER VARYING(64) DEFAULT '' NOT NULL,
	MESSAGE_SCRUBBED CHARACTER VARYING(1024) DEFAULT '' NOT NULL,
	CONSTRAINT DIAG_SAMPLE_PK PRIMARY KEY (ID)
);

CREATE INDEX DIAG_SAMPLE_SIG_IDX ON DIAG_SAMPLE (SIG_ID);
CREATE INDEX DIAG_SAMPLE_RECEIVED_IDX ON DIAG_SAMPLE (RECEIVED_MS);

-- The reader's checkpoint, one row per tailed log stream. SEGMENT_COUNT is the
-- tinylog {count} of the file currently being read, BYTE_OFFSET the position
-- within it. Lets the reader resume exactly-once and detect rotation (a higher
-- count appeared) / gaps (its count was pruned) without inode heuristics.
CREATE TABLE DIAG_INGEST_CURSOR (
	STREAM_ID CHARACTER VARYING(64) NOT NULL,
	SEGMENT_COUNT BIGINT DEFAULT 0 NOT NULL,
	BYTE_OFFSET BIGINT DEFAULT 0 NOT NULL,
	LAST_LINE_TS BIGINT DEFAULT 0 NOT NULL,
	UPDATED BIGINT DEFAULT 0 NOT NULL,
	CONSTRAINT DIAG_INGEST_CURSOR_PK PRIMARY KEY (STREAM_ID)
);
