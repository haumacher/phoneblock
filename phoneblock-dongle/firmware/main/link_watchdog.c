#include "link_watchdog.h"

#include <string.h>

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

// Pure decision logic, no ESP-IDF — see link_watchdog.h. Host-tested in
// test/test_link_watchdog.c.

void link_wd_init(link_wd_t *wd)
{
    memset(wd, 0, sizeof(*wd));
    wd->backoff_s = LINK_WD_BACKOFF_MIN_S;
}

uint32_t link_wd_last_gap_s(const link_wd_t *wd)
{
    return wd->last_gap_s;
}

link_wd_action_t link_wd_tick(link_wd_t *wd, uint32_t now_s, const link_wd_input_t *in)
{
    // No IP, or the pairing machinery owns the station: the regular
    // reconnect / failsafe paths in wifi.c are in charge. Every window
    // restarts once the link is back.
    if (in->pairing_active || !in->has_ip) {
        wd->ip_up    = false;
        wd->rssi_low = false;
        wd->healthy  = false;
        return LINK_WD_NONE;
    }
    if (!wd->ip_up) {
        wd->ip_up      = true;
        wd->ip_since_s = now_s;
    }
    uint32_t up_s = now_s - wd->ip_since_s;

    // Low-signal window: every sample must be below the threshold.
    bool low = in->rssi_valid && in->rssi < LINK_WD_RSSI_LOW_DBM;
    if (!low) {
        wd->rssi_low = false;
    } else if (!wd->rssi_low) {
        wd->rssi_low         = true;
        wd->rssi_low_since_s = now_s;
    }

    // Healthy window: good signal and (if SIP is used) a registrar that
    // answers. Long enough of it forgets the backoff history.
    bool sip_silent = in->sip_configured && in->registrar_unreachable_s > 0;
    if (low || sip_silent) {
        wd->healthy = false;
    } else if (!wd->healthy) {
        wd->healthy         = true;
        wd->healthy_since_s = now_s;
    } else if (wd->acted && now_s - wd->healthy_since_s >= LINK_WD_HEALTHY_RESET_S) {
        wd->acted     = false;
        wd->backoff_s = LINK_WD_BACKOFF_MIN_S;
    }

    link_wd_action_t action = LINK_WD_NONE;
    if (in->sip_configured) {
        // Count the registrar's silence only from the start of this IP
        // period: a registrar that went silent while Wi-Fi was down (router
        // reboot) gets the full grace time once the link is back.
        uint32_t dead_s = in->registrar_unreachable_s < up_s
                        ? in->registrar_unreachable_s : up_s;
        if (dead_s >= LINK_WD_REGISTRAR_DEAD_S) {
            action = LINK_WD_RECONNECT_REGISTRAR;
        }
    }
    if (action == LINK_WD_NONE && wd->rssi_low
            && now_s - wd->rssi_low_since_s >= LINK_WD_RSSI_LOW_S) {
        action = LINK_WD_RECONNECT_WEAK_SIGNAL;
    }
    if (action == LINK_WD_NONE) {
        return LINK_WD_NONE;
    }

    // Never cut a call the dongle answered. Windows and backoff stay as they
    // are, so the action fires on the first tick after the call — if the
    // condition still holds then.
    if (in->call_active) {
        return LINK_WD_NONE;
    }

    // Backoff: the condition stays armed, the action waits for its slot.
    if (wd->acted && (int32_t)(now_s - wd->next_allowed_s) < 0) {
        return LINK_WD_NONE;
    }

    wd->acted          = true;
    wd->last_gap_s     = wd->backoff_s;
    wd->next_allowed_s = now_s + wd->backoff_s;
    wd->backoff_s      = wd->backoff_s * 2 > LINK_WD_BACKOFF_MAX_S
                       ? LINK_WD_BACKOFF_MAX_S : wd->backoff_s * 2;

    // The reconnect starts a new association: fresh windows.
    wd->ip_since_s = now_s;
    wd->rssi_low   = false;
    wd->healthy    = false;
    return action;
}
