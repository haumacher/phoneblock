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
