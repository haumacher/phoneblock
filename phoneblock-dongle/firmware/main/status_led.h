#pragma once

// Start the status-LED task. Polls the system state (WiFi, SIP,
// token) every 50 ms and drives CONFIG_STATUS_LED_GPIO with one of
// five blink patterns:
//
//   PAIRING    — fast blink (100 ms on / 100 ms off):
//                WPS-PBC active, press the router's pairing button
//   CONNECTING — slow blink (500 ms on / 500 ms off):
//                WiFi up but no IP yet, or reconnecting
//   SETUP      — short pulse (100 ms on / 900 ms off, mostly dark):
//                online but SIP credentials or PhoneBlock token not
//                yet entered — the web UI's setup steps are unfinished
//   DEGRADED   — short dropout (900 ms on / 100 ms off, mostly lit):
//                fully configured but something is currently failing —
//                SIP REGISTER not in place (bad credentials, registrar
//                unreachable, transient reconnect) or the API token
//                was rejected by phoneblock.net (401/403). Same
//                signals the dashboard surfaces; the LED tells the
//                user "open the web UI to see what's wrong"
//   READY      — solid on:
//                WiFi up + SIP registered + token set + token accepted
//
// No-op when CONFIG_STATUS_LED_GPIO is < 0.
void status_led_start(void);
