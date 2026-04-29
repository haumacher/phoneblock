#pragma once

#include <stdbool.h>

// Start the SIP registration task. Reads credentials from NVS via
// config_sip_host()/config_sip_user()/config_sip_pass() — populated
// by the web setup wizard (TR-064 autoprovisioning or manual form).
// If SIP is not configured yet, the task is not started. Runs forever
// once started; keeps the registration alive via periodic re-REGISTER
// and log messages indicate state transitions.
void sip_register_start(void);

// True if the last REGISTER exchange completed with a 200 OK.
bool sip_register_is_registered(void);

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
