-- Cover the prefix-check range aggregations: include the filter column
-- (CNT) and the projected columns (PREFIX, VOTES) so the queries in
-- SpamReports.getAggregation10/100ByHashPrefix become index-only.
-- The planner already picks AGG10/AGG100_SHA1_IDX, the cost was just
-- the per-row fetch for the SELECT columns. See issue #329.
DROP INDEX AGG10_SHA1_IDX;
CREATE INDEX AGG10_SHA1_IDX ON NUMBERS_AGGREGATION_10 (SHA1, CNT, PREFIX, VOTES);

DROP INDEX AGG100_SHA1_IDX;
CREATE INDEX AGG100_SHA1_IDX ON NUMBERS_AGGREGATION_100 (SHA1, CNT, PREFIX, VOTES);
