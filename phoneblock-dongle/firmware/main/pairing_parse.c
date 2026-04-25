#include "pairing_parse.h"

#include <string.h>

uint32_t pairing_crc32(const uint8_t *data, size_t len)
{
    // Bitwise CRC-32/ISO-HDLC. Small flash blob (28 bytes) → no need
    // for a 256-entry table; readable + portable wins over throughput.
    uint32_t crc = 0xFFFFFFFFu;
    for (size_t i = 0; i < len; i++) {
        crc ^= data[i];
        for (int b = 0; b < 8; b++) {
            uint32_t mask = -(crc & 1u);
            crc = (crc >> 1) ^ (0xEDB88320u & mask);
        }
    }
    return crc ^ 0xFFFFFFFFu;
}

static uint16_t read_u16_le(const uint8_t *p)
{
    return (uint16_t)p[0] | ((uint16_t)p[1] << 8);
}

static uint32_t read_u32_le(const uint8_t *p)
{
    return  (uint32_t)p[0]
         | ((uint32_t)p[1] << 8)
         | ((uint32_t)p[2] << 16)
         | ((uint32_t)p[3] << 24);
}

bool pairing_parse(const uint8_t *buf, size_t buf_len,
                   uint8_t out_secret[PAIRING_SECRET_LEN])
{
    if (buf == NULL || out_secret == NULL) return false;
    if (buf_len < PAIRING_HEADER_LEN)      return false;

    if (read_u32_le(buf + 0) != PAIRING_MAGIC)        return false;
    if (read_u16_le(buf + 4) != PAIRING_VERSION)      return false;
    if (read_u16_le(buf + 6) != PAIRING_SECRET_LEN)   return false;

    uint32_t want = read_u32_le(buf + 24);
    uint32_t got  = pairing_crc32(buf, 24);
    if (want != got) return false;

    memcpy(out_secret, buf + 8, PAIRING_SECRET_LEN);
    return true;
}
