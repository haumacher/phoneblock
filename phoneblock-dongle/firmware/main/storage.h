// The "storage" partition, mounted at STORAGE_BASE_PATH, shared by every file
// the dongle keeps: the community and personal blocklists (blocklist_sync.c),
// the localized announcement, UI and mail packs, the dialog clips
// (i18n_sync.c, dialog_store.h) and the user's own recorded announcement
// (announcement.c). Its filesystem depends on the target
// (CONFIG_PHONEBLOCK_STORAGE_LITTLEFS): SPIFFS on the classic ESP32 (640 KB,
// partitions.csv), LittleFS on the ESP32-C3 (640 KB, partitions-c3.csv) and
// the ESP32-S3 (~9.6 MB, partitions-16mb.csv); the Linux port
// (STORAGE_HOSTFS) uses a plain directory instead. Only this module knows
// which: it mounts the partition (storage_mount()) and reports its usage
// (storage_info()).
//
// This module is the single owner of the partition's budget (the arithmetic
// and its derivation per filesystem live in storage_budget.h) and of the
// write discipline every writer shares:
//
//  * Precedence. The rarely written files come first: storage_make_room()
//    evicts the cached community list when one of them needs the space. The
//    community list gets what remains — storage_community_budget(), sent to
//    the server as &maxBytes.
//
//  * One write buffer. Every file goes to flash through storage_file_open()'s
//    STORAGE_WRITE_BUF stdio buffer; the SPIFFS budget's stale-page
//    arithmetic assumes exactly that size.
//
//  * One out-of-space recovery. storage_download() retries a download once
//    after a garbage-collection pass (a no-op on LittleFS and the Linux
//    port) when the write ran out of space (issue #499, #530).
#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>

#include "storage_budget.h"

// Mount point of the partition on the device, for both filesystems. The name
// is historical (SPIFFS came first); the file users spell their paths with
// it ("/spiffs/community.bin", ...). The partition label is "storage" in
// every table.
#define STORAGE_BASE_PATH "/spiffs"
#define STORAGE_LABEL     "storage"

// Mounts the partition at STORAGE_BASE_PATH (device only), formatting it if
// it holds no valid filesystem (a first boot: milliseconds on LittleFS, about
// 13 s per MB on SPIFFS). False (logged at ERROR) if that fails; the callers
// then run without storage.
bool storage_mount(void);

// The partition's size and the bytes in use, as the filesystem reports them
// (device only: esp_spiffs_info() / esp_littlefs_info()). False if it is not
// mounted.
bool storage_info(size_t *total, size_t *used);

// stdio buffer every file is written through (see storage_budget.h: each
// SPIFFS write call leaves one stale index page behind, so the buffer size
// sets the write amplification there; on LittleFS it is one block).
#define STORAGE_WRITE_BUF 4096

// Bytes the community blocklist may take on flash, computed from what is on
// the partition now. Call it after the old community file was deleted. Returns
// -1 if the partition cannot be read (not mounted).
int64_t storage_community_budget(void);

// Largest dialog clip set file (#429) the partition takes, in bytes: the
// tier of its filesystem and size (storage_budget.h) — S on SPIFFS, on
// LittleFS storage_budget_lfs_dialog() (M on the C3, the L ceiling
// STORAGE_DIALOG_PLAIN_FS_MAX on the S3), STORAGE_DIALOG_PLAIN_FS_MAX on a
// plain filesystem (the Linux port). Both the downloaded tier and an own set
// (POST /api/dialog) are held to it.
uint64_t storage_dialog_budget(void);

// Makes room for a rarely written file of `bytes` that is about to be written
// (precedence over the community cache). If the partition's budget does not
// allow it, the cached community list is evicted first; if that is still not
// enough and `replaces` names an existing file the new one supersedes, that
// old copy is removed up front as well. Never refuses: the caller writes the
// file regardless, the storage_download() retry being the last line.
void storage_make_room(size_t bytes, const char *replaces);

// Runs a garbage-collection pass that tries to make a file of `bytes`
// writable (the file's footprint, see storage_budget_footprint_pages()).
// Logged at INFO: not reaching the target is not itself a failure. SPIFFS
// only; a no-op on LittleFS and the Linux port, which reclaim deleted blocks
// themselves.
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

// Writes `n` bytes. False (errno set, ENOSPC when the filesystem ran out of
// space) on a short write.
bool storage_file_write(storage_file_t *sf, const void *data, size_t n);

// Flushes and closes the file and frees the buffer, also after a failed
// write. False (errno set) if the final flush failed. Safe on a file that was
// never opened.
bool storage_file_close(storage_file_t *sf);

// Outcome of one download attempt.
typedef enum {
    STORAGE_DL_OK,      // file written
    STORAGE_DL_FULL,    // out of space (ENOSPC) — worth a GC+retry
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
