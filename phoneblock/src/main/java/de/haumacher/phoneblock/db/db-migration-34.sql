-- Decay-only visibility (epic #300): drop the soft-delete ACTIVE column.
--
-- With the snapshot-driven version sweep (#342) and the decay-aware
-- visibility filter `(SPAM_EVIDENCE - LEGIT_EVIDENCE) >= maxRawSpam`,
-- the ACTIVE flag has nothing left to add — every row that was archived
-- via archiveByHeatAndEvidenceBelow is also far below the visibility
-- floor, so the WHERE ACTIVE predicate was redundant.
--
-- The archive sweep (archiveByHeatAndEvidenceBelow / archiveOldReports)
-- is removed; hard-delete of decay-faded rows is the subject of #341.
-- Until then, rows just stay in NUMBERS / NUMBERS_HISTORY and the decay
-- filter handles all visibility decisions.
DROP INDEX NUMBERS_SPAM_EVIDENCE_IDX;
DROP INDEX NUMBERS_SHA1_IDX;
DROP INDEX NUMBERS_ACTIVE_IDX;

ALTER TABLE NUMBERS DROP COLUMN ACTIVE;
ALTER TABLE NUMBERS_HISTORY DROP COLUMN ACTIVE;

-- Re-create the two indexes that referenced ACTIVE, now without it.
CREATE INDEX NUMBERS_SHA1_IDX ON NUMBERS (SHA1, PHONE);
CREATE INDEX NUMBERS_SPAM_EVIDENCE_IDX ON NUMBERS (SPAM_EVIDENCE DESC);
