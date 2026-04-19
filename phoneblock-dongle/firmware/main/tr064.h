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
// `token_2fa` is an optional <avm:token>-header value obtained from
// tr064_auth_start + tr064_auth_poll once the user completed the
// second factor. Pass NULL for the initial attempt; on 2FA-enforced
// boxes this fails with out->error_code == 866 and the caller then
// drives the 2FA handshake and retries with the received token.
//
// Returns ESP_OK on success and fills `out`. On failure out->error_code
// and out->error_message carry the Fritz!Box's UPnPError details.
esp_err_t tr064_provision_sip_client(
    const char *host,
    int         port,
    const char *admin_user,
    const char *admin_pass,
    const char *phone_name,
    const char *token_2fa,
    tr064_sip_result_t *out);

// Two-factor-authentication helpers
// ---------------------------------

// Start a 2FA session against X_AVM-DE_Auth:SetConfig(NewAction=start).
// The Fritz!Box returns a token to carry in the <avm:token> SOAP header
// of subsequent 2FA-protected actions, its current state (typically
// "waitingforauth") and a comma-separated list of usable methods
// ("button", "dtmf;<seq>", "googleauth"). See X_AVM-DE_Auth SCPD.
esp_err_t tr064_auth_start(
    const char *host, int port,
    const char *admin_user, const char *admin_pass,
    char *out_token,   size_t token_cap,
    char *out_state,   size_t state_cap,
    char *out_methods, size_t methods_cap);

// Poll the current 2FA state via X_AVM-DE_Auth:GetState.
//   "waitingforauth"   — user hasn't confirmed yet
//   "authenticated"    — good to go, retry the original action
//   "stopped"          — user cancelled
//   "blocked"          — too many tries
//
// `token_2fa` must be the token returned by tr064_auth_start — per the
// TR-064 Authentication spec (§6.4), GetState requires the token in the
// `<avm:token>` SOAP header to identify the running 2FA context.
esp_err_t tr064_auth_get_state(
    const char *host, int port,
    const char *admin_user, const char *admin_pass,
    const char *token_2fa,
    char *out_state, size_t state_cap);
