#include "announcement.h"

#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>
#include <errno.h>

#ifdef ESP_PLATFORM
#include "esp_log.h"
#include "esp_spiffs.h"
#endif

#include "config.h"
#include "platform.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

static const char *TAG = "announcement";

// No announcement is baked into the firmware anymore (issue #460): the
// binary carried a single German recording, which does not scale to the
// languages the web UI supports. The active announcement is now either a
// user-uploaded custom file or a localized file downloaded from the CDN for
// the selected ui_lang (see i18n_sync.c); with neither present the caller
// answers silently and hangs up (announcement_open returns len == 0).

#ifdef ESP_PLATFORM
#define SPIFFS_BASE_PATH  "/spiffs"
#define SPIFFS_LABEL      "storage"
#define SPIFFS_FILE       "/spiffs/announcement.alaw"
#define SPIFFS_TEMP       "/spiffs/announcement.alaw.tmp"
#define SPIFFS_LOCALIZED_PREFIX "/spiffs/announcement-"
#else
#ifndef PHONEBLOCK_DATA_DIR
#define PHONEBLOCK_DATA_DIR "/var/lib/phoneblock"
#endif
#define SPIFFS_BASE_PATH  PHONEBLOCK_DATA_DIR
#define SPIFFS_LABEL      "storage"
#define SPIFFS_FILE       PHONEBLOCK_DATA_DIR "/announcement.alaw"
#define SPIFFS_TEMP       PHONEBLOCK_DATA_DIR "/announcement.alaw.tmp"
#define SPIFFS_LOCALIZED_PREFIX PHONEBLOCK_DATA_DIR "/announcement-"
#endif
#define SPIFFS_LOCALIZED_SUFFIX ".alaw"

static bool     s_storage_ready = false;
// Latch for "there is no usable custom file" so we don't keep stat()-ing
// SPIFFS (and re-warning about a bad size) on every /api/status poll.
// Cleared whenever a new file is written or the current one is reset.
static bool     s_no_custom       = false;

// Streaming-write session state (see announcement_write_begin).
static FILE    *s_write_file     = NULL;
static size_t   s_write_total    = 0;
static size_t   s_write_got      = 0;

// Drop the "no custom file" latch so the next open()/stat() re-checks
// SPIFFS. Called after a write or reset changes what's on flash.
static void forget_custom_state(void)
{
    s_no_custom = false;
}

// Size of a valid custom SPIFFS announcement, or -1 if there is none
// (not mounted, missing, empty, or over the cap). stat()-only — never
// touches the heap — so the dashboard can poll it freely. Latches the
// "no custom" result so repeated polls don't re-stat or re-warn.
static long custom_size(void)
{
    if (!s_storage_ready) return -1;
    if (s_no_custom)       return -1;

    struct stat st;
    if (stat(SPIFFS_FILE, &st) != 0) {
        s_no_custom = true;
        return -1;
    }
    if (st.st_size <= 0 || (size_t)st.st_size > ANNOUNCEMENT_MAX_BYTES) {
        pb_log_warn(TAG, "SPIFFS file has invalid size %ld, ignoring",
                 (long)st.st_size);
        s_no_custom = true;
        return -1;
    }
    return (long)st.st_size;
}

void announcement_localized_path(char *out, size_t cap, const char *lang)
{
    // lang comes from config_ui_lang(), which only ever returns a
    // config_lang_code_valid() string — no separators or "..", so this
    // cannot escape the SPIFFS namespace.
    snprintf(out, cap, "%s%s%s",
             SPIFFS_LOCALIZED_PREFIX, lang, SPIFFS_LOCALIZED_SUFFIX);
}

// Size of the single downloaded announcement for the active ui_lang, or -1 if
// there is none / it is unusable. There is only ever one localized file on the
// device: i18n_sync.c picks the content via the ui_lang → en → de fallback
// chain at *download* time and stores it under the ui_lang name, so playback
// just reads that one file. stat()-only, like custom_size(); not latched — a
// missing file is the normal (not warned) case and it changes on locale
// switch / download.
static long localized_size(void)
{
    if (!s_storage_ready) return -1;
    char path[48];
    announcement_localized_path(path, sizeof(path), config_ui_lang());
    struct stat st;
    if (stat(path, &st) != 0) return -1;
    if (st.st_size <= 0 || (size_t)st.st_size > ANNOUNCEMENT_MAX_BYTES) return -1;
    return (long)st.st_size;
}

int announcement_init(void)
{
#ifdef ESP_PLATFORM
    esp_vfs_spiffs_conf_t cfg = {
        .base_path              = SPIFFS_BASE_PATH,
        .partition_label        = SPIFFS_LABEL,
        .max_files              = 2,
        .format_if_mount_failed = true,
    };
    int err = esp_vfs_spiffs_register(&cfg);
    if (err != PB_OK) {
        pb_log_err(TAG, "SPIFFS mount failed: %s", "SPIFFS error");
        // Keep going — the embedded default will still work.
        return err;
    }
    s_storage_ready = true;

    size_t total = 0, used = 0;
    if (esp_spiffs_info(SPIFFS_LABEL, &total, &used) == PB_OK) {
        pb_log_info(TAG, "SPIFFS mounted: %u B used of %u B",
                 (unsigned)used, (unsigned)total);
    }
    // Legacy cleanup: older firmware uploaded via a temp file plus a
    // rename(). Drop any leftover temp so it doesn't waste a slot —
    // the current code writes the live file in place (see write_begin).
    unlink(SPIFFS_TEMP);
    s_storage_ready = true;
    return PB_OK;
#else
    if (mkdir(PHONEBLOCK_DATA_DIR, 0750) != 0 && errno != EEXIST) {
        pb_log_err(TAG, "mkdir %s: %s", PHONEBLOCK_DATA_DIR, strerror(errno));
        return PB_FAIL;
    }
    s_storage_ready = true;
    return PB_OK;
#endif
}

int announcement_open(announcement_src_t *src)
{
    if (!src) return PB_ERR_INVALID_ARG;
    src->file = NULL;
    src->mem  = NULL;
    src->pos  = 0;
    src->len  = 0;

    // Resolution order: user-uploaded custom file > the single downloaded
    // localized file for the active locale > nothing (empty source → silent
    // pickup). The download-time ui_lang → en → de fallback lives in
    // i18n_sync.c, so there is only one file to consider here.
    const char *path = SPIFFS_FILE;
    char lpath[48];
    long sz = custom_size();
    if (sz <= 0) {
        long lsz = localized_size();
        if (lsz > 0) {
            announcement_localized_path(lpath, sizeof(lpath), config_ui_lang());
            path = lpath;
            sz   = lsz;
        }
    }

    if (sz > 0) {
        FILE *f = fopen(path, "rb");
        if (f) {
            src->file = f;
            src->len  = (size_t)sz;
            return PB_OK;
        }
        // Lost the race with a delete, or a SPIFFS hiccup. There is no
        // embedded fallback anymore — leave the source empty so the caller
        // answers silently and goes to BYE rather than serving nothing.
        pb_log_warn(TAG, "fopen(%s): %s — no announcement, silent pickup",
                 path, strerror(errno));
    }
    src->len = 0;   // no audio available (see announcement.h)
    return PB_OK;
}

size_t announcement_read(announcement_src_t *src, uint8_t *out, size_t max)
{
    if (!src || !out || max == 0) return 0;
    if (src->file) {
        return fread(out, 1, max, src->file);
    }
    if (src->mem) {
        size_t remaining = src->len - src->pos;
        size_t n = remaining < max ? remaining : max;
        memcpy(out, src->mem + src->pos, n);
        src->pos += n;
        return n;
    }
    return 0;
}

void announcement_close(announcement_src_t *src)
{
    if (src && src->file) {
        fclose(src->file);
        src->file = NULL;
    }
}

int announcement_write_begin(size_t total_bytes)
{
    if (!s_storage_ready) return PB_FAIL;
    if (s_write_file)      return PB_FAIL;
    if (total_bytes == 0 || total_bytes > ANNOUNCEMENT_MAX_BYTES) {
        return PB_ERR_INVALID_ARG;
    }
    // Write straight into the live file: fopen("wb") truncates it in
    // place, so the partition only ever holds one announcement-sized
    // blob. The old temp+rename approach needed room for both the temp
    // and the live file at once and then a rename(), which SPIFFS fails
    // once the slot is near-full — that left every re-upload of a large
    // (near-240 KB) announcement permanently stuck on the embedded
    // default (issue #359). Losing the announcement if a write is
    // interrupted is acceptable here: the default takes over until the
    // user re-uploads.
    forget_custom_state();

    s_write_file = fopen(SPIFFS_FILE, "wb");
    if (!s_write_file) {
        pb_log_err(TAG, "fopen(%s): %s", SPIFFS_FILE, strerror(errno));
        return PB_FAIL;
    }
    // Crank the stdio buffer up: SPIFFS pays per flush, not per byte,
    // so bigger batched writes are noticeably faster than the default
    // BUFSIZ (~1 KB on IDF).
    static char s_write_bufio[8192];
    setvbuf(s_write_file, s_write_bufio, _IOFBF, sizeof(s_write_bufio));
    s_write_total = total_bytes;
    s_write_got   = 0;
    return PB_OK;
}

int announcement_write_append(const uint8_t *buf, size_t len)
{
    if (!s_write_file) return PB_FAIL;
    if (!buf || len == 0) return PB_ERR_INVALID_ARG;
    if (s_write_got + len > s_write_total) return PB_ERR_INVALID_SIZE;
    size_t w = fwrite(buf, 1, len, s_write_file);
    if (w != len) {
        pb_log_err(TAG, "short write: %u of %u bytes", (unsigned)w, (unsigned)len);
        return PB_FAIL;
    }
    s_write_got += len;
    return PB_OK;
}

int announcement_write_commit(void)
{
    if (!s_write_file) return PB_FAIL;
    fclose(s_write_file);
    s_write_file = NULL;
    if (s_write_got != s_write_total) {
        pb_log_err(TAG, "commit: got %u of expected %u",
                 (unsigned)s_write_got, (unsigned)s_write_total);
        // Drop the partially written live file → fall back to default.
        unlink(SPIFFS_FILE);
        forget_custom_state();
        s_write_got = s_write_total = 0;
        return PB_ERR_INVALID_SIZE;
    }
    size_t stored = s_write_got;
    s_write_got = s_write_total = 0;
    forget_custom_state();
    pb_log_info(TAG, "stored custom announcement: %u bytes", (unsigned)stored);
    return PB_OK;
}

void announcement_write_abort(void)
{
    if (s_write_file) {
        fclose(s_write_file);
        s_write_file = NULL;
    }
    // The aborted write was going straight into the live file, so it is
    // now partial — discard it and fall back to the embedded default.
    unlink(SPIFFS_FILE);
    forget_custom_state();
    s_write_got = s_write_total = 0;
}

int announcement_reset(void)
{
    if (!s_storage_ready) return PB_FAIL;
    if (unlink(SPIFFS_FILE) != 0 && errno != ENOENT) {
        pb_log_warn(TAG, "unlink(%s): %s", SPIFFS_FILE, strerror(errno));
    }
    unlink(SPIFFS_TEMP);  // best-effort cleanup of any stale temp
    forget_custom_state();
    pb_log_info(TAG, "custom announcement reset (localized/silent takes over)");
    return PB_OK;
}

bool announcement_is_custom(void)
{
    return custom_size() > 0;
}

const char *announcement_source(void)
{
    if (custom_size() > 0)    return "custom";
    if (localized_size() > 0) return "localized";
    return "none";
}

size_t announcement_length(void)
{
    long sz = custom_size();
    if (sz > 0) return (size_t)sz;
    sz = localized_size();
    return sz > 0 ? (size_t)sz : 0;
}
