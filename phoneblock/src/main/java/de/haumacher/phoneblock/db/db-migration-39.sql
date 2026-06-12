-- Publication state moves out of NUMBERS into the narrow BLOCKLIST table
-- (#342). The per-row VERSION / PUBLISHED_* columns made every publication
-- sweep rewrite scattered rows (plus NUMBERS_VERSION_IDX entries) of the
-- big table — the H2 MVStore could never reclaim the dead pages fast
-- enough. VOTES is the bucket floor (2, 4, 10, 20, 50, 100) of the net
-- evidence, frozen at publication; 0 marks a tombstone (removal signal for
-- incremental sync).
--
-- A Java hook seeds BLOCKLIST from the current published state (the bucket
-- thresholds are EMA projections of the migration moment and cannot be
-- expressed as SQL constants) and afterwards drops NUMBERS_VERSION_IDX and
-- the NUMBERS columns VERSION, PUBLISHED_LASTPING, PUBLISHED_SPAM_EVIDENCE,
-- PUBLISHED_LEGIT_EVIDENCE.

CREATE TABLE BLOCKLIST (
	PHONE CHARACTER VARYING(100) NOT NULL,
	VOTES INTEGER NOT NULL,
	LASTPING BIGINT DEFAULT 0 NOT NULL,
	UPDATED BIGINT DEFAULT 0 NOT NULL,
	VERSION BIGINT NOT NULL,
	CONSTRAINT BLOCKLIST_PK PRIMARY KEY (PHONE)
);

CREATE INDEX BLOCKLIST_VERSION_IDX ON BLOCKLIST (VERSION, PHONE);

-- The bucket-based sweep is a pure state comparison (live bucket vs.
-- published bucket); the last-sweep timestamp is no longer needed.
DELETE FROM PROPERTIES WHERE NAME = 'blocklist.lastAssignTime';
