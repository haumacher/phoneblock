// The "storage" SPIFFS partition (640 KB, mounted at /spiffs by
// announcement.c), shared by every file the dongle keeps: the community and
// personal blocklists (blocklist_sync.c), the localized announcement, UI and
// mail packs (i18n_sync.c) and the user's own recorded announcement
// (announcement.c).
//
// This module is the single owner of the partition's budget (the arithmetic
// and its derivation live in storage_budget.h) and of the write discipline
// every writer shares:
//
//  * Precedence. The rarely written files come first: storage_make_room()
//    evicts the cached community list when one of them needs the space. The
//    community list gets what remains — storage_community_budget(), sent to
//    the server as &maxBytes.
//
//  * One write buffer. Every file goes to flash through storage_file_open()'s
//    STORAGE_WRITE_BUF stdio buffer; the budget's stale-page arithmetic
//    assumes exactly that size.
//
//  * One out-of-space recovery. storage_download() retries a download once
//    after a garbage-collection pass when the write ran out of pages
//    (issue #499, #530).
#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>

// stdio buffer every file is written through (see storage_budget.h: each
// SPIFFS write call leaves one stale index page behind, so the buffer size
// sets the write amplification).
#define STORAGE_WRITE_BUF 4096

// Bytes the community blocklist may take on flash, computed from what is on
// the partition now. Call it after the old community file was deleted. Returns
// -1 if the partition cannot be read (not mounted).
int64_t storage_community_budget(void);

// Makes room for a rarely written file of `bytes` that is about to be written
// (precedence over the community cache). If the partition's budget does not
// allow it, the cached community list is evicted first; if that is still not
// enough and `replaces` names an existing file the new one supersedes, that
// old copy is removed up front as well. Never refuses: the caller writes the
// file regardless, the storage_download() retry being the last line.
void storage_make_room(size_t bytes, const char *replaces);

// Runs a garbage-collection pass that tries to make a file of `bytes`
// writable (the file's footprint, see storage_budget_footprint_pages()). Logged at
// INFO: not reaching the target is not itself a failure.
void storage_gc(const char *why, size_t bytes);

// A file opened for writing through the shared STORAGE_WRITE_BUF buffer.
typedef struct {
    FILE *f;
    char *buf;
} storage_file_t;

// fopen(path, "wb") with the shared write buffer. False (errno set) if the
// file cannot be opened; a failed buffer allocation only costs speed and
// write amplification, not the write.
bool storage_file_open(storage_file_t *sf, const char *path);

// Writes `n` bytes. False (errno set, ENOSPC when SPIFFS ran out of pages)
// on a short write.
bool storage_file_write(storage_file_t *sf, const void *data, size_t n);

// Flushes and closes the file and frees the buffer, also after a failed
// write. False (errno set) if the final flush failed. Safe on a file that was
// never opened.
bool storage_file_close(storage_file_t *sf);

// Outcome of one download attempt.
typedef enum {
    STORAGE_DL_OK,      // file written
    STORAGE_DL_FULL,    // SPIFFS ran out of pages (ENOSPC) — worth a GC+retry
    STORAGE_DL_FAILED,  // anything else (network, HTTP, too large, ...)
} storage_dl_t;

// One download attempt. Must remove whatever partial file it wrote before
// returning anything but STORAGE_DL_OK, and on STORAGE_DL_FULL set *bytes to
// the size of the file it tried to write.
typedef storage_dl_t (*storage_attempt_fn)(void *ctx, size_t *bytes,
                                           char *err, size_t err_cap);

// Runs `attempt`; if it ran out of space, collects garbage for the file's
// size and tries exactly once more. A single retry: if that fails too, the
// callers' fallbacks (live API lookup, embedded English, next sync) take
// over — not worth hammering the flash for. Returns true on success; `err`
// holds the last attempt's error otherwise. Logs nothing above INFO, so the
// caller's one WARN per failed sync stays the only line in the log panel.
bool storage_download(const char *what, storage_attempt_fn attempt, void *ctx,
                      char *err, size_t err_cap);
