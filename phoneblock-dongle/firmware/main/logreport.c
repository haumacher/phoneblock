#include "logreport.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "esp_crt_bundle.h"
#include "esp_err.h"
#include "esp_http_client.h"
#include "esp_log.h"
#include "esp_system.h"

#include "config.h"
#include "http_util.h"
#include "stats.h"
#include "wifi.h"

static const char *TAG = "logreport";

#define LOGREPORT_HTTP_TIMEOUT_MS   30000

// Same heap headroom rationale as crashreport.c: the TLS handshake to
// phoneblock.net wants ~30 KB of bignum scratch for the cert-chain
// verify. Below this, defer to the next cycle rather than fail the
// handshake noisily.
#define LOGREPORT_MIN_FREE_HEAP     (64 * 1024)

// Upper bound for the assembled text body. The ring holds at most
// STATS_MAX_ERRORS (32) entries, each at most a tag plus a 128-byte
// message plus framing — comfortably under this. Sized with margin and
// used as a hard truncation guard so a malformed entry can never make us
// over-run the buffer.
#define LOGREPORT_BODY_CAP          6144

// High-water mark: the largest at_us already shipped. esp_timer time is
// uptime-relative and resets to ~0 on reboot, so after a reboot the
// current ring is shipped once more — which is intended (the reboot
// itself is worth surfacing). RAM-only; there is no value in persisting
// it across boots.
static int64_t s_reported_through_us = 0;

void logreport_flush(void)
{
    // Opt-in: the same toggle that governs crash reports ("send errors to
    // PhoneBlock"). Off → never ship anything.
    if (!config_crash_report_enabled()) return;
    if (strlen(config_phoneblock_token()) == 0) return;
    // No network → try again next cycle instead of failing a pointless
    // TLS handshake.
    if (!wifi_has_ip()) return;

    stats_error_t errs[STATS_MAX_ERRORS];
    int n = stats_snapshot_errors(errs, STATS_MAX_ERRORS);
    if (n <= 0) return;

    char *body = malloc(LOGREPORT_BODY_CAP);
    if (!body) return;

    // stats_snapshot_errors returns newest-first; walk it back to front so
    // the body reads oldest-first. Advance the high-water mark only to the
    // at_us of WARN/ERROR entries we actually append: esp_timer is
    // monotonic, so anything recorded after this snapshot has a larger
    // at_us and is caught next round — nothing is lost, and a truncated
    // tail is simply retried.
    size_t  len = 0;
    int     shipped = 0;
    int64_t shipped_through = s_reported_through_us;
    for (int i = n - 1; i >= 0; i--) {
        const stats_error_t *e = &errs[i];
        if (e->at_us <= s_reported_through_us) continue;   // already shipped

        // INFO is never shipped, even when the user enabled the INFO view
        // and the ring holds INFO lines.
        char lvl;
        if      (e->level == ESP_LOG_ERROR) lvl = 'E';
        else if (e->level == ESP_LOG_WARN)  lvl = 'W';
        else continue;

        int w = snprintf(body + len, LOGREPORT_BODY_CAP - len,
                         "%c +%llds %s: %s\n",
                         lvl, (long long)(e->at_us / 1000000),
                         e->tag, e->message);
        if (w < 0 || (size_t)w >= LOGREPORT_BODY_CAP - len) {
            // Wouldn't fit — stop here and leave the rest (and the
            // high-water mark) for the next cycle.
            break;
        }
        len += (size_t)w;
        shipped_through = e->at_us;
        shipped++;
    }

    if (shipped == 0) {
        // Nothing new at WARN/ERROR — the healthy path. No POST, no heap
        // pressure, no server load.
        free(body);
        return;
    }

    size_t free_heap = esp_get_free_heap_size();
    if (free_heap < LOGREPORT_MIN_FREE_HEAP) {
        // Defer; the high-water mark is untouched so we retry next cycle.
        free(body);
        return;
    }

    char auth[160];
    snprintf(auth, sizeof(auth), "Bearer %s", config_phoneblock_token());

    char url[200];
    snprintf(url, sizeof(url), "%s/api/dongle/log",
             config_phoneblock_base_url());

    esp_http_client_config_t cfg = {
        .url               = url,
        .method            = HTTP_METHOD_POST,
        .crt_bundle_attach = esp_crt_bundle_attach,
        .timeout_ms        = LOGREPORT_HTTP_TIMEOUT_MS,
        .auth_type         = HTTP_AUTH_TYPE_NONE,
    };
    esp_http_client_handle_t client = esp_http_client_init(&cfg);
    if (!client) {
        free(body);
        return;
    }
    // The User-Agent carries the firmware version and the stable per-device
    // id, so the server can attribute each line to a specific dongle (the
    // Bearer token already attributes it to the account/user).
    http_util_set_user_agent(client);
    esp_http_client_set_header(client, "Authorization", auth);
    esp_http_client_set_header(client, "Content-Type", "text/plain");
    esp_http_client_set_post_field(client, body, (int)len);

    esp_err_t err = esp_http_client_perform(client);
    int status = (err == ESP_OK) ? esp_http_client_get_status_code(client) : -1;
    esp_http_client_cleanup(client);
    free(body);

    if (status >= 200 && status < 300) {
        s_reported_through_us = shipped_through;
        // INFO, deliberately: a WARN/ERROR here would itself land in the
        // ring and ship next cycle as a self-amplifying "log upload"
        // entry. Same reason for the failure branch below.
        ESP_LOGI(TAG, "shipped %d log entr%s to server",
                 shipped, shipped == 1 ? "y" : "ies");
    } else {
        // Keep the high-water mark unchanged → retry next cycle.
        ESP_LOGI(TAG, "log upload failed (err=%s status=%d) — retry next cycle",
                 esp_err_to_name(err), status);
    }
}
