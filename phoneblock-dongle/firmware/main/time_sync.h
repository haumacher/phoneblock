#pragma once

#include <stdbool.h>
#include <stdint.h>

// Wall-clock time synchronisation for the dongle.
//
// The dongle has no battery-backed RTC; esp_timer only gives uptime
// (microseconds since boot). This module acquires real UTC over SNTP and
// applies a POSIX timezone, so the scheduler can fire jobs at a specified
// local time of day rather than only on boot-relative intervals.
//
// SNTP server discovery order (lwIP tries them front to back):
//   1. DHCP option 42 — the server(s) the DHCP server advertises. On a
//      Fritz!Box LAN that is the router itself. Requires
//      CONFIG_LWIP_DHCP_GET_NTP_SRV=y.
//   2. The default gateway — the home router, which virtually always
//      serves NTP even when it does not populate option 42.
//   3. pool.ntp.org — internet fallback if both LAN paths fail.
//
// Once SNTP sets the clock, gettimeofday()/time() advance off the same
// monotonic source as esp_timer, so drift between syncs is bridged for
// free.

// Initialise SNTP and apply the persisted timezone. Idempotent. Call once
// from app_main *before* the network comes up (it registers the GOT_IP
// handler that actually starts SNTP on the first lease).
void time_sync_start(void);

// True once the clock has been set from an SNTP server at least once.
bool time_sync_valid(void);

// UTC seconds since the epoch, or 0 when !time_sync_valid().
int64_t time_sync_now_epoch(void);

// Apply a POSIX TZ string at runtime: setenv("TZ")+tzset(). Called by the
// web settings handler after persisting a new timezone so the change
// takes effect without a reboot. A NULL/empty argument is ignored.
//
// Note: must be a POSIX TZ string ("CET-1CEST,M3.5.0,M10.5.0/3"), not an
// IANA name ("Europe/Berlin") — ESP-IDF's newlib ships no zone database.
void time_sync_set_timezone(const char *posix_tz);
