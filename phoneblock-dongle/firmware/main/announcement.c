#include "announcement.h"

#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
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

static bool     s_spiffs_mounted = false;
static uint8_t *s_cache           = NULL;    // NULL = use embedded default
static size_t   s_cache_len       = 0;

static void invalidate_cache(void)
{
    if (s_cache) {
        free(s_cache);
        s_cache = NULL;
    }
    s_cache_len = 0;
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

    struct stat st;
    if (stat(SPIFFS_FILE, &st) != 0) return false;
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

esp_err_t announcement_write(const uint8_t *buf, size_t len)
{
    if (!s_spiffs_mounted) return ESP_ERR_INVALID_STATE;
    if (!buf || len == 0 || len > ANNOUNCEMENT_MAX_BYTES) {
        return ESP_ERR_INVALID_ARG;
    }

    FILE *f = fopen(SPIFFS_FILE, "wb");
    if (!f) {
        ESP_LOGE(TAG, "fopen(%s, wb): %s", SPIFFS_FILE, strerror(errno));
        return ESP_FAIL;
    }
    size_t written = fwrite(buf, 1, len, f);
    fclose(f);
    if (written != len) {
        ESP_LOGE(TAG, "short write: %u of %u bytes",
                 (unsigned)written, (unsigned)len);
        unlink(SPIFFS_FILE);
        return ESP_FAIL;
    }
    invalidate_cache();
    ESP_LOGI(TAG, "stored custom announcement: %u bytes", (unsigned)len);
    return ESP_OK;
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
