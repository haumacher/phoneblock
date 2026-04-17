#pragma once

#include <stdbool.h>

// Start the SIP registration task. Reads credentials from Kconfig
// (CONFIG_SIP_REGISTRAR_HOST/PORT, CONFIG_SIP_USERNAME, CONFIG_SIP_PASSWORD,
// CONFIG_SIP_EXPIRES) and keeps the registration alive via periodic
// re-REGISTER. Runs forever; log messages indicate state transitions.
void sip_register_start(void);

// True if the last REGISTER exchange completed with a 200 OK.
bool sip_register_is_registered(void);
