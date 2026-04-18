#pragma once

#include <stddef.h>
#include "esp_err.h"

// TR-064 auto-provisioning: the dongle uses the Fritz!Box's SOAP API
// on port 49000 (plain HTTP, LAN-only) to create a fresh IP-phone
// entry for itself. This replaces the manual "Telefoniegeräte → Neues
// Gerät einrichten" clicking-around in the Fritz!Box admin UI.
//
// Reference implementation: ~/git/fritz_tr064 (Dart).
// Spec: https://avm.de/service/schnittstellen/ → TR-064_VoIP.pdf.

typedef struct {
    char sip_user[32];
    char sip_pass[48];
    char internal_number[16];
    // Filled in on failure: the Fritz!Box's own errorCode + Description
    // extracted from the UPnPError SOAP fault, so the caller can show a
    // specific hint to the user ("wrong password", "2FA required", …).
    int  error_code;         // 0 on success
    char error_message[128];
} tr064_sip_result_t;

// Create a new SIP client on the Fritz!Box and return the generated
// credentials. The caller typically hands these to config_update() so
// the SIP REGISTER loop picks them up.
//
// Flow internally:
//   1. InitChallenge on X_VoIP → receive nonce + realm from a 503 fault.
//   2. Compute MD5(MD5(user:realm:pass):nonce).
//   3. ClientAuth + X_AVM-DE_GetNumberOfClients → learn next free index.
//   4. ClientAuth + X_AVM-DE_SetClient4 with a generated username
//      ("phoneblock-<mac-suffix>") and random password → returns the
//      internal extension number assigned by the box.
//
// `host` may be an IP or hostname ("fritz.box", "192.168.178.1").
// `port` is 49000 for all current Fritz!Boxen.
// `phone_name` is the human-readable label shown in the Fritz!Box UI.
//
// Returns ESP_OK on success and fills `out`. Any failure returns an
// ESP_FAIL-ish error and leaves `out` untouched.
esp_err_t tr064_provision_sip_client(
    const char *host,
    int         port,
    const char *admin_user,
    const char *admin_pass,
    const char *phone_name,
    tr064_sip_result_t *out);
