// Implementation of blocklist_sync.h: streaming HTTPS download into a
// SPIFFS temp file, atomic rename, daily background task.

#include "blocklist_sync.h"

#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#ifdef ESP_PLATFORM
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"
#include "esp_log.h"
#include "esp_timer.h"
#include "esp_http_client.h"
#include "esp_crt_bundle.h"
#include "esp_spiffs.h"
#else
#include <sys/stat.h>
#include <sys/statvfs.h>
#endif

#include "config.h"
#include "heap_guard.h"
#include "platform.h"
#include "strbuf.h"
#include "scheduler.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

// The downloads here and in i18n_sync.c need a garbage-collection budget far
// above the IDF default of 10 blocks — see the rationale in sdkconfig.defaults
// and issue #499. sdkconfig.defaults only seeds a *new* sdkconfig, so a
// checkout that was built before the setting was raised keeps the old value
// and would silently ship the bug again. Fail the build instead: delete
// `sdkconfig` (local settings belong in sdkconfig.defaults.local) and rebuild.
#if defined(ESP_PLATFORM) && CONFIG_SPIFFS_GC_MAX_RUNS < 64
#  error "CONFIG_SPIFFS_GC_MAX_RUNS too low — stale sdkconfig, delete it and rebuild"
#endif

static const char *TAG = "blsync";

// Stream-read chunk for the HTTPS download. Small to stay friendly with
// the stack-allocated buffer; the stdio buffer below batches these chunks
// into larger SPIFFS writes.
#define DOWNLOAD_CHUNK     1024

// stdio buffer for the temp file. SPIFFS pays per write *call* — cache
// flush, object-index update and one garbage-collection check each time —
// not per byte, so batching the 1 KB HTTP chunks into 4 KB writes cuts that
// cost (and the GC churn) fourfold. Heap-allocated for the duration of the
// download rather than static: 4 KB of DRAM is worth more to the heap the
// TLS session comes out of, and the scheduler task's 16 KB stack has to
// hold the handshake as well.
#define SPIFFS_IO_BUF      4096

#define COMMUNITY_TMP      BLOCKLIST_COMMUNITY_PATH ".tmp"
#define PERSONAL_TMP       BLOCKLIST_PERSONAL_PATH ".tmp"

// SPIFFS partition label shared with announcement.c — the community,
// personal and announcement files all live on this one mount.
#define SPIFFS_LABEL       "storage"

// Headroom kept free on the storage partition: SPIFFS block/metadata
// overhead the raw free-byte count overstates, plus room for the small
// personal list. Used both to size the budget advertised to the server
// (&maxBytes) and to guard each download against a mid-write ENOSPC.
#define BLOCKLIST_FS_MARGIN      (48 * 1024)

// Community budget advertised when the filesystem size cannot be read.
#define DEFAULT_COMMUNITY_BUDGET (256 * 1024)

static pb_mutex_t * s_lock    = NULL;
static blocklist_sync_status_t s_status;

// ---------------------------------------------------------------------------
// Status helpers (caller may or may not hold s_lock; documented per use).
// ---------------------------------------------------------------------------

static void set_error(const char *msg)
{
    if (!msg) return;
    strncpy(s_status.last_error, msg, sizeof(s_status.last_error) - 1);
    s_status.last_error[sizeof(s_status.last_error) - 1] = '\0';
}

static void refresh_sizes_locked(void)
{
    blocklist_t *bl;
    if ((bl = blocklist_open(BLOCKLIST_COMMUNITY_PATH)) != NULL) {
        s_status.have_community = true;
        s_status.community_size = blocklist_size(bl);
        blocklist_close(bl);
    } else {
        s_status.have_community = false;
        s_status.community_size = 0;
    }
    if ((bl = blocklist_open(BLOCKLIST_PERSONAL_PATH)) != NULL) {
        s_status.have_personal = true;
        s_status.personal_size = blocklist_size(bl);
        blocklist_close(bl);
    } else {
        s_status.have_personal = false;
        s_status.personal_size = 0;
    }
}

// ---------------------------------------------------------------------------
// Streaming HTTPS download into a temp file.
// ---------------------------------------------------------------------------

// Free bytes on the storage SPIFFS partition, or -1 if it cannot be read.
static int64_t spiffs_free_bytes(void)
{
#ifdef ESP_PLATFORM
    size_t total = 0, used = 0;
    if (esp_spiffs_info(SPIFFS_LABEL, &total, &used) != ESP_OK) return -1;
    return (int64_t)total - (int64_t)used;
#else
    struct statvfs info;
    if (statvfs(PHONEBLOCK_DATA_DIR, &info) != 0) return -1;
    return (int64_t)info.f_bavail * (int64_t)info.f_frsize;
#endif
}

// Storage budget the dongle offers the community file: current free space
// minus a margin for SPIFFS overhead and the small personal list. The old
// community file is deleted before the download starts (see sync_one), so
// its bytes are already part of this free count — no need to add them back.
// Sent to the server as &maxBytes so it caps the encoded file to what fits.
static int64_t community_budget_bytes(void)
{
    int64_t freeb = spiffs_free_bytes();
    if (freeb < 0) {
        return DEFAULT_COMMUNITY_BUDGET;
    }
    int64_t budget = freeb - BLOCKLIST_FS_MARGIN;
    return budget > 0 ? budget : 0;
}

// Runs a garbage-collection pass that tries to make `want` bytes writable on
// the storage partition.
//
// SPIFFS reclaims deleted pages lazily — one logical block per GC run — and
// the GC that runs implicitly inside a write gives up after
// CONFIG_SPIFFS_GC_MAX_RUNS blocks. After the daily unlink of the old
// community list (see sync_one), whose pages are scattered across
// partially-used blocks, that budget could be exhausted before a single
// contiguous page was free: the write then failed with SPIFFS_ERR_FULL,
// surfacing as ENOSPC and a short fwrite, while the partition still reported
// hundreds of KB free (issue #499). Asking for the space up front does the
// consolidation in one deliberate pass on the background scheduler task.
//
// Logged at INFO on purpose: not reaching the target is not a failure — the
// write needs free pages, not consolidated blocks, and may well succeed
// anyway — and pb_log_warn lands in the "Protokoll" panel of the web UI.
static void force_gc(const char *why, size_t want)
{
#ifdef ESP_PLATFORM
    esp_err_t result = esp_spiffs_gc(SPIFFS_LABEL, want);
    pb_log_info(TAG, "gc for %s (%u B): %d", why, (unsigned)want, result);
#else
    (void)why;
    (void)want;
#endif
}

// Downloads `url` into `tmp_path`. The caller has already removed the live
// file this temp will be renamed onto, so the whole free partition is
// available here. On any error returns false and writes a message to `err`.
//
// `*gc_want` is set to the number of bytes a retry would need the filesystem
// to make available, but only when the failure was an out-of-space one — it
// stays 0 for every other error, so the caller can tell the one failure a
// garbage-collection pass can fix from the ones it cannot.
typedef struct {
    FILE *file;
    int64_t bytes;
    size_t *gc_want;
    char *error;
    size_t error_cap;
    bool failed;
} blocklist_download_t;

static void write_download_chunk(const void *data, size_t length, void *context)
{
    blocklist_download_t *download = context;
    if (download->failed) return;
    size_t written = fwrite(data, 1, length, download->file);
    download->bytes += (int64_t)written;
    if (written == length) return;

    int error = errno;
    snprintf(download->error, download->error_cap,
             "fwrite short at %lld bytes: %s",
             (long long)download->bytes, strerror(error));
#ifdef ESP_PLATFORM
    if (error == ENOSPC)
        *download->gc_want = (size_t)download->bytes + length
                           + BLOCKLIST_FS_MARGIN;
#endif
    download->failed = true;
}

static bool download_to_tmp(const char *url, const char *tmp_path,
                            size_t *gc_want, char *err, size_t err_cap)
{
    *gc_want = 0;
#ifndef ESP_PLATFORM
    if (mkdir(PHONEBLOCK_DATA_DIR, 0750) != 0 && errno != EEXIST) {
        snprintf(err, err_cap, "mkdir %s: %s", PHONEBLOCK_DATA_DIR,
                 strerror(errno));
        return false;
    }
#endif
    FILE *out = fopen(tmp_path, "wb");
    if (!out) {
        snprintf(err, err_cap, "fopen %s: %s", tmp_path, strerror(errno));
        return false;
    }

    char *io_buffer = malloc(SPIFFS_IO_BUF);
    if (io_buffer) setvbuf(out, io_buffer, _IOFBF, SPIFFS_IO_BUF);

    char authorization[128];
    snprintf(authorization, sizeof(authorization), "Bearer %s",
             config_phoneblock_token());
    const pb_http_header_t headers[] = {
        { "Authorization", authorization },
        { "Accept", "application/octet-stream" },
    };
    blocklist_download_t download = {
        .file = out,
        .gc_want = gc_want,
        .error = err,
        .error_cap = err_cap,
    };
    int status = 0;
    int result = pb_http_request("GET", url, headers,
                                 sizeof(headers) / sizeof(headers[0]),
                                 NULL, 0, 30000, NULL, 0, NULL, &status,
                                 write_download_chunk, &download, NULL);
    bool ok = result == PB_HTTP_OK && status == 200 && !download.failed;
    if (result != PB_HTTP_OK)
        snprintf(err, err_cap, "HTTP transport failed (%d)", result);
    else if (status != 200)
        snprintf(err, err_cap, "HTTP %d", status);

    if (fflush(out) != 0) {
        snprintf(err, err_cap, "flush %s: %s", tmp_path, strerror(errno));
#ifdef ESP_PLATFORM
        if (errno == ENOSPC)
            *gc_want = (size_t)download.bytes + BLOCKLIST_FS_MARGIN;
#endif
        ok = false;
    }
    if (fclose(out) != 0) {
        snprintf(err, err_cap, "close %s: %s", tmp_path, strerror(errno));
        ok = false;
    }
    free(io_buffer);
    if (!ok) {
        unlink(tmp_path);
        return false;
    }
    pb_log_info(TAG, "downloaded %lld bytes → %s",
                (long long)download.bytes, tmp_path);
    return true;
}

// Validates that the file at `tmp_path` parses as a binary blocklist.
// Returns true if it does. Catches truncated downloads, wrong content-type
// from a misconfigured proxy, and the like.
static bool tmp_parses_ok(const char *tmp_path)
{
    blocklist_t *bl = blocklist_open(tmp_path);
    if (bl == NULL) {
        return false;
    }
    blocklist_close(bl);
    return true;
}

// Downloads one of the two list types and atomic-renames it into place.
// Sets *err on failure.
static bool sync_one(const char *type, const char *path, const char *tmp_path,
                     char *err, size_t err_cap)
{
    // Drop the old cached file up front, before downloading the new one.
    // SPIFFS has no atomic replace — rename() onto an existing name returns
    // SPIFFS_ERR_CONFLICTING_NAME, which the VFS maps to the catch-all EIO,
    // so a temp+rename swap fails with "rename …: I/O error" on every run
    // after the first. Removing the live file first makes the rename target
    // absent (so it succeeds) and means only one copy is ever on flash, so
    // the download never needs double the space. The cost is no on-flash
    // fallback during the download window: if the sync fails, the call-time
    // path falls back to the PhoneBlock API until the next successful run.
    unlink(path);

    char url[256];
    strbuf_t ub = sb_init(url, sizeof(url));
    sb_appendf(&ub, "%s/api/blocklist?format=binary&type=%s",
               config_phoneblock_base_url(), type);

    // The community list carries no per-entry vote counts, so the server
    // applies our two thresholds at encode time. Send them so the
    // downloaded list matches exactly what the API-fallback path
    // (api.c: direct >= min_direct, wildcard >= min_range) would decide.
    // The personal list is the user's explicit black/white set — no
    // thresholding, so no parameters.
    if (strcmp(type, "community") == 0) {
        // maxBytes lets the server cap the (size-unbounded) community list to
        // our free flash: it keeps all wildcards + whitelist and truncates the
        // Heat-ranked direct numbers to fit. minDirect/minRange keep the
        // encoded verdict identical to the API-fallback path.
        sb_appendf(&ub, "&minDirect=%d&minRange=%d&maxBytes=%lld",
                   config_min_direct_votes(), config_min_range_votes(),
                   (long long) community_budget_bytes());
    }

    unlink(tmp_path);

    size_t gc_want = 0;
    if (!download_to_tmp(url, tmp_path, &gc_want, err, err_cap)) {
        unlink(tmp_path);
        if (gc_want == 0) {
            return false;
        }
        // The write ran out of writable pages although the byte count said
        // otherwise, i.e. garbage collection could not keep up (issue #499).
        // Unlinking the half-written temp just released more pages, so a
        // second, unhurried GC pass now has more to work with than the one
        // inside the failed write did — try the download once more. A single
        // retry: if it fails again the call-time path falls back to the
        // PhoneBlock API, which is not worth hammering the flash for.
        force_gc("retry", gc_want);
        gc_want = 0;
        if (!download_to_tmp(url, tmp_path, &gc_want, err, err_cap)) {
            unlink(tmp_path);
            return false;
        }
        pb_log_info(TAG, "%s list downloaded on retry after gc", type);
    }

    if (!tmp_parses_ok(tmp_path)) {
        snprintf(err, err_cap, "downloaded %s is malformed", type);
        unlink(tmp_path);
        return false;
    }

    if (rename(tmp_path, path) != 0) {
        snprintf(err, err_cap, "rename %s: %s", type, strerror(errno));
        unlink(tmp_path);
        return false;
    }
    return true;
}

// ---------------------------------------------------------------------------
// Run-loop.
// ---------------------------------------------------------------------------

static void run_once(void)
{
    if (config_phoneblock_token()[0] == '\0') {
        pb_log_info(TAG, "skipped — no PhoneBlock token");
        pb_mutex_lock(s_lock);
        s_status.ever_ran    = true;
        s_status.last_ok     = false;
        s_status.last_at_us  = pb_monotonic_us();
        set_error("no PhoneBlock token");
        refresh_sizes_locked();
        pb_mutex_unlock(s_lock);
        return;
    }

    char err[64] = "";
    bool community_ok = sync_one("community",
                                 BLOCKLIST_COMMUNITY_PATH, COMMUNITY_TMP,
                                 err, sizeof(err));
    if (!community_ok) {
        pb_log_warn(TAG, "community sync failed: %s", err);
    }

    char err_personal[64] = "";
    bool personal_ok = sync_one("personal",
                                BLOCKLIST_PERSONAL_PATH, PERSONAL_TMP,
                                err_personal, sizeof(err_personal));
    if (!personal_ok) {
        pb_log_warn(TAG, "personal sync failed: %s", err_personal);
    }

    pb_mutex_lock(s_lock);
    s_status.ever_ran   = true;
    s_status.last_ok    = community_ok && personal_ok;
    s_status.last_at_us = pb_monotonic_us();
    s_status.last_error[0] = '\0';
    if (!community_ok) {
        set_error(err);
    } else if (!personal_ok) {
        set_error(err_personal);
    }
    refresh_sizes_locked();
    pb_mutex_unlock(s_lock);

    if (community_ok && personal_ok) {
        pb_log_info(TAG, "sync done: community=%d entries, personal=%d entries",
                 s_status.community_size, s_status.personal_size);
    }
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

void blocklist_sync_init(void)
{
    if (s_lock != NULL) {
        return;
    }
    memset(&s_status, 0, sizeof(s_status));
    s_lock = pb_mutex_create();

    pb_mutex_lock(s_lock);
    refresh_sizes_locked();
    pb_mutex_unlock(s_lock);
}

void blocklist_sync_run(void)
{
    if (s_lock == NULL) {
        return;
    }
    // Breadcrumb for the heap sentinel: downloads and parses a large blob from
    // the CDN into heap buffers, so it is worth being able to place a
    // corruption relative to it.
    heap_guard_note("blocklist:sync");
    if (!config_blocklist_enabled()) {
        // Feature switched off in the web UI: don't refresh the on-flash
        // files. The existing files stay put but are never consulted (the
        // call-time path skips them too), so they simply age out.
        pb_log_info(TAG, "skipped — local blocklist cache disabled");
        return;
    }

    pb_mutex_lock(s_lock);
    s_status.running = true;
    pb_mutex_unlock(s_lock);

    run_once();

    pb_mutex_lock(s_lock);
    s_status.running = false;
    pb_mutex_unlock(s_lock);
}

bool blocklist_sync_trigger_now(void)
{
    if (s_lock == NULL || !config_blocklist_enabled()) {
        return false;
    }
    pb_mutex_lock(s_lock);
    bool running = s_status.running;
    pb_mutex_unlock(s_lock);
    if (running) {
        return false;
    }
    // Hand off to the scheduler task, which runs blocklist_sync_run() on
    // its own stack — the download must not run on the caller's httpd
    // thread.
    return scheduler_request_blocklist_sync();
}

blocklist_verdict_t blocklist_sync_check(const char *digits,
                                         bool community_wildcards)
{
    return blocklist_sync_check_ex(digits, community_wildcards, NULL, NULL);
}

blocklist_verdict_t blocklist_sync_check_ex(const char *digits,
                                            bool community_wildcards,
                                            bool *wildcard_out,
                                            bool *personal_out)
{
    if (wildcard_out) *wildcard_out = false;
    if (personal_out) *personal_out = false;

    blocklist_verdict_t v = BLOCKLIST_UNKNOWN;
    bool wildcard = false;

    blocklist_t *personal = blocklist_open(BLOCKLIST_PERSONAL_PATH);
    if (personal != NULL) {
        // Always true, not community_wildcards: the personal prefixes are the
        // user's own range rules — see blocklist_sync.h (#516).
        v = blocklist_lookup_ex(personal, digits, true, &wildcard);
        blocklist_close(personal);
    }
    if (v != BLOCKLIST_UNKNOWN) {
        if (wildcard_out) *wildcard_out = wildcard;
        if (personal_out) *personal_out = true;
        return v;
    }

    blocklist_t *community = blocklist_open(BLOCKLIST_COMMUNITY_PATH);
    if (community != NULL) {
        v = blocklist_lookup_ex(community, digits, community_wildcards, &wildcard);
        blocklist_close(community);
    }
    if (v != BLOCKLIST_UNKNOWN && wildcard_out) {
        *wildcard_out = wildcard;
    }
    return v;
}

void blocklist_sync_snapshot(blocklist_sync_status_t *out)
{
    if (!out) return;
    if (s_lock == NULL) {
        memset(out, 0, sizeof(*out));
        return;
    }
    pb_mutex_lock(s_lock);
    *out = s_status;
    pb_mutex_unlock(s_lock);
}
