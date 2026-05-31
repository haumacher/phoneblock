-- Region-aware Heat (issue #340 / epic #300): the space-limited blocklist
-- composition (#336) must be driven by the region from which spam reports,
-- searches, and votes originate. A US number heating up among US victims
-- should not push numbers off the German top-N. NUMBERS.HEAT remains the
-- global archive gate; NUMBERS_LOCALE.HEAT is the per-DIAL signal the
-- regional blocklist orders by.
ALTER TABLE NUMBERS_LOCALE
	ADD COLUMN HEAT DOUBLE PRECISION DEFAULT 0 NOT NULL;

-- (DIAL, HEAT DESC): equality column first so a fixed-dial top-N is one
-- seek plus an ordered range scan within the slice. The reverse order
-- would force a global Heat scan that filters by DIAL row-by-row — bad
-- for sparse dials.
CREATE INDEX NUMBERS_LOCALE_HEAT_IDX ON NUMBERS_LOCALE (DIAL, HEAT DESC);

-- Backfill from existing counters runs in Java (DB.backfillNumbersLocaleHeat)
-- so the signal weights and τ come from one place (Signals / Ema). Treats
-- each row's events as if they all happened at LASTACCESS, projected to
-- the EMA reference epoch — same rationale as migration 29 for the global
-- EMAs. This file exists so the migration loop sees a script for version 30.
SELECT 1;
