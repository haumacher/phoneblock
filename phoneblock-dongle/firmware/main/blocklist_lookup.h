// Local-blocklist lookup for the dongle. C port of the Java reference
// implementation in phoneblock-shared/sync/binary/BlocklistLookup.java.
//
// The on-device file format is documented in
// phoneblock-shared/.../BlocklistBinaryFormat.java: a 16-byte little-endian
// header (magic 'PBBL', version 1, prefix-length bitmap, two record-section
// counts) followed by the exact section then the prefix section, each made
// of 8-byte little-endian uint64 records.
//
// A record packs a 56-bit base-11 key in bits 63..8 plus a black/white
// payload bit in bit 0; whether an entry is exact or a wildcard prefix is
// implied by which physical section holds it. Both sections are sorted
// unsigned-ascending, so the lookup is one binary search over the exact
// section plus zero or more binary searches over the prefix section (one
// per length bit set in the header bitmap).
//
// Storage strategy
// ----------------
// Both files (community.bin / personal.bin) stay on SPIFFS; lookups go
// through fseek/fread. The dongle never loads the whole file into RAM —
// the personal list has no enforced size cap, and even the community list
// (~156 KB) wants to stay out of the working-set.
#pragma once

#include <stdbool.h>
#include <stdint.h>
#include <stddef.h>

#define BLOCKLIST_MAGIC     0x4C424250u    // 'P','B','B','L' (little-endian)
#define BLOCKLIST_VERSION   1
#define BLOCKLIST_HEADER_SIZE 16
#define BLOCKLIST_RECORD_SIZE 8

// Maximum significant digits in an E.164 number.
#define BLOCKLIST_MAX_DIGITS 15

typedef enum {
    BLOCKLIST_UNKNOWN = 0,
    BLOCKLIST_SPAM,
    BLOCKLIST_LEGIT,
} blocklist_verdict_t;

typedef struct blocklist blocklist_t;

// Opens the given file and parses the header. Returns NULL if the file is
// missing, the magic is wrong, the format version is unsupported, or the
// record counts overflow a 31-bit signed int. The file is not held open
// across calls; each lookup re-opens it (a SPIFFS rename during sync would
// otherwise serve stale bytes).
//
// `path_owned` is duplicated internally; the caller may free its copy.
blocklist_t *blocklist_open(const char *path_owned);

// Releases the handle. Safe with NULL.
void blocklist_close(blocklist_t *bl);

// Looks up the verdict for an E.164 digit string ('0'..'9', no '+'). Up to
// BLOCKLIST_MAX_DIGITS characters are taken into account; anything past
// digit 15 is sub-addressing and silently truncated (a spammer adding an
// extension can therefore still be caught by a wildcard, but cannot forge
// an exact hit on a shorter entry).
//
// If `consult_wildcards` is false the prefix section is not searched — the
// caller passes the user's "wildcards on/off" preference here, so the same
// binary file works for both setting values without server involvement.
//
// Returns BLOCKLIST_UNKNOWN when the file cannot be opened or read, when
// the verdict cannot be determined, or when no entry matches.
blocklist_verdict_t blocklist_lookup(blocklist_t *bl, const char *digits,
                                     bool consult_wildcards);

// As blocklist_lookup(), but also reports whether the hit came from the
// prefix (wildcard/range) section rather than the exact section. On a hit,
// `*matched_wildcard` is set to true for a range match and false for an
// exact-number match; it is left false on a miss. Pass NULL when the
// distinction is not needed (blocklist_lookup() is the thin wrapper).
blocklist_verdict_t blocklist_lookup_ex(blocklist_t *bl, const char *digits,
                                        bool consult_wildcards,
                                        bool *matched_wildcard);

// Diagnostics — total record count across both sections.
int blocklist_size(const blocklist_t *bl);

// Implementation details exposed for host-side tests. Not used at runtime.
uint64_t blocklist_key(const char *digits);
uint64_t blocklist_truncate_key(uint64_t key, int length);
