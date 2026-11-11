#pragma once

// Start the status-LED task. Polls the system state (WiFi, SIP,
// token) every 50 ms and drives the status LED with one of five blink
// patterns. Which LED, on which pins, is a setting with a detected default
// (board.h): a plain GPIO LED, an APA102 or a WS2812. The RGB LEDs show
// the state in colour as well (in brackets below), on top of the same
// pattern.
//
//   PAIRING    — fast blink (100 ms on / 100 ms off) [blue]:
//                WPS-PBC active, press the router's pairing button
//   CONNECTING — slow blink (500 ms on / 500 ms off) [amber]:
//                WiFi up but no IP yet, or reconnecting
//   SETUP      — short pulse (100 ms on / 900 ms off, mostly dark) [white]:
//                online but SIP credentials or PhoneBlock token not
//                yet entered — the web UI's setup steps are unfinished
//   DEGRADED   — short dropout (900 ms on / 100 ms off, mostly lit) [red]:
//                fully configured but something is currently failing —
//                SIP REGISTER not in place (bad credentials, registrar
//                unreachable, transient reconnect) or the API token
//                was rejected by phoneblock.net (401/403). Same
//                signals the dashboard surfaces; the LED tells the
//                user "open the web UI to see what's wrong"
//   READY      — solid on [green]:
//                WiFi up + SIP registered + token set + token accepted
//
// Without an LED the task idles, so one configured later takes effect.
void status_led_start(void);

// The LED setting changed: the task releases the old LED and takes over
// the new one (board_effective()) within a tick.
void status_led_reconfigure(void);
