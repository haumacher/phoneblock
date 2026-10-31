// Implementation of storage.h. Three builds share this file: the device,
// where the partition holds LittleFS on every board; the Linux port
// (STORAGE_HOSTFS: a plain filesystem, the free bytes statvfs() reports);
// and test/test_storage_sim.c, which runs the LittleFS backend against the
// real littlefs core on a RAM flash (test/lfs_host).

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
#include "esp_err.h"
#include "esp_littlefs.h"
#include "esp_log.h"
#include "esp_partition.h"
#endif

#include "blocklist_sync.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

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

#else
// ---------------------------------------------------------------------------
// LittleFS backend (every board). Whole blocks, freed once a deletion is
// committed, no garbage collection: the budget is the partition minus what
// is on it minus a small reserve for copy-on-write in flight
// (storage_budget.h, STORAGE_LFS_RESERVE_BLOCKS). The community list stays
// the one file that yields (storage.h).
// ---------------------------------------------------------------------------

// The host simulation (test_storage_sim.c) maps file removal onto its
// RAM-backed LittleFS.
#ifndef STORAGE_UNLINK
#  define STORAGE_UNLINK unlink
#else
int STORAGE_UNLINK(const char *path);
#endif

// The block size esp_littlefs.c mounts a flash partition with (its
// CONFIG_LITTLEFS_BLOCK_SIZE, the erase sector; not configurable).
#define LFS_BLOCK_SIZE 4096u

static const storage_lfs_geometry_t s_geometry = {
    .block_size     = LFS_BLOCK_SIZE,
    .reserve_blocks = STORAGE_LFS_RESERVE_BLOCKS,
};

// What the partition holds before the first mount. LittleFS writes its
// superblock ("littlefs" right after the revision count and the first tag,
// at offset 8) into the metadata pair at blocks 0 and 1. A SPIFFS starts
// each block with its object lookup page — the ids of the block's pages,
// all 0xFF only while the block holds nothing — so one with files on it is
// foreign, an empty one reads as erased (nothing on it to lose).
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
            // Once per partition: the classic ESP32's SPIFFS on the first boot
            // of 1.7 (partitions.csv), or a board's demo firmware. The one
            // line in the web UI's log panel tells the user what it cost:
            // everything on it is downloaded again — except their own
            // recorded announcement (announcement.c). Settings are in NVS.
            // Kept within the panel's STATS_ERROR_MSG_LEN (test_storage_sim.c).
            ESP_LOGW(TAG, STORAGE_REFORMAT_WARNING);
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
    // Not mounted: the tier of the 640 KB partitions, the smallest layout.
    if (!storage_info(&total, &used)) return STORAGE_DIALOG_TIER_M;
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
    if (replaces != NULL && STORAGE_UNLINK(replaces) == 0) {
        STORAGE_LOGI(TAG, "%s removed up front to make room for %u B",
                     replaces, (unsigned) bytes);
    }
}

#endif  // STORAGE_HOSTFS

bool storage_file_open(storage_file_t *sf, const char *path)
{
    sf->buf = NULL;
    sf->f   = fopen(path, "wb");
    if (sf->f == NULL) return false;
    // Without the buffer stdio still works, one filesystem write per caller
    // chunk — slower, but not worth failing the write over. The buffer must
    // outlive every write, i.e. be freed only after fclose().
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
    storage_dl_t r = attempt(ctx, err, err_cap);
    if (r != STORAGE_DL_FULL) return r == STORAGE_DL_OK;

    // The write ran out of space although the budget said the file fits:
    // another writer took blocks in between (an upload on the web server's
    // task next to a sync on the scheduler's), or the metadata grew beyond
    // the reserve. The attempt removed its partial file, so the second one
    // claims its room (storage_make_room()) on the partition as it is now.
    r = attempt(ctx, err, err_cap);
    if (r == STORAGE_DL_OK) {
        STORAGE_LOGI(TAG, "%s written on retry", what);
    }
    return r == STORAGE_DL_OK;
}
