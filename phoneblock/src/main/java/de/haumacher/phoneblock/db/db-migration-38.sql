-- Indexes for NUMBERS_HISTORY. The table only had its primary key (RMAX, PHONE),
-- which serves the snapshot self-join (p.RMAX = ? AND p.PHONE = ?) and
-- outdateHistorySnapshot (RMAX = MAX), but nothing else:
--   * Per-number history reads (getSearchHistory / getHistoryEntry) filter
--     PHONE = ? with a RANGE on RMAX/RMIN. With RMAX leading and used as a
--     range, PHONE is not seekable, so they scan the whole RMAX >= rev band
--     (which contains every still-open row, RMAX = 0x7fffffff). PHONE must lead.
--   * Revision scans (cleanRevision, getHistoryEntries, getActivityHistory)
--     filter / group by RMIN, which is in no index at all -> full table scan.
CREATE INDEX NUMBERS_HISTORY_PHONE_IDX ON NUMBERS_HISTORY (PHONE, RMIN);
CREATE INDEX NUMBERS_HISTORY_RMIN_IDX ON NUMBERS_HISTORY (RMIN);
