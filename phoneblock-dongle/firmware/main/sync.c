#include "sync.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>



#include "api.h"
#include "config.h"
#include "phone_norm.h"
#include "scheduler.h"
#include "stats.h"
#include "platform.h"
#include "tr064.h"
#include "tr064_parse.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

static const char *TAG = "sync";

// The 24 h cadence and the manual-trigger wakeup now live in the shared
// scheduler task (see scheduler.c); sync.c only provides the run body,
// the status snapshot, and the trigger entry point the web UI calls.

static pb_mutex_t * s_lock = NULL;    // guards s_status updates
static sync_status_t     s_status;

static void set_status_running(bool running)
{
    pb_mutex_lock(s_lock);
    s_status.running = running;
    pb_mutex_unlock(s_lock);
}

static void set_status_result(bool ok, int pushed, int failed, int skipped,
                              const char *err)
{
    pb_mutex_lock(s_lock);
    s_status.ever_ran     = true;
    s_status.last_ok      = ok;
    s_status.running      = false;
    s_status.last_at_us   = pb_monotonic_us();
    s_status.last_pushed  = pushed;
    s_status.last_failed  = failed;
    s_status.last_skipped = skipped;
    if (err) {
        strncpy(s_status.last_error, err, sizeof(s_status.last_error) - 1);
        s_status.last_error[sizeof(s_status.last_error) - 1] = '\0';
    } else {
        s_status.last_error[0] = '\0';
    }
    pb_mutex_unlock(s_lock);
}

// Simple HTTP GET into a heap buffer. Returns bytes written, or -1.
typedef struct { char *buf; int len; int cap; } http_buf_t;

static void http_collect_cb(const void *data, size_t length, void *context)
{
    http_buf_t *buffer = context;
    int available = buffer->cap - buffer->len - 1;
    size_t copied = length < (size_t)(available > 0 ? available : 0)
                  ? length : (size_t)(available > 0 ? available : 0);
    if (copied > 0) {
        memcpy(buffer->buf + buffer->len, data, copied);
        buffer->len += (int)copied;
        buffer->buf[buffer->len] = 0;
    }
}

static int http_get_to_buf(const char *url, char *buf, int cap)
{
    http_buf_t output = { .buf = buf, .cap = cap };
    buf[0] = 0;
    const pb_http_header_t headers[] = { { "Accept", "application/xml" } };
    size_t length = 0;
    int status = 0;
    int result = pb_http_request("GET", url, headers, 1, NULL, 0, 10000,
                                 NULL, 0, &length, &status,
                                 http_collect_cb, &output, NULL);
    if (result != PB_HTTP_OK) {
        pb_log_err(TAG, "GET %s failed (result=%d)", url, result);
        return -1;
    }
    if (status != 200) {
        pb_log_err(TAG, "GET %s: HTTP %d", url, status);
        return -1;
    }
    return output.len;
}

// Contact parser lives in tr064_parse.c so the host test harness
// can exercise the real AVM XML shapes. sync.c only glues the
// callback plus per-entry PhoneBlock submission around it.

// Normalisation of a Fritz!Box call-barring entry into the E.164 form the
// PhoneBlock /api/rate endpoint accepts now lives in phone_norm.c, so it
// can be exercised by the host test suite. Wildcards ("+43*") and bare
// non-E.164 numbers are classified PHONE_SKIP and never reach the network
// (issue #469) — sending them produced HTTP 400s retried on every run.

typedef struct {
    const char *host;
    const char *app_user;
    const char *app_pass;
    int pushed;
    int failed;
    int skipped;
} run_ctx_t;

static void process_contact(const char *uid, const char *number, void *user)
{
    run_ctx_t *c = user;
    char normalised[48];
    if (phone_normalise(number, normalised, sizeof(normalised),
                        config_dial_prefix()) != PHONE_RATEABLE) {
        // Wildcard or non-normalisable entry — leave it in the Fritz!Box
        // silently. Deliberately not WARN: it recurs every sync run and
        // would flush the 32-entry log ring (issue #469). The run summary
        // reports the aggregate skip count at INFO.
        c->skipped++;
        return;
    }

    if (!phoneblock_rate(normalised, "B_MISSED", NULL)) {
        pb_log_warn(TAG, "rate failed for %s, keeping in Fritz!Box", normalised);
        c->failed++;
        return;
    }

    int code = 0;
    char detail[96] = "";
    int derr = tr064_call_barring_delete(
        c->host, 49000, c->app_user, c->app_pass, uid,
        &code, detail, sizeof(detail));
    if (derr != PB_OK) {
        pb_log_warn(TAG, "delete UID %s failed: code=%d %s — will retry next run",
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
        pb_log_info(TAG, "sync skipped — no Fritz!Box app credentials");
        set_status_result(false, 0, 0, 0, "not set up for Fritz!Box");
        return;
    }
    if (strlen(config_phoneblock_token()) == 0) {
        pb_log_info(TAG, "sync skipped — no PhoneBlock token");
        set_status_result(false, 0, 0, 0, "no PhoneBlock token");
        return;
    }
    // The config_sync_enabled() gate is applied in the task loop
    // only for the timer-driven path — a manual trigger always
    // reaches run_once, so the user can fire one-off syncs without
    // flipping the auto toggle on.

    pb_log_info(TAG, "sync run starting");
    set_status_running(true);

    char url[256] = "";
    int  code = 0;
    char detail[96] = "";
    int err = tr064_call_barring_list_url(
        host, 49000, app_user, app_pass,
        url, sizeof(url), &code, detail, sizeof(detail));
    if (err != PB_OK) {
        pb_log_err(TAG, "GetCallBarringList failed: code=%d %s", code, detail);
        char msg[80];
        snprintf(msg, sizeof(msg), "list: %.60s",
                 detail[0] ? detail : "network");
        set_status_result(false, 0, 0, 0, msg);
        return;
    }

    pb_log_info(TAG, "GET %s", url);
    char *xml = malloc(8192);
    if (!xml) {
        set_status_result(false, 0, 0, 0, "out of memory");
        return;
    }
    int len = http_get_to_buf(url, xml, 8192);
    if (len <= 0) {
        pb_log_err(TAG, "list download failed (len=%d)", len);
        free(xml);
        set_status_result(false, 0, 0, 0, "list download failed");
        return;
    }
    pb_log_info(TAG, "phonebook XML: %d bytes", len);
    // Emit the body in chunks — ESP_LOG caps per-line length at ~1 KB.
    for (int off = 0; off < len; off += 700) {
        int chunk = (len - off) > 700 ? 700 : (len - off);
        pb_log_info(TAG, "  [%d..%d] %.*s", off, off + chunk, chunk, xml + off);
    }

    run_ctx_t ctx = {
        .host = host, .app_user = app_user, .app_pass = app_pass,
        .pushed = 0, .failed = 0, .skipped = 0,
    };
    int total = tr064_parse_phonebook_contacts(xml, len,
                                                process_contact, &ctx);
    free(xml);
    pb_log_info(TAG, "sync done: %d contacts, %d pushed, %d failed, %d skipped",
             total, ctx.pushed, ctx.failed, ctx.skipped);

    // Skipped (unrateable) entries are not failures — a run with only
    // skips still reports ok, so the dashboard doesn't flag it red.
    set_status_result(ctx.failed == 0, ctx.pushed, ctx.failed, ctx.skipped,
                      NULL);
}

void sync_run(bool manual)
{
    if (!manual && !config_sync_enabled()) {
        // Scheduled run with auto-sync disabled — skip silently.
        // Manual triggers always run.
        return;
    }
    run_once();
}

bool sync_trigger_now(void)
{
    if (!s_lock) return false;
    pb_mutex_lock(s_lock);
    bool running = s_status.running;
    pb_mutex_unlock(s_lock);
    if (running) return false;
    // Hand off to the scheduler task, which runs sync_run(true) on its
    // own (8 KB) stack — sync must not run on the caller's httpd thread.
    return scheduler_request_sync();
}

void sync_snapshot(sync_status_t *out)
{
    if (!out) return;
    if (!s_lock) { memset(out, 0, sizeof(*out)); return; }
    pb_mutex_lock(s_lock);
    *out = s_status;
    pb_mutex_unlock(s_lock);
}

void sync_init(void)
{
    if (s_lock) return;
    memset(&s_status, 0, sizeof(s_status));
    s_lock = pb_mutex_create();
}
