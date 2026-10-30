#include "webhook.h"

#include <stdlib.h>
#include <string.h>

#include "cJSON.h"
#include "config.h"
#include "http_util.h"
#include "net_local.h"
#include "platform.h"
#include "report_queue.h"
#include "stats.h"
#include "time_sync.h"
#include "web.h"
#include "web_access.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

static const char *TAG = "webhook";

// The receiver is in the home network and only has to accept the POST:
// a few seconds are plenty, and the worker also sends the spam reports.
#define WEBHOOK_TIMEOUT_MS 3000

static void on_call_listed(uint32_t seq)
{
    if (config_webhook_url()[0]) report_queue_enqueue_webhook(seq);
}

void webhook_start(void)
{
    stats_set_call_listener(on_call_listed);
}

bool webhook_url_valid(const char *url)
{
    if (!url) return false;
    if (!url[0]) return true;
    if (strlen(url) >= CONFIG_WEBHOOK_URL_CAP) return false;
    char host[CONFIG_WEBHOOK_URL_CAP];   // a part of the URL, so it fits
    return netloc_http_url_host(url, host, sizeof(host));
}

// The receiver's answer does not matter beyond its status.
static void discard_body(const void *data, size_t length, void *context)
{
    (void)data;
    (void)length;
    (void)context;
}

void webhook_deliver(uint32_t call_seq)
{
    // A copy: the web UI may change the setting while this runs.
    char url[CONFIG_WEBHOOK_URL_CAP];
    strncpy(url, config_webhook_url(), sizeof(url) - 1);
    url[sizeof(url) - 1] = '\0';
    if (!url[0]) return;

    stats_call_t call;
    if (!stats_get_call(call_seq, &call)) {
        pb_log_info(TAG, "call %u left the list before it was posted",
                    (unsigned)call_seq);
        return;
    }

    char host[CONFIG_WEBHOOK_URL_CAP];   // a part of the URL, so it fits
    if (!netloc_http_url_host(url, host, sizeof(host))) {
        pb_log_warn(TAG, "not sent: %s is not a plain http URL", url);
        return;
    }
    switch (web_host_locality(host)) {
        case WEB_HOST_LOCAL:
            break;
        case WEB_HOST_REMOTE:
            pb_log_warn(TAG, "not sent: %s is not in the home network", host);
            return;
        case WEB_HOST_UNRESOLVED:
            pb_log_warn(TAG, "not sent: cannot resolve %s", host);
            return;
    }

    // A spam call is posted when it has ended (stats_call_ended()), but
    // after the caller's BYE the stream may still be stopping; posting next
    // to it would compete for the heap like the spam report would.
    if (call.verdict == VERDICT_SPAM) report_queue_wait_stream_end();

    cJSON *json = web_call_json(&call, (int64_t)pb_monotonic_us(),
                                time_sync_now_epoch());
    char *body = json ? cJSON_PrintUnformatted(json) : NULL;
    cJSON_Delete(json);
    if (!body) {
        pb_log_warn(TAG, "not sent: out of memory");
        return;
    }

    const pb_http_header_t headers[] = {
        { "Content-Type", "application/json" },
        { "User-Agent",   http_util_user_agent() },
    };
    int status = 0;
    int result = pb_http_request("POST", url, headers,
                                 sizeof(headers) / sizeof(headers[0]),
                                 body, strlen(body), WEBHOOK_TIMEOUT_MS,
                                 NULL, 0, NULL, &status, discard_body, NULL,
                                 NULL);
    free(body);

    if (result != PB_HTTP_OK) {
        pb_log_warn(TAG, "POST to %s failed: %s", host, pb_http_err_name(result));
    } else if (status < 200 || status >= 300) {
        pb_log_warn(TAG, "POST to %s: HTTP %d", host, status);
    } else {
        pb_log_info(TAG, "call %u posted to %s (HTTP %d)",
                    (unsigned)call_seq, host, status);
    }
}
