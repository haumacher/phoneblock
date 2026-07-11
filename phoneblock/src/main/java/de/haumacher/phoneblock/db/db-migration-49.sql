-- Diagnostics: security rule for a dongle whose web UI is reachable from the
-- internet. The tell is the embedded HTTP server receiving malformed requests
-- (parse_block / "Bad request syntax") — scanner garbage a normal LAN browser
-- never sends. The specific probed path (/boaform, /shelly, /HNAP1, …) is masked
-- to <ARG> in the signature, and matching plain "method not allowed" would
-- false-positive on a LAN browser's favicon request, so we key on the malformed
-- markers instead (near-zero false positive; all exposed devices exhibit them).
--
-- Unlike the persistence-based rules this fires on FIRST detection
-- (min_distinct_days = 1, min_events = 1): an exposed dongle is under active
-- attack and must be warned as soon as possible, not after days. Ships in SHADOW
-- like the others (promote to LIVE + set diag.mail.enabled to actually mail).
INSERT INTO DIAG_TEMPLATE (TEMPLATE_KEY, LANG, SUBJECT, BODY, UPDATED) VALUES
	('help-internet-exposed', 'de',
	 'Sicherheitshinweis: Dein PhoneBlock-Dongle ist aus dem Internet erreichbar',
	 '<p>Hallo,</p><p>wir haben festgestellt, dass die Weboberflaeche deines PhoneBlock-Dongles (Geraet {deviceId}) aus dem Internet erreichbar ist: Das Geraet erhaelt automatisierte Anfragen von Schwachstellen-Scannern aus dem Netz.</p><p><strong>Das ist ein Sicherheitsrisiko.</strong> Der Dongle ist fuer den Betrieb im Heimnetz gedacht und muss nicht aus dem Internet erreichbar sein.</p><p>Bitte pruefe die Einstellungen deines Routers (z. B. Fritz!Box) und entferne eine eventuell eingerichtete Portfreigabe bzw. "Exposed Host"/DMZ-Regel, die auf den Dongle zeigt.</p><p>Viele Gruesse<br/>Dein PhoneBlock-Team</p>',
	 0);

INSERT INTO DIAG_RULE (NAME, SOURCE, MATCH_TAG, MATCH_REGEX, CATEGORY, ACTOR, MIN_DISTINCT_DAYS, MIN_EVENTS, TEMPLATE_KEY, STATE, AUTHOR, NOTES, CREATED, UPDATED) VALUES
	('Dongle web UI internet-exposed', 'DONGLE', NULL, 'parse_block|Bad request syntax', 'security-exposed', 'USER', 1, 1, 'help-internet-exposed', 'SHADOW', 'seed', 'Embedded HTTP server receiving scanner garbage -> reachable from the internet. Fires on first detection.', 0, 0);
