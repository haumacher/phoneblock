#include "logreport.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "esp_log.h"
#include "esp_system.h"

#include "api.h"
#include "config.h"
#include "stats.h"
#include "wifi.h"

static const char *TAG = "logreport";

// Same heap headroom rationale as crashreport.c: a full TLS handshake to
// phoneblock.net wants ~30 KB of bignum scratch for the cert-chain
// verify. The upload normally resumes the selftest's session ticket and
// skips that, but the per-worker ticket can miss and force a full
// handshake — so keep the guard. Below this, defer to the next cycle
// rather than fail the handshake noisily.
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

// Snapshots the WARN/ERROR ring and assembles the upload body on the
// heap. The 32-entry stats_error_t snapshot (~5 KB) lives only in this
// frame and is gone the moment it returns — so it is not sitting on the
// stack underneath the subsequent TLS handshake. That matters: the
// upload runs on the 8 KB selftest task, and a full handshake's P-384
// cert-chain verify already comes close to that limit on its own. The
// 1.3.5 field crashes were exactly this — the ~5 KB array plus the
// handshake tipped the task over its stack guard.
//
// Returns the malloc'd body (caller frees) and fills *out_len /
// *out_through / *out_shipped, or NULL when there is nothing new to ship.
static char *logreport_build_body(size_t *out_len, int64_t *out_through,
                                  int *out_shipped)
{
    stats_error_t errs[STATS_MAX_ERRORS];
    int n = stats_snapshot_errors(errs, STATS_MAX_ERRORS);
    if (n <= 0) return NULL;

    // De-noising rule: a lone WARN is almost always transient or benign —
    // ESP-IDF's httpd logs every client socket reset (errno 104/113) at
    // WARN, a single self-healed retry warns once, a brief WiFi flap warns
    // and reconnects. Shipping that drip is pure noise. Only report when a
    // *new* ERROR is present (a failure the firmware itself flagged), and
    // then ship the whole new WARN/ERROR window so the surrounding context
    // rides along. When there's no new error we deliberately leave the
    // high-water mark untouched, so the benign WARNs accumulate (bounded
    // by the 32-entry ring) and are still there as context the moment an
    // error eventually does fire.
    bool have_new_error = false;
    for (int i = 0; i < n; i++) {
        if (errs[i].at_us > s_reported_through_us
                && errs[i].level == ESP_LOG_ERROR) {
            have_new_error = true;
            break;
        }
    }
    if (!have_new_error) return NULL;

    char *body = malloc(LOGREPORT_BODY_CAP);
    if (!body) return NULL;

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
        return NULL;
    }

    *out_len     = len;
    *out_through = shipped_through;
    *out_shipped = shipped;
    return body;
}

void logreport_flush(void)
{
    // Opt-in: the same toggle that governs crash reports ("send errors to
    // PhoneBlock"). Off → never ship anything.
    if (!config_crash_report_enabled()) return;
    if (strlen(config_phoneblock_token()) == 0) return;
    // No network → try again next cycle instead of failing a pointless
    // TLS handshake.
    if (!wifi_has_ip()) return;

    size_t  len;
    int64_t shipped_through;
    int     shipped;
    char *body = logreport_build_body(&len, &shipped_through, &shipped);
    if (!body) return;   // nothing new to ship; errs[] already off the stack

    size_t free_heap = esp_get_free_heap_size();
    if (free_heap < LOGREPORT_MIN_FREE_HEAP) {
        // Defer; the high-water mark is untouched so we retry next cycle.
        free(body);
        return;
    }

    // Hands off to the shared session-resuming client (POST
    // /api/dongle/log). The selftest just primed the TLS ticket, so this
    // normally resumes it and skips the cert-chain verify entirely.
    int status = phoneblock_post_log(body, len);
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
        ESP_LOGI(TAG, "log upload failed (status=%d) — retry next cycle", status);
    }
}
