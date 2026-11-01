#pragma once

// Link watchdog decision logic (issue #585), kept free of ESP-IDF so the
// host tests drive it with simulated time (test/test_link_watchdog.c).
//
// The failure it targets: after a reconnect the dongle can end up associated
// with a distant mesh repeater at about -80 dBm. The station still counts as
// connected (it has an IP, the Fritz!Box lists it as logged in), so neither
// the Wi-Fi reconnect path nor the failsafe in wifi.c ever fire — yet the
// web UI and SIP are dead. The AP choice happens only on (re)connect, so the
// remedy is a deliberate reconnect, whose full-channel scan picks the
// strongest AP again.
//
// wifi.c feeds link_wd_tick() every LINK_WD_TICK_S with a snapshot of the
// link and acts on the verdict. The triggers, each measured only while the
// station holds an IP without interruption:
//
//   - the SIP registrar has not answered for LINK_WD_REGISTRAR_DEAD_S, while
//     SIP is configured at all (no SIP configured never triggers), or
//   - the signal stayed below LINK_WD_RSSI_LOW_DBM for LINK_WD_RSSI_LOW_S
//     (every sample; one sample at or above the threshold restarts the
//     window).
//
// A genuinely bad location must not produce a reconnect storm: after an
// action the next one waits at least LINK_WD_BACKOFF_MIN_S, the gap doubling
// per action up to LINK_WD_BACKOFF_MAX_S. Once the link has been healthy for
// LINK_WD_HEALTHY_RESET_S, the gap starts small again.
//
// A call the dongle answered (announcement or dialog, which has no time
// limit) is never cut: while call_active the watchdog does not act, but its
// windows keep counting. A trigger that came due during the call fires on the
// first tick after it, if its condition still holds then; the backoff is not
// touched by the deferral.
//
// Nothing triggers before the first connection completed (both triggers need
// an uninterrupted IP period), nor while pairing (WPS, failsafe,
// provisioning, a scan) owns the station.

#include <stdbool.h>
#include <stdint.h>

#define LINK_WD_TICK_S             30u
#define LINK_WD_REGISTRAR_DEAD_S   (5u * 60u)
#define LINK_WD_RSSI_LOW_DBM       (-80)
#define LINK_WD_RSSI_LOW_S         (10u * 60u)
#define LINK_WD_BACKOFF_MIN_S      (10u * 60u)
#define LINK_WD_BACKOFF_MAX_S      (2u * 60u * 60u)
#define LINK_WD_HEALTHY_RESET_S    (60u * 60u)

typedef struct {
    bool     has_ip;           // station associated and holding an IP
    bool     pairing_active;   // WPS / failsafe / provisioning / scan in progress
    bool     rssi_valid;       // rssi below is known (associated)
    int      rssi;             // dBm of the associated AP
    bool     sip_configured;   // SIP registration task running
    // Seconds the registrar has gone unanswered (0 while it answers, and
    // while it has never answered since SIP was (re)configured — see
    // sip_register_unreachable_s()).
    uint32_t registrar_unreachable_s;
    // The dongle has a call in progress that it answered (SIP dialog
    // answered/streaming, or announcement/dialog media running). A call
    // that only rings at the other phones does not count.
    bool     call_active;
} link_wd_input_t;

typedef enum {
    LINK_WD_NONE = 0,
    LINK_WD_RECONNECT_REGISTRAR,   // registrar dead although Wi-Fi is up
    LINK_WD_RECONNECT_WEAK_SIGNAL, // RSSI persistently too low
} link_wd_action_t;

typedef struct {
    bool     ip_up;            // has_ip on the previous tick
    uint32_t ip_since_s;       // start of the current uninterrupted IP period
    bool     rssi_low;         // in a low-signal window
    uint32_t rssi_low_since_s;
    bool     healthy;          // in a healthy window
    uint32_t healthy_since_s;
    bool     acted;            // at least one action since the last reset
    uint32_t next_allowed_s;   // earliest time of the next action (if acted)
    uint32_t backoff_s;        // gap after the next action
    uint32_t last_gap_s;       // gap armed by the most recent action
} link_wd_t;

void link_wd_init(link_wd_t *wd);

// Evaluate one sample taken at monotonic time now_s (seconds). Returns the
// action to take now; LINK_WD_NONE in the common case. The caller performs
// the reconnect when anything else is returned — the watchdog has then
// already restarted its windows and armed the backoff.
link_wd_action_t link_wd_tick(link_wd_t *wd, uint32_t now_s, const link_wd_input_t *in);

// Gap (seconds) the watchdog will keep after the most recent action before
// it may act again — for the log line that announces an action.
uint32_t link_wd_last_gap_s(const link_wd_t *wd);
