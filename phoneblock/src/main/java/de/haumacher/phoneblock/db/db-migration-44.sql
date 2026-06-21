-- Per-user spam-evidence cap: each user may add at most one decoded unit of
-- SPAM_EVIDENCE (resp. LEGIT_EVIDENCE) to any single number, no matter how many
-- calls they intercept from it. The user's contribution lives on the
-- PERSONALIZATION row and is fully described by the time of its last spam/legit
-- activity: the stored contribution is increment(1, LAST_ACTIVITY). An
-- intercepted call tops the decoded contribution back up to 1 (adding only the
-- part decayed since LAST_ACTIVITY); removal subtracts the residual.
ALTER TABLE PERSONALIZATION ADD COLUMN LAST_ACTIVITY BIGINT DEFAULT 0 NOT NULL;

-- Forward-only seed: existing rows did not track activity, so anchor it at the
-- creation time. This under-estimates the residual subtracted on later removal
-- for rows that were topped up by calls before this migration; that is accepted.
UPDATE PERSONALIZATION SET LAST_ACTIVITY = CREATED;
