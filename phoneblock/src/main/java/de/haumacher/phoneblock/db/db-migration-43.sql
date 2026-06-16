-- Denormalize the published rating into BLOCKLIST so the incremental-sync and
-- full-sync reads (getBlocklistChangesSince / getBlocklist) no longer LEFT JOIN
-- the wide NUMBERS table once per row. A blocklist API entry carries only a
-- single rating (the dominant category) plus the last-activity timestamp, so we
-- store exactly those two columns -- not the seven raw category counters. Both
-- are frozen at publication time (written by the publication sweep), matching
-- the already-frozen VOTES bucket.
ALTER TABLE BLOCKLIST ADD CATEGORY CHARACTER VARYING(15) DEFAULT 'B_MISSED' NOT NULL;
ALTER TABLE BLOCKLIST ADD LASTPING BIGINT DEFAULT 0 NOT NULL;

-- One-time backfill from NUMBERS, reproducing DB.dominantCategory: the argmax
-- over the category counters with tie priority FRAUD > GAMBLE > ADVERTISING >
-- POLL > PING, falling back to B_MISSED when every counter is zero. The all-zero
-- guard must come first -- otherwise FRAUD >= every-other-zero would wrongly win
-- 'G_FRAUD'. Tombstones (no NUMBERS row) keep the column defaults.
UPDATE BLOCKLIST b SET
  LASTPING = COALESCE((select s.LASTPING from NUMBERS s where s.PHONE = b.PHONE), 0),
  CATEGORY = COALESCE((select
      CASE WHEN s.FRAUD = 0 AND s.GAMBLE = 0 AND s.ADVERTISING = 0 AND s.POLL = 0 AND s.PING = 0 THEN 'B_MISSED'
           WHEN s.FRAUD       >= s.GAMBLE AND s.FRAUD >= s.ADVERTISING AND s.FRAUD >= s.POLL AND s.FRAUD >= s.PING THEN 'G_FRAUD'
           WHEN s.GAMBLE      >= s.ADVERTISING AND s.GAMBLE >= s.POLL AND s.GAMBLE >= s.PING THEN 'F_GAMBLE'
           WHEN s.ADVERTISING >= s.POLL AND s.ADVERTISING >= s.PING THEN 'E_ADVERTISING'
           WHEN s.POLL        >= s.PING THEN 'D_POLL'
           ELSE 'C_PING' END
      from NUMBERS s where s.PHONE = b.PHONE), 'B_MISSED')
where exists (select 1 from NUMBERS s where s.PHONE = b.PHONE);
