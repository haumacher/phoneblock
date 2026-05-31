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
DROP TABLE SEARCHES;
DROP TABLE SPAMREPORTS;
DROP TABLE OLDREPORTS;
DROP TABLE BLOCKLIST;
DROP TABLE EXCLUDES;
