-- Diagnostics: dongle liveness (silence) detection. A dongle runs a daily
-- self-test that uses its auth token, so TOKENS.LASTACCESS is a heartbeat. If it
-- stops for several days the device may be decommissioned — or something happened
-- to the user. This is an ABSENCE signal (no log line), handled by
-- DongleSilenceDetector over the TOKENS table, but it reuses the notification
-- ledger + template + mail plumbing via this carrier rule.
--
-- The rule's SOURCE ('DONGLE-LIVENESS') matches no ingested signatures and its
-- regex never matches, so DiagnosticsMatcher no-ops on it; the detector reads its
-- STATE (SHADOW/LIVE — promote like any other rule), ACTOR and TEMPLATE_KEY.
INSERT INTO DIAG_TEMPLATE (TEMPLATE_KEY, LANG, SUBJECT, BODY, UPDATED) VALUES
	('help-device-silent', 'de',
	 'Meldet sich dein PhoneBlock-Dongle noch?',
	 '<p>Hallo,</p><p>dein PhoneBlock-Dongle (Geraet {deviceId}) hat sich seit mehreren Tagen nicht mehr bei PhoneBlock gemeldet.</p><p>Falls du das Geraet bewusst ausser Betrieb genommen hast, kannst du diese Nachricht ignorieren.</p><p>Andernfalls pruefe bitte, ob der Dongle mit Strom versorgt und mit deinem Heimnetz (WLAN/LAN) verbunden ist.</p><p>Viele Gruesse<br/>Dein PhoneBlock-Team</p>',
	 0);

INSERT INTO DIAG_RULE (NAME, SOURCE, MATCH_TAG, MATCH_REGEX, CATEGORY, ACTOR, MIN_DISTINCT_DAYS, MIN_EVENTS, TEMPLATE_KEY, STATE, AUTHOR, NOTES, CREATED, UPDATED) VALUES
	('Dongle silent (no contact)', 'DONGLE-LIVENESS', NULL, '(?!)', 'device-silent', 'USER', 1, 1, 'help-device-silent', 'SHADOW', 'seed', 'Heartbeat gap: no token use for several days. Detected by DongleSilenceDetector, not the signature matcher.', 0, 0);
