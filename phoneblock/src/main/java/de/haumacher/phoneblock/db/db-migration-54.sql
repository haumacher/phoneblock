-- Partner report exchange (docs/plans/2026-11-23-partner-report-exchange-design.md).
--
-- EXTERNAL_REPORTS is the ledger of spam reports imported from a partner service (e.g. 'nize'):
-- (SOURCE, ID) deduplicates repeated or overlapping import windows, SENDER (the partner's
-- anonymized reporter id) is kept for later analysis, e.g. a per-sender cap, and the ledger allows
-- reverting a source's votes should the exchange end. Reports that are not counted are not stored.
CREATE TABLE EXTERNAL_REPORTS (
	SOURCE CHARACTER VARYING(30) NOT NULL,
	ID CHARACTER VARYING(64) NOT NULL,
	PHONE CHARACTER VARYING(100) NOT NULL,
	SENDER CHARACTER VARYING(64),
	CREATED BIGINT NOT NULL,
	RATING CHARACTER VARYING(15) NOT NULL,
	CONSTRAINT EXTERNAL_REPORTS_PK PRIMARY KEY (SOURCE, ID)
);
CREATE INDEX EXTERNAL_REPORTS_SENDER_IDX ON EXTERNAL_REPORTS (SOURCE, SENDER, PHONE);

-- The partner export (GET /api/partner/reports) selects the blocked personal block-list entries
-- created in a time window.
CREATE INDEX PERSONALIZATION_CREATED_IDX ON PERSONALIZATION (CREATED);

-- Token capability for the partner export. Minted by a direct DB update on an ordinary token (no
-- UI to create one), like ACCESS_DIAGNOSTICS / ACCESS_ADMIN.
ALTER TABLE TOKENS ADD COLUMN ACCESS_PARTNER BOOLEAN DEFAULT false NOT NULL;
