#pragma once

// Block-sanity judgement for the heap sentinel (heap_guard.c), kept free of
// every ESP-IDF dependency so the host test suite can cover it.
//
// This is the whole decision behind a deliberate panic, which is why it lives
// on its own: a false positive reboots a perfectly healthy dongle. Every
// predicate below is one that a healthy TLSF heap cannot violate, and
// heap_guard_start() proves that empirically on each boot by running the same
// walk over the known-good heap before arming the sentinel.

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

// Bounds of the heap a block belongs to, as reported to a heap_caps_walk()
// callback in walker_heap_into_t. `end` is exclusive.
typedef struct {
    uintptr_t start;
    uintptr_t end;
} hg_pool_t;

typedef enum {
    HG_OK = 0,
    HG_BAD_PTR,     // block payload lies outside its own heap
    HG_BAD_SIZE,    // zero-length block (the walker never reports the last one)
    HG_BAD_BOUNDS,  // block claims to extend past the end of the heap
    HG_BAD_ALIGN,   // payload pointer or size not 4-byte aligned
} hg_verdict_t;

// Judge one block as reported by the heap walker. `size` is the block size
// TLSF has stored in the block header, i.e. exactly the value the walker will
// use to step to the next block — so HG_BAD_BOUNDS is the case that would
// otherwise fault on the following iteration.
hg_verdict_t hg_classify(hg_pool_t pool, uintptr_t ptr, size_t size);

const char *hg_verdict_str(hg_verdict_t v);

// Compute the byte range to snapshot as evidence: `before` bytes ahead of
// `center` through `after` bytes past it, clamped to the heap's own bounds so
// the resulting memcpy can never read outside mapped RAM.
//
// `center` is the payload pointer of the block whose header was found smashed.
// The bytes *before* it are the tail of the preceding allocation — the payload
// that overran — plus the smashed header itself, which is the evidence that
// names the writer. Yields *out_len == 0 when `center` is not inside the heap,
// leaving the caller nothing to copy.
void hg_window(hg_pool_t pool, uintptr_t center, size_t before, size_t after,
               uintptr_t *out_from, size_t *out_len);
