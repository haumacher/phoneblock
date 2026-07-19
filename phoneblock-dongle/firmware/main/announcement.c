#include "announcement.h"

#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>
#include <errno.h>

#include "esp_log.h"
#include "esp_spiffs.h"

#include "config.h"

static const char *TAG = "announcement";

// No announcement is baked into the firmware anymore (issue #460): the
// binary carried a single German recording, which does not scale to the
// languages the web UI supports. The active announcement is now either a
// user-uploaded custom file or a localized file downloaded from the CDN for
// the selected ui_lang (see i18n_sync.c); with neither present the caller
// answers silently and hangs up (announcement_open returns len == 0).

#define SPIFFS_BASE_PATH  "/spiffs"
#define SPIFFS_LABEL      "storage"
#define SPIFFS_FILE       "/spiffs/announcement.alaw"
#define SPIFFS_TEMP       "/spiffs/announcement.alaw.tmp"
// Downloaded, per-locale announcement: "/spiffs/announcement-<lang>.alaw".
#define SPIFFS_LOCALIZED_PREFIX "/spiffs/announcement-"
#define SPIFFS_LOCALIZED_SUFFIX ".alaw"

static bool     s_spiffs_mounted = false;
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
    if (!s_spiffs_mounted) return -1;
    if (s_no_custom)       return -1;

    struct stat st;
    if (stat(SPIFFS_FILE, &st) != 0) {
        s_no_custom = true;
        return -1;
    }
    if (st.st_size <= 0 || (size_t)st.st_size > ANNOUNCEMENT_MAX_BYTES) {
        ESP_LOGW(TAG, "SPIFFS file has invalid size %ld, ignoring",
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

// Size of the downloaded announcement for the current ui_lang, or -1 if
// there is none / it is unusable. stat()-only, like custom_size(); not
// latched because it legitimately changes when the locale is switched or a
// fresh file is downloaded, and a missing file is the normal (not warned)
// case here.
static long localized_size(void)
{
    if (!s_spiffs_mounted) return -1;
    char path[48];
    announcement_localized_path(path, sizeof(path), config_ui_lang());
    struct stat st;
    if (stat(path, &st) != 0) return -1;
    if (st.st_size <= 0 || (size_t)st.st_size > ANNOUNCEMENT_MAX_BYTES) {
        return -1;
    }
    return (long)st.st_size;
}

esp_err_t announcement_init(void)
{
    esp_vfs_spiffs_conf_t cfg = {
        .base_path              = SPIFFS_BASE_PATH,
        .partition_label        = SPIFFS_LABEL,
        .max_files              = 2,
        .format_if_mount_failed = true,
    };
    esp_err_t err = esp_vfs_spiffs_register(&cfg);
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "SPIFFS mount failed: %s", esp_err_to_name(err));
        // Keep going — the embedded default will still work.
        return err;
    }
    s_spiffs_mounted = true;

    size_t total = 0, used = 0;
    if (esp_spiffs_info(SPIFFS_LABEL, &total, &used) == ESP_OK) {
        ESP_LOGI(TAG, "SPIFFS mounted: %u B used of %u B",
                 (unsigned)used, (unsigned)total);
    }
    // Legacy cleanup: older firmware uploaded via a temp file plus a
    // rename(). Drop any leftover temp so it doesn't waste a slot —
    // the current code writes the live file in place (see write_begin).
    unlink(SPIFFS_TEMP);
    return ESP_OK;
}

esp_err_t announcement_open(announcement_src_t *src)
{
    if (!src) return ESP_ERR_INVALID_ARG;
    src->file = NULL;
    src->mem  = NULL;
    src->pos  = 0;
    src->len  = 0;

    // Resolution order: user-uploaded custom file > downloaded localized
    // file for the active locale > nothing (empty source → silent pickup).
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
            return ESP_OK;
        }
        // Lost the race with a delete, or a SPIFFS hiccup. There is no
        // embedded fallback anymore — leave the source empty so the caller
        // answers silently and goes to BYE rather than serving nothing.
        ESP_LOGW(TAG, "fopen(%s): %s — no announcement, silent pickup",
                 path, strerror(errno));
    }
    src->len = 0;   // no audio available (see announcement.h)
    return ESP_OK;
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

esp_err_t announcement_write_begin(size_t total_bytes)
{
    if (!s_spiffs_mounted) return ESP_ERR_INVALID_STATE;
    if (s_write_file)      return ESP_ERR_INVALID_STATE;
    if (total_bytes == 0 || total_bytes > ANNOUNCEMENT_MAX_BYTES) {
        return ESP_ERR_INVALID_ARG;
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
        ESP_LOGE(TAG, "fopen(%s): %s", SPIFFS_FILE, strerror(errno));
        return ESP_FAIL;
    }
    // Crank the stdio buffer up: SPIFFS pays per flush, not per byte,
    // so bigger batched writes are noticeably faster than the default
    // BUFSIZ (~1 KB on IDF).
    static char s_write_bufio[8192];
    setvbuf(s_write_file, s_write_bufio, _IOFBF, sizeof(s_write_bufio));
    s_write_total = total_bytes;
    s_write_got   = 0;
    return ESP_OK;
}

esp_err_t announcement_write_append(const uint8_t *buf, size_t len)
{
    if (!s_write_file) return ESP_ERR_INVALID_STATE;
    if (!buf || len == 0) return ESP_ERR_INVALID_ARG;
    if (s_write_got + len > s_write_total) return ESP_ERR_INVALID_SIZE;
    size_t w = fwrite(buf, 1, len, s_write_file);
    if (w != len) {
        ESP_LOGE(TAG, "short write: %u of %u bytes", (unsigned)w, (unsigned)len);
        return ESP_FAIL;
    }
    s_write_got += len;
    return ESP_OK;
}

esp_err_t announcement_write_commit(void)
{
    if (!s_write_file) return ESP_ERR_INVALID_STATE;
    fclose(s_write_file);
    s_write_file = NULL;
    if (s_write_got != s_write_total) {
        ESP_LOGE(TAG, "commit: got %u of expected %u",
                 (unsigned)s_write_got, (unsigned)s_write_total);
        // Drop the partially written live file → fall back to default.
        unlink(SPIFFS_FILE);
        forget_custom_state();
        s_write_got = s_write_total = 0;
        return ESP_ERR_INVALID_SIZE;
    }
    size_t stored = s_write_got;
    s_write_got = s_write_total = 0;
    forget_custom_state();
    ESP_LOGI(TAG, "stored custom announcement: %u bytes", (unsigned)stored);
    return ESP_OK;
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

esp_err_t announcement_reset(void)
{
    if (!s_spiffs_mounted) return ESP_ERR_INVALID_STATE;
    if (unlink(SPIFFS_FILE) != 0 && errno != ENOENT) {
        ESP_LOGW(TAG, "unlink(%s): %s", SPIFFS_FILE, strerror(errno));
    }
    unlink(SPIFFS_TEMP);  // best-effort cleanup of any stale temp
    forget_custom_state();
    ESP_LOGI(TAG, "custom announcement reset (localized/silent takes over)");
    return ESP_OK;
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
