#pragma once

// Pure-C parser for the "pairing" flash partition, split out of
// pairing.c so it can be unit-tested on the host (see firmware/test/).
// No ESP-IDF, no flash access — only <stdint.h>/<stdbool.h>/<string.h>.
//
// Partition layout (first 28 bytes used; rest is 0xFF padding):
//
//   offset  0  uint32  magic   = 0x504B5042  ("PBPK", little-endian)
//   offset  4  uint16  version = 1
//   offset  6  uint16  length  = 16
//   offset  8  uint8[16] secret
//   offset 24  uint32  crc32   over bytes 0..23 (poly 0xEDB88320, init
//                              0xFFFFFFFF, final XOR 0xFFFFFFFF — the
//                              standard "CRC-32/ISO-HDLC" used by zlib,
//                              esptool, and Java's java.util.zip.CRC32)
//
// The exact same magic/version/CRC must be produced by the server-side
// pairing.bin generator, so an erased / OTA-only / corrupted partition
// fails the check and the firmware skips the registration handshake.

#include <stdbool.h>
#include <stdint.h>
#include <stddef.h>

#define PAIRING_MAGIC      0x504B5042u   // 'P' 'B' 'P' 'K' little-endian
#define PAIRING_VERSION    1u
#define PAIRING_SECRET_LEN 16u
#define PAIRING_HEADER_LEN 28u           // magic(4)+ver(2)+len(2)+secret(16)+crc(4)

// Compute the standard CRC-32/ISO-HDLC over `data[0..len-1]`. Same
// polynomial as zlib / esptool / java.util.zip.CRC32 so the server can
// generate matching blobs.
uint32_t pairing_crc32(const uint8_t *data, size_t len);

// Validate `buf` (at least PAIRING_HEADER_LEN bytes) as a pairing
// partition image and copy the 16-byte secret into `out_secret`.
// Returns true iff magic, version, length, and CRC all match.
//
// `buf_len` is the number of bytes available in `buf`; values smaller
// than PAIRING_HEADER_LEN return false without reading past the end.
bool pairing_parse(const uint8_t *buf, size_t buf_len,
                   uint8_t out_secret[PAIRING_SECRET_LEN]);
