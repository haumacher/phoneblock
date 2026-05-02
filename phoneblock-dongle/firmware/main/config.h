#pragma once

#include <stdbool.h>

#include "esp_err.h"

// Runtime configuration with a two-stage source of truth:
//
//   1. NVS (namespace "phoneblock") — what the user configured via the
//      web UI; lives across reboots and firmware updates.
//   2. Kconfig — compile-time defaults, used whenever the corresponding
//      NVS key is unset. Keeps `sdkconfig.defaults.local` useful for
//      development.
//
// On boot, config_load() populates an in-RAM cache from NVS or the
// Kconfig fallback. Getters return pointers into that cache; treat
// them as read-only and valid until the next config_update() call.

void config_load(void);

// SIP
const char *config_sip_host(void);
int         config_sip_port(void);
const char *config_sip_user(void);
const char *config_sip_pass(void);
int         config_sip_expires(void);
// Internal extension number assigned by the Fritz!Box TR-064 flow,
// for display in the dashboard. Empty when SIP is configured manually
// or via a provider preset (the server does not hand it back).
const char *config_sip_internal_number(void);
// Extended SIP parameters persisted for provider setups that need
// more than the Fritz!Box happy path. The current sip_register.c
// only implements UDP + single-user digest; these fields are still
// captured so the UI can round-trip them and so later backend
// extensions (TLS, separate auth-user, outbound proxy, realm, SRTP)
// do not need another NVS migration.
//   transport: "udp" (default) | "tcp" | "tls"
//   auth_user: empty = use sip_user
//   outbound:  outbound proxy "host[:port]", empty = go via registrar
//   realm:     override the realm from the server's challenge
//   srtp:      "off" (default) | "optional" | "mandatory"
const char *config_sip_transport(void);
const char *config_sip_auth_user(void);
const char *config_sip_outbound(void);
const char *config_sip_realm(void);
const char *config_sip_srtp(void);
const char *config_contact_host_override(void);
int         config_contact_port_override(void);

// Fritz!Box "app" credentials created by
// X_AVM-DE_AppSetup:RegisterApp during the setup wizard. Only
// Phone rights, no internet access — used by the later sync task
// to talk TR-064 without keeping the admin password around.
// Empty strings mean "setup never ran successfully against a
// Fritz!Box" (or ran against an older one without AppSetup).
const char *config_fritzbox_app_user(void);
const char *config_fritzbox_app_pass(void);

// Whether the daily + manual Fritz!Box-blocklist sync is enabled.
// Default off — the feature is explicit opt-in.
bool        config_sync_enabled(void);

// Whether calls that bypass the PhoneBlock API check — Fritz!Box
// address-book matches and internal/non-dialable numbers (**NN
// codes, *21# feature dials, …) — should be added to the recent-
// calls list. Counters (total/legitimate) are always updated; this
// flag only controls whether the list itself records the call.
// Default on so new users see every call the dongle handled.
bool        config_log_known_calls(void);

// Whether the web UI requires "Login with PhoneBlock" before
// showing call data or accepting setting changes. Default off —
// during initial provisioning the LAN-local UI must be reachable
// without credentials. Activated explicitly by the user only after
// a successful round-trip through the SSO flow, which proves the
// browser will be able to log in (avoids the lockout case).
bool        config_auth_enabled(void);

// PhoneBlock user-name pinned at first activation of the access
// gate. Subsequent logins are accepted only when the JWT subject
// returned by the server matches this string. Empty when the gate
// is off; cleared together with config_auth_enabled when the user
// disables the gate. Trust-on-first-use: deliberately *not* tied
// to the configured API token, so deleting that token on
// phoneblock.net does not lock the user out of their own dongle.
const char *config_auth_user(void);

// Persistent "stay logged in" cookie value. Set when the user
// completes a successful SSO round-trip with the "remember me" box
// checked, cleared on logout, factory-reset, gate-disable, or
// when another browser opts-in (only one remembered browser at a
// time). Empty string = no remembered browser. The plaintext hex
// blob is also the cookie value sent to the browser, so anyone
// with NVS read access could replay it — the threat model already
// assumes physical custody of the dongle.
const char *config_auth_persist(void);

// Whether the daily firmware self-update task is allowed to install
// a newer version from the CDN. Default on. Auto-disabled when the
// user uploads a firmware image manually via the web UI — without
// this guard the auto-updater would happily overwrite a hand-picked
// build on the next nightly poll. Re-enable explicitly from the UI
// when ready to follow the released stream again.
bool        config_auto_update_enabled(void);

// PhoneBlock
// Site root URL, without "/api" suffix — api.c appends "/api/…",
// web.c appends "/mobile/…" directly.
const char *config_phoneblock_base_url(void);
const char *config_phoneblock_token(void);

// Minimum-vote thresholds for SPAM blocking. Two independent values
// because direct hits ("this exact number was reported") are a much
// stronger signal than range hits ("numbers in the same 10/100-block
// were reported"); applying one threshold to both flattens that
// distinction.
//
//   SPAM ⟺  direct  >= min_direct_votes
//        ∨  (min_range_votes >= 1  ∧  wildcard >= min_range_votes)
//
// Defaults: direct=4 (matches DB.MIN_VOTES on the server / public
// blocklist export), range=10 (more conservative since range evidence
// is weaker). min_range_votes=0 disables range-blocking entirely.
int         config_min_direct_votes(void);
int         config_min_range_votes(void);

// Version string of the most recent OTA download that did NOT survive
// to the next successful boot, or "" if no such record exists. Set by
// the auto-update task before invoking esp_https_ota; cleared in
// app_main once the running image equals this version (i.e. the
// bootloader did not roll back). Used as a guard against the
// download → brick → rollback → retry-same-bits loop.
const char *config_last_failed_ota(void);

// Persist or clear the "last failed OTA" marker. Pass NULL or "" to
// clear. Writes through to NVS immediately; the in-RAM cache is
// updated on success.
esp_err_t   config_set_last_failed_ota(const char *version);

// Update the NVS-backed settings atomically. Any field passed as NULL
// or 0 is left untouched. The in-RAM cache is refreshed after NVS
// commit succeeds; on failure the cache is unchanged.
typedef struct {
    const char *sip_host;
    int         sip_port;           // 0 = keep current
    const char *sip_user;
    const char *sip_pass;
    int         sip_expires;        // 0 = keep current
    const char *sip_internal_number;
    const char *sip_transport;
    const char *sip_auth_user;
    const char *sip_outbound;
    const char *sip_realm;
    const char *sip_srtp;
    const char *fritzbox_app_user;
    const char *fritzbox_app_pass;
    // "1" = enable sync, "0" = disable, NULL = leave unchanged.
    const char *sync_enabled;
    // "1" = log known/internal calls, "0" = skip them, NULL = leave
    // unchanged. Default when the key is unset is "log them".
    const char *log_known_calls;
    // "1" = require "Login with PhoneBlock" before serving the UI,
    // "0" = open access. NULL = leave unchanged. Default when the
    // key is unset is "open access" (setup phase).
    const char *auth_enabled;
    // PhoneBlock user-name pinned as the dongle's "owner" at first
    // activation. Empty string clears the pin (used when disabling
    // the gate); NULL leaves the current value untouched.
    const char *auth_user;
    // Persistent "remember me" cookie value. Empty string clears the
    // pin (logout / disable); NULL leaves the current value untouched.
    const char *auth_persist;
    // "1" = let the daily self-update task install newer firmware,
    // "0" = freeze on the current build. NULL = leave unchanged.
    // Default when unset is "1" (auto-update on).
    const char *auto_update;
    const char *phoneblock_base_url;
    const char *phoneblock_token;
    // Direct-hit SPAM threshold. 0 = leave current value untouched
    // (0 itself is not a meaningful value — would block on no
    // evidence at all).
    int         min_direct_votes;
    // Range-hit SPAM threshold. Uses an explicit has_min_range_votes
    // flag rather than the 0-sentinel pattern because 0 is a real
    // value here (= disable range-blocking).
    bool        has_min_range_votes;
    int         min_range_votes;
} config_update_t;

esp_err_t config_update(const config_update_t *u);

// Erase the entire NVS namespace used by the dongle. Leaves other
// namespaces (WiFi credentials in the `nvs.net80211` namespace, etc.)
// intact. Caller should call esp_restart() afterwards, as the RAM
// cache is *not* refreshed to mirror the erase — a fresh boot
// guarantees a clean state.
esp_err_t config_erase(void);

// Fixed SIP-client username used when provisioning the dongle on a
// Fritz!Box. Same value on every device and every boot: the box's
// tr064_provision_sip_client path finds an existing entry with this
// name via find_client_slot and overwrites it, so re-running setup
// does not pile up new "phoneblock-<hex>" clients. The only case
// this collides is two dongles on the same box — so unusual that
// we'd add an explicit "device name" UI field before trying to
// derive one automatically again.
void config_dongle_username(char *out, size_t cap);
