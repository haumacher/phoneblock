-- Add ACTIVE as a filter column to NUMBERS_SHA1_IDX so the prefix-check
-- range scan (SpamReports.getPhoneInfosByHashPrefix, USE INDEX hint)
-- can skip inactive bucket members without a row fetch. See issue #329.
DROP INDEX NUMBERS_SHA1_IDX;
CREATE INDEX NUMBERS_SHA1_IDX ON NUMBERS (SHA1, ACTIVE, PHONE);
