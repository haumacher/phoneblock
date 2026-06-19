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

// Local UDP/TCP port the dongle binds and advertises for SIP, and the
// UDP port it binds for RTP audio. Both default to a high, "unsuspicious"
// value (15060 / 16000) that dodges router SIP-ALGs and is forwardable
// 1:1 behind NAT. Always return a usable port (never 0). Configurable so
// a user with a restrictive router can move them.
int         config_sip_local_port(void);
int         config_rtp_port(void);

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

// Whether the global log hook also mirrors INFO lines (not just
// WARN/ERROR) into the log panel. Default off; a troubleshooting toggle
// — INFO carries the context (tried host:port, request URLs, …) that a
// bare WARN/ERROR omits. Floods the 32-entry ring, so meant to be turned
// on, reproduced, read, and turned off again.
bool        config_log_info(void);

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

// OTA update channel the daily self-update and the manual "check"
// button poll. The manifest URL is built as
// <CONFIG_PHONEBLOCK_OTA_BASE_URL>/<channel>/manifest.json, so the
// channel is the path segment that selects the released stream.
// Always returns one of the two known-safe literals "stable" (default)
// or "beta": any other / corrupt NVS value maps to "stable", which
// keeps the result safe to splice straight into the poll URL. "beta"
// is opt-in for users testing pre-release builds; releases publish to
// both channels so beta is always >= stable (see scripts/release.sh).
const char *config_ota_channel(void);

// Whether crashreport.c is allowed to upload a stored core dump to
// the PhoneBlock backend on the boot following a panic. Default on
// — the dump is otherwise dead weight in flash and the operator's
// only signal of field crashes. Set to false from the web UI to
// opt out; crashreport.c then erases pending dumps without sending
// them, so flipping the toggle later doesn't ship historical state
// the user no longer wants out the door.
bool        config_crash_report_enabled(void);

// Whether '*'-prefixed callers (Fritz!Box internal dial codes like
// **622 or *21#) are answered as SPAM, bypassing the API and the
// phone-book skip. Lets the user exercise the full
// answer + audio + BYE path by calling the dongle's extension from
// another internal Fritz!Box phone, without blacklisting a real
// external number on PhoneBlock. Default comes from
// CONFIG_SIP_TEST_FORCE_SPAM_STAR_NUMBERS until the user toggles
// the runtime setting in the web UI.
bool        config_accept_test_calls(void);

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

// Status email (SMTP). The dongle sends status mails directly through
// the user's own mail account (authenticated submission), so the
// provider relays them — no central mail budget, and SPF/DKIM pass via
// the provider's relay rather than the dongle's IP. Credentials live in
// NVS in plaintext, consistent with the SIP / Fritz!Box passwords.
const char *config_smtp_host(void);
// Effective submission port: the stored value, or — when 0 ("auto") —
// 465 for implicit TLS / 587 for STARTTLS, per config_smtp_security().
int         config_smtp_port(void);
// "tls" (implicit TLS on connect, default) | "starttls". Clamped: any
// other stored value reads back as "tls".
const char *config_smtp_security(void);
const char *config_smtp_user(void);
const char *config_smtp_pass(void);
// Sender address; falls back to config_smtp_user() when unset.
const char *config_smtp_from(void);
// Recipient of the status mails.
const char *config_smtp_to(void);
// Trigger toggles (both default off — status mail is opt-in):
//   on_error: a daily mail when an ERROR was logged since the last one;
//             also drives the immediate crash mail at boot.
//   on_spam:  a daily mail when spam calls were caught since the last one.
bool        config_mail_on_error(void);
bool        config_mail_on_spam(void);

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
    // Uses an explicit has_sip_port flag rather than the 0-sentinel
    // pattern because 0 is a real value here (= "auto": let DNS-SRV /
    // the transport default pick the port). A provider switch must be
    // able to clear a previously pinned port back to auto.
    bool        has_sip_port;
    int         sip_port;
    // Local SIP / RTP bind ports. Explicit has_ flags: 0 is a meaningful
    // value here (= reset to the built-in default).
    bool        has_sip_local_port;
    int         sip_local_port;
    bool        has_rtp_port;
    int         rtp_port;
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
    // "1" = also capture INFO log lines, "0" = only WARN/ERROR. NULL =
    // leave unchanged. Default when unset is "off".
    const char *log_info;
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
    // OTA update channel: "stable" | "beta". NULL = leave unchanged.
    // The caller is expected to pass only these two literals; any
    // other value is persisted verbatim but read back as "stable" by
    // config_ota_channel().
    const char *ota_channel;
    // "1" = upload core dumps to the PhoneBlock backend after a
    // panic-and-reboot, "0" = erase them locally without sending.
    // NULL = leave unchanged. Default when unset is "1".
    const char *crash_report;
    // "1" = answer Fritz!Box internal *-codes as SPAM (test hook),
    // "0" = treat them like any other caller. NULL = leave unchanged.
    // Default when unset comes from CONFIG_SIP_TEST_FORCE_SPAM_STAR_NUMBERS.
    const char *accept_test_calls;
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
    // Status email (SMTP). String fields follow the usual "NULL = leave
    // unchanged" rule — in particular the web UI passes smtp_pass = NULL
    // when the user did not re-enter the password, so a settings save
    // never clears it.
    const char *smtp_host;
    // Explicit has_smtp_port flag: 0 is meaningful here (= "auto", derive
    // the port from the security mode), distinct from "leave unchanged".
    bool        has_smtp_port;
    int         smtp_port;
    const char *smtp_security;   // "tls" | "starttls"
    const char *smtp_user;
    const char *smtp_pass;
    const char *smtp_from;
    const char *smtp_to;
    // "1" = enable, "0" = disable, NULL = leave unchanged. Default off.
    const char *mail_on_error;
    const char *mail_on_spam;
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

// Stable per-device id (UUIDv4 string, 36 chars), minted on first boot
// and persisted in NVS. Survives OTA updates; cleared only by a factory
// reset. Returns a pointer to a static buffer valid for the process
// lifetime (set once in config_load(), never mutated afterwards). Empty
// string only if NVS was unavailable at boot. Used as the SIP
// +sip.instance and embedded in the HTTP User-Agent.
const char *config_device_id(void);
