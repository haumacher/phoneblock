-- Snapshot-driven blocklist versioning (issue #342 / epic #300): the
-- BlocklistVersionService sweep is now the single source of VERSION bumps.
-- Visibility-class flips between the current state and the last published
-- snapshot are detected purely in SQL — no event-driven PENDING_UPDATE flag,
-- no crossesThreshold trigger on the vote write path. Decay-induced flips
-- (a number that decays below the floor over time, with no new votes) now
-- show up in ?since=N diffs in the same release cycle as event-induced
-- flips.

-- Carry the legit-evidence snapshot alongside the spam-evidence snapshot so
-- the visibility check can use the same net-evidence semantic the live
-- filter uses since #342 step 4.
ALTER TABLE NUMBERS
	ADD COLUMN PUBLISHED_LEGIT_EVIDENCE DOUBLE PRECISION DEFAULT 0 NOT NULL;

-- PENDING_UPDATE is dropped by the Java hook in DB.setupSchema (v32) after
-- the LEGIT_EVIDENCE snapshot has been seeded and after the codebase that
-- referenced it is gone. The matching index goes with it.
DROP INDEX NUMBERS_PENDING_UPDATE_IDX;
