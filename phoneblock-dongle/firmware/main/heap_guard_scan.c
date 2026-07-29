#include "heap_guard_scan.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

// TLSF aligns every block size and payload pointer to 4 bytes on a 32-bit
// target (ALIGN_SIZE). A violation means the header no longer describes a real
// block — and a misaligned successor would fault with LoadStoreAlignment
// instead of LoadProhibited, so it is worth catching separately.
#define HG_ALIGN 4u

hg_verdict_t hg_classify(hg_pool_t pool, uintptr_t ptr, size_t size)
{
    // Order matters: the bounds test below subtracts `ptr` from `pool.end`, so
    // the pointer has to be known good first.
    if (ptr < pool.start || ptr >= pool.end)
        return HG_BAD_PTR;

    // The walker stops at the last block, whose stored size is zero, so a
    // zero-size block never reaches a callback on a healthy heap.
    if (size == 0)
        return HG_BAD_SIZE;

    // The step the walker is about to take. Compared rather than added, so a
    // wild size cannot overflow the arithmetic and wrap back into range.
    if (size > (size_t)(pool.end - ptr))
        return HG_BAD_BOUNDS;

    if ((ptr % HG_ALIGN) != 0 || (size % HG_ALIGN) != 0)
        return HG_BAD_ALIGN;

    return HG_OK;
}

const char *hg_verdict_str(hg_verdict_t v)
{
    switch (v) {
    case HG_OK:         return "ok";
    case HG_BAD_PTR:    return "block outside heap";
    case HG_BAD_SIZE:   return "zero-size block";
    case HG_BAD_BOUNDS: return "block extends past heap end";
    case HG_BAD_ALIGN:  return "misaligned block";
    default:            return "unknown";
    }
}

void hg_window(hg_pool_t pool, uintptr_t center, size_t before, size_t after,
               uintptr_t *out_from, size_t *out_len)
{
    *out_from = 0;
    *out_len  = 0;

    if (center < pool.start || center >= pool.end)
        return;                                  // nothing safe to copy

    // Clamp both ends into the heap. Written as comparisons against the
    // available distance so neither side can under- or overflow.
    uintptr_t from = (before > (size_t)(center - pool.start))
                   ? pool.start : center - before;

    size_t room = (size_t)(pool.end - center);
    uintptr_t to = (after > room) ? pool.end : center + after;

    *out_from = from;
    *out_len  = (size_t)(to - from);
}
