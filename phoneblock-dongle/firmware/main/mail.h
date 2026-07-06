#pragma once

#include <stdbool.h>

// Status email via authenticated SMTP submission through the user's own
// mail account (host/user/pass/recipient configured in the web UI). The
// provider relays the mail, so SPF/DKIM pass via the provider's IPs and
// there is no central mail budget — the load spreads across each owner's
// own provider, one mail per device.
//
// Supports both submission styles: implicit TLS (port 465, "tls") and
// STARTTLS (port 587, "starttls"). The peer certificate is verified
// against the ESP-IDF root bundle. Credentials live in NVS in plaintext,
// consistent with the SIP / Fritz!Box passwords.

// True when host, user, password and recipient are all set — i.e. a send
// can be attempted. The web UI uses this to validate before the test
// button, and the daily flush skips when it is false.
bool mail_configured(void);

// Send a fixed test mail synchronously over SMTP, so the user can verify
// the configured credentials. Returns true on success; logs the outcome
// at INFO/WARN (which the web UI's log panel surfaces). Performs the TLS
// handshake with full cert-chain verification inline, so it must run on a
// generously-sized stack — it is driven from the scheduler task (16 KB),
// never from the httpd worker (see scheduler_request_mail_test). The web
// UI's test button triggers it through that scheduler notification.
bool mail_send_test(void);

// One daily evaluation, called by the scheduler: if the on-error /
// on-spam toggles fire — a new ERROR was logged, or spam calls were
// caught, since the last mail — build a status body and send it. A
// post-crash reboot surfaces as a logged ERROR and is therefore covered
// by the on-error path. No-op when the feature is off, unconfigured,
// offline, or nothing is noteworthy.
void mail_daily_flush(void);

// Latch that this boot is the first one of a freshly OTA-flashed image, so
// the next scheduler mail run sends a one-shot "you were updated" notice.
// Called from app_main when the running partition is in PENDING_VERIFY (any
// OTA — CDN auto-update or a manual POST /api/firmware — but not a USB
// factory flash); `version` is the now-running firmware version. Cheap and
// non-blocking (no send here) — the actual mail goes out from
// mail_report_update() on the scheduler task.
void mail_note_update(const char *version);

// Send the one-shot firmware-update notice if mail_note_update() latched
// one this boot. Driven from the scheduler mail run (alongside
// mail_daily_flush) so the blocking SMTP/TLS send happens on the 16 KB
// scheduler stack, never the httpd worker. Gated on the mail_on_update
// toggle (default on) and mail_configured(); retries on the next run while
// offline, and clears the latch only after a confirmed send. Includes a
// link to the release's changelog when the version is a released build.
void mail_report_update(void);
