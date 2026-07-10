// Host-side unit tests for main/blocklist_lookup.{c,h}.
//
// Builds a binary blocklist file in a tempfile by hand — same layout the
// server emits — then exercises the lookup code path against it. Keeping
// the encoder out of the test avoids dragging Java into the C test
// pipeline; the byte construction is small enough to inline.

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdbool.h>
#include <stdint.h>
#include <unistd.h>

#include "blocklist_lookup.h"

static int g_tests = 0;
static int g_failures = 0;

#define CHECK_INT(name, expected, got) do {                          \
    g_tests++;                                                       \
    if ((long)(expected) != (long)(got)) {                           \
        fprintf(stderr, "FAIL %s: expected %ld got %ld\n",           \
                name, (long)(expected), (long)(got));                \
        g_failures++;                                                \
    }                                                                \
} while (0)

#define CHECK_VERDICT(name, expected, got)                           \
    CHECK_INT(name, (int)(expected), (int)(got))

// ---------------------------------------------------------------------------
// Local copy of the key/record encoding to build test files.
// ---------------------------------------------------------------------------

#define SLOTS 16
static const uint64_t POW11[SLOTS + 1] = {
    1ULL, 11ULL, 121ULL, 1331ULL, 14641ULL, 161051ULL, 1771561ULL,
    19487171ULL, 214358881ULL, 2357947691ULL, 25937424601ULL,
    285311670611ULL, 3138428376721ULL, 34522712143931ULL,
    379749833583241ULL, 4177248169415651ULL, 45949729863572161ULL,
};

static uint64_t key_of(const char *digits)
{
    return blocklist_key(digits);
}

static int length_of(uint64_t key)
{
    for (int i = 0; i < SLOTS; i++) {
        uint64_t step = POW11[SLOTS - 1 - i];
        uint64_t symbol = (key / step) % 11ULL;
        if (symbol == 0) return i;
    }
    return SLOTS;
}

#define KEY_SHIFT  8
#define FLAG_BLACK 1ULL

static uint64_t make_record(const char *digits, bool black)
{
    return (key_of(digits) << KEY_SHIFT) | (black ? FLAG_BLACK : 0ULL);
}

static int cmp_u64(const void *a, const void *b)
{
    uint64_t x = *(const uint64_t *)a;
    uint64_t y = *(const uint64_t *)b;
    if (x < y) return -1;
    if (x > y) return 1;
    return 0;
}

static void put_u16(uint8_t *p, uint16_t v) { p[0] = v; p[1] = v >> 8; }
static void put_u32(uint8_t *p, uint32_t v) { p[0] = v; p[1] = v >> 8; p[2] = v >> 16; p[3] = v >> 24; }
static void put_u64(uint8_t *p, uint64_t v) { for (int i = 0; i < 8; i++) p[i] = (uint8_t)(v >> (8 * i)); }

// Writes one binary blocklist file. `exact` / `prefix` arrays are records
// in any order; the function sorts them, computes the prefix-length bitmap
// and writes the file at `path`. Returns 0 on success.
static int write_file(const char *path,
                      uint64_t *exact, int n_exact,
                      uint64_t *prefix, int n_prefix)
{
    qsort(exact, n_exact, sizeof(uint64_t), cmp_u64);
    qsort(prefix, n_prefix, sizeof(uint64_t), cmp_u64);

    uint16_t bitmap = 0;
    for (int i = 0; i < n_prefix; i++) {
        uint64_t key = (prefix[i] >> KEY_SHIFT) & 0x00FFFFFFFFFFFFFFULL;
        int len = length_of(key);
        if (len >= 1 && len <= BLOCKLIST_MAX_DIGITS) {
            bitmap |= (uint16_t)(1u << len);
        }
    }

    uint8_t hdr[BLOCKLIST_HEADER_SIZE];
    put_u32(hdr, BLOCKLIST_MAGIC);
    put_u16(hdr + 4, BLOCKLIST_VERSION);
    put_u16(hdr + 6, bitmap);
    put_u32(hdr + 8, (uint32_t)n_exact);
    put_u32(hdr + 12, (uint32_t)n_prefix);

    FILE *f = fopen(path, "wb");
    if (!f) return -1;
    fwrite(hdr, 1, sizeof(hdr), f);
    uint8_t buf[BLOCKLIST_RECORD_SIZE];
    for (int i = 0; i < n_exact; i++) {
        put_u64(buf, exact[i]);
        fwrite(buf, 1, sizeof(buf), f);
    }
    for (int i = 0; i < n_prefix; i++) {
        put_u64(buf, prefix[i]);
        fwrite(buf, 1, sizeof(buf), f);
    }
    fclose(f);
    return 0;
}

static char g_path[] = "/tmp/test_blocklist_lookup.XXXXXX";

static const char *temp_path(void)
{
    static bool initialised = false;
    if (!initialised) {
        int fd = mkstemp(g_path);
        if (fd < 0) {
            perror("mkstemp");
            exit(2);
        }
        close(fd);
        initialised = true;
    }
    return g_path;
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

static void test_key_encoding(void)
{
    // Empty input → key 0.
    CHECK_INT("key empty", 0, (long)blocklist_key(""));
    // Leading '0' (German trunk prefix in the JSON-API E.164 form would be
    // dropped beforehand; here we test the raw codec) is symbol 1 in the
    // MSB slot, i.e. 11^15.
    CHECK_INT("key '0' is 11^15",
              (long)4177248169415651LL, (long)blocklist_key("0"));
    // Truncation: 16-digit input gets folded to 15 digits.
    CHECK_INT("key 16-digit truncated",
              (long)blocklist_key("123456789012345"),
              (long)blocklist_key("1234567890123456"));

    // Truncate exposes prefix sharing.
    uint64_t full = blocklist_key("123456");
    CHECK_INT("truncate to 3",
              (long)blocklist_key("123"),
              (long)blocklist_truncate_key(full, 3));
    CHECK_INT("truncate to 0", 0L,
              (long)blocklist_truncate_key(full, 0));
}

static void test_exact_lookup(void)
{
    uint64_t exact[] = {
        make_record("4930123456", true),
        make_record("18886749072", true),
        make_record("4915112345", false),  // a white exact (e.g. global whitelist)
    };
    if (write_file(temp_path(), exact, 3, NULL, 0) != 0) {
        fprintf(stderr, "write_file failed\n");
        g_failures++;
        return;
    }

    blocklist_t *bl = blocklist_open(temp_path());
    CHECK_INT("open returns non-null", 1, (long)(bl != NULL));
    CHECK_INT("size 3", 3, blocklist_size(bl));

    CHECK_VERDICT("exact spam hit (DE)",
                  BLOCKLIST_SPAM,
                  blocklist_lookup(bl, "4930123456", true));
    CHECK_VERDICT("exact spam hit (US)",
                  BLOCKLIST_SPAM,
                  blocklist_lookup(bl, "18886749072", true));
    CHECK_VERDICT("exact white hit",
                  BLOCKLIST_LEGIT,
                  blocklist_lookup(bl, "4915112345", true));
    CHECK_VERDICT("exact miss",
                  BLOCKLIST_UNKNOWN,
                  blocklist_lookup(bl, "4930999999", true));
    CHECK_VERDICT("prefix-of-exact miss",
                  BLOCKLIST_UNKNOWN,
                  blocklist_lookup(bl, "493012345", true));

    blocklist_close(bl);
}

static void test_wildcards_and_longest_match(void)
{
    uint64_t exact[] = {
        make_record("4930111111", true),
    };
    uint64_t prefix[] = {
        make_record("4930", true),     // outer black wildcard
        make_record("4930999", false), // white block carving a hole into 4930*
        make_record("100", true),
    };
    if (write_file(temp_path(), exact, 1, prefix, 3) != 0) {
        fprintf(stderr, "write_file failed\n");
        g_failures++;
        return;
    }
    blocklist_t *bl = blocklist_open(temp_path());

    CHECK_VERDICT("exact wins over enclosing wildcard",
                  BLOCKLIST_SPAM,
                  blocklist_lookup(bl, "4930111111", true));
    CHECK_VERDICT("longer wildcard wins over shorter",
                  BLOCKLIST_LEGIT,
                  blocklist_lookup(bl, "4930999000", true));
    CHECK_VERDICT("falls back to outer black wildcard",
                  BLOCKLIST_SPAM,
                  blocklist_lookup(bl, "4930222222", true));
    CHECK_VERDICT("unrelated number",
                  BLOCKLIST_UNKNOWN,
                  blocklist_lookup(bl, "4920000000", true));
    CHECK_VERDICT("US wildcard hit",
                  BLOCKLIST_SPAM,
                  blocklist_lookup(bl, "10012345", true));

    blocklist_close(bl);
}

static void test_consult_wildcards_flag(void)
{
    uint64_t prefix[] = { make_record("4930", true) };
    if (write_file(temp_path(), NULL, 0, prefix, 1) != 0) {
        fprintf(stderr, "write_file failed\n");
        g_failures++;
        return;
    }
    blocklist_t *bl = blocklist_open(temp_path());

    CHECK_VERDICT("wildcard on → hit",
                  BLOCKLIST_SPAM,
                  blocklist_lookup(bl, "4930123456", true));
    CHECK_VERDICT("wildcard off → no hit",
                  BLOCKLIST_UNKNOWN,
                  blocklist_lookup(bl, "4930123456", false));

    blocklist_close(bl);
}

static void test_over_long_query(void)
{
    // 21-digit dial = spammer with 6-digit extension past the 15-digit
    // E.164 limit. Truncation policy must still allow a wildcard match.
    uint64_t prefix[] = { make_record("4930", true) };
    if (write_file(temp_path(), NULL, 0, prefix, 1) != 0) {
        fprintf(stderr, "write_file failed\n");
        g_failures++;
        return;
    }
    blocklist_t *bl = blocklist_open(temp_path());

    CHECK_VERDICT("21-digit dial still matches 4930*",
                  BLOCKLIST_SPAM,
                  blocklist_lookup(bl, "493012345678901234567", true));

    blocklist_close(bl);
}

static void test_open_rejects_bad_magic(void)
{
    FILE *f = fopen(temp_path(), "wb");
    if (!f) {
        g_failures++;
        return;
    }
    uint8_t junk[BLOCKLIST_HEADER_SIZE];
    memset(junk, 0xFF, sizeof(junk));
    fwrite(junk, 1, sizeof(junk), f);
    fclose(f);

    blocklist_t *bl = blocklist_open(temp_path());
    CHECK_INT("open rejects bad magic", 1, (long)(bl == NULL));
}

static void test_open_rejects_bad_version(void)
{
    uint64_t prefix[] = { make_record("4930", true) };
    if (write_file(temp_path(), NULL, 0, prefix, 1) != 0) {
        g_failures++;
        return;
    }
    // Stomp the version byte.
    FILE *f = fopen(temp_path(), "r+b");
    if (!f) {
        g_failures++;
        return;
    }
    fseek(f, 4, SEEK_SET);
    uint8_t bad = 99;
    fwrite(&bad, 1, 1, f);
    fclose(f);

    blocklist_t *bl = blocklist_open(temp_path());
    CHECK_INT("open rejects bad version", 1, (long)(bl == NULL));
}

static void test_empty_file_returns_unknown(void)
{
    if (write_file(temp_path(), NULL, 0, NULL, 0) != 0) {
        g_failures++;
        return;
    }
    blocklist_t *bl = blocklist_open(temp_path());
    CHECK_INT("empty open OK", 1, (long)(bl != NULL));
    CHECK_INT("empty size 0", 0, blocklist_size(bl));
    CHECK_VERDICT("empty file → unknown",
                  BLOCKLIST_UNKNOWN,
                  blocklist_lookup(bl, "4930123456", true));
    blocklist_close(bl);
}

static void test_lookup_handles_null_inputs(void)
{
    CHECK_VERDICT("null handle → unknown",
                  BLOCKLIST_UNKNOWN,
                  blocklist_lookup(NULL, "4930123456", true));

    uint64_t exact[] = { make_record("4930", true) };
    if (write_file(temp_path(), exact, 1, NULL, 0) != 0) {
        g_failures++;
        return;
    }
    blocklist_t *bl = blocklist_open(temp_path());
    CHECK_VERDICT("null digits → unknown",
                  BLOCKLIST_UNKNOWN,
                  blocklist_lookup(bl, NULL, true));
    CHECK_VERDICT("empty digits → unknown",
                  BLOCKLIST_UNKNOWN,
                  blocklist_lookup(bl, "", true));
    blocklist_close(bl);
}

// blocklist_lookup_ex() must report whether the hit came from the exact
// section or a prefix/wildcard entry — the dongle uses this to label a
// blocklist hit "(Nummer)" vs "(Bereich)".
static void test_wildcard_flag(void)
{
    uint64_t exact[] = {
        make_record("4930111111", true),   // exact black number
    };
    uint64_t prefix[] = {
        make_record("4930", true),          // black wildcard for the block
    };
    if (write_file(temp_path(), exact, 1, prefix, 1) != 0) {
        fprintf(stderr, "write_file failed\n");
        g_failures++;
        return;
    }
    blocklist_t *bl = blocklist_open(temp_path());

    bool wc = true;  // seed with the wrong value to prove it gets written
    CHECK_VERDICT("exact hit verdict", BLOCKLIST_SPAM,
                  blocklist_lookup_ex(bl, "4930111111", true, &wc));
    CHECK_INT("exact hit → not wildcard", 0, (long)wc);

    wc = false;
    CHECK_VERDICT("wildcard hit verdict", BLOCKLIST_SPAM,
                  blocklist_lookup_ex(bl, "4930222222", true, &wc));
    CHECK_INT("prefix hit → wildcard", 1, (long)wc);

    wc = true;
    CHECK_VERDICT("miss verdict", BLOCKLIST_UNKNOWN,
                  blocklist_lookup_ex(bl, "4920000000", true, &wc));
    CHECK_INT("miss → wildcard cleared", 0, (long)wc);

    // NULL out-pointer must be tolerated (blocklist_lookup() relies on it).
    CHECK_VERDICT("null out-ptr tolerated", BLOCKLIST_SPAM,
                  blocklist_lookup_ex(bl, "4930111111", true, NULL));

    blocklist_close(bl);
}

int main(void)
{
    test_key_encoding();
    test_exact_lookup();
    test_wildcards_and_longest_match();
    test_wildcard_flag();
    test_consult_wildcards_flag();
    test_over_long_query();
    test_open_rejects_bad_magic();
    test_open_rejects_bad_version();
    test_empty_file_returns_unknown();
    test_lookup_handles_null_inputs();

    unlink(g_path);

    fprintf(stderr, "%d tests, %d failures\n", g_tests, g_failures);
    return g_failures == 0 ? 0 : 1;
}
