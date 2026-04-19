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

// Sentinel values for tr064_sip_result_t::error_code when the failure
// did not produce a UPnPError SOAP fault. Real AVM codes are positive
// (402, 820, 866, …), so negative sentinels do not collide.
#define TR064_ERR_TRANSPORT  (-1)   // DNS, TCP, TLS, timeout — box not reached
#define TR064_ERR_HTTP       (-2)   // HTTP status != 200 without UPnPError body
#define TR064_ERR_AUTH       (-3)   // HTTP 503 + faultstring "Unauthenticated"
#define TR064_ERR_PARSE      (-4)   // unexpected/malformed response body

typedef struct {
    char sip_user[32];
    char sip_pass[48];
    char internal_number[16];
    // Filled in on failure: the Fritz!Box's own errorCode + Description
    // extracted from the UPnPError SOAP fault (positive AVM codes), or
    // one of the TR064_ERR_* sentinels above with a human-readable
    // diagnostic in error_message.
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

// Return the Fritz!Box's default login username — the account whose
// password the web login form would accept by itself (matches the
// "last logged-in user" logic the AVM UI uses). Lets the dongle's
// setup wizard make the user-name input optional: the user only has
// to type their password.
//
// On success writes the username into `out` and returns ESP_OK.
// On failure writes the AVM UPnPError (or one of the TR064_ERR_*
// sentinels) into `*out_err_code` / `out_err_msg`, returns ESP_FAIL.
esp_err_t tr064_get_default_username(
    const char *host, int port,
    const char *admin_user, const char *admin_pass,
    char *out, size_t cap,
    int *out_err_code,
    char *out_err_msg, size_t err_msg_cap);

// Register a dedicated Fritz!Box "app" instance for the dongle via
// X_AVM-DE_AppSetup:RegisterApp. Only Phone rights (read-write),
// no internet access. Used so the later sync task can talk TR-064
// without us having to keep the admin password around.
//
// On success writes the generated username + password (buffers owned
// by caller) and returns ESP_OK. On failure, *out_err_code /
// out_err_msg are populated with the same sentinel/AVM-code scheme
// as tr064_provision_sip_client.
//
// `token_2fa` is the <avm:token> from the 2FA handshake when the
// admin user requires it (pass NULL otherwise). The same token
// that was used for SetClient4 is valid for RegisterApp within the
// same session.
esp_err_t tr064_register_dongle_app(
    const char *host, int port,
    const char *admin_user, const char *admin_pass,
    const char *token_2fa,
    char *out_user, size_t user_cap,
    char *out_pass, size_t pass_cap,
    int  *out_err_code,
    char *out_err_msg, size_t err_msg_cap);

// Call-barring phonebook ("Rufsperre") helpers
// --------------------------------------------
//
// The Fritz!Box exposes its call-barring list as a dedicated phone
// book. Both actions live under X_AVM-DE_OnTel and only require the
// Phone right — the dongle's app credentials are sufficient, the
// admin password is not needed.

// Fetch the signed URL of an XML document listing all call-barring
// entries. The caller then does a plain HTTP GET on that URL.
esp_err_t tr064_call_barring_list_url(
    const char *host, int port,
    const char *user, const char *pass,
    char *out_url, size_t url_cap,
    int *out_err_code, char *out_err_msg, size_t err_msg_cap);

// Remove a specific call-barring entry by its UniqueID (as returned
// inside a <contact><uniqueid>…</uniqueid></contact> block).
esp_err_t tr064_call_barring_delete(
    const char *host, int port,
    const char *user, const char *pass,
    const char *uid,
    int *out_err_code, char *out_err_msg, size_t err_msg_cap);

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
