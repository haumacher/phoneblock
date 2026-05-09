#pragma once

// Best-effort upload of any pending ESP32 core dump from the previous
// boot. Spawns a one-shot low-priority task that:
//   1. Checks the "coredump" partition for a valid stored dump.
//   2. Reads it into a heap buffer (max 52 KB — the partition size).
//   3. POSTs the raw ELF bytes to ${BASE}/api/dongle/coredump with the
//      device's PhoneBlock bearer token and the current firmware
//      version as a query parameter.
//   4. On 2xx, erases the partition slot. On a transport error or
//      HTTP 503 (server has no storage configured), leaves the dump
//      in place so the next boot can retry.
//
// Tolerates ESP_ERR_NOT_FOUND from esp_core_dump_image_check() — that's
// what dongles flashed with the old layout (no coredump partition)
// return, and uploading nothing is the right answer there.
//
// Caller responsibility: WiFi must be up and the system clock should
// be ready (TLS validates server cert against time). Call after
// example_connect/wifi_connect, before any heavyweight subsystems.
void crashreport_upload_async(void);
