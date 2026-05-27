-- Decay-aware visibility filter (issue #342 / epic #300): the snapshot column
-- and the per-region counter move to the same projected-EMA encoding the
-- live SPAM_EVIDENCE already uses, so the blocklist filter, the per-region
-- ranking, and the publish/incremental-sync snapshot all live on one axis.
--
-- This script only adds the new columns and the visibility index. The Java
-- hook in DB.setupSchema (version == 31) then:
--   * projects NUMBERS_LOCALE.VOTES into NUMBERS_LOCALE.SPAM_EVIDENCE using
--     LASTACCESS as the assumed event time and DIRECT_VOTE_EVIDENCE_WEIGHT
--     as the per-event weight (same template as backfillNumbersLocaleHeat),
--   * copies NUMBERS.SPAM_EVIDENCE into PUBLISHED_SPAM_EVIDENCE — the live
--     EMA is the only ground truth at migration time; the next scheduled
--     BlocklistVersionService sweep produces the proper snapshot value,
--   * drops the now-obsolete VOTES and PUBLISHED_VOTES columns.
-- DROPs run from Java so they happen *after* the backfill.

ALTER TABLE NUMBERS_LOCALE
	ADD COLUMN SPAM_EVIDENCE DOUBLE PRECISION DEFAULT 0 NOT NULL;

ALTER TABLE NUMBERS
	ADD COLUMN PUBLISHED_SPAM_EVIDENCE DOUBLE PRECISION DEFAULT 0 NOT NULL;

-- Visibility filter on decay-aware evidence: enables
--   WHERE ACTIVE AND SPAM_EVIDENCE >= projectedThreshold(now)
-- as an index seek for the blocklist read path.
CREATE INDEX NUMBERS_SPAM_EVIDENCE_IDX ON NUMBERS (ACTIVE, SPAM_EVIDENCE DESC);
