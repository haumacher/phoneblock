// Host test for storage_budget.c — the arithmetic behind the shared budget of
// the "storage" partition (see storage_budget.h for the derivation): LittleFS
// on every board (640 KB on the classic ESP32 and the C3, ~9.6 MB on the S3),
// and the dialog tiers. Pure C, exact expected values worked out by hand in
// the comments; test_storage_sim.c runs the same budget against the real
// littlefs core.
#include <stdio.h>

#include "stats.h"            // the call list's size (#589)
#include "storage_budget.h"

static int tests_run, tests_failed;

#define CHECK(cond, what) do {                                  \
    tests_run++;                                                \
    if (cond) printf("  ok: %s\n", (what));                     \
    else { tests_failed++; printf("  FAIL: %s (%s:%d)\n",       \
                                  (what), __FILE__, __LINE__); } \
} while (0)

#define CHECK_EQ(got, want, what) do {                                      \
    unsigned long long g_ = (unsigned long long)(got);                      \
    unsigned long long w_ = (unsigned long long)(want);                     \
    tests_run++;                                                            \
    if (g_ == w_) printf("  ok: %s = %llu\n", (what), g_);                  \
    else { tests_failed++; printf("  FAIL: %s: got %llu, want %llu (%s:%d)\n", \
                                  (what), g_, w_, __FILE__, __LINE__); }    \
} while (0)

// --- LittleFS ----------------------------------------------------------------

static const storage_lfs_geometry_t L = {
    .block_size = 4096, .reserve_blocks = STORAGE_LFS_RESERVE_BLOCKS,
};

// partitions.csv (classic ESP32) and partitions-c3.csv: 0xA0000, 160 blocks
// (test_partitions() reads both tables).
#define K640_TOTAL 655360ull
#define S3_TOTAL 0x9A0000ull         // partitions-16mb.csv: 2464 blocks
#define ROOT     (2ull * 4096)       // the root metadata pair, used from the start

static uint64_t blocks(uint64_t bytes)
{
    return storage_budget_lfs_file_blocks(&L, bytes) * 4096;
}

static void test_lfs_file_blocks(void)
{
    printf("littlefs file blocks\n");
    CHECK_EQ(storage_budget_lfs_file_blocks(&L, 0), 0, "empty file");
    CHECK_EQ(storage_budget_lfs_file_blocks(&L, 1), 1, "1 byte");
    CHECK_EQ(storage_budget_lfs_file_blocks(&L, 4096), 1, "one full block (no pointer)");
    // Block 1 starts with ctz(1) + 1 = 1 pointer: 2 blocks hold 8188 bytes.
    CHECK_EQ(storage_budget_lfs_file_blocks(&L, 4097), 2, "one byte into block 2");
    CHECK_EQ(storage_budget_lfs_file_blocks(&L, 8188), 2, "2 blocks minus a pointer");
    CHECK_EQ(storage_budget_lfs_file_blocks(&L, 8189), 3, "one byte more");
    // 3 blocks: 12288 - 4 * (1 + 2) pointers.
    CHECK_EQ(storage_budget_lfs_file_blocks(&L, 12276), 3, "3 blocks");
    CHECK_EQ(storage_budget_lfs_file_blocks(&L, 12277), 4, "4 blocks");
    // 128 KiB: 32 blocks would hold 131072 - 4 * (62 - popcount(31)) = 130844.
    CHECK_EQ(storage_budget_lfs_file_blocks(&L, 128 * 1024), 33, "128 KiB");

    // Never fewer blocks than the plain block count, never more than the
    // pointers' share beyond it (on average just under two per block, 8 of
    // 4096 bytes), and monotonic.
    bool ok = true;
    uint64_t prev = 0;
    for (uint64_t b = 1; b < 3u * 1024 * 1024; b += 997) {
        uint64_t n = storage_budget_lfs_file_blocks(&L, b);
        uint64_t plain = (b + 4095) / 4096;
        if (n < plain || n > plain + plain / 256 + 1 || n < prev) ok = false;
        prev = n;
    }
    CHECK(ok, "plain count plus the pointers' share, non-decreasing");
}

static void test_lfs_community(void)
{
    printf("littlefs community budget\n");
    // Empty 640 KB partition: 160 - 2 root - 8 reserve = 150 blocks,
    // 614400 - 4 * (298 - popcount(149)) = 613224 bytes.
    CHECK_EQ(storage_budget_lfs_community(&L, K640_TOTAL, ROOT), 613224, "empty 640 KB partition");
    CHECK(storage_budget_lfs_file_blocks(&L, 613224) == 150 &&
          storage_budget_lfs_file_blocks(&L, 613225) == 151,
          "the budget is exactly what the free blocks hold");
    CHECK_EQ(storage_budget_lfs_community(&L, K640_TOTAL, K640_TOTAL), 0, "full partition");
    CHECK_EQ(storage_budget_lfs_community(&L, K640_TOTAL, K640_TOTAL - 8 * 4096), 0,
             "only the reserve left");
    CHECK_EQ(storage_budget_lfs_community(&L, K640_TOTAL, ROOT + 1),
             storage_budget_lfs_community(&L, K640_TOTAL, ROOT + 4096),
             "used rounds up to blocks");

    // The reserve is exactly STORAGE_LFS_RESERVE_BLOCKS blocks.
    storage_lfs_geometry_t none = L;
    none.reserve_blocks = 0;
    CHECK_EQ(storage_budget_lfs_file_blocks(&none,
                 storage_budget_lfs_community(&none, K640_TOTAL, ROOT))
             - storage_budget_lfs_file_blocks(&L,
                 storage_budget_lfs_community(&L, K640_TOTAL, ROOT)),
             STORAGE_LFS_RESERVE_BLOCKS, "reserve blocks kept free");
    CHECK_EQ(STORAGE_LFS_RESERVE_BLOCKS, 8, "reserve: 2 split + 2 relocation + 4 unbudgeted");

    uint64_t prev = storage_budget_lfs_community(&L, K640_TOTAL, 0);
    bool monotonic = true;
    for (uint64_t used = 4096; used <= K640_TOTAL; used += 4096) {
        uint64_t b = storage_budget_lfs_community(&L, K640_TOTAL, used);
        if (b > prev) monotonic = false;
        prev = b;
    }
    CHECK(monotonic, "budget non-increasing in used");
}

static void test_lfs_asset_fits(void)
{
    printf("littlefs asset fits\n");
    uint64_t used = ROOT + 20 * 4096;
    uint64_t limit = storage_budget_lfs_community(&L, K640_TOTAL, used);
    CHECK(storage_budget_lfs_asset_fits(&L, K640_TOTAL, used, limit), "fits at the limit");
    CHECK(!storage_budget_lfs_asset_fits(&L, K640_TOTAL, used, limit + 1),
          "one byte over does not fit");
    CHECK(storage_budget_lfs_asset_fits(&L, K640_TOTAL, used, 0), "empty file fits");
    // The largest own recording next to the packs and a 128 KB list on a
    // 640 KB partition.
    uint64_t ann_mode = ROOT + blocks(52 * 1024) + blocks(4096) + blocks(4096)
                        + blocks(128 * 1024);
    CHECK(storage_budget_lfs_asset_fits(&L, K640_TOTAL, ann_mode, 240 * 1024),
          "own recording next to packs and a 128 KB list (640 KB)");
}

// The storage partitions of the partition tables, as the Makefile reads them:
// the classic ESP32 and the C3 share the 640 KB the M tier is derived for.
static void test_partitions(void)
{
    printf("partition tables\n");
    CHECK_EQ(PART_CLASSIC, K640_TOTAL, "partitions.csv storage");
    CHECK_EQ(PART_C3, K640_TOTAL, "partitions-c3.csv storage");
    CHECK_EQ(PART_S3, S3_TOTAL, "partitions-16mb.csv storage");
    CHECK_EQ(storage_budget_lfs_dialog(&L, PART_CLASSIC), STORAGE_DIALOG_TIER_M,
             "classic ESP32: M");
    CHECK_EQ(storage_budget_lfs_dialog(&L, PART_S3), STORAGE_DIALOG_PLAIN_FS_MAX,
             "S3: the L ceiling");
}

// The dialog tiers by the partition's size; the M derivation for the 640 KB
// partitions in numbers (storage_budget.h).
static void test_dialog(void)
{
    printf("dialog tiers\n");
    CHECK(STORAGE_DIALOG_TIER_M == 320 * 1024, "M is 320 KiB");
    CHECK(STORAGE_DIALOG_TIER_M < STORAGE_DIALOG_PLAIN_FS_MAX, "M < L ceiling");

    // 640 KB: 160 - 8 reserve - 2 root - 33 community - (14 + 1 + 1 + 3 + 3)
    // packs, list, index, call list - 14 (a pack update's second copy) = 81
    // blocks for the set (84 before the call list, #589).
    CHECK_EQ(storage_budget_lfs_dialog_room(&L, K640_TOTAL), 331144, "640 KB room for the set");
    CHECK(STORAGE_DIALOG_TIER_M <= storage_budget_lfs_dialog_room(&L, K640_TOTAL) &&
          STORAGE_DIALOG_TIER_M + 16 * 1024 > storage_budget_lfs_dialog_room(&L, K640_TOTAL) &&
          STORAGE_DIALOG_TIER_M % (16 * 1024) == 0,
          "M is the 640 KB room rounded down to 16 KiB");
    CHECK_EQ(storage_budget_lfs_dialog(&L, K640_TOTAL), STORAGE_DIALOG_TIER_M,
             "classic ESP32 and C3 take M");
    CHECK_EQ(storage_budget_lfs_dialog(&L, S3_TOTAL), STORAGE_DIALOG_PLAIN_FS_MAX,
             "S3 takes the L ceiling");
    CHECK_EQ(storage_budget_lfs_dialog(&L, 64 * 4096), 0, "too small for M: no tier");
    CHECK_EQ(storage_budget_lfs_dialog(&L, 0), 0, "nothing known: no tier");

    // The budget never shrinks as the partition grows.
    uint64_t prev = 0;
    bool monotonic = true;
    for (uint64_t t = 0; t < 16ull * 1024 * 1024; t += 4096) {
        uint64_t b = storage_budget_lfs_dialog(&L, t);
        if (b < prev) monotonic = false;
        prev = b;
    }
    CHECK(monotonic, "dialog budget non-decreasing in the partition size");

    // Everyday 640 KB dongle in dialog mode: an M set and the packs on flash. The
    // community list keeps (at least) the 128 KB bucket, and next to such a
    // list a pack update's second copy still fits — no eviction.
    uint64_t c3 = ROOT + blocks(52 * 1024) + blocks(4096) + blocks(4096) + blocks(8192)
                  + blocks(STORAGE_LFS_CALLS_BYTES) + blocks(STORAGE_DIALOG_TIER_M);
    uint64_t c3_community = storage_budget_lfs_community(&L, K640_TOTAL, c3);
    printf("    640 KB community budget next to an M set: %llu B\n",
           (unsigned long long) c3_community);
    CHECK(c3_community >= 128 * 1024 && c3_community < 256 * 1024,
          "640 KB with an M set: the 128 KB bucket");
    CHECK(storage_budget_lfs_asset_fits(&L, K640_TOTAL, c3 + blocks(128 * 1024), 52 * 1024),
          "640 KB: a pack update fits next to the 128 KB list");
    // The set's replacement is delete-first: the new set fits where the old
    // one was, next to the list.
    CHECK(storage_budget_lfs_asset_fits(&L, K640_TOTAL,
                                        c3 - blocks(STORAGE_DIALOG_TIER_M) + blocks(128 * 1024),
                                        STORAGE_DIALOG_TIER_M),
          "640 KB: a new M set fits next to the 128 KB list");

    // S3: an L set at the ceiling and the packs leave a community budget far
    // above the server's largest bucket (512 KB).
    uint64_t s3 = ROOT + blocks(52 * 1024) + blocks(4096) + blocks(4096) + blocks(8192)
                  + blocks(STATS_CALLS_FILE_BYTES(STATS_CALL_CAPACITY_LARGE))
                  + blocks(STORAGE_DIALOG_PLAIN_FS_MAX);
    uint64_t s3_community = storage_budget_lfs_community(&L, S3_TOTAL, s3);
    printf("    S3 community budget next to a 2 MB set: %llu B\n",
           (unsigned long long) s3_community);
    CHECK(s3_community > 512 * 1024, "S3: every bucket");
    // Next to the largest list the S3's call list can be rewritten whole
    // (an update of its first record) far outside the reserve.
    CHECK(storage_budget_lfs_asset_fits(&L, S3_TOTAL, s3 + blocks(512 * 1024),
                                        STATS_CALLS_FILE_BYTES(STATS_CALL_CAPACITY_LARGE)),
          "S3: an in-place rewrite of the 200-call list fits next to a 512 KB list");
}

// The call list (#589) on the shared partition: its size per layout, and
// that its in-place updates fit what the budget keeps free.
static void test_calls_file(void)
{
    printf("call list\n");
    CHECK_EQ(STORAGE_LFS_CALLS_BYTES, STATS_CALLS_FILE_BYTES(STATS_CALL_CAPACITY_SMALL),
             "storage_budget.h and stats.h agree on the 4 MB call list");
    CHECK_EQ(STATS_CALLS_FILE_BYTES(STATS_CALL_CAPACITY_SMALL), 12032,
             "4 MB boards: 50 calls");
    CHECK_EQ(storage_budget_lfs_file_blocks(&L, STORAGE_LFS_CALLS_BYTES), 3,
             "4 MB boards: 3 blocks");
    CHECK_EQ(storage_budget_lfs_file_blocks(&L,
                 STATS_CALLS_FILE_BYTES(STATS_CALL_CAPACITY_LARGE)), 12,
             "S3 / Linux: 200 calls in 12 blocks");
    // A record written into the call list rewrites it from that block on:
    // at most its 3 blocks in flight, which the reserve's 4 blocks for
    // unbudgeted small writes hold (the personal list's growth and the
    // inlined dialog marker need next to none).
    CHECK(storage_budget_lfs_file_blocks(&L, STORAGE_LFS_CALLS_BYTES)
              <= STORAGE_LFS_RESERVE_BLOCKS - 4,
          "640 KB: an in-place update fits the reserve's unbudgeted blocks");
    // The 640 KB partition at its fullest in dialog mode (an M set, the
    // packs, the call list, the list at its budget): exactly the reserve
    // free, and the call list's rewrite fits in it.
    uint64_t full = ROOT + blocks(52 * 1024) + blocks(4096) + blocks(4096) + blocks(8192)
                    + blocks(STORAGE_LFS_CALLS_BYTES) + blocks(STORAGE_DIALOG_TIER_M);
    uint64_t list = storage_budget_lfs_community(&L, K640_TOTAL, full);
    uint64_t free_blocks = K640_TOTAL / 4096 - (full + blocks(list)) / 4096;
    CHECK(free_blocks == STORAGE_LFS_RESERVE_BLOCKS
          && free_blocks >= storage_budget_lfs_file_blocks(&L, STORAGE_LFS_CALLS_BYTES),
          "640 KB at its fullest: the reserve holds the call list's rewrite");
}

int main(void)
{
    test_partitions();
    test_lfs_file_blocks();
    test_lfs_community();
    test_lfs_asset_fits();
    test_dialog();
    test_calls_file();

    printf("\n%d checks, %d failed\n", tests_run, tests_failed);
    return tests_failed ? 1 : 0;
}
