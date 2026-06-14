-- Add a discriminator flag distinguishing prefix-wildcard personalizations
-- (#377) from exact-number entries. A wildcard row stores the bare prefix in
-- PHONE (no '*'), has a null SHA1, and is matched by prefix on the device.
-- WILDCARD is a plain attribute, not part of the key: a digit string is either
-- exact or a prefix for a given user, never both.
ALTER TABLE PERSONALIZATION ADD COLUMN WILDCARD BOOLEAN DEFAULT false NOT NULL;

-- Index for the per-user wildcard list (e.g. the personalization sync) so it is
-- an index range scan instead of a 'PHONE LIKE' suffix scan.
CREATE INDEX PERSONALIZATION_WILDCARD_IDX ON PERSONALIZATION (USERID, WILDCARD, PHONE);
