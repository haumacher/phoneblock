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
