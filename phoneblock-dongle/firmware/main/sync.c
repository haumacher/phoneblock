#include "sync.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "freertos/semphr.h"

#include "esp_log.h"
#include "esp_timer.h"
#include "esp_http_client.h"
#include "esp_crt_bundle.h"

#include "api.h"
#include "config.h"
#include "http_util.h"
#include "stats.h"
#include "tr064.h"

static const char *TAG = "sync";

// 24 h between scheduled runs, as per design.
#define SYNC_INTERVAL_US    (24LL * 3600LL * 1000000LL)

static TaskHandle_t     s_task       = NULL;
static SemaphoreHandle_t s_trigger   = NULL;    // binary semaphore
static SemaphoreHandle_t s_lock      = NULL;    // guards s_status updates
static sync_status_t    s_status;

static void set_status_running(bool running)
{
    xSemaphoreTake(s_lock, portMAX_DELAY);
    s_status.running = running;
    xSemaphoreGive(s_lock);
}

static void set_status_result(bool ok, int pushed, int failed, const char *err)
{
    xSemaphoreTake(s_lock, portMAX_DELAY);
    s_status.ever_ran    = true;
    s_status.last_ok     = ok;
    s_status.running     = false;
    s_status.last_at_us  = esp_timer_get_time();
    s_status.last_pushed = pushed;
    s_status.last_failed = failed;
    if (err) {
        strncpy(s_status.last_error, err, sizeof(s_status.last_error) - 1);
        s_status.last_error[sizeof(s_status.last_error) - 1] = '\0';
    } else {
        s_status.last_error[0] = '\0';
    }
    xSemaphoreGive(s_lock);
}

// Simple HTTP GET into a heap buffer. Returns bytes written, or -1.
typedef struct { char *buf; int len; int cap; } http_buf_t;

static esp_err_t http_collect_cb(esp_http_client_event_t *evt)
{
    http_buf_t *b = evt->user_data;
    if (evt->event_id == HTTP_EVENT_ON_DATA) {
        // esp_http_client already de-chunks chunked bodies before the
        // ON_DATA callback — copy unconditionally. Some earlier code
        // in this project gated on !is_chunked_response() which drops
        // exactly the payloads we care about here.
        int remaining = b->cap - b->len - 1;
        int copy = evt->data_len < remaining ? evt->data_len : remaining;
        if (copy > 0) {
            memcpy(b->buf + b->len, evt->data, copy);
            b->len += copy;
            b->buf[b->len] = '\0';
        }
    }
    return ESP_OK;
}

static int http_get_to_buf(const char *url, char *buf, int cap)
{
    http_buf_t hb = { .buf = buf, .len = 0, .cap = cap };
    buf[0] = '\0';
    esp_http_client_config_t cfg = {
        .url               = url,
        .method            = HTTP_METHOD_GET,
        .event_handler     = http_collect_cb,
        .user_data         = &hb,
        .crt_bundle_attach = esp_crt_bundle_attach,
        .timeout_ms        = 10000,
    };
    esp_http_client_handle_t c = esp_http_client_init(&cfg);
    if (!c) return -1;
    http_util_set_user_agent(c);
    esp_err_t err = esp_http_client_perform(c);
    int status = (err == ESP_OK) ? esp_http_client_get_status_code(c) : 0;
    esp_http_client_cleanup(c);
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "GET %s: %s", url, esp_err_to_name(err));
        return -1;
    }
    if (status != 200) {
        ESP_LOGE(TAG, "GET %s: HTTP %d", url, status);
        return -1;
    }
    return hb.len;
}

// Extract the text content of the first <tag>…</tag> inside the region
// [from, from+len). Returns bytes copied or -1 on miss.
static int find_inside(const char *from, int len, const char *tag,
                       char *out, size_t out_cap)
{
    char open[32], close[32];
    snprintf(open,  sizeof(open),  "<%s>",  tag);
    snprintf(close, sizeof(close), "</%s>", tag);
    const char *a = memmem(from, len, open,  strlen(open));
    if (!a) return -1;
    a += strlen(open);
    int rem = len - (a - from);
    const char *b = memmem(a, rem, close, strlen(close));
    if (!b) return -1;
    int n = b - a;
    if ((size_t)n >= out_cap) n = out_cap - 1;
    memcpy(out, a, n);
    out[n] = '\0';
    return n;
}

// Walk a Fritz!Box phonebook-style XML and invoke cb() for each
// <contact> block, passing its (uid, number). Returns the number of
// blocks iterated.
typedef void (*contact_cb_t)(const char *uid, const char *number, void *user);

static int parse_contacts(const char *xml, int xml_len,
                          contact_cb_t cb, void *user)
{
    int count = 0;
    const char *p = xml;
    int remaining = xml_len;
    while (1) {
        const char *open  = memmem(p,    remaining, "<contact>",  9);
        if (!open) break;
        const char *close = memmem(open, remaining - (open - p),
                                   "</contact>", 10);
        if (!close) break;
        int block_len = (close - open) + 10;
        char uid[32]    = "";
        char number[48] = "";
        find_inside(open, block_len, "uniqueid", uid,    sizeof(uid));
        find_inside(open, block_len, "number",   number, sizeof(number));
        if (uid[0] && number[0]) {
            cb(uid, number, user);
            count++;
        }
        p = close + 10;
        remaining = xml_len - (p - xml);
        if (remaining <= 0) break;
    }
    return count;
}

// Convert what we read from the Fritz!Box into a form the PhoneBlock
// /api/rate endpoint accepts. The server is lenient but prefers +49…
// E.164. "00…" → "+…", leading-zero national → "+49…". Anything
// already starting with "+" or "*" is passed through.
static void normalise_phone(const char *in, char *out, size_t cap)
{
    if (!in || !*in) { out[0] = '\0'; return; }
    if (in[0] == '+' || in[0] == '*') {
        strncpy(out, in, cap - 1);
        out[cap - 1] = '\0';
        return;
    }
    if (in[0] == '0' && in[1] == '0') {
        snprintf(out, cap, "+%s", in + 2);
        return;
    }
    if (in[0] == '0') {
        snprintf(out, cap, "+49%s", in + 1);
        return;
    }
    strncpy(out, in, cap - 1);
    out[cap - 1] = '\0';
}

typedef struct {
    const char *host;
    const char *app_user;
    const char *app_pass;
    int pushed;
    int failed;
} run_ctx_t;

static void process_contact(const char *uid, const char *number, void *user)
{
    run_ctx_t *c = user;
    char normalised[48];
    normalise_phone(number, normalised, sizeof(normalised));
    if (!normalised[0]) { c->failed++; return; }

    if (!phoneblock_rate(normalised, "B_MISSED", NULL)) {
        ESP_LOGW(TAG, "rate failed for %s, keeping in Fritz!Box", normalised);
        c->failed++;
        return;
    }

    int code = 0;
    char detail[96] = "";
    esp_err_t derr = tr064_call_barring_delete(
        c->host, 49000, c->app_user, c->app_pass, uid,
        &code, detail, sizeof(detail));
    if (derr != ESP_OK) {
        ESP_LOGW(TAG, "delete UID %s failed: code=%d %s — will retry next run",
                 uid, code, detail);
        // Rated but not deleted — next run will try again. Since we
        // rate before deleting, the server-side idempotency has to
        // absorb the duplicate push. That's fine; /rate overwrites
        // the user's own rating for the same (user,phone) pair.
        c->failed++;
        return;
    }
    c->pushed++;
}

static void run_once(void)
{
    const char *host     = config_sip_host();
    const char *app_user = config_fritzbox_app_user();
    const char *app_pass = config_fritzbox_app_pass();
    if (!host[0] || !app_user[0] || !app_pass[0]) {
        ESP_LOGI(TAG, "sync skipped — no Fritz!Box app credentials");
        set_status_result(false, 0, 0, "not set up for Fritz!Box");
        return;
    }
    if (strlen(config_phoneblock_token()) == 0) {
        ESP_LOGI(TAG, "sync skipped — no PhoneBlock token");
        set_status_result(false, 0, 0, "no PhoneBlock token");
        return;
    }
    // The config_sync_enabled() gate is applied in the task loop
    // only for the timer-driven path — a manual trigger always
    // reaches run_once, so the user can fire one-off syncs without
    // flipping the auto toggle on.

    ESP_LOGI(TAG, "sync run starting");
    set_status_running(true);

    char url[256] = "";
    int  code = 0;
    char detail[96] = "";
    esp_err_t err = tr064_call_barring_list_url(
        host, 49000, app_user, app_pass,
        url, sizeof(url), &code, detail, sizeof(detail));
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "GetCallBarringList failed: code=%d %s", code, detail);
        char msg[80];
        snprintf(msg, sizeof(msg), "list: %.60s",
                 detail[0] ? detail : "network");
        set_status_result(false, 0, 0, msg);
        stats_record_error("sync", msg);
        return;
    }

    ESP_LOGI(TAG, "GET %s", url);
    char *xml = malloc(8192);
    if (!xml) {
        set_status_result(false, 0, 0, "out of memory");
        return;
    }
    int len = http_get_to_buf(url, xml, 8192);
    if (len <= 0) {
        ESP_LOGE(TAG, "list download failed (len=%d)", len);
        free(xml);
        set_status_result(false, 0, 0, "list download failed");
        return;
    }
    ESP_LOGI(TAG, "phonebook XML: %d bytes, head: %.200s", len, xml);

    run_ctx_t ctx = {
        .host = host, .app_user = app_user, .app_pass = app_pass,
        .pushed = 0, .failed = 0,
    };
    int total = parse_contacts(xml, len, process_contact, &ctx);
    free(xml);
    ESP_LOGI(TAG, "sync done: %d contacts, %d pushed, %d failed",
             total, ctx.pushed, ctx.failed);

    set_status_result(ctx.failed == 0, ctx.pushed, ctx.failed, NULL);
}

static void sync_task(void *arg)
{
    (void)arg;
    TickType_t timeout = pdMS_TO_TICKS(SYNC_INTERVAL_US / 1000);
    // On first boot don't fire immediately — the user may still be
    // in the middle of setup. Wait one interval before the first
    // scheduled run; a manual trigger can fire sooner.
    while (1) {
        bool manual = (xSemaphoreTake(s_trigger, timeout) == pdTRUE);
        if (!manual && !config_sync_enabled()) {
            // Scheduled run with auto-sync disabled — skip silently.
            // Manual triggers always run (see run_once()).
            continue;
        }
        run_once();
    }
}

bool sync_trigger_now(void)
{
    if (!s_task || !s_trigger) return false;
    xSemaphoreTake(s_lock, portMAX_DELAY);
    bool running = s_status.running;
    xSemaphoreGive(s_lock);
    if (running) return false;
    xSemaphoreGive(s_trigger);
    return true;
}

void sync_snapshot(sync_status_t *out)
{
    if (!out) return;
    if (!s_lock) { memset(out, 0, sizeof(*out)); return; }
    xSemaphoreTake(s_lock, portMAX_DELAY);
    *out = s_status;
    xSemaphoreGive(s_lock);
}

void sync_start(void)
{
    if (s_task) return;
    memset(&s_status, 0, sizeof(s_status));
    s_lock    = xSemaphoreCreateMutex();
    s_trigger = xSemaphoreCreateBinary();
    xTaskCreate(sync_task, "sync", 6144, NULL, 3, &s_task);
}
