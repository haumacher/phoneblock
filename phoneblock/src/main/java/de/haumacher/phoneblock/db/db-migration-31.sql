-- Decay-aware visibility filter (issue #342 / epic #300): the snapshot column
-- and the per-region signal gain a projected-EMA encoding matching the
-- live SPAM_EVIDENCE, so the blocklist filter, the per-region ranking, and
-- the publish/incremental-sync snapshot all live on one axis. The raw
-- per-region VOTES counter is retained alongside (additive, never dropped).
--
-- This script only adds the new columns and the visibility index. The Java
-- hook in DB.setupSchema (version == 31) then:
--   * projects NUMBERS_LOCALE.VOTES into NUMBERS_LOCALE.SPAM_EVIDENCE using
--     LASTACCESS as the assumed event time and DIRECT_VOTE_EVIDENCE_WEIGHT
--     as the per-event weight (same template as backfillNumbersLocaleHeat),
--   * copies NUMBERS.SPAM_EVIDENCE into PUBLISHED_SPAM_EVIDENCE — the live
--     EMA is the only ground truth at migration time; the next scheduled
--     BlocklistVersionService sweep produces the proper snapshot value,
--   * drops the now-obsolete NUMBERS.PUBLISHED_VOTES column.
-- NUMBERS_LOCALE.VOTES is NOT dropped: it is kept as the non-decaying raw
-- per-region counter and updated additively alongside SPAM_EVIDENCE going
-- forward (epic #300). The projection above only seeds the EMA from the
-- accumulated history. The DROP runs from Java so it happens *after* the
-- backfill.

ALTER TABLE NUMBERS_LOCALE
	ADD COLUMN SPAM_EVIDENCE DOUBLE PRECISION DEFAULT 0 NOT NULL;

ALTER TABLE NUMBERS
	ADD COLUMN PUBLISHED_SPAM_EVIDENCE DOUBLE PRECISION DEFAULT 0 NOT NULL;

-- Visibility filter on decay-aware evidence: enables
--   WHERE ACTIVE AND SPAM_EVIDENCE >= projectedThreshold(now)
-- as an index seek for the blocklist read path.
CREATE INDEX NUMBERS_SPAM_EVIDENCE_IDX ON NUMBERS (ACTIVE, SPAM_EVIDENCE DESC);
