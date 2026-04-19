#include "announcement.h"

#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>
#include <errno.h>

#include "esp_log.h"
#include "esp_spiffs.h"

static const char *TAG = "announcement";

// EMBED_FILES in main/CMakeLists.txt makes the linker emit these.
extern const uint8_t announcement_default_start[] asm("_binary_announcement_alaw_start");
extern const uint8_t announcement_default_end[]   asm("_binary_announcement_alaw_end");

#define SPIFFS_BASE_PATH  "/spiffs"
#define SPIFFS_LABEL      "storage"
#define SPIFFS_FILE       "/spiffs/announcement.alaw"
#define SPIFFS_TEMP       "/spiffs/announcement.alaw.tmp"

static bool     s_spiffs_mounted = false;
static uint8_t *s_cache           = NULL;    // NULL = use embedded default
static size_t   s_cache_len       = 0;
// Latch for "there is no custom file" so we don't keep stat()-ing
// SPIFFS on every /api/status poll. Cleared whenever a new file is
// written or the current one is reset.
static bool     s_no_custom       = false;

// Streaming-write session state (see announcement_write_begin).
static FILE    *s_write_file     = NULL;
static size_t   s_write_total    = 0;
static size_t   s_write_got      = 0;

static void invalidate_cache(void)
{
    if (s_cache) {
        free(s_cache);
        s_cache = NULL;
    }
    s_cache_len = 0;
    s_no_custom = false;
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
    return ESP_OK;
}

// Try to load the SPIFFS file into s_cache. Returns true on success.
static bool try_load_spiffs(void)
{
    if (!s_spiffs_mounted) return false;
    if (s_no_custom) return false;

    struct stat st;
    if (stat(SPIFFS_FILE, &st) != 0) {
        s_no_custom = true;
        return false;
    }
    if (st.st_size <= 0 || (size_t)st.st_size > ANNOUNCEMENT_MAX_BYTES) {
        ESP_LOGW(TAG, "SPIFFS file has invalid size %ld, ignoring",
                 (long)st.st_size);
        return false;
    }

    FILE *f = fopen(SPIFFS_FILE, "rb");
    if (!f) {
        ESP_LOGW(TAG, "fopen(%s): %s", SPIFFS_FILE, strerror(errno));
        return false;
    }

    uint8_t *buf = malloc(st.st_size);
    if (!buf) {
        ESP_LOGE(TAG, "OOM loading announcement (%ld bytes)", (long)st.st_size);
        fclose(f);
        return false;
    }

    size_t got = fread(buf, 1, st.st_size, f);
    fclose(f);
    if (got != (size_t)st.st_size) {
        ESP_LOGE(TAG, "short read: %u of %ld bytes", (unsigned)got, (long)st.st_size);
        free(buf);
        return false;
    }

    s_cache     = buf;
    s_cache_len = got;
    ESP_LOGI(TAG, "loaded custom announcement: %u bytes", (unsigned)got);
    return true;
}

esp_err_t announcement_get(const uint8_t **buf, size_t *len)
{
    if (!buf || !len) return ESP_ERR_INVALID_ARG;

    if (!s_cache) try_load_spiffs();
    if (s_cache) {
        *buf = s_cache;
        *len = s_cache_len;
        return ESP_OK;
    }
    *buf = announcement_default_start;
    *len = announcement_default_end - announcement_default_start;
    return ESP_OK;
}

esp_err_t announcement_write_begin(size_t total_bytes)
{
    if (!s_spiffs_mounted) return ESP_ERR_INVALID_STATE;
    if (s_write_file)      return ESP_ERR_INVALID_STATE;
    if (total_bytes == 0 || total_bytes > ANNOUNCEMENT_MAX_BYTES) {
        return ESP_ERR_INVALID_ARG;
    }
    s_write_file = fopen(SPIFFS_TEMP, "wb");
    if (!s_write_file) {
        ESP_LOGE(TAG, "fopen(%s): %s", SPIFFS_TEMP, strerror(errno));
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
        unlink(SPIFFS_TEMP);
        s_write_got = s_write_total = 0;
        return ESP_ERR_INVALID_SIZE;
    }
    // Atomic replace: the previous announcement stays intact if the
    // rename step itself fails.
    if (rename(SPIFFS_TEMP, SPIFFS_FILE) != 0) {
        ESP_LOGE(TAG, "rename(%s → %s): %s",
                 SPIFFS_TEMP, SPIFFS_FILE, strerror(errno));
        unlink(SPIFFS_TEMP);
        s_write_got = s_write_total = 0;
        return ESP_FAIL;
    }
    size_t stored = s_write_got;
    s_write_got = s_write_total = 0;
    invalidate_cache();
    ESP_LOGI(TAG, "stored custom announcement: %u bytes", (unsigned)stored);
    return ESP_OK;
}

void announcement_write_abort(void)
{
    if (s_write_file) {
        fclose(s_write_file);
        s_write_file = NULL;
    }
    unlink(SPIFFS_TEMP);
    s_write_got = s_write_total = 0;
}

esp_err_t announcement_reset(void)
{
    if (!s_spiffs_mounted) return ESP_ERR_INVALID_STATE;
    if (unlink(SPIFFS_FILE) != 0 && errno != ENOENT) {
        ESP_LOGW(TAG, "unlink(%s): %s", SPIFFS_FILE, strerror(errno));
    }
    invalidate_cache();
    ESP_LOGI(TAG, "announcement reset to embedded default");
    return ESP_OK;
}

bool announcement_is_custom(void)
{
    if (!s_cache) try_load_spiffs();
    return s_cache != NULL;
}

size_t announcement_length(void)
{
    const uint8_t *buf;
    size_t len;
    if (announcement_get(&buf, &len) != ESP_OK) return 0;
    return len;
}
