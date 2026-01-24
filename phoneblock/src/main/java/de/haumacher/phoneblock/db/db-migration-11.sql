-- Migration 11: Add VERSION and PENDING_UPDATE columns for incremental blocklist synchronization
--
-- This migration enables clients to download only blocklist changes since their last sync,
-- reducing bandwidth usage and improving scalability.
--
-- VERSION: Tracks when a number's blocklist status changed (assigned by nightly job)
-- PENDING_UPDATE: Marks numbers that crossed vote thresholds and need version assignment

ALTER TABLE NUMBERS ADD COLUMN VERSION BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE NUMBERS ADD COLUMN PENDING_UPDATE BOOLEAN DEFAULT FALSE NOT NULL;

-- Mark all existing numbers as part of the initial blocklist version
-- (New numbers added later will get VERSION=0 from the DEFAULT)
UPDATE NUMBERS SET VERSION = 1;

-- Index for efficient incremental queries (get all changes since version X)
CREATE INDEX NUMBERS_VERSION_IDX ON NUMBERS (VERSION DESC, PHONE);

-- Index for efficient nightly job (find all pending updates)
CREATE INDEX NUMBERS_PENDING_UPDATE_IDX ON NUMBERS (PENDING_UPDATE, PHONE);

-- Initialize global version counter (starts at 1, so since=0 returns full blocklist)
INSERT INTO PROPERTIES (NAME, VAL) VALUES('blocklist.version', '1');
