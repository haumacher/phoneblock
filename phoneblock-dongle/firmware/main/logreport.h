#pragma once

// Best-effort upload of the captured WARN/ERROR log ring to the
// PhoneBlock server. Piggybacks on the daily self-test wakeup so it adds
// no extra connection cadence of its own.
//
// De-noising rule: report only when a *new* ERROR has appeared since the
// last flush — a lone WARN is almost always transient or benign (ESP-IDF
// httpd logs every client socket reset at WARN, a brief WiFi flap warns
// and self-heals, etc.). When an error does fire, the whole new WARN/ERROR
// window is shipped so the surrounding context comes along. INFO is never
// sent, even when the user enabled the INFO log view (config_log_info).
//
// No-op unless crash reporting is enabled (the same "send errors to
// PhoneBlock" opt-in), a token is set, and the network is up — so a
// healthy dongle sends nothing at all, keeping fleet-wide server load at
// essentially zero.
void logreport_flush(void);
