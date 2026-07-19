#include "mail_i18n.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "cJSON.h"
#include "esp_log.h"

#include "config.h"

static const char *TAG = "mail_i18n";

// Embedded ENGLISH mail pack — the universal offline fallback, baked into the
// image at build from i18n/l10n/mail/mail_en.arb (stripped to mail_en.json, see
// main/CMakeLists.txt). Used whenever the downloaded pack for the active
// ui_lang is missing/invalid, or a key is absent from it. This mirrors the web
// UI, which embeds the English ui_en pack the same way — German is NOT compiled
// in; it is a normal downloaded locale (mail_de.arb is the translation source).
extern const char mail_en_json_start[] asm("_binary_mail_en_json_start");
extern const char mail_en_json_end[]   asm("_binary_mail_en_json_end");

// Parsed lazily on first use, then kept forever (its valuestrings must outlive
// every mail_i18n_str() return). Only ever touched from the single mail task.
static cJSON *s_en = NULL;

static const char *en_fallback(const char *key)
{
    if (!s_en) {
        // target_add_binary_data appends a NUL terminator, so the blob is a
        // C string; the -1 drops it from the parsed length.
        s_en = cJSON_ParseWithLength(mail_en_json_start,
                                     (size_t)(mail_en_json_end - mail_en_json_start - 1));
        if (!s_en) ESP_LOGE(TAG, "embedded English mail pack did not parse");
    }
    if (s_en) {
        cJSON *v = cJSON_GetObjectItem(s_en, key);
        if (cJSON_IsString(v) && v->valuestring && v->valuestring[0]) {
            return v->valuestring;
        }
    }
    ESP_LOGW(TAG, "no fallback for key '%s'", key);
    return key;
}

// Lazily-loaded downloaded pack for the active locale. Mutated only from the
// (single) mail build task, so no locking needed.
static cJSON *s_pack      = NULL;
static char   s_pack_lang[12] = "";

static void ensure_pack(const char *lang)
{
    if (strcmp(lang, s_pack_lang) == 0) return;   // already loaded (or tried)

    if (s_pack) { cJSON_Delete(s_pack); s_pack = NULL; }
    strncpy(s_pack_lang, lang, sizeof(s_pack_lang) - 1);
    s_pack_lang[sizeof(s_pack_lang) - 1] = '\0';

    char path[48];
    snprintf(path, sizeof(path), "/spiffs/mail-%s.json", lang);
    FILE *f = fopen(path, "rb");
    if (!f) return;                               // not downloaded yet → fallback
    fseek(f, 0, SEEK_END);
    long sz = ftell(f);
    fseek(f, 0, SEEK_SET);
    if (sz <= 0 || sz > 16 * 1024) { fclose(f); return; }
    char *buf = malloc((size_t)sz + 1);
    if (!buf) { fclose(f); return; }
    size_t got = fread(buf, 1, (size_t)sz, f);
    fclose(f);
    buf[got] = '\0';
    s_pack = cJSON_Parse(buf);
    free(buf);
    if (!s_pack) ESP_LOGW(TAG, "mail pack %s did not parse", path);
    else         ESP_LOGI(TAG, "loaded mail pack for '%s'", lang);
}

const char *mail_i18n_str(const char *key)
{
    ensure_pack(config_ui_lang());
    if (s_pack) {
        cJSON *v = cJSON_GetObjectItem(s_pack, key);
        if (cJSON_IsString(v) && v->valuestring && v->valuestring[0]) {
            return v->valuestring;
        }
    }
    return en_fallback(key);
}
