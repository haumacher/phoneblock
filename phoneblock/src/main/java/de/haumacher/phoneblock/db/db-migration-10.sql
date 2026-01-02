-- Migration 10: Add index on USERID and PHONE for efficient comment lookup
--
-- This ensures efficient lookup of existing user comments when replacing them.
-- USERID is the leading column to support queries for all comments by a user.
-- The index also helps with the deleteUserComment(userId, phone) operation.
-- Note: We cannot create a UNIQUE index because anonymous comments have NULL USERID,
-- and H2 doesn't support partial unique indexes (WHERE USERID IS NOT NULL).
-- The application logic enforces the one-comment-per-user-per-number constraint.

CREATE INDEX COMMENTS_USER_PHONE_IDX ON COMMENTS (USERID, PHONE);
