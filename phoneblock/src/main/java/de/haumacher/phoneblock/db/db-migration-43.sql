-- Per-subject rate-limit counters for expensive API calls (blocklist download,
-- CardDAV synchronization, number lookups). Until now the "max once per
-- month / day" limit was only documented, never enforced.
--
-- Each row is a fixed-window counter for one (subject, bucket) pair. The
-- subject is either an API token (SUBJECT_KIND=1, SUBJECT_ID=TOKENS.ID) so that
-- every API key gets its own budget, or the user account (SUBJECT_KIND=0,
-- SUBJECT_ID=USERS.ID) when the call is authenticated by account password
-- (CardDAV from a FRITZ!Box carries no persistent token). No foreign key: the
-- subject references one of two tables, so the rows are removed explicitly when
-- a token or account is deleted.
CREATE TABLE API_QUOTA (
	SUBJECT_KIND TINYINT NOT NULL,
	SUBJECT_ID BIGINT NOT NULL,
	BUCKET TINYINT NOT NULL,
	QUOTA_COUNT INTEGER DEFAULT 0 NOT NULL,
	QUOTA_TIME BIGINT DEFAULT 0 NOT NULL,
	CONSTRAINT API_QUOTA_PK PRIMARY KEY (SUBJECT_KIND, SUBJECT_ID, BUCKET)
);
