#pragma once

// Start the status-LED task. Polls the system state (WiFi, SIP, token)
// every 50 ms and drives CONFIG_STATUS_LED_GPIO with one of four
// blink patterns:
//
//   PAIRING    — fast blink (100 ms on / 100 ms off):
//                WPS-PBC active, press the router's pairing button
//   CONNECTING — slow blink (500 ms on / 500 ms off):
//                WiFi up but no IP yet, or reconnecting
//   SETUP      — short pulse (100 ms on / 900 ms off):
//                online but SIP unregistered or PhoneBlock token missing
//   READY      — solid on:
//                WiFi up + SIP registered + PhoneBlock token set
//
// No-op when CONFIG_STATUS_LED_GPIO is < 0.
void status_led_start(void);
