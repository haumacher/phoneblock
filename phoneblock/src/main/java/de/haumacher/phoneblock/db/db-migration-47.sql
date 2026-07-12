-- Diagnostics framework Phase 3/4: the hot-editable rule engine + notification
-- ledger (docs/plans/2026-07-11-diagnostics-framework-design.md). Rules classify
-- signatures and, on a persistence threshold, notify — SHADOW rules only project
-- (dry-run), LIVE+USER rules mail the user, LIVE+DEV rules feed a dev digest.

-- Detection rules. SOURCE NULL = applies to all sources. MATCH_REGEX is matched
-- (in Java) against DIAG_SIGNATURE.SIGNATURE; MATCH_TAG optionally narrows first.
-- TEMPLATE_KEY (USER actor) names a DIAG_TEMPLATE family resolved by user language.
CREATE TABLE DIAG_RULE (
	ID BIGINT NOT NULL AUTO_INCREMENT,
	NAME CHARACTER VARYING(128) DEFAULT '' NOT NULL,
	SOURCE CHARACTER VARYING(32),
	MATCH_TAG CHARACTER VARYING(64),
	MATCH_REGEX CHARACTER VARYING(512) NOT NULL,
	CATEGORY CHARACTER VARYING(64) DEFAULT '' NOT NULL,
	ACTOR CHARACTER VARYING(8) DEFAULT 'NONE' NOT NULL,
	MIN_DISTINCT_DAYS INTEGER DEFAULT 1 NOT NULL,
	MIN_EVENTS INTEGER DEFAULT 1 NOT NULL,
	TEMPLATE_KEY CHARACTER VARYING(64),
	STATE CHARACTER VARYING(8) DEFAULT 'DRAFT' NOT NULL,
	AUTHOR CHARACTER VARYING(64) DEFAULT '' NOT NULL,
	NOTES CHARACTER VARYING(1024) DEFAULT '' NOT NULL,
	CREATED BIGINT DEFAULT 0 NOT NULL,
	UPDATED BIGINT DEFAULT 0 NOT NULL,
	CONSTRAINT DIAG_RULE_PK PRIMARY KEY (ID)
);

CREATE INDEX DIAG_RULE_STATE_IDX ON DIAG_RULE (STATE);

-- Mail copy as data (safe placeholder substitution only). One row per
-- (TEMPLATE_KEY, LANG); the notifier picks the user's language.
CREATE TABLE DIAG_TEMPLATE (
	ID BIGINT NOT NULL AUTO_INCREMENT,
	TEMPLATE_KEY CHARACTER VARYING(64) NOT NULL,
	LANG CHARACTER VARYING(8) DEFAULT 'de' NOT NULL,
	SUBJECT CHARACTER VARYING(256) DEFAULT '' NOT NULL,
	BODY CHARACTER VARYING(8192) DEFAULT '' NOT NULL,
	UPDATED BIGINT DEFAULT 0 NOT NULL,
	CONSTRAINT DIAG_TEMPLATE_PK PRIMARY KEY (ID),
	CONSTRAINT DIAG_TEMPLATE_KEY_UQ UNIQUE (TEMPLATE_KEY, LANG)
);

-- Notification ledger: idempotency (one-shot latch per origin+rule), audit and
-- dry-run projection. A row is PENDING (matched, mail not yet/ never sent for
-- SHADOW), SENT (mailed), CLEARED (signature went quiet -> rearmed) or SUPPRESSED
-- (blocked by a cap / kill switch).
CREATE TABLE DIAG_NOTIFICATION (
	ID BIGINT NOT NULL AUTO_INCREMENT,
	SOURCE CHARACTER VARYING(32) NOT NULL,
	ORIGIN_ID CHARACTER VARYING(128) NOT NULL,
	USER_ID CHARACTER VARYING(64),
	RULE_ID BIGINT NOT NULL,
	STATE CHARACTER VARYING(12) DEFAULT 'PENDING' NOT NULL,
	DRY_RUN BOOLEAN DEFAULT FALSE NOT NULL,
	FIRST_MATCHED BIGINT DEFAULT 0 NOT NULL,
	SENT_AT BIGINT,
	CLEARED_AT BIGINT,
	CONSTRAINT DIAG_NOTIFICATION_PK PRIMARY KEY (ID)
);

CREATE INDEX DIAG_NOTIFICATION_LATCH_IDX ON DIAG_NOTIFICATION (RULE_ID, ORIGIN_ID, STATE);
CREATE INDEX DIAG_NOTIFICATION_SENT_IDX ON DIAG_NOTIFICATION (STATE, SENT_AT);

-- Bumped on any rule/template write so the matcher reloads its in-memory rule set
-- without a redeploy; the mail kill switch defaults OFF (no user mail until a
-- human enables it, on top of per-rule LIVE promotion).
INSERT INTO PROPERTIES (NAME, VAL) VALUES('diag.ruleset.version', '1');
INSERT INTO PROPERTIES (NAME, VAL) VALUES('diag.mail.enabled', 'false');

-- Seed the initial DONGLE rule set from the design appendix, all in SHADOW so
-- they only classify/project until a human promotes them. Matches operate on the
-- normalized signature (numbers are already <N>).
INSERT INTO DIAG_TEMPLATE (TEMPLATE_KEY, LANG, SUBJECT, BODY, UPDATED) VALUES
	('help-register-rejected', 'de',
	 'Dein PhoneBlock-Dongle kann sich nicht anmelden',
	 '<p>Hallo,</p><p>dein PhoneBlock-Dongle (Geraet {deviceId}) versucht seit mehreren Tagen vergeblich, sich bei deiner Fritz!Box anzumelden - die Anmeldung (SIP REGISTER) wird abgelehnt.</p><p>Bitte pruefe in der Fritz!Box unter "Telefonie / Telefoniegeraete" den Benutzernamen und die Nebenstelle des IP-Telefons, das du fuer PhoneBlock eingerichtet hast.</p><p>Viele Gruesse<br/>Dein PhoneBlock-Team</p>',
	 0);

INSERT INTO DIAG_RULE (NAME, SOURCE, MATCH_TAG, MATCH_REGEX, CATEGORY, ACTOR, MIN_DISTINCT_DAYS, MIN_EVENTS, TEMPLATE_KEY, STATE, AUTHOR, NOTES, CREATED, UPDATED) VALUES
	('SIP registration rejected', 'DONGLE', 'sip', 'REGISTER rejected', 'user-install-sip', 'USER', 2, 5, 'help-register-rejected', 'SHADOW', 'seed', 'Wrong SIP user / extension in the Fritz!Box.', 0, 0),
	('rate API rejects submissions', 'DONGLE', 'api', 'rate: HTTP', 'firmware-bug', 'DEV', 1, 10, NULL, 'SHADOW', 'seed', 'See issue #469 (wildcards / non-E.164 to /api/rate).', 0, 0),
	('WiFi transient disconnects', 'DONGLE', 'wifi', 'disconnected', 'environmental', 'NONE', 1, 1, NULL, 'SHADOW', 'seed', 'Noisy RF/environment; classify only.', 0, 0);
