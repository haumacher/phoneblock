// C port of the Java reference implementation in
// phoneblock-shared/.../BlocklistLookup.java. See blocklist_lookup.h for
// the file format and storage rationale.

#include "blocklist_lookup.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

// Powers of 11 used in the base-11 key encoding. POW11[i] is the place
// value of slot SLOTS - 1 - i, i.e. the contribution of a symbol at the
// i-th least-significant slot. 11^16 ≈ 4.6e16 fits comfortably in 56 bits.
#define BLOCKLIST_SLOTS 16
static const uint64_t POW11[BLOCKLIST_SLOTS + 1] = {
    1ULL,
    11ULL,
    121ULL,
    1331ULL,
    14641ULL,
    161051ULL,
    1771561ULL,
    19487171ULL,
    214358881ULL,
    2357947691ULL,
    25937424601ULL,
    285311670611ULL,
    3138428376721ULL,
    34522712143931ULL,
    379749833583241ULL,
    4177248169415651ULL,
    45949729863572161ULL,
};

#define KEY_SHIFT      8
#define FLAG_BLACK     1ULL
#define SEARCH_MASK    (~FLAG_BLACK)

struct blocklist {
    char    *path;
    uint16_t prefix_lengths;
    uint32_t exact_count;
    uint32_t prefix_count;
    uint64_t exact_offset;     // file offset of the exact section
    uint64_t prefix_offset;    // file offset of the prefix section
};

// ---------------------------------------------------------------------------
// Little-endian decoders.
// ---------------------------------------------------------------------------

static uint16_t read_u16(const uint8_t *p)
{
    return (uint16_t)((uint16_t)p[0] | ((uint16_t)p[1] << 8));
}

static uint32_t read_u32(const uint8_t *p)
{
    return (uint32_t)p[0]
        | ((uint32_t)p[1] << 8)
        | ((uint32_t)p[2] << 16)
        | ((uint32_t)p[3] << 24);
}

static uint64_t read_u64(const uint8_t *p)
{
    uint64_t v = 0;
    for (int i = 0; i < 8; i++) {
        v |= (uint64_t)p[i] << (8 * i);
    }
    return v;
}

// ---------------------------------------------------------------------------
// Key encoding (exposed for tests via blocklist_lookup.h).
// ---------------------------------------------------------------------------

uint64_t blocklist_key(const char *digits)
{
    size_t n = 0;
    if (digits != NULL) {
        n = strlen(digits);
        if (n > BLOCKLIST_MAX_DIGITS) {
            // Truncation policy — see lookup() for the rationale.
            n = BLOCKLIST_MAX_DIGITS;
        }
    }
    uint64_t k = 0;
    for (int i = 0; i < BLOCKLIST_SLOTS; i++) {
        int symbol;
        if (i < (int)n) {
            char c = digits[i];
            if (c < '0' || c > '9') {
                // Garbage past the prefix → treat as terminator. The caller
                // controls input shape (we get bare E.164 digits from
                // normalize_de + '+'-strip), so this is just defence.
                symbol = 0;
            } else {
                symbol = (c - '0') + 1;
            }
        } else {
            symbol = 0;
        }
        k = k * 11ULL + (uint64_t)symbol;
    }
    return k;
}

uint64_t blocklist_truncate_key(uint64_t key, int length)
{
    if (length >= BLOCKLIST_SLOTS) {
        return key;
    }
    if (length <= 0) {
        return 0;
    }
    uint64_t step = POW11[BLOCKLIST_SLOTS - length];
    return key - (key % step);
}

// ---------------------------------------------------------------------------
// Open / close.
// ---------------------------------------------------------------------------

blocklist_t *blocklist_open(const char *path_owned)
{
    if (path_owned == NULL) {
        return NULL;
    }
    FILE *f = fopen(path_owned, "rb");
    if (f == NULL) {
        return NULL;
    }

    uint8_t hdr[BLOCKLIST_HEADER_SIZE];
    if (fread(hdr, 1, sizeof(hdr), f) != sizeof(hdr)) {
        fclose(f);
        return NULL;
    }
    // Grab the real file size so the header's record counts can be sanity
    // checked below.
    long file_size = -1;
    if (fseek(f, 0, SEEK_END) == 0) {
        file_size = ftell(f);
    }
    fclose(f);
    if (file_size < BLOCKLIST_HEADER_SIZE) {
        return NULL;
    }

    uint32_t magic = read_u32(hdr);
    if (magic != BLOCKLIST_MAGIC) {
        return NULL;
    }
    uint16_t version = read_u16(hdr + 4);
    if (version != BLOCKLIST_VERSION) {
        return NULL;
    }
    uint16_t prefix_lengths = read_u16(hdr + 6);
    uint32_t exact_count = read_u32(hdr + 8);
    uint32_t prefix_count = read_u32(hdr + 12);

    // Reject a header whose record counts don't fit the actual file. A corrupt
    // or hostile count would otherwise drive read_record()'s offset past 2 GiB,
    // where the (long)off seek truncates on a 32-bit target and reads the wrong
    // record. Bounding the counts by the real (sub-megabyte) file keeps every
    // computed offset representable.
    uint64_t need = (uint64_t)BLOCKLIST_HEADER_SIZE
                  + ((uint64_t)exact_count + (uint64_t)prefix_count)
                        * BLOCKLIST_RECORD_SIZE;
    if (need > (uint64_t)file_size) {
        return NULL;
    }

    blocklist_t *bl = (blocklist_t *)calloc(1, sizeof(*bl));
    if (bl == NULL) {
        return NULL;
    }
    bl->path = strdup(path_owned);
    if (bl->path == NULL) {
        free(bl);
        return NULL;
    }
    bl->prefix_lengths = prefix_lengths;
    bl->exact_count = exact_count;
    bl->prefix_count = prefix_count;
    bl->exact_offset = BLOCKLIST_HEADER_SIZE;
    bl->prefix_offset = (uint64_t)BLOCKLIST_HEADER_SIZE
                      + (uint64_t)exact_count * BLOCKLIST_RECORD_SIZE;
    return bl;
}

void blocklist_close(blocklist_t *bl)
{
    if (bl == NULL) {
        return;
    }
    free(bl->path);
    free(bl);
}

int blocklist_size(const blocklist_t *bl)
{
    if (bl == NULL) {
        return 0;
    }
    return (int)bl->exact_count + (int)bl->prefix_count;
}

// ---------------------------------------------------------------------------
// Binary search within one section.
// ---------------------------------------------------------------------------

// Reads the record at index `idx` (zero-based within the section that
// starts at file offset `section_offset`). Returns true on success.
static bool read_record(FILE *f, uint64_t section_offset, uint32_t idx,
                        uint64_t *out)
{
    uint64_t off = section_offset + (uint64_t)idx * BLOCKLIST_RECORD_SIZE;
    if (fseek(f, (long)off, SEEK_SET) != 0) {
        return false;
    }
    uint8_t buf[BLOCKLIST_RECORD_SIZE];
    if (fread(buf, 1, sizeof(buf), f) != sizeof(buf)) {
        return false;
    }
    *out = read_u64(buf);
    return true;
}

// Returns the matching record (>= 0 on hit, with bit 0 = black/white).
// Sets *hit = true on hit, false otherwise. On miss the post-search lower
// bound is written to *insertion_point so the caller can shrink subsequent
// searches (the prefix loop truncates the query monotonically downward).
//
// `hi_inclusive` bounds the search above; pass section_count - 1 to scan
// the whole section, or a tighter bound to skip work.
static bool find_in_section(FILE *f, uint64_t section_offset,
                            uint32_t section_count, uint64_t target,
                            int32_t hi_inclusive,
                            uint64_t *out_record, int32_t *insertion_point)
{
    uint64_t needle = target & SEARCH_MASK;
    int32_t lo = 0;
    int32_t hi = hi_inclusive;
    if (hi >= (int32_t)section_count) {
        hi = (int32_t)section_count - 1;
    }
    while (lo <= hi) {
        int32_t mid = lo + (hi - lo) / 2;
        uint64_t record;
        if (!read_record(f, section_offset, (uint32_t)mid, &record)) {
            *insertion_point = lo;
            return false;
        }
        uint64_t mid_masked = record & SEARCH_MASK;
        if (mid_masked < needle) {
            lo = mid + 1;
        } else if (mid_masked > needle) {
            hi = mid - 1;
        } else {
            *out_record = record;
            return true;
        }
    }
    *insertion_point = lo;
    return false;
}

static blocklist_verdict_t verdict_of(uint64_t record)
{
    return (record & FLAG_BLACK) ? BLOCKLIST_SPAM : BLOCKLIST_LEGIT;
}

// ---------------------------------------------------------------------------
// Public lookup.
// ---------------------------------------------------------------------------

blocklist_verdict_t blocklist_lookup(blocklist_t *bl, const char *digits,
                                     bool consult_wildcards)
{
    return blocklist_lookup_ex(bl, digits, consult_wildcards, NULL);
}

blocklist_verdict_t blocklist_lookup_ex(blocklist_t *bl, const char *digits,
                                        bool consult_wildcards,
                                        bool *matched_wildcard)
{
    if (matched_wildcard) *matched_wildcard = false;

    if (bl == NULL || digits == NULL || digits[0] == '\0') {
        return BLOCKLIST_UNKNOWN;
    }

    FILE *f = fopen(bl->path, "rb");
    if (f == NULL) {
        return BLOCKLIST_UNKNOWN;
    }

    uint64_t key = blocklist_key(digits);
    blocklist_verdict_t result = BLOCKLIST_UNKNOWN;

    // Exact-section: one binary search for the full key.
    if (bl->exact_count > 0) {
        uint64_t target = key << KEY_SHIFT;
        uint64_t hit_record;
        int32_t insertion;
        if (find_in_section(f, bl->exact_offset, bl->exact_count, target,
                            (int32_t)bl->exact_count - 1,
                            &hit_record, &insertion)) {
            result = verdict_of(hit_record);
            fclose(f);
            return result;  // exact hit — matched_wildcard stays false
        }
    }

    if (!consult_wildcards || bl->prefix_count == 0) {
        fclose(f);
        return BLOCKLIST_UNKNOWN;
    }

    // Prefix-section: one binary search per length bit, longest to shortest
    // (longest-match wins). As L shrinks the truncated key is monotonically
    // non-increasing, so the previous miss's insertion point bounds the
    // next search from above — we shrink `hi` across iterations.
    int32_t hi = (int32_t)bl->prefix_count - 1;
    for (int L = BLOCKLIST_MAX_DIGITS; L >= 1 && hi >= 0; L--) {
        if ((bl->prefix_lengths & (1u << L)) == 0) {
            continue;
        }
        uint64_t truncated = blocklist_truncate_key(key, L);
        uint64_t target = truncated << KEY_SHIFT;
        uint64_t hit_record;
        int32_t insertion;
        if (find_in_section(f, bl->prefix_offset, bl->prefix_count, target,
                            hi, &hit_record, &insertion)) {
            result = verdict_of(hit_record);
            if (matched_wildcard) *matched_wildcard = true;
            fclose(f);
            return result;
        }
        hi = insertion - 1;
    }

    fclose(f);
    return BLOCKLIST_UNKNOWN;
}
