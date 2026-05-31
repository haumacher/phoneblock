-- Drop two legacy tables left over from the pre-consolidation schema
-- (the era before the unified NUMBERS table). Both are read only by the
-- pre-NUMBERS upgrade path (db-migration-02.sql), which runs before NUMBERS
-- exists and therefore never sees a database that has already reached this
-- migration. Current code keeps every signal on NUMBERS instead:
--   * SEARCHES: per-number daily search counter, superseded by
--     NUMBERS.SEARCHES / SEARCHES_CURRENT / SEARCHES_BACKUP (incSearchCount,
--     updateSearches). No runtime writer remains.
--   * SPAMREPORTS: per-number vote aggregate, superseded by NUMBERS.VOTES.
DROP TABLE SEARCHES;
DROP TABLE SPAMREPORTS;
