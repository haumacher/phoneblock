-- Diagnostics framework: hot-editable anonymizer rules (DIAG_SCRUB_RULE).
-- The built-in Scrubber rule set stays the always-on baseline; LIVE rows here are
-- layered on top so an agent can grow the anonymizer (via POST /api/admin/diag/scrub,
-- informed by POST /api/admin/diag/scrub/audit) without a server redeploy. The built-in
-- baseline masks the high-confidence PII shapes; three signature-collapse rules
-- (validated against production logs, see below) are seeded LIVE.
CREATE TABLE DIAG_SCRUB_RULE (
	ID BIGINT NOT NULL AUTO_INCREMENT,
	NAME CHARACTER VARYING(128) DEFAULT '' NOT NULL,
	SOURCE CHARACTER VARYING(32),
	PATTERN CHARACTER VARYING(512) NOT NULL,
	REPLACEMENT CHARACTER VARYING(128) DEFAULT '' NOT NULL,
	APPLIES_TO CHARACTER VARYING(10) DEFAULT 'BOTH' NOT NULL,
	STATE CHARACTER VARYING(8) DEFAULT 'DRAFT' NOT NULL,
	VERSION INTEGER DEFAULT 1 NOT NULL,
	AUTHOR CHARACTER VARYING(64) DEFAULT '' NOT NULL,
	UPDATED BIGINT DEFAULT 0 NOT NULL,
	CONSTRAINT DIAG_SCRUB_RULE_PK PRIMARY KEY (ID)
);

CREATE INDEX DIAG_SCRUB_RULE_STATE_IDX ON DIAG_SCRUB_RULE (STATE);

-- Seeded signature-collapse rules, validated via POST /api/admin/diag/scrub/audit against
-- production logs. Each folds a variable tail that otherwise fragments one error across many
-- signatures; diag-addressbook-path (BOTH) also masks a URL-encoded email the baseline misses.
INSERT INTO DIAG_SCRUB_RULE (NAME, SOURCE, PATTERN, REPLACEMENT, APPLIES_TO, STATE) VALUES ('diag-dyndns-host', 'SERVER', '(wrong password \(\d+ characters\): ).*', '$1<DYNDNS-HOST>', 'SIGNATURE', 'LIVE');
INSERT INTO DIAG_SCRUB_RULE (NAME, SOURCE, PATTERN, REPLACEMENT, APPLIES_TO, STATE) VALUES ('diag-addressbook-path', 'SERVER', '(/phoneblock/contacts/addresses/)[^/]+/[^''\s]*', '$1<BOOK>/<CARD>', 'BOTH', 'LIVE');
INSERT INTO DIAG_SCRUB_RULE (NAME, SOURCE, PATTERN, REPLACEMENT, APPLIES_TO, STATE) VALUES ('diag-address-card', 'SERVER', '(Prevent deleting card: ).*', '$1<CARD>', 'BOTH', 'LIVE');
