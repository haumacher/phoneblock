-- Spread×mass block-spam gate (#300 follow-up). The /100 aggregation now records, instead of the
-- old vote-promotion counters, how the spam is distributed across its /10 sub-blocks:
--   MEMBERS = total currently-spam numbers in the /100,
--   TENS    = number of its /10 sub-blocks that have at least SPREAD_TEN_CONTRIB such numbers.
-- A /100 is a spam block when MEMBERS >= SPREAD_MIN_NUMBERS and TENS >= SPREAD_MIN_TENS, so a
-- spammer spreading 1-2 numbers per /10 across the block is caught, while a single dense /10 (one
-- sub-block) never lifts its /100. NUMBERS_AGGREGATION_10.CNT keeps meaning "current spam members
-- of the /10" (now counted at the >= 2 displayed-votes membership threshold).
ALTER TABLE NUMBERS_AGGREGATION_100 ADD COLUMN MEMBERS INTEGER DEFAULT 0 NOT NULL;
ALTER TABLE NUMBERS_AGGREGATION_100 ADD COLUMN TENS INTEGER DEFAULT 0 NOT NULL;

-- The VOTES column is dead: the aggregation is rebuilt from the current NUMBERS evidence and the
-- magnitude is the decoded SPAM_EVIDENCE, not a stored vote sum. Drop it (and rebuild the SHA1
-- indexes without it). DROP INDEX first — H2 will not drop a column that an index references.
DROP INDEX AGG10_SHA1_IDX;
DROP INDEX AGG100_SHA1_IDX;
ALTER TABLE NUMBERS_AGGREGATION_10 DROP COLUMN VOTES;
ALTER TABLE NUMBERS_AGGREGATION_100 DROP COLUMN VOTES;
CREATE INDEX AGG10_SHA1_IDX ON NUMBERS_AGGREGATION_10 (SHA1, CNT, PREFIX);
CREATE INDEX AGG100_SHA1_IDX ON NUMBERS_AGGREGATION_100 (SHA1, CNT, PREFIX);
