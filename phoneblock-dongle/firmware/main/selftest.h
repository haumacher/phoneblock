#pragma once

// Daily token health-check. Spawns a background task that re-runs
// phoneblock_selftest() once every 24 h, so a token that gets
// revoked or rotated long after setup shows up in the dashboard's
// error list (via stats_record_error) before the next real call.
//
// The boot-time check stays a synchronous phoneblock_selftest()
// call from app_main, and the manual web-UI test button keeps
// hitting phoneblock_selftest() directly — the task only adds the
// recurring trigger.

void selftest_start(void);
