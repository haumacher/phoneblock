-- Stop letting H2 assign REVISION.ID. The IDENTITY sequence advances even when
-- the inserting transaction rolls back, so a failed history-snapshot attempt
-- left a gap in the id space. The watermark logic (getRevisionDate(newRevId-1))
-- then read a non-existent revision, collapsed lastSnapshot to 0 and
-- re-snapshotted the whole corpus on every following run. The application now
-- assigns the id as max(ID) + 1, so the column becomes a plain integer.
ALTER TABLE REVISION ALTER COLUMN ID DROP IDENTITY;
