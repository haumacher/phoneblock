-- Drop legacy tables left over from the pre-consolidation schema (the era
-- before the unified NUMBERS / PERSONALIZATION tables). All of them are read
-- only by the pre-NUMBERS upgrade path (db-migration-02.sql), which runs
-- before NUMBERS exists and therefore never sees a database that has already
-- reached this migration. Current code keeps every signal elsewhere:
--   * SEARCHES: per-number daily search counter, superseded by
--     NUMBERS.SEARCHES / SEARCHES_CURRENT / SEARCHES_BACKUP (incSearchCount,
--     updateSearches). No runtime writer remains.
--   * SPAMREPORTS: per-number vote aggregate, superseded by NUMBERS.VOTES.
--   * OLDREPORTS: historical vote aggregate, folded into NUMBERS by
--     migration 02.
--   * BLOCKLIST / EXCLUDES: per-user blocked / allowed numbers, superseded by
--     PERSONALIZATION (BLOCKED flag); see the BlockList mapper.
--   * RATINGS: per-category rating counters, superseded by the NUMBERS
--     category columns (LEGITIMATE / PING / POLL / ADVERTISING / GAMBLE /
--     FRAUD). The only remaining reader was the JMX getRatings() metric,
--     dropped together with this table.
--   * RATINGHISTORY: per-revision rating counters, superseded by the
--     NUMBERS_HISTORY category columns.
DROP TABLE SEARCHES;
DROP TABLE SPAMREPORTS;
DROP TABLE OLDREPORTS;
DROP TABLE BLOCKLIST;
DROP TABLE EXCLUDES;
DROP TABLE RATINGS;
DROP TABLE RATINGHISTORY;

-- SEARCHCLUSTER and SEARCHHISTORY predate even the db-schema.sql baseline:
-- they were never part of a fresh install and are read only by the
-- pre-NUMBERS path (SEARCHCLUSTER -> REVISION, SEARCHHISTORY ->
-- NUMBERS_HISTORY in migration 02). They linger only in databases old enough
-- to have been migrated through that path, so use IF EXISTS — every other DB
-- never had them.
DROP TABLE IF EXISTS SEARCHCLUSTER;
DROP TABLE IF EXISTS SEARCHHISTORY;

-- SPAMREPORTS_10 / SPAMREPORTS_100 are the former names of the
-- NUMBERS_AGGREGATION_10 / NUMBERS_AGGREGATION_100 tables. The rename
-- (Issue #91, "Clarified table names") was applied in place to db-schema.sql
-- and migration 02 without an ALTER TABLE RENAME, so databases that ran the
-- pre-rename migration 02 may still carry the old names as orphans. The live
-- code only ever touches NUMBERS_AGGREGATION_*, so wherever these still
-- exist they are dead. IF EXISTS, since renamed/fresh databases lack them.
DROP TABLE IF EXISTS SPAMREPORTS_10;
DROP TABLE IF EXISTS SPAMREPORTS_100;
