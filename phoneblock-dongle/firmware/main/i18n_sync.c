// Implementation of i18n_sync.h: fetch the asset manifest from the CDN,
// download the announcement (and mail pack) for the active ui_lang with
// SHA-256 verification, prune stale-locale files.

#ifndef ESP_PLATFORM
#define _POSIX_C_SOURCE 200809L
#endif

#include "i18n_sync.h"

#include <dirent.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <sys/stat.h>
#include <unistd.h>


#include "cJSON.h"


#include "announcement.h"
#include "config.h"
#include "http_util.h"
#include "platform.h"
#ifndef PHONEBLOCK_DATA_DIR
#define PHONEBLOCK_DATA_DIR "/var/lib/phoneblock"
#endif
#ifndef CONFIG_PHONEBLOCK_OTA_BASE_URL
#define CONFIG_PHONEBLOCK_OTA_BASE_URL "https://cdn.phoneblock.net/dongle/firmware"
#endif
#include "scheduler.h"
#include "storage.h"
#include "version_cmp.h"

static const char *TAG = "i18nsync";

#ifdef ESP_PLATFORM

#define SPIFFS_DIR       "/spiffs"
#else
#define SPIFFS_DIR       PHONEBLOCK_DATA_DIR
#endif
#define DOWNLOAD_CHUNK   1024
// Temp file path next to an asset: SPIFFS names are short, a Linux data
// directory can be anywhere.
#ifdef ESP_PLATFORM
#define TMP_PATH_CAP     80
#else
#define TMP_PATH_CAP     512
#endif
// Manifest is small JSON (a few assets per locale); cap generously and
// heap-allocate so a growing locale set never smashes the task stack.
#define MANIFEST_CAP     (8 * 1024)

static pb_mutex_t *   s_lock = NULL;
static i18n_sync_status_t  s_status;

// ---------------------------------------------------------------------------
// Small helpers.
// ---------------------------------------------------------------------------

static void set_status(bool ok, const char *lang, const char *err)
{
    if (!s_lock) return;
    pb_mutex_lock(s_lock);
    s_status.ever_ran   = true;
    s_status.last_ok    = ok;
    s_status.last_at_us = pb_monotonic_us();
    strncpy(s_status.lang, lang ? lang : "", sizeof(s_status.lang) - 1);
    s_status.lang[sizeof(s_status.lang) - 1] = '\0';
    if (ok || !err) {
        s_status.last_error[0] = '\0';
    } else {
        strncpy(s_status.last_error, err, sizeof(s_status.last_error) - 1);
        s_status.last_error[sizeof(s_status.last_error) - 1] = '\0';
    }
    pb_mutex_unlock(s_lock);
}



static void bytes_to_hex(const uint8_t *in, size_t n, char *out)
{
    static const char hex[] = "0123456789abcdef";
    for (size_t i = 0; i < n; i++) {
        out[i * 2]     = hex[in[i] >> 4];
        out[i * 2 + 1] = hex[in[i] & 0x0f];
    }
    out[n * 2] = '\0';
}

// SHA-256 of an existing file as lowercase hex, into out[65]. False if the
// file cannot be read.
static bool sha256_file_hex(const char *path, char out[65])
{
    FILE *f = fopen(path, "rb");
    if (!f) return false;
    pb_sha256_t *sha = pb_sha256_create();
    if (!sha) {
        fclose(f);
        return false;
    }
    uint8_t buf[DOWNLOAD_CHUNK];
    size_t n;
    while ((n = fread(buf, 1, sizeof(buf), f)) > 0) {
        if (pb_sha256_update(sha, buf, n) != PB_OK) {
            fclose(f);
            pb_sha256_destroy(sha);
            return false;
        }
    }
    bool read_ok = !ferror(f);
    fclose(f);
    uint8_t digest[32];
    bool hash_ok = pb_sha256_finish(sha, digest) == PB_OK;
    pb_sha256_destroy(sha);
    if (!hash_ok) return false;
    if (!read_ok) return false;
    bytes_to_hex(digest, sizeof(digest), out);
    return true;
}

// ---------------------------------------------------------------------------
// HTTP GET into a caller buffer (for the small manifest file).
// ---------------------------------------------------------------------------

static bool http_get_buf(const char *url, char *body, size_t cap,
                         char *err, size_t err_cap)
{
    const pb_http_header_t headers[] = {
        { "User-Agent", http_util_user_agent() },
    };
    pb_http_stream_t *stream = NULL;
    int64_t content_len = 0;
    int status = 0;
    int rc = pb_http_stream_open(url, headers, 1, 15000, &stream, &status,
                                 &content_len);
    if (rc != PB_HTTP_OK) {
        snprintf(err, err_cap, "open: %s", pb_http_err_name(rc));
        return false;
    }

    bool ok = false;
    if (status != 200) {
        snprintf(err, err_cap, "HTTP %d", status);
        goto out;
    }
    int total = 0;
    while (total < (int)cap - 1) {
        int n = pb_http_stream_read(stream, body + total, cap - 1 - total);
        if (n <= 0) break;
        total += n;
        if (content_len > 0 && total >= content_len) break;
    }
    body[total] = '\0';
    if (total == 0) {
        snprintf(err, err_cap, "empty body");
    } else if (content_len > 0 && total < content_len) {
        snprintf(err, err_cap, "truncated (%d/%lld)", total,
                 (long long)content_len);
    } else if (content_len <= 0 && total >= (int)cap - 1) {
        snprintf(err, err_cap, "manifest too large");
    } else {
        ok = true;
    }
out:
    pb_http_stream_close(stream);
    return ok;
}

// ---------------------------------------------------------------------------
// Streaming asset download → SHA-256 verify → atomic rename into place
// (both platforms).
// ---------------------------------------------------------------------------

// One asset download: `url` into `tmp_path`, SHA-256 checked against
// `expected_sha_hex`; `final_path` is the file the temp supersedes (handed
// to storage_make_room() so the old copy goes first when space is short).
typedef struct {
    const char *url;
    const char *final_path;
    const char *tmp_path;
    const char *expected_sha_hex;
} asset_dl_t;

// One download attempt of an asset into its temp file, SHA-256 checked
// (storage_attempt_fn). Removes the temp file again on any failure.
static storage_dl_t download_to_tmp(void *ctx, size_t *bytes,
                                    char *err, size_t err_cap)
{
    const asset_dl_t *a = ctx;
    storage_dl_t result = STORAGE_DL_FAILED;
    int64_t content_length = 0;
    int64_t total = 0;

    storage_file_t out;
    if (!storage_file_open(&out, a->tmp_path)) {
        snprintf(err, err_cap, "fopen %s: %s", a->tmp_path, strerror(errno));
        return STORAGE_DL_FAILED;
    }

    pb_http_stream_t *stream = NULL;
    pb_sha256_t *sha = pb_sha256_create();
    if (!sha) { snprintf(err, err_cap, "sha256 init"); goto out; }

    const pb_http_header_t headers[] = {
        { "User-Agent", http_util_user_agent() },
    };
    int status = 0;
    int rc = pb_http_stream_open(a->url, headers, 1, 30000, &stream, &status,
                                 &content_length);
    if (rc != PB_HTTP_OK) {
        snprintf(err, err_cap, "open: %s", pb_http_err_name(rc));
        goto out;
    }
    if (status != 200) { snprintf(err, err_cap, "HTTP %d", status); goto out; }

    if (content_length > 0) {
        if (content_length > ANNOUNCEMENT_MAX_BYTES) {
            snprintf(err, err_cap, "asset too large: %lld B", (long long)content_length);
            goto out;
        }
        // The assets have precedence over the community blocklist cache on
        // the shared partition (storage.h); then make the whole file
        // writable before the first byte arrives.
        storage_make_room((size_t)content_length, a->final_path);
        storage_gc(a->tmp_path, (size_t)content_length);
    }

    char buf[DOWNLOAD_CHUNK];
    for (;;) {
        int n = pb_http_stream_read(stream, buf, sizeof(buf));
        if (n < 0) { snprintf(err, err_cap, "read failed at %lld", (long long)total); goto out; }
        if (n == 0) break;
        if (pb_sha256_update(sha, buf, (size_t)n) != PB_OK) {
            snprintf(err, err_cap, "sha256 update failed");
            goto out;
        }
        if (!storage_file_write(&out, buf, (size_t)n)) {
            snprintf(err, err_cap, "fwrite short at %lld: %s",
                     (long long)total, strerror(errno));
            if (errno == ENOSPC) result = STORAGE_DL_FULL;
            goto out;
        }
        total += n;
        if (total > ANNOUNCEMENT_MAX_BYTES) {
            snprintf(err, err_cap, "asset too large: over %u B",
                     (unsigned)ANNOUNCEMENT_MAX_BYTES);
            goto out;
        }
    }
    pb_http_stream_close(stream);
    stream = NULL;

    if (!storage_file_close(&out)) {
        snprintf(err, err_cap, "close %s: %s", a->tmp_path, strerror(errno));
        if (errno == ENOSPC) result = STORAGE_DL_FULL;
        goto out;
    }

    uint8_t digest[32];
    char got_hex[65];
    if (pb_sha256_finish(sha, digest) != PB_OK) {
        snprintf(err, err_cap, "sha256 finish failed");
        goto out;
    }
    bytes_to_hex(digest, sizeof(digest), got_hex);
    if (strcasecmp(got_hex, a->expected_sha_hex) != 0) {
        snprintf(err, err_cap, "sha mismatch");
        goto out;
    }
    pb_sha256_destroy(sha);
    return STORAGE_DL_OK;

out:
    pb_http_stream_close(stream);
    storage_file_close(&out);
    pb_sha256_destroy(sha);
    unlink(a->tmp_path);
    *bytes = (size_t)(content_length > 0 ? content_length : total);
    return result;
}

static bool download_verify_rename(const char *url, const char *final_path,
                                   const char *expected_sha_hex,
                                   char *err, size_t err_cap)
{
    char tmp_path[TMP_PATH_CAP];
    snprintf(tmp_path, sizeof(tmp_path), "%s.tmp", final_path);
    unlink(tmp_path);

    asset_dl_t a = {
        .url              = url,
        .final_path       = final_path,
        .tmp_path         = tmp_path,
        .expected_sha_hex = expected_sha_hex,
    };
    if (!storage_download(tmp_path, download_to_tmp, &a, err, err_cap)) {
        return false;
    }

    // SPIFFS has no atomic replace: rename() onto an existing name fails, so
    // drop the live file first (same reasoning as blocklist_sync.c). The
    // window without a file is harmless — the announcement just falls back
    // to silence until the rename lands.
    unlink(final_path);
    if (rename(tmp_path, final_path) != 0) {
        snprintf(err, err_cap, "rename: %s", strerror(errno));
        unlink(tmp_path);
        return false;
    }
    pb_log_info(TAG, "downloaded → %s", final_path);

    return true;
}

// ---------------------------------------------------------------------------
// Prune localized files that are not for `keep_lang`.
// ---------------------------------------------------------------------------

// True if `name` is "<prefix><lang><suffix>" for some lang != keep_lang.
static bool is_stale_localized(const char *name, const char *prefix,
                               const char *suffix, const char *keep_lang)
{
    size_t pn = strlen(prefix), sn = strlen(suffix), nn = strlen(name);
    if (nn <= pn + sn) return false;
    if (strncmp(name, prefix, pn) != 0) return false;
    if (strcmp(name + nn - sn, suffix) != 0) return false;
    size_t lang_len = nn - pn - sn;
    return !(lang_len == strlen(keep_lang) &&
             strncmp(name + pn, keep_lang, lang_len) == 0);
}

static void prune_stale(const char *keep_lang)
{
    DIR *d = opendir(SPIFFS_DIR);
    if (!d) return;
    struct dirent *e;
    while ((e = readdir(d)) != NULL) {
        // readdir on SPIFFS yields the basename; match against the localized
        // announcement / mail-pack naming. The user-uploaded custom file
        // "announcement.alaw" has no "-<lang>" and never matches.
        bool stale =
            is_stale_localized(e->d_name, "announcement-", ".alaw", keep_lang) ||
            is_stale_localized(e->d_name, "mail-", ".json", keep_lang) ||
            is_stale_localized(e->d_name, "ui-", ".json", keep_lang) ||
            // Temp files a download interrupted by a reboot left behind, of
            // any locale (an empty keep_lang matches every one): nothing else
            // ever removes them, and they hold space for good.
            is_stale_localized(e->d_name, "announcement-", ".alaw.tmp", "") ||
            is_stale_localized(e->d_name, "mail-", ".json.tmp", "") ||
            is_stale_localized(e->d_name, "ui-", ".json.tmp", "");
        if (stale) {
            // Sized for SPIFFS_DIR + '/' + a full dirent name so the
            // formatted path can never be truncated (-Wformat-truncation).
            char path[sizeof(SPIFFS_DIR) + sizeof(e->d_name)];
            snprintf(path, sizeof(path), "%s/%s", SPIFFS_DIR, e->d_name);
            if (unlink(path) == 0) {
                pb_log_info(TAG, "pruned stale asset %s", e->d_name);
            }
        }
    }
    closedir(d);
}

// ---------------------------------------------------------------------------
// Download one manifest asset into final_path (skip if the on-disk SHA already
// matches). The asset is stored under the ui_lang name regardless of which
// fallback locale it came from — the device keeps exactly one file per kind.
// ---------------------------------------------------------------------------

static bool download_asset(cJSON *asset, const char *kind, const char *base_url,
                           const char *final_path, char *err, size_t err_cap)
{
    cJSON *jpath = cJSON_GetObjectItem(asset, "path");
    cJSON *jsha  = cJSON_GetObjectItem(asset, "sha256");
    if (!cJSON_IsString(jpath) || !cJSON_IsString(jsha) ||
        strlen(jsha->valuestring) != 64) {
        snprintf(err, err_cap, "%s: bad asset entry", kind);
        return false;
    }

    char on_disk[65];
    if (sha256_file_hex(final_path, on_disk) &&
        strcasecmp(on_disk, jsha->valuestring) == 0) {
        pb_log_info(TAG, "%s up to date", kind);
        return true;   // already have exactly this content
    }

    char url[256];
    snprintf(url, sizeof(url), "%s/%s", base_url, jpath->valuestring);
    // No log here: run_once() reports a failed kind in one WARN line.
    return download_verify_rename(url, final_path, jsha->valuestring,
                                  err, err_cap);

}

// Download a single asset of `kind` (announcement / mail) for the first locale
// in `chain` (ui_lang → en → de) whose manifest entry has it, storing it under
// final_path. Fetching only ONE file — the fallback is over which locale's
// content to pick, not over how many are kept. If no chain locale offers it,
// remove any stale file and treat as success (that content is unavailable, the
// caller's compiled/inline fallback or silence takes over).
static bool sync_kind_chain(cJSON *assets, const char *kind, const char *base_url,
                            const char *final_path, const char **chain, int chain_n,
                            char *err, size_t err_cap)
{
    for (int i = 0; i < chain_n; i++) {
        cJSON *lo = cJSON_GetObjectItem(assets, chain[i]);
        cJSON *asset = cJSON_IsObject(lo) ? cJSON_GetObjectItem(lo, kind) : NULL;
        if (cJSON_IsObject(asset)) {
            if (i > 0) pb_log_info(TAG, "%s: no '%s', falling back to '%s'",
                                kind, chain[0], chain[i]);
            return download_asset(asset, kind, base_url, final_path, err, err_cap);
        }
    }
    unlink(final_path);   // not offered in any chain locale → serve fallback
    return true;
}

// ---------------------------------------------------------------------------
// Run-loop.
// ---------------------------------------------------------------------------

static void run_once(void)
{
    const char *lang = config_ui_lang();   // validated, safe for URLs/paths
    // i18n is keyed by the release TAG. A release build reports its exact tag
    // (version.txt), e.g. "1.5.0" or "1.5.0-rc2" or "1.6.0-rc1" — each gets its
    // own bundle (an rc must be able to ship new strings). A dev build reports
    // git-describe, "<tag>-<N>-g<hash>[-dirty]"; we strip only that dev suffix
    // so it reuses its tag's bundle instead of needing a publish per commit.
    // release.sh publishes i18n under the same tag (VERSION).
    char tag[48];
    strncpy(tag, pb_firmware_version(), sizeof(tag) - 1);
    tag[sizeof(tag) - 1] = '\0';
    version_release_tag(tag);   // in-place strip of the git-describe dev suffix
    char base[160];
    snprintf(base, sizeof(base), "%s/%s/i18n",
             CONFIG_PHONEBLOCK_OTA_BASE_URL, tag);
    char err[64] = "";

    char *manifest = malloc(MANIFEST_CAP);
    if (!manifest) {
        set_status(false, lang, "out of memory");
        return;
    }

    char url[256];
    snprintf(url, sizeof(url), "%s/manifest.json", base);
    if (!http_get_buf(url, manifest, MANIFEST_CAP, err, sizeof(err))) {
        pb_log_warn(TAG, "manifest fetch %s: %s", url, err);
        free(manifest);
        set_status(false, lang, err);
        return;
    }

    cJSON *root = cJSON_Parse(manifest);
    free(manifest);
    if (!root) {
        set_status(false, lang, "manifest parse");
        return;
    }

    cJSON *assets = cJSON_GetObjectItem(root, "assets");
    if (!cJSON_IsObject(assets)) {
        pb_log_warn(TAG, "manifest has no assets object");
        cJSON_Delete(root);
        set_status(false, lang, "no assets");
        return;
    }

    // The announcement has NO embedded fallback (a missing recording means the
    // device answers silently), so we try to salvage SOME speech with a
    // download-time chain: prefer the active ui_lang, then English, then German
    // recording. Whichever is chosen is stored under the ui_lang name, so only
    // ONE announcement ever lives on the device (announcement.c reads it).
    const char *ann_chain[3];
    int acn = 0;
    ann_chain[acn++] = lang;
    if (strcmp(lang, "en") != 0) ann_chain[acn++] = "en";
    if (strcmp(lang, "de") != 0) ann_chain[acn++] = "de";

    char ann_path[48];
    announcement_localized_path(ann_path, sizeof(ann_path), lang);
    char mail_path[48];
    snprintf(mail_path, sizeof(mail_path), "%s/mail-%s.json", SPIFFS_DIR, lang);
    char ui_path[48];
    snprintf(ui_path, sizeof(ui_path), "%s/ui-%s.json", SPIFFS_DIR, lang);

    // Prune the PREVIOUS locale's assets BEFORE downloading the new ones. The
    // storage partition (~640 KB, shared with the blocklist) cannot hold two
    // locales at once — an announcement alone is ~80-96 KB — so downloading
    // first and pruning last overflowed SPIFFS mid-switch (fwrite short), which
    // left the new UI/mail pack half-written and falling back to English. Now
    // peak usage is one locale's assets. On-disk SHAs matching the manifest are
    // still skipped inside sync_kind_chain, so a no-op re-sync re-downloads
    // nothing. (A crash between prune and download self-heals on the next sync;
    // meanwhile the embedded English UI/mail and silent announcement cover it.)
    prune_stale(lang);

    // The user's own recorded announcement supersedes the localized one
    // (announcement_open() prefers it), so while there is one the localized
    // file is dead weight on the shared storage partition — up to 96 KB that
    // the community blocklist and a re-recording need more (#542). Neither
    // kept nor downloaded; announcement.c asks for a sync when the user's
    // recording goes away.
    bool custom_ann = announcement_is_custom();
    if (custom_ann && unlink(ann_path) == 0) {
        pb_log_info(TAG, "own announcement active — localized one removed");
    }

    // Download order is chosen for perceived responsiveness: the UI pack FIRST
    // (a browser waiting on the language switch re-renders as soon as it lands,
    // ~24 KB), then the small mail pack, and the big announcement (~80-96 KB)
    // LAST — it is only needed on the next answered call, so it must not delay
    // the visible UI update.
    //
    // Mail and UI packs both have an embedded ENGLISH fallback (mail_i18n.c /
    // web.c), so they download ONLY the active locale's pack — no en/de chain.
    // If it isn't published, download nothing and use the embedded English. (A
    // chain fallback would pull German for a missing locale, contradicting the
    // English fallback.) en itself is embedded, so downloading it is redundant
    // but harmless — the manifest publishes every locale uniformly.
    const char *single_chain[1] = { lang };
    bool ok = true;
    if (!sync_kind_chain(assets, "ui", base, ui_path, single_chain, 1,
                         err, sizeof(err))) {
        pb_log_warn(TAG, "ui pack sync: %s", err);
        ok = false;
    }
    char err2[64] = "";
    if (!sync_kind_chain(assets, "mail", base, mail_path, single_chain, 1,
                         err2, sizeof(err2))) {
        pb_log_warn(TAG, "mail pack sync: %s", err2);
        if (ok) { ok = false; strncpy(err, err2, sizeof(err) - 1); }
    }
    // The announcement has no embedded fallback (silence), so it uses the
    // [ui_lang, en, de] chain built above; downloaded last (biggest, least
    // time-critical).
    char err3[64] = "";
    if (!custom_ann &&
        !sync_kind_chain(assets, "announcement", base, ann_path, ann_chain, acn,
                         err3, sizeof(err3))) {
        pb_log_warn(TAG, "announcement sync: %s", err3);
        if (ok) { ok = false; strncpy(err, err3, sizeof(err) - 1); }
    }

    cJSON_Delete(root);
    set_status(ok, lang, ok ? NULL : err);
    if (ok) pb_log_info(TAG, "i18n assets in sync for '%s'", lang);
}

// ---------------------------------------------------------------------------
// Public API.
// ---------------------------------------------------------------------------

void i18n_sync_init(void)
{
    if (s_lock) return;
    memset(&s_status, 0, sizeof(s_status));
    s_lock = pb_mutex_create();
}

bool i18n_sync_run(void)
{
    if (!s_lock) return false;
    pb_mutex_lock(s_lock);
    s_status.running = true;
    pb_mutex_unlock(s_lock);

    run_once();

    pb_mutex_lock(s_lock);
    s_status.running = false;
    bool ok = s_status.last_ok;
    pb_mutex_unlock(s_lock);
    return ok;
}

bool i18n_sync_trigger_now(void)
{
    if (!s_lock) return false;
    pb_mutex_lock(s_lock);
    bool running = s_status.running;
    pb_mutex_unlock(s_lock);
    if (running) return false;
    return scheduler_request_i18n_sync();
}

void i18n_sync_snapshot(i18n_sync_status_t *out)
{
    if (!out) return;
    if (!s_lock) { memset(out, 0, sizeof(*out)); return; }
    pb_mutex_lock(s_lock);
    *out = s_status;
    pb_mutex_unlock(s_lock);
}

void i18n_sync_ui_path(char *out, size_t cap, const char *lang)
{
    snprintf(out, cap, "%s/ui-%s.json", SPIFFS_DIR, lang ? lang : "");
}

void i18n_sync_mail_path(char *out, size_t cap, const char *lang)
{
    snprintf(out, cap, "%s/mail-%s.json", SPIFFS_DIR, lang ? lang : "");
}

bool i18n_sync_have_ui_pack(const char *lang)
{
    if (!lang || !*lang) return false;
    char path[48];
    i18n_sync_ui_path(path, sizeof(path), lang);
    struct stat st;
    return stat(path, &st) == 0 && st.st_size > 0;
}
