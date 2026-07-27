#pragma once

// Heap-corruption sentinel.
//
// Why this exists: firmware 1.5.0 and 1.5.1 both crashed in tlsf_walk_pool,
// reached from heap_caps_get_largest_free_block() while httpd served
// GET /api/status. That code only *reads* heap metadata — it was the first
// thing to walk an already-corrupted heap, so the backtrace names the victim
// and never the culprit. Two things made those dumps nearly useless:
//
//   * detection was unbounded in time — the smashed block sat there until
//     somebody happened to open the dashboard, by which point the payload
//     that identified the writer had long been freed and reused; and
//   * the core dump holds only task stacks and TCBs, so the heap itself —
//     the actual evidence — is absent. Capturing all of DRAM is not an
//     option either: the coredump partition is 52 KB wedged in the alignment
//     gap before ota_0, and enlarging it means a USB reflash of every dongle
//     in the field (see partitions.csv).
//
// So this sentinel walks the heap every few seconds, and when it finds a
// block whose header can no longer be true it copies the bytes *around* that
// block onto its own stack before deliberately panicking. Task stacks are
// what a core dump preserves, so the evidence rides out in the dump — headed
// by the ASCII marker "PBHEAPGUARD1" and a one-line summary, both findable
// with `strings` alone. The panic is not a cost: a dongle with a corrupted
// heap is already doomed and meanwhile answers calls from an undefined state,
// and the reboot reinitialises the heap. What the sentinel buys is *when* the
// crash happens and what it carries.
//
// The walk is deliberately cheap and allocates nothing, so it is safe to run
// often. It runs from an esp_timer callback rather than the scheduler task,
// which blocks for minutes inside OTA downloads and SMTP sends — exactly the
// heap-heavy moments that most need covering.

#include <stdbool.h>
#include <stddef.h>

// Validate the predicate against the (healthy) heap and, if it holds, arm the
// periodic sentinel. Refuses to arm if a healthy heap trips the predicate —
// that would mean the assumption is wrong rather than the heap, and arming
// would reboot working dongles every few seconds. Logs the measured walk
// duration and block count either way, which is how the real interrupts-off
// cost gets reported from the field instead of estimated here.
//
// Call after crashreport_upload_async(), so a dump from the previous boot gets
// its chance to upload before this can produce another.
void heap_guard_start(void);

// Leave a breadcrumb naming an operation, so a capture can say what the dongle
// was doing in the seconds before the heap went bad — which is the attribution
// that turns "the heap is corrupt" into "audit this subsystem". Call it freely
// around anything that parses external data into heap buffers; it costs a
// pointer store and a timestamp, takes no lock and cannot fail.
//
// The last few notes are kept, not just one, so a frequent caller (the
// dashboard polling /api/status) cannot mask a rarer, more interesting one.
//
// `what` must be a string literal or otherwise outlive the device — only the
// pointer is kept, never a copy, and it is read much later from a core dump.
void heap_guard_note(const char *what);

#if CONFIG_CRASH_TEST_ENABLE
// Prove the sentinel actually fires: stage a real overflow across two of this
// function's own adjacent allocations, check that the walk catches it, put the
// bytes back and check the heap reads clean again. Writes a one-line outcome
// into `out` and returns true only if both halves held.
//
// This is the one part of the sentinel the host tests cannot reach — they cover
// the predicate, not the integration with heap_caps_walk_all(). Run it on the
// bench after any change to the walk.
//
// On demand rather than at boot on purpose: app_main cancels OTA rollback long
// before the sentinel is armed, so a rehearsal that panicked inside its briefly
// corrupted window would leave a committed image crash-looping with no rollback
// left. Between the smash and the restore the heap really is corrupt — a few
// microseconds in which another task allocating could fault — so this exists
// only in CONFIG_CRASH_TEST_ENABLE builds, never in production.
bool heap_guard_rehearse(char *out, size_t cap);
#endif
