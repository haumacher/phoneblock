-- Index supporting the status page's "newly blocked" list
-- (SpamReports.getLatestBlocklistEntries): ORDER BY ADDED DESC LIMIT 10 over
-- the visibility filter. Before the ACTIVE flag was dropped (migration 34) the
-- query restricted to active rows via NUMBERS_ACTIVE_IDX before sorting; with
-- that pre-filter gone the query degenerated into a full-table scan plus sort.
-- An ADDED-ordered index lets the engine walk newest-first and stop after the
-- first ten rows that pass the (SPAM_EVIDENCE - LEGIT_EVIDENCE) filter.
CREATE INDEX NUMBERS_ADDED_IDX ON NUMBERS (ADDED DESC);
