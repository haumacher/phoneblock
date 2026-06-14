#pragma once

// Best-effort upload of the captured WARN/ERROR log ring to the
// PhoneBlock server. Piggybacks on the daily self-test wakeup so it adds
// no extra connection cadence of its own.
//
// Ships only entries that are new since the last successful flush, and
// only at WARN/ERROR level — INFO is never sent, even when the user
// enabled the INFO log view (config_log_info) and the ring therefore
// holds INFO lines. No-op unless crash reporting is enabled (the same
// "send errors to PhoneBlock" opt-in), a token is set, and the network
// is up — so a healthy dongle with nothing new to report sends nothing
// at all, which keeps the fleet-wide server load at essentially zero.
void logreport_flush(void);
