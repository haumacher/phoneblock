// Implementation of storage_budget.h. Pure arithmetic — see the header for
// the derivation; the SPIFFS references below are to ESP-IDF v5.3,
// components/spiffs/spiffs/src.
//
//  * One deleted page per write call: spiffs_object_append() (spiffs_nucleus.c)
//    finishes every call with spiffs_object_update_index_hdr(), which writes
//    a new copy of the object index page and deletes the old one.
//  * Victim choice: spiffs_gc_find_candidate() (spiffs_gc.c) scores
//    deleted * SPIFFS_GC_HEUR_W_DELET (5) + used * SPIFFS_GC_HEUR_W_USED (-1)
//    + erase_age * SPIFFS_GC_HEUR_W_ERASE_AGE (50), the weights fixed in
//    IDF's spiffs_config.h; the age term only drops out once no free page is
//    left ("fs_crammed").
//  * Relocation cost: spiffs_gc_clean() moves each live data page and then
//    rewrites the owning object's index page, one new page per object per
//    cleaned block.
//  * Write admission: spiffs_gc_check() lets a write through without GC only
//    while more than three blocks are free, and otherwise collects until
//    `len` fits — at most CONFIG_SPIFFS_GC_MAX_RUNS blocks per call.

#include "storage_budget.h"

static uint64_t div_up(uint64_t a, uint64_t b)
{
    return (a + b - 1) / b;
}

// Live pages a file of `bytes` holds: its data pages plus the object index
// header page. (A file needs further index pages only beyond ~490 data pages,
// more than the partition has.)
static uint64_t live_pages(const storage_geometry_t *g, uint64_t bytes)
{
    return bytes ? div_up(bytes, g->page_payload) + 1 : 0;
}

uint32_t storage_budget_footprint_pages(const storage_geometry_t *g,
                                        uint64_t bytes)
{
    if (bytes == 0) return 0;
    return (uint32_t) (live_pages(g, bytes) + div_up(bytes, g->write_buf));
}

uint32_t storage_budget_overhead_pages(const storage_geometry_t *g,
                                       uint32_t live)
{
    // Stale index pages of the files that are there: one per write call,
    // i.e. per write_buf bytes of content.
    uint64_t stale = div_up((uint64_t) live * g->page_payload, g->write_buf);
    // One relocation page per block those files occupy (GC rotation).
    uint64_t blocks = div_up((uint64_t) live + stale, g->data_pages_per_block);
    uint64_t reserve = (uint64_t) g->reserve_blocks * g->data_pages_per_block;
    return (uint32_t) (stale + blocks + reserve);
}

// Whether `live` pages of files plus their overhead fit on a partition of
// `capacity` data pages.
static bool within(const storage_geometry_t *g, uint64_t capacity, uint64_t live)
{
    return live + storage_budget_overhead_pages(g, (uint32_t) live) <= capacity;
}

uint64_t storage_budget_community(const storage_geometry_t *g, uint64_t total,
                                  uint64_t used)
{
    uint64_t capacity = total / g->page_payload;
    uint64_t live     = div_up(used, g->page_payload);
    if (!within(g, capacity, live)) return 0;

    // Largest file size that still fits. The predicate is monotonic in the
    // size, so a binary search over bytes finds it exactly.
    uint64_t lo = 0, hi = total;
    while (lo < hi) {
        uint64_t mid = lo + (hi - lo + 1) / 2;
        if (within(g, capacity, live + live_pages(g, mid))) {
            lo = mid;
        } else {
            hi = mid - 1;
        }
    }
    return lo;
}

bool storage_budget_asset_fits(const storage_geometry_t *g, uint64_t total,
                               uint64_t used, uint64_t bytes)
{
    uint64_t capacity = total / g->page_payload;
    uint64_t live     = div_up(used, g->page_payload);
    return within(g, capacity, live + live_pages(g, bytes));
}
