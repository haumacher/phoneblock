-- Confidence model (issue #331 / epic #300): two decaying axes per number.
-- HEAT measures activity ("how active right now?"); SPAM_EVIDENCE and
-- LEGIT_EVIDENCE drive classification ("spam or legitimate?"). All three are
-- stored as projected EMAs: each cell holds Σ wᵢ·exp((tᵢ − t0)/τ), so writes
-- are pure additions and ranking by the raw column matches ranking by the
-- decoded value. No behaviour change yet — columns start at 0 and become
-- meaningful once signals are wired in (#332).
ALTER TABLE NUMBERS ADD COLUMN HEAT DOUBLE PRECISION DEFAULT 0 NOT NULL;
ALTER TABLE NUMBERS ADD COLUMN SPAM_EVIDENCE DOUBLE PRECISION DEFAULT 0 NOT NULL;
ALTER TABLE NUMBERS ADD COLUMN LEGIT_EVIDENCE DOUBLE PRECISION DEFAULT 0 NOT NULL;

-- Index-backed ranking for the space-limited Heat-ordered blocklist (#336).
-- DESC matches the read pattern (hottest first); no EXP() needed in queries
-- because all rows share the same decay factor at read time.
CREATE INDEX NUMBERS_HEAT_IDX ON NUMBERS (HEAT DESC);
