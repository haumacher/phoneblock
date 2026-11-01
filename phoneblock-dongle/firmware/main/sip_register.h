#pragma once

#include <stdbool.h>
#include <stdint.h>

// Start the SIP registration task. Reads credentials from NVS via
// config_sip_host()/config_sip_user()/config_sip_pass() — populated
// by the web setup wizard (TR-064 autoprovisioning or manual form).
// If SIP is not configured yet, the task is not started. Runs forever
// once started; keeps the registration alive via periodic re-REGISTER
// and log messages indicate state transitions.
void sip_register_start(void);

// True if the last REGISTER exchange completed with a 200 OK.
bool sip_register_is_registered(void);

// True while the registration task runs, i.e. SIP host and user are
// configured. Input of the Wi-Fi link watchdog (link_watchdog.h, #585).
bool sip_register_active(void);

// Seconds the registrar has gone unanswered: since the first REGISTER
// exchange (or receive on the SIP connection) that failed after the
// registrar last answered — any response counts, a rejection too. 0 while it
// answers, and also as long as it has not answered once since the task
// started or the SIP config was reloaded: a registrar that never answered is
// a setup problem, not a dead link, and must not make the watchdog cycle
// Wi-Fi. Input of the Wi-Fi link watchdog.
uint32_t sip_register_unreachable_s(void);

// True while the SIP dialog slot holds a call the dongle answered (200 OK
// sent, waiting for the ACK, or media streaming). Calls that only ring at
// the other phones (TRYING/PROCEEDING) or are being declined do not count.
// Input of the Wi-Fi link watchdog, which must not cut such a call.
bool sip_register_call_answered(void);

// The dongle's public IP as the registrar reported it via the Via
// "received=" parameter, or "" until the first REGISTER response has
// been seen. Shown in the web UI so the user can confirm what the
// dongle advertises for incoming UDP calls behind NAT.
const char *sip_register_public_ip(void);

// Signal the SIP task to tear down its current binding and re-register
// with whatever credentials are currently in config.c's cache. The
// request is latched; the task picks it up the next time its select()
// wakes up (capped at 500 ms by the loop, so worst case ~0.5 s
// before the new REGISTER goes out — plus `needs_settle` if asked).
//
// `needs_settle`: insert a 1.5 s pause before the new REGISTER. Pass
// `true` only after Fritz!Box TR-064 provisioning, where the box
// needs a beat to make the freshly created extension live on its own
// SIP stack — without the pause the first REGISTER hits a not-yet-
// live slot, times out, and falls into the 30 s retry. For manual
// edits and provider presets, pass `false` so the user does not eat
// 1.5 s of dead air after every Save.
void sip_register_request_reload(bool needs_settle);
