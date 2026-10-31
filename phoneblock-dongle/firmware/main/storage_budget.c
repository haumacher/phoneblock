// Implementation of storage_budget.h. Pure arithmetic — see the header for
// the derivation; the SPIFFS references below are to ESP-IDF v5.3,
// components/spiffs/spiffs/src, the LittleFS ones to littlefs v2 (lfs.c,
// DESIGN.md) as the joltwallet/littlefs component bundles it.
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

// --- LittleFS ----------------------------------------------------------------

// Bytes `n` blocks of a CTZ skip list hold: block i (i >= 1) starts with
// ctz(i) + 1 four-byte pointers, and the sum of ctz(i) + 1 over i = 1..m is
// 2m - popcount(m) (lfs_ctz_index() inverts the same identity).
static uint64_t lfs_capacity(const storage_lfs_geometry_t *g, uint64_t n)
{
    if (n == 0) return 0;
    uint64_t m = n - 1;
    return n * g->block_size - 4u * (2u * m - (uint64_t) __builtin_popcountll(m));
}

uint64_t storage_budget_lfs_file_blocks(const storage_lfs_geometry_t *g,
                                        uint64_t bytes)
{
    if (bytes == 0) return 0;
    // The pointers take at most 4 * (ctz + 1) <= 4 * 65 bytes of a block, so
    // the plain block count is a lower bound and a few steps up reach it.
    uint64_t n = div_up(bytes, g->block_size);
    while (lfs_capacity(g, n) < bytes) n++;
    return n;
}

// Blocks the partition has for files beyond `used` and the reserve; 0 if
// none.
static uint64_t lfs_free_blocks(const storage_lfs_geometry_t *g, uint64_t total,
                                uint64_t used)
{
    uint64_t capacity = total / g->block_size;
    uint64_t taken    = div_up(used, g->block_size) + g->reserve_blocks;
    return taken < capacity ? capacity - taken : 0;
}

uint64_t storage_budget_lfs_community(const storage_lfs_geometry_t *g,
                                      uint64_t total, uint64_t used)
{
    return lfs_capacity(g, lfs_free_blocks(g, total, used));
}

bool storage_budget_lfs_asset_fits(const storage_lfs_geometry_t *g,
                                   uint64_t total, uint64_t used,
                                   uint64_t bytes)
{
    return storage_budget_lfs_file_blocks(g, bytes)
           <= lfs_free_blocks(g, total, used);
}

// --- The dialog clip set -------------------------------------------------------

// The root directory's metadata pair: on a freshly formatted partition it is
// what lfs_fs_size() already reports as used.
#define LFS_ROOT_BLOCKS 2u

uint64_t storage_budget_lfs_dialog_room(const storage_lfs_geometry_t *g,
                                        uint64_t total)
{
    static const uint32_t peers[] = STORAGE_LFS_DIALOG_PEERS;
    uint64_t used = LFS_ROOT_BLOCKS
                    + storage_budget_lfs_file_blocks(g, STORAGE_LFS_DIALOG_COMMUNITY);
    uint64_t largest = 0;
    for (unsigned i = 0; i < sizeof(peers) / sizeof(peers[0]); i++) {
        uint64_t n = storage_budget_lfs_file_blocks(g, peers[i]);
        used += n;
        if (n > largest) largest = n;
    }
    // The pack update in flight: its old copy stays until the rename.
    used += largest;
    return lfs_capacity(g, lfs_free_blocks(g, total, used * g->block_size));
}

uint64_t storage_budget_lfs_dialog(const storage_lfs_geometry_t *g,
                                   uint64_t total)
{
    uint64_t room = storage_budget_lfs_dialog_room(g, total);
    if (room >= STORAGE_DIALOG_PLAIN_FS_MAX) return STORAGE_DIALOG_PLAIN_FS_MAX;
    if (room >= STORAGE_DIALOG_TIER_M) return STORAGE_DIALOG_TIER_M;
    return STORAGE_DIALOG_TIER_S;
}
