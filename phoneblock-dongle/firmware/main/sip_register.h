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
// wakes up (either from incoming traffic or at the next refresh
// deadline — worst case CONFIG_SIP_EXPIRES/2 seconds).
void sip_register_request_reload(void);
