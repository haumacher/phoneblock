#pragma once

// Daily token health-check. Re-runs phoneblock_selftest() once every
// 24 h (driven by the shared scheduler task — see scheduler.c), so a
// token that gets revoked or rotated long after setup shows up in the
// dashboard's error list (via stats_record_error) before the next real
// call. Also flushes any new WARN/ERROR log lines to the server.
//
// The boot-time check stays a synchronous phoneblock_selftest() call
// from app_main, and the manual web-UI test button keeps hitting
// phoneblock_selftest() directly.

// One scheduled self-test pass: token check + log-report flush. Skips
// when no PhoneBlock token is configured. Called by the scheduler.
void selftest_run(void);
