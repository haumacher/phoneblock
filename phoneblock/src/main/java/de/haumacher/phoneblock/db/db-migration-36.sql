-- Index for the status page's top-spammers list
-- (SpamReports.getTopSpammers): ORDER BY VOTES DESC LIMIT n. Without it H2
-- full-scans NUMBERS and sorts every row on each status-page load; with the
-- VOTES DESC index it reads only the top n rows ("index sorted").
CREATE INDEX NUMBERS_VOTES_IDX ON NUMBERS (VOTES DESC);
