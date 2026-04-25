#pragma once

// Reads the per-install pairing secret written by the browser flasher
// into the dedicated 4 KB "pairing" partition (custom data subtype
// 0x40). The secret lets the install page locate the freshly-flashed
// dongle on the LAN without depending on mDNS or DHCP host-name (the
// Fritz!Box notoriously pins the latter to the first DHCP request and
// won't update afterwards).
//
// OTA-only dongles never see this partition written — the parser
// rejects an erased (0xFF) partition and the caller silently skips the
// registration handshake. See pairing_parse.h for the on-flash format.

#include <stdbool.h>
#include <stdint.h>

#include "pairing_parse.h"  // PAIRING_SECRET_LEN

// Locate the "pairing" partition, read its first PAIRING_HEADER_LEN
// bytes, and validate them via pairing_parse(). On success, copies the
// 16-byte secret into `out_secret` and returns true. Returns false if
// the partition is missing, erased, or fails magic/version/CRC checks.
bool pairing_load(uint8_t out_secret[PAIRING_SECRET_LEN]);

// Spawn a one-shot FreeRTOS task that POSTs the secret + the dongle's
// LAN IP to phoneblock.net's /api/dongle/register endpoint, with
// exponential backoff (1 s, 4 s, 15 s, 60 s — then give up). The task
// is fire-and-forget: it does not block the caller, frees the secret
// from heap when it terminates, and never retries beyond the four
// attempts.
//
// `secret` is copied into a heap buffer owned by the task — the caller
// can let its own copy go out of scope immediately.
//
// Must be called after WiFi/IP is up and after web_start() so a
// concurrent OTA-image-validation pass has already happened and the
// device is in a stable runtime state.
void pairing_register_async(const uint8_t secret[PAIRING_SECRET_LEN]);
