// Host-side tests for pairing_parse.c. Verify CRC interop, parse
// success on a hand-crafted blob, parse failure on the four common
// rejection paths (erased flash, wrong magic, wrong version, wrong
// CRC), and the short-buffer guard.

#include <assert.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>

#include "../main/pairing_parse.h"

static void put_u16_le(uint8_t *p, uint16_t v)
{
    p[0] = (uint8_t)(v & 0xFF);
    p[1] = (uint8_t)((v >> 8) & 0xFF);
}

static void put_u32_le(uint8_t *p, uint32_t v)
{
    p[0] = (uint8_t)(v & 0xFF);
    p[1] = (uint8_t)((v >> 8) & 0xFF);
    p[2] = (uint8_t)((v >> 16) & 0xFF);
    p[3] = (uint8_t)((v >> 24) & 0xFF);
}

// Build a valid pairing-partition image into `buf` (must be at least
// PAIRING_HEADER_LEN bytes). The remainder of any larger buffer is
// untouched — callers that simulate a flash-page-sized image fill the
// tail with 0xFF themselves.
static void build_valid(uint8_t buf[PAIRING_HEADER_LEN],
                        const uint8_t secret[PAIRING_SECRET_LEN])
{
    put_u32_le(buf + 0, PAIRING_MAGIC);
    put_u16_le(buf + 4, PAIRING_VERSION);
    put_u16_le(buf + 6, PAIRING_SECRET_LEN);
    memcpy(buf + 8, secret, PAIRING_SECRET_LEN);
    uint32_t crc = pairing_crc32(buf, 24);
    put_u32_le(buf + 24, crc);
}

static void test_crc32_known_vectors(void)
{
    // CRC-32/ISO-HDLC reference values (zlib / esptool / Java
    // java.util.zip.CRC32). Catches accidental polynomial/init/xor
    // changes that would silently desync from the server generator.
    assert(pairing_crc32((const uint8_t *)"", 0)            == 0x00000000u);
    assert(pairing_crc32((const uint8_t *)"a", 1)           == 0xE8B7BE43u);
    assert(pairing_crc32((const uint8_t *)"123456789", 9)   == 0xCBF43926u);
    printf("  crc32 known vectors: ok\n");
}

static void test_parse_valid(void)
{
    uint8_t secret[PAIRING_SECRET_LEN] = {
        0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77,
        0x88, 0x99, 0xAA, 0xBB, 0xCC, 0xDD, 0xEE, 0xFF,
    };
    uint8_t buf[PAIRING_HEADER_LEN];
    build_valid(buf, secret);

    uint8_t out[PAIRING_SECRET_LEN] = { 0 };
    assert(pairing_parse(buf, sizeof(buf), out) == true);
    assert(memcmp(out, secret, PAIRING_SECRET_LEN) == 0);
    printf("  parse valid: ok\n");
}

static void test_parse_full_4k_partition_with_padding(void)
{
    // A real flash read returns the requested byte count, but the
    // partition itself is 4 KB with the tail erased to 0xFF. Confirm
    // the parser doesn't depend on the tail and works the same on a
    // page-sized buffer.
    uint8_t secret[PAIRING_SECRET_LEN] = { 1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16 };
    uint8_t page[4096];
    memset(page, 0xFF, sizeof(page));
    build_valid(page, secret);

    uint8_t out[PAIRING_SECRET_LEN] = { 0 };
    assert(pairing_parse(page, sizeof(page), out) == true);
    assert(memcmp(out, secret, PAIRING_SECRET_LEN) == 0);
    printf("  parse with 0xFF padding: ok\n");
}

static void test_parse_erased(void)
{
    // OTA-only dongles see all 0xFF — must reject without dereferencing
    // garbage and without setting out_secret.
    uint8_t buf[PAIRING_HEADER_LEN];
    memset(buf, 0xFF, sizeof(buf));
    uint8_t out[PAIRING_SECRET_LEN] = { 0xAA };
    assert(pairing_parse(buf, sizeof(buf), out) == false);
    printf("  parse erased (0xFF): rejected\n");
}

static void test_parse_wrong_magic(void)
{
    uint8_t secret[PAIRING_SECRET_LEN] = { 0 };
    uint8_t buf[PAIRING_HEADER_LEN];
    build_valid(buf, secret);
    buf[0] ^= 0x01;                       // flip a magic bit
    uint8_t out[PAIRING_SECRET_LEN] = { 0 };
    assert(pairing_parse(buf, sizeof(buf), out) == false);
    printf("  parse wrong magic: rejected\n");
}

static void test_parse_wrong_version(void)
{
    uint8_t secret[PAIRING_SECRET_LEN] = { 0 };
    uint8_t buf[PAIRING_HEADER_LEN];
    build_valid(buf, secret);
    put_u16_le(buf + 4, PAIRING_VERSION + 1);
    // Re-CRC so the failure is unambiguously the version, not the CRC.
    uint32_t crc = pairing_crc32(buf, 24);
    put_u32_le(buf + 24, crc);
    uint8_t out[PAIRING_SECRET_LEN] = { 0 };
    assert(pairing_parse(buf, sizeof(buf), out) == false);
    printf("  parse wrong version: rejected\n");
}

static void test_parse_wrong_length_field(void)
{
    uint8_t secret[PAIRING_SECRET_LEN] = { 0 };
    uint8_t buf[PAIRING_HEADER_LEN];
    build_valid(buf, secret);
    put_u16_le(buf + 6, PAIRING_SECRET_LEN - 1);
    uint32_t crc = pairing_crc32(buf, 24);
    put_u32_le(buf + 24, crc);
    uint8_t out[PAIRING_SECRET_LEN] = { 0 };
    assert(pairing_parse(buf, sizeof(buf), out) == false);
    printf("  parse wrong length: rejected\n");
}

static void test_parse_wrong_crc(void)
{
    uint8_t secret[PAIRING_SECRET_LEN] = { 0 };
    uint8_t buf[PAIRING_HEADER_LEN];
    build_valid(buf, secret);
    buf[24] ^= 0x01;                      // bit-flip in CRC field
    uint8_t out[PAIRING_SECRET_LEN] = { 0 };
    assert(pairing_parse(buf, sizeof(buf), out) == false);
    printf("  parse wrong crc: rejected\n");
}

static void test_parse_short_buffer(void)
{
    uint8_t buf[PAIRING_HEADER_LEN - 1];
    memset(buf, 0xFF, sizeof(buf));
    uint8_t out[PAIRING_SECRET_LEN] = { 0 };
    assert(pairing_parse(buf, sizeof(buf), out) == false);
    printf("  parse short buffer: rejected\n");
}

static void test_parse_null_args(void)
{
    uint8_t buf[PAIRING_HEADER_LEN];
    uint8_t out[PAIRING_SECRET_LEN] = { 0 };
    assert(pairing_parse(NULL, sizeof(buf), out) == false);
    assert(pairing_parse(buf, sizeof(buf), NULL) == false);
    printf("  parse NULL args: rejected\n");
}

int main(void)
{
    printf("test_pairing_parse:\n");
    test_crc32_known_vectors();
    test_parse_valid();
    test_parse_full_4k_partition_with_padding();
    test_parse_erased();
    test_parse_wrong_magic();
    test_parse_wrong_version();
    test_parse_wrong_length_field();
    test_parse_wrong_crc();
    test_parse_short_buffer();
    test_parse_null_args();
    printf("ALL OK\n");
    return 0;
}
