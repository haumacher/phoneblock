-- Diagnostics framework Phase 2: two new AuthToken capabilities gating the
-- diagnostics REST API. accessDiagnostics = read / dry-run / author up to SHADOW;
-- accessAdmin = the elevated capability (e.g. promoting a rule to LIVE). Both are
-- minted by a direct DB update on an ordinary token (no UI to create one).
ALTER TABLE TOKENS ADD COLUMN ACCESS_DIAGNOSTICS BOOLEAN DEFAULT false NOT NULL;
ALTER TABLE TOKENS ADD COLUMN ACCESS_ADMIN BOOLEAN DEFAULT false NOT NULL;
