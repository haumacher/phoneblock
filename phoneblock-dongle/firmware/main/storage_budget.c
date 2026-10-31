// Implementation of storage_budget.h. Pure arithmetic — see the header for
// the derivation; the references are to littlefs v2 (lfs.c, DESIGN.md) as
// the joltwallet/littlefs component bundles it.

#include "storage_budget.h"

static uint64_t div_up(uint64_t a, uint64_t b)
{
    return (a + b - 1) / b;
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
    return 0;
}
