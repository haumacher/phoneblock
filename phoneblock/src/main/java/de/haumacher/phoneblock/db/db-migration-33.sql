-- Consolidate call-report handling (epic #300 / issue #342 follow-up).
--
-- Two obsolete tables go:
--   * CALLREPORT: backed the never-shipped /callreport bulk-upload API.
--     The endpoint and its servlet are gone in this same change.
--   * CALLERS: existed only to derive the firstFromUser boolean that
--     gated the wildcard-implicit-evidence path. With the unified
--     reporting model (every call adds +1 heat +1 evidence; per-day
--     quota at the API gate guards against abuse) there is no
--     per-user dedup anymore — the table is write-only dead weight.
DROP TABLE CALLREPORT;
DROP TABLE CALLERS;
