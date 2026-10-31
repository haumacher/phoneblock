// Implementation of storage.h. Four builds share this file: the device with
// SPIFFS (the classic ESP32) or with LittleFS (CONFIG_PHONEBLOCK_STORAGE_LITTLEFS:
// the C3 and the S3), the Linux port (STORAGE_HOSTFS: a plain filesystem,
// where none of the SPIFFS page arithmetic applies), and, against the SPIFFS
// simulation in test/spiffs_host, test/test_storage_sim.c.

#include "storage.h"

#include <errno.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#ifdef STORAGE_HOSTFS
#include <sys/stat.h>
#include <sys/statvfs.h>

#include "platform.h"
#else
#include "sdkconfig.h"
#include "esp_err.h"
#include "esp_log.h"
#if CONFIG_PHONEBLOCK_STORAGE_LITTLEFS
#include "esp_littlefs.h"
#include "esp_partition.h"
#else
#include "esp_spiffs.h"
#endif
#endif

#include "blocklist_sync.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

#if !defined(STORAGE_HOSTFS) && !CONFIG_PHONEBLOCK_STORAGE_LITTLEFS
// The downloads need a garbage-collection budget far above the IDF default of
// 10 blocks — see the rationale in sdkconfig.defaults and issue #499.
// sdkconfig.defaults only seeds a *new* sdkconfig, so a checkout that was
// built before the setting was raised keeps the old value and would silently
// ship the bug again. Fail the build instead: delete `sdkconfig` (local
// settings belong in sdkconfig.defaults.local) and rebuild.
#if CONFIG_SPIFFS_GC_MAX_RUNS < 64
#  error "CONFIG_SPIFFS_GC_MAX_RUNS too low — stale sdkconfig, delete it and rebuild"
#endif
#endif

static const char *TAG = "storage";

// One log spelling for both backends: ESP_LOGI on the device (and the sim's
// shim of it), pb_log_info on the Linux port.
#ifdef STORAGE_HOSTFS
#define STORAGE_LOGI pb_log_info
#else
#define STORAGE_LOGI ESP_LOGI
#endif

#ifdef STORAGE_HOSTFS
// ---------------------------------------------------------------------------
// Plain-filesystem backend (the Linux port). A real filesystem has no stale
// pages, erase blocks or lazy reclamation: the budget is the free bytes
// statvfs() reports, minus a flat margin every writer keeps, and
// storage_make_room() judges by that alone. The community list stays the
// one file that yields (storage.h).
// ---------------------------------------------------------------------------

// Headroom kept free in the storage directory (matches the guard the
// pre-storage.c Linux writers used: blocklist_sync's BLOCKLIST_FS_MARGIN,
// i18n_sync's FS_MARGIN).
#define STORAGE_FS_MARGIN (48 * 1024)

// Free bytes in the data directory, or false if it cannot be read. Creates
// the directory first: on a fresh install the first caller is the budget
// query, before any writer had a reason to create it.
static bool fs_info(int64_t *free_bytes)
{
    if (mkdir(PHONEBLOCK_DATA_DIR, 0750) != 0 && errno != EEXIST) return false;
    struct statvfs info;
    if (statvfs(PHONEBLOCK_DATA_DIR, &info) != 0) return false;
    *free_bytes = (int64_t)info.f_bavail * (int64_t)info.f_frsize;
    return true;
}

int64_t storage_community_budget(void)
{
    int64_t free_bytes;
    if (!fs_info(&free_bytes)) return -1;
    int64_t budget = free_bytes - STORAGE_FS_MARGIN;
    return budget > 0 ? budget : 0;
}

uint64_t storage_dialog_budget(void)
{
    // Space on a real filesystem is the operator's business: the largest
    // tier of a locale (`l`, 450–530 KB) fits the ceiling, and
    // storage_make_room() still runs.
    return STORAGE_DIALOG_PLAIN_FS_MAX;
}

bool storage_mount(void)
{
    // announcement.c creates the data directory; nothing to mount.
    return true;
}

bool storage_info(size_t *total, size_t *used)
{
    // Not used on Linux (web.c reports it on the device only).
    *total = 0;
    *used  = 0;
    return false;
}

void storage_make_room(size_t bytes, const char *replaces)
{
    int64_t free_bytes;
    if (!fs_info(&free_bytes)) return;
    if (free_bytes - (int64_t)bytes >= STORAGE_FS_MARGIN) return;

    // The community list is a cache — calls fall back to the live API
    // lookup until the next daily sync fetches a list sized for what is
    // left — so it is the first to go.
    if (blocklist_sync_evict_community()) {
        STORAGE_LOGI(TAG, "community list evicted to make room for %u B",
                    (unsigned)bytes);
        if (!fs_info(&free_bytes)) return;
        if (free_bytes - (int64_t)bytes >= STORAGE_FS_MARGIN) return;
    }

    // Still short: give up the old copy now rather than holding both the old
    // and the new one at once. Costs the old content for the duration of the
    // download (the callers' fallbacks cover it).
    if (replaces != NULL && unlink(replaces) == 0) {
        STORAGE_LOGI(TAG, "%s removed up front to make room for %u B",
                    replaces, (unsigned)bytes);
    }
}

void storage_gc(const char *why, size_t bytes)
{
    // A plain filesystem reclaims deleted blocks itself; there is nothing to
    // collect. The storage_download() retry after ENOSPC still runs — with
    // the partial file unlinked, the space is back by then.
    (void)why;
    (void)bytes;
}

#elif CONFIG_PHONEBLOCK_STORAGE_LITTLEFS
// ---------------------------------------------------------------------------
// LittleFS backend (the C3 and the S3). Whole blocks, freed once a deletion
// is committed, no garbage collection: the budget is the partition minus
// what is on it minus a small reserve for copy-on-write in flight
// (storage_budget.h, STORAGE_LFS_RESERVE_BLOCKS). The community list stays
// the one file that yields (storage.h).
// ---------------------------------------------------------------------------

// The block size esp_littlefs.c mounts a flash partition with (its
// CONFIG_LITTLEFS_BLOCK_SIZE, the erase sector; not configurable).
#define LFS_BLOCK_SIZE 4096u

static const storage_lfs_geometry_t s_geometry = {
    .block_size     = LFS_BLOCK_SIZE,
    .reserve_blocks = STORAGE_LFS_RESERVE_BLOCKS,
};

// What the partition holds before the first mount. LittleFS writes its
// superblock ("littlefs" right after the revision count and the first tag,
// at offset 8) into the metadata pair at blocks 0 and 1.
typedef enum { PART_LITTLEFS, PART_ERASED, PART_FOREIGN, PART_UNREADABLE } part_content_t;

static part_content_t partition_content(void)
{
    const esp_partition_t *p = esp_partition_find_first(
        ESP_PARTITION_TYPE_DATA, ESP_PARTITION_SUBTYPE_ANY, STORAGE_LABEL);
    if (p == NULL) return PART_UNREADABLE;
    bool erased = true;
    for (uint32_t block = 0; block < 2; block++) {
        uint8_t head[16];
        if (esp_partition_read(p, block * LFS_BLOCK_SIZE, head, sizeof(head)) != ESP_OK) {
            return PART_UNREADABLE;
        }
        if (memcmp(head + 8, "littlefs", 8) == 0) return PART_LITTLEFS;
        for (size_t i = 0; i < sizeof(head); i++) {
            if (head[i] != 0xFF) erased = false;
        }
    }
    return erased ? PART_ERASED : PART_FOREIGN;
}

bool storage_mount(void)
{
    // Format here, not through format_if_mount_failed: the library's failed
    // mount logs an ERROR ("Corrupted dir pair") that would sit in the web
    // UI's log panel of every freshly installed dongle. A partition that
    // holds a LittleFS and still fails to mount is a real fault and keeps
    // the library's lines.
    part_content_t content = partition_content();
    if (content == PART_ERASED || content == PART_FOREIGN) {
        if (content == PART_ERASED) {
            STORAGE_LOGI(TAG, "storage partition empty (first start): formatting LittleFS");
        } else {
            // The 4 MB boards' old SPIFFS, or a board's demo firmware.
            ESP_LOGW(TAG, "storage partition holds no LittleFS (an older firmware's "
                          "SPIFFS?): formatting, its files are lost and are fetched again");
        }
        esp_err_t ferr = esp_littlefs_format(STORAGE_LABEL);
        if (ferr != ESP_OK) {
            ESP_LOGE(TAG, "LittleFS format failed: %s", esp_err_to_name(ferr));
            return false;
        }
    }
    esp_vfs_littlefs_conf_t cfg = {
        .base_path              = STORAGE_BASE_PATH,
        .partition_label        = STORAGE_LABEL,
        .format_if_mount_failed = true,
    };
    esp_err_t err = esp_vfs_littlefs_register(&cfg);
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "LittleFS mount failed: %s", esp_err_to_name(err));
        return false;
    }
    size_t total, used;
    if (storage_info(&total, &used)) {
        STORAGE_LOGI(TAG, "LittleFS mounted: %u B used of %u B",
                     (unsigned) used, (unsigned) total);
    }
    return true;
}

bool storage_info(size_t *total, size_t *used)
{
    *total = 0;
    *used  = 0;
    return esp_littlefs_info(STORAGE_LABEL, total, used) == ESP_OK;
}

int64_t storage_community_budget(void)
{
    size_t total, used;
    if (!storage_info(&total, &used)) return -1;
    return (int64_t) storage_budget_lfs_community(&s_geometry, total, used);
}

uint64_t storage_dialog_budget(void)
{
    size_t total, used;
    // Not mounted: the smallest tier, which every layout takes.
    if (!storage_info(&total, &used)) return STORAGE_DIALOG_TIER_S;
    return storage_budget_lfs_dialog(&s_geometry, total);
}

void storage_make_room(size_t bytes, const char *replaces)
{
    size_t total, used;
    if (!storage_info(&total, &used)) return;
    if (storage_budget_lfs_asset_fits(&s_geometry, total, used, bytes)) return;

    // The community list is a cache — calls fall back to the live API
    // lookup until the next daily sync fetches a list sized for what is
    // left — so it is the first to go.
    if (blocklist_sync_evict_community()) {
        STORAGE_LOGI(TAG, "community list evicted to make room for %u B",
                     (unsigned) bytes);
        if (!storage_info(&total, &used)) return;
        if (storage_budget_lfs_asset_fits(&s_geometry, total, used, bytes)) return;
    }

    // Still short: give up the old copy now rather than holding both the old
    // and the new one at once (copy-on-write keeps the old copy's blocks
    // until it is removed). Costs the old content for the duration of the
    // download (the callers' fallbacks cover it).
    if (replaces != NULL && unlink(replaces) == 0) {
        STORAGE_LOGI(TAG, "%s removed up front to make room for %u B",
                     replaces, (unsigned) bytes);
    }
}

void storage_gc(const char *why, size_t bytes)
{
    // LittleFS frees a deleted file's blocks with the commit of the deletion;
    // there is nothing to collect. The storage_download() retry after ENOSPC
    // still runs — with the partial file unlinked, the space is back by then.
    (void) why;
    (void) bytes;
}

#else  // SPIFFS backend (the classic ESP32 and the host simulation)

// The host simulation maps file removal onto its RAM-backed SPIFFS.
#ifndef STORAGE_UNLINK
#  define STORAGE_UNLINK unlink
#else
int STORAGE_UNLINK(const char *path);
#endif

#define SPIFFS_LABEL STORAGE_LABEL

// SPIFFS geometry, as esp_spiffs.c mounts the partition: the logical block
// is the flash erase sector, the logical page CONFIG_SPIFFS_PAGE_SIZE.
#define STORAGE_BLOCK_SIZE    4096u
#define PAGE_SIZE             ((uint32_t) CONFIG_SPIFFS_PAGE_SIZE)
#define PAGES_PER_BLOCK       (STORAGE_BLOCK_SIZE / PAGE_SIZE)
// spiffs_nucleus.h: SPIFFS_OBJ_LOOKUP_PAGES — one 2-byte object id per page
// of the block, at least one page.
#define LOOKUP_PAGES_RAW      ((PAGES_PER_BLOCK * 2u) / PAGE_SIZE)
#define LOOKUP_PAGES          (LOOKUP_PAGES_RAW > 0 ? LOOKUP_PAGES_RAW : 1u)
// spiffs_nucleus.h: SPIFFS_DATA_PAGE_SIZE — the page minus the packed
// spiffs_page_header (obj_id 2 B, span_ix 2 B, flags 1 B).
#define PAGE_PAYLOAD          (PAGE_SIZE - 5u)

// Blocks kept free on top of the derived terms (storage_budget.h):
// spiffs_gc_check() admits a write without collecting only while more than
// three blocks are free — four blocks — plus one write call in flight, a
// STORAGE_WRITE_BUF of data pages and the rewritten index page, which spans
// up to two more. The simulation (test_storage_sim.c) runs the daily cycle
// without a single failed write at budgets 40 % above this, so the reserve
// is margin, not a fudge.
#define RESERVE_BLOCKS        6u

_Static_assert(PAGES_PER_BLOCK > LOOKUP_PAGES, "SPIFFS block holds no data page");

static const storage_geometry_t s_geometry = {
    .page_payload         = PAGE_PAYLOAD,
    .data_pages_per_block = PAGES_PER_BLOCK - LOOKUP_PAGES,
    .write_buf            = STORAGE_WRITE_BUF,
    .reserve_blocks       = RESERVE_BLOCKS,
};

static bool fs_info(size_t *total, size_t *used)
{
    *total = 0;
    *used  = 0;
    return esp_spiffs_info(SPIFFS_LABEL, total, used) == ESP_OK;
}

bool storage_info(size_t *total, size_t *used)
{
    return fs_info(total, used);
}

#ifdef ESP_PLATFORM
bool storage_mount(void)
{
    esp_vfs_spiffs_conf_t cfg = {
        .base_path              = STORAGE_BASE_PATH,
        .partition_label        = SPIFFS_LABEL,
        .max_files              = 2,
        .format_if_mount_failed = true,
    };
    esp_err_t err = esp_vfs_spiffs_register(&cfg);
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "SPIFFS mount failed: %s", esp_err_to_name(err));
        return false;
    }
    size_t total, used;
    if (fs_info(&total, &used)) {
        STORAGE_LOGI(TAG, "SPIFFS mounted: %u B used of %u B",
                     (unsigned) used, (unsigned) total);
    }
    return true;
}
#endif

int64_t storage_community_budget(void)
{
    size_t total, used;
    if (!fs_info(&total, &used)) return -1;
    return (int64_t) storage_budget_community(&s_geometry, total, used);
}

uint64_t storage_dialog_budget(void)
{
    // The S tier, mounted or not (storage_budget.h): the tier the simulation
    // validated next to the other files on the 640 KB SPIFFS.
    return STORAGE_DIALOG_TIER_S;
}

void storage_make_room(size_t bytes, const char *replaces)
{
    size_t total, used;
    if (!fs_info(&total, &used)) return;
    if (storage_budget_asset_fits(&s_geometry, total, used, bytes)) return;

    // The community list is a cache — calls fall back to the live API
    // lookup until the next daily sync fetches a list sized for what is
    // left — so it is the first to go.
    if (blocklist_sync_evict_community()) {
        STORAGE_LOGI(TAG, "community list evicted to make room for %u B",
                 (unsigned) bytes);
        if (!fs_info(&total, &used)) return;
        if (storage_budget_asset_fits(&s_geometry, total, used, bytes)) return;
    }

    // Still short: give up the old copy now rather than holding both the old
    // and the new one at once. Costs the old content for the duration of the
    // download (the callers' fallbacks cover it), but frees its pages —
    // including the stale index pages a file written by older firmware
    // through a small buffer keeps trapped — before the write needs them.
    if (replaces != NULL && STORAGE_UNLINK(replaces) == 0) {
        STORAGE_LOGI(TAG, "%s removed up front to make room for %u B",
                 replaces, (unsigned) bytes);
    }
}

// SPIFFS reclaims deleted pages lazily, inside the write that needs them, and
// only up to CONFIG_SPIFFS_GC_MAX_RUNS blocks per write call. Asking for the
// whole file's footprint up front does the collecting in one deliberate pass
// on the background task (issue #499).
//
// Logged at INFO on purpose: not reaching the target is not a failure — the
// write needs free pages, not consolidated blocks, and may well succeed —
// and ESP_LOGW lands in the "Protokoll" panel of the web UI.
void storage_gc(const char *why, size_t bytes)
{
    size_t want = (size_t) storage_budget_footprint_pages(&s_geometry, bytes)
                  * s_geometry.page_payload;
    esp_err_t e = esp_spiffs_gc(SPIFFS_LABEL, want);
    STORAGE_LOGI(TAG, "gc for %s (%u B): %s", why, (unsigned) want,
             esp_err_to_name(e));
}

#endif  // STORAGE_HOSTFS

bool storage_file_open(storage_file_t *sf, const char *path)
{
    sf->buf = NULL;
    sf->f   = fopen(path, "wb");
    if (sf->f == NULL) return false;
    // Without the buffer stdio still works, one SPIFFS write per caller
    // chunk — slower and with more stale index pages, but not worth failing
    // the write over. The buffer must outlive every write, i.e. be freed only
    // after fclose().
    sf->buf = malloc(STORAGE_WRITE_BUF);
    if (sf->buf != NULL) {
        setvbuf(sf->f, sf->buf, _IOFBF, STORAGE_WRITE_BUF);
    }
    return true;
}

bool storage_file_write(storage_file_t *sf, const void *data, size_t n)
{
    return fwrite(data, 1, n, sf->f) == n;
}

bool storage_file_close(storage_file_t *sf)
{
    bool ok = true;
    int  err = 0;
    if (sf->f != NULL) {
        // With the buffer in play this is where an out-of-space failure most
        // likely surfaces: up to STORAGE_WRITE_BUF bytes go to flash here.
        if (fflush(sf->f) != 0) { ok = false; err = errno; }
        // fclose() releases the stream even when it fails.
        if (fclose(sf->f) != 0 && ok) { ok = false; err = errno; }
        sf->f = NULL;
    }
    free(sf->buf);
    sf->buf = NULL;
    if (!ok) errno = err;
    return ok;
}

bool storage_download(const char *what, storage_attempt_fn attempt, void *ctx,
                      char *err, size_t err_cap)
{
    size_t bytes = 0;
    storage_dl_t r = attempt(ctx, &bytes, err, err_cap);
    if (r != STORAGE_DL_FULL) return r == STORAGE_DL_OK;

    // The write ran out of writable pages although the budget said the file
    // fits, i.e. garbage collection inside the write could not keep up.
    // Removing the partial file just released more pages, so an unhurried
    // pass now has more to work with than the one inside the failed write.
    storage_gc(what, bytes);
    bytes = 0;
    r = attempt(ctx, &bytes, err, err_cap);
    if (r == STORAGE_DL_OK) {
        STORAGE_LOGI(TAG, "%s written on retry after gc", what);
    }
    return r == STORAGE_DL_OK;
}
