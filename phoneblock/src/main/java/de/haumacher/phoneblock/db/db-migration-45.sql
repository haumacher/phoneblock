-- Replace the snapshot-and-diff search/activity history with a per-(number, day)
-- activity ledger that stores the daily increments directly.
--
-- The old model mirrored the cumulative NUMBERS counters into NUMBERS_HISTORY
-- snapshots (only for numbers changed since the last revision) and reconstructed
-- per-day activity by differencing two snapshots. That made every chart depend on
-- a surviving baseline snapshot, which the LASTPING-filtered snapshotting and the
-- equal-length retention window did not guarantee: the 30-day activity chart lost
-- first-appearance / sparse-number activity, and the hand-rotated
-- SEARCHES_CURRENT / SEARCHES_BACKUP "today and yesterday" counters froze for
-- numbers that went quiet (their rotation only ran while LASTPING kept advancing).
--
-- NUMBER_ACTIVITY stores what the pages display: one row per (number, UTC day)
-- holding that day's search / call / vote increments. No baseline, no differencing,
-- no rotation. DAY is the UTC epoch-day (floor(epochMillis / 86400000)). Retention
-- is a single date-bound delete (see DB.runActivityRetention) — deltas are
-- self-contained, so nothing has to be preserved as a baseline.
-- EPOCH_DAY, not DAY: DAY is a reserved keyword in H2 2.4.
CREATE TABLE NUMBER_ACTIVITY (
	PHONE CHARACTER VARYING(100) NOT NULL,
	EPOCH_DAY INTEGER NOT NULL,
	SEARCHES INTEGER DEFAULT 0 NOT NULL,
	CALLS INTEGER DEFAULT 0 NOT NULL,
	VOTES INTEGER DEFAULT 0 NOT NULL,
	CONSTRAINT NUMBER_ACTIVITY_PK PRIMARY KEY (PHONE, EPOCH_DAY)
);

-- (EPOCH_DAY, PHONE): the global activity chart and the top-searches list both
-- aggregate by day over a recent window, so the day-leading index turns those into
-- an ordered range scan instead of a full-table group-by. The PHONE primary key
-- still serves the per-number history read (all days of one number).
CREATE INDEX NUMBER_ACTIVITY_DAY_IDX ON NUMBER_ACTIVITY (EPOCH_DAY, PHONE);

-- Retire the snapshot-and-diff machinery.
DROP INDEX NUMBERS_HISTORY_PHONE_IDX;
DROP INDEX NUMBERS_HISTORY_RMIN_IDX;
DROP TABLE NUMBERS_HISTORY;
DROP TABLE REVISION;

-- Retire the hand-rotated "today and yesterday" search counters; superseded by
-- summing NUMBER_ACTIVITY over the last two days.
DROP INDEX NUMBERS_SEARCHES_CURRENT_IDX;
ALTER TABLE NUMBERS DROP COLUMN SEARCHES_CURRENT;
ALTER TABLE NUMBERS DROP COLUMN SEARCHES_BACKUP;
