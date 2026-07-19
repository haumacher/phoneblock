#include "mail_i18n.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "cJSON.h"
#include "esp_log.h"

#include "config.h"

static const char *TAG = "mail_i18n";

// Compiled-in German fallback — kept in sync with the German mail source
// scripts/i18n/l10n/mail/mail_de.arb (the translation source). Used whenever
// ui_lang is "de", the downloaded pack is missing/invalid, or a key is absent.
static const struct { const char *key, *val; } DE[] = {
    { "intro",                 "Statusmeldung deines PhoneBlock-Dongles." },

    { "subj.error_and_calls",  "PhoneBlock-Dongle: Fehler und neue Anrufe" },
    { "subj.error",            "PhoneBlock-Dongle: Fehler im Protokoll" },
    { "subj.calls",            "PhoneBlock-Dongle: Neue Anrufe" },
    { "subj.status",           "PhoneBlock-Dongle: Statusmeldung" },
    { "subj.update",           "PhoneBlock-Dongle: Firmware aktualisiert" },

    { "newcalls.one",          "Seit der letzten Meldung ist <b>1</b> neuer Anruf eingegangen." },
    { "newcalls.many",         "Seit der letzten Meldung sind <b>%d</b> neue Anrufe eingegangen." },

    { "sum.device",            "Ger&auml;t:" },
    { "sum.uptime",            "Laufzeit: %lldh %lldmin" },
    { "sum.calls",             "Anrufe gesamt: %u &nbsp;|&nbsp; SPAM blockiert: %u &nbsp;|&nbsp; durchgestellt: %u" },

    { "calls.heading",         "Letzte Anrufe" },
    { "calls.time",            "Zeit" },
    { "calls.number",          "Nummer" },
    { "calls.name",            "Name" },
    { "calls.rating",          "Bewertung" },

    { "verdict.scope.range",   "Bereich" },
    { "verdict.scope.number",  "Nummer" },
    { "verdict.spam_blacklist","SPAM (Blacklist, %s)" },
    { "verdict.spam_blocklist","SPAM (Blockliste, %s)" },
    { "verdict.spam",          "SPAM" },
    { "verdict.spam_votes",    "SPAM (%d direkt, %d Range)" },
    { "verdict.spam_suspect",  "SPAM-VERDACHT (%d direkt, %d Range)" },
    { "verdict.legitimate",    "legitim" },
    { "verdict.error",         "Fehler" },
    { "verdict.unknown",       "unbekannt" },

    { "log.heading",           "Neue Meldungen im Protokoll" },

    { "update.intro1",         "Die Firmware auf deinem " },
    { "update.link",           "PhoneBlock-Dongle" },
    { "update.intro2",         " wurde auf " },
    { "update.version_prefix", "Version " },
    { "update.intro3",         " aktualisiert." },
};

static const char *de_fallback(const char *key)
{
    for (size_t i = 0; i < sizeof(DE) / sizeof(DE[0]); i++) {
        if (strcmp(DE[i].key, key) == 0) return DE[i].val;
    }
    ESP_LOGW(TAG, "no fallback for key '%s'", key);
    return key;
}

// Lazily-loaded downloaded pack for the active non-German locale. Mutated
// only from the (single) mail build task, so no locking needed.
static cJSON *s_pack      = NULL;
static char   s_pack_lang[12] = "";

static void ensure_pack(const char *lang)
{
    if (strcmp(lang, s_pack_lang) == 0) return;   // already loaded (or tried)

    if (s_pack) { cJSON_Delete(s_pack); s_pack = NULL; }
    strncpy(s_pack_lang, lang, sizeof(s_pack_lang) - 1);
    s_pack_lang[sizeof(s_pack_lang) - 1] = '\0';

    if (strcmp(lang, "de") == 0) return;          // German uses the compiled table

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
    return de_fallback(key);
}
