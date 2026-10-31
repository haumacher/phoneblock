// The "storage" partition, mounted at STORAGE_BASE_PATH, shared by every file
// the dongle keeps: the community and personal blocklists (blocklist_sync.c),
// the localized announcement, UI and mail packs, the dialog clips
// (i18n_sync.c, dialog_store.h) and the user's own recorded announcement
// (announcement.c). Its filesystem is LittleFS on every board: the classic
// ESP32 (640 KB, partitions.csv — the partition kept its spiffs subtype,
// the first boot of 1.7 reformats the old SPIFFS), the ESP32-C3 (640 KB,
// partitions-c3.csv) and the ESP32-S3 (~9.6 MB, partitions-16mb.csv); the
// Linux port (STORAGE_HOSTFS) uses a plain directory instead. Only this
// module knows which: it mounts the partition (storage_mount()) and reports
// its usage (storage_info()).
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
//    STORAGE_WRITE_BUF stdio buffer.
//
//  * One out-of-space recovery. storage_download() retries a download once
//    when the write ran out of space (issue #499, #530).
#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>

#include "storage_budget.h"

// Mount point of the partition on the device. The name is historical (the
// partition held SPIFFS up to 1.6); the file users spell their paths with
// it ("/spiffs/community.bin", ...). The partition label is "storage" in
// every table.
#define STORAGE_BASE_PATH "/spiffs"
#define STORAGE_LABEL     "storage"

// Mounts the partition at STORAGE_BASE_PATH (device only), formatting it
// (milliseconds) if it holds no LittleFS: quietly when it is erased (a first
// start), with one WARN when it held something else — an older firmware's
// SPIFFS, whose files are lost (STORAGE_REFORMAT_WARNING). False (logged at
// ERROR) if that fails; the callers then run without storage.
bool storage_mount(void);

// The WARN of a reformat, the one line the web UI's log panel shows of it:
// what the user has to do again. Everything else on the partition is
// downloaded again, and the settings are in NVS. At most
// STATS_ERROR_MSG_LEN - 1 characters (test_storage_sim.c).
#define STORAGE_REFORMAT_WARNING \
    "storage reformatted (now LittleFS): settings kept, downloads come back; " \
    "upload your own announcement again"

// The partition's size and the bytes in use, as the filesystem reports them
// (device only: esp_littlefs_info()). False if it is not mounted.
bool storage_info(size_t *total, size_t *used);

// stdio buffer every file is written through: one block per filesystem write
// call.
#define STORAGE_WRITE_BUF 4096

// Bytes the community blocklist may take on flash, computed from what is on
// the partition now. Call it after the old community file was deleted. Returns
// -1 if the partition cannot be read (not mounted).
int64_t storage_community_budget(void);

// Largest dialog clip set file (#429) the partition takes, in bytes: the
// tier of its size (storage_budget.h) — storage_budget_lfs_dialog() on
// LittleFS (M on the 640 KB partitions of the classic ESP32 and the C3, the
// L ceiling STORAGE_DIALOG_PLAIN_FS_MAX on the S3), STORAGE_DIALOG_PLAIN_FS_MAX
// on a plain filesystem (the Linux port). Both the downloaded tier and an
// own set (POST /api/dialog) are held to it.
uint64_t storage_dialog_budget(void);

// Makes room for a rarely written file of `bytes` that is about to be written
// (precedence over the community cache). If the partition's budget does not
// allow it, the cached community list is evicted first; if that is still not
// enough and `replaces` names an existing file the new one supersedes, that
// old copy is removed up front as well. Never refuses: the caller writes the
// file regardless, the storage_download() retry being the last line.
void storage_make_room(size_t bytes, const char *replaces);

// A file opened for writing through the shared STORAGE_WRITE_BUF buffer.
typedef struct {
    FILE *f;
    char *buf;
} storage_file_t;

// fopen(path, "wb") with the shared write buffer. False (errno set) if the
// file cannot be opened; a failed buffer allocation only costs speed and
// write calls, not the write.
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
    STORAGE_DL_FULL,    // out of space (ENOSPC) — worth one retry
    STORAGE_DL_FAILED,  // anything else (network, HTTP, too large, ...)
} storage_dl_t;

// One download attempt. Must remove whatever partial file it wrote before
// returning anything but STORAGE_DL_OK.
typedef storage_dl_t (*storage_attempt_fn)(void *ctx, char *err, size_t err_cap);

// Runs `attempt`; if it ran out of space, tries exactly once more (with the
// partial file gone, the attempt's storage_make_room() judges the partition
// as it is then). A single retry: if that fails too, the
// callers' fallbacks (live API lookup, embedded English, next sync) take
// over — not worth hammering the flash for. Returns true on success; `err`
// holds the last attempt's error otherwise. Logs nothing above INFO, so the
// caller's one WARN per failed sync stays the only line in the log panel.
bool storage_download(const char *what, storage_attempt_fn attempt, void *ctx,
                      char *err, size_t err_cap);
