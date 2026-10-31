// Host test for storage_budget.c — the arithmetic behind the shared budget of
// the "storage" partition (see storage_budget.h for the derivation): SPIFFS
// on the classic ESP32, LittleFS on the C3 and the S3, and the dialog tiers.
//
// The SPIFFS geometry is the classic ESP32's: 1024-byte logical pages (1019 bytes of file
// data each), 4 KB blocks with three data pages, 4 KB write buffer, six
// reserved blocks. `total` is what esp_spiffs_info() reports for the 640 KB
// partition: (160 - 2 spare blocks) * 3 + 1 = 475 data pages = 484025 bytes
// (test_storage_sim.c checks that number against the real SPIFFS core).
// Pure C, exact expected values worked out by hand in the comments.
#include <stdio.h>

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

static const storage_geometry_t G = {
    .page_payload = 1019, .data_pages_per_block = 3,
    .write_buf = 4096, .reserve_blocks = 6,
};

#define TOTAL 484025ull   // 475 data pages

static void test_footprint(void)
{
    printf("footprint\n");
    CHECK_EQ(storage_budget_footprint_pages(&G, 0), 0, "empty file");
    // 1 data page + index header + 1 stale index page (one write call).
    CHECK_EQ(storage_budget_footprint_pages(&G, 1), 3, "1 byte");
    CHECK_EQ(storage_budget_footprint_pages(&G, 1019), 3, "exactly one page");
    CHECK_EQ(storage_budget_footprint_pages(&G, 1020), 4, "one byte into page 2");
    // One full write buffer: ceil(4096/1019) = 5 data + 1 header + 1 stale.
    CHECK_EQ(storage_budget_footprint_pages(&G, 4096), 7, "one write buffer");
    // 40 KB, a typical community list: 41 data + 1 + 10 stale.
    CHECK_EQ(storage_budget_footprint_pages(&G, 40960), 52, "40 KB");
}

static void test_overhead(void)
{
    printf("overhead\n");
    // Nothing on flash: only the reserve, 6 blocks * 3 pages.
    CHECK_EQ(storage_budget_overhead_pages(&G, 0), 18, "empty partition");
    // 1 page: 1 stale + ceil(2/3) = 1 rotation page + 18.
    CHECK_EQ(storage_budget_overhead_pages(&G, 1), 20, "one page");
    // 100 pages: stale ceil(101900/4096) = 25, blocks ceil(125/3) = 42.
    CHECK_EQ(storage_budget_overhead_pages(&G, 100), 85, "100 pages");
    // 300 pages: stale ceil(305700/4096) = 75, blocks ceil(375/3) = 125.
    CHECK_EQ(storage_budget_overhead_pages(&G, 300), 218, "300 pages");
}

static void test_community(void)
{
    printf("community budget\n");
    // Empty partition. 277168 B = 272 full pages, live 273 (+ header):
    // 273 + stale 68 + blocks ceil(341/3) = 114 + 18 = 473 <= 475. One byte
    // more needs a 274th page: 274 + 69 + 115 + 18 = 476 > 475.
    CHECK_EQ(storage_budget_community(&G, TOTAL, 0), 277168, "empty partition");
    // Typical dongle: German UI pack (29 KB), mail pack, announcement
    // (79 KB) and personal list — 120 pages in use.
    CHECK_EQ(storage_budget_community(&G, TOTAL, 120 * 1019), 154888,
             "typical i18n assets on flash");
    // ... where the old formula, free - 48 KB, offered 312593 B.
    CHECK_EQ(storage_budget_community(&G, TOTAL, 150 * 1019), 124318,
             "150 pages in use");
    CHECK_EQ(storage_budget_community(&G, TOTAL, 200 * 1019), 73368,
             "200 pages in use");
    CHECK_EQ(storage_budget_community(&G, TOTAL, 250 * 1019), 22418,
             "250 pages in use");
    // 280 pages: 280 + 70 + 117 + 18 = 485 > 475 even without a list.
    CHECK_EQ(storage_budget_community(&G, TOTAL, 280 * 1019), 0,
             "own recording + assets leave no room");
    CHECK_EQ(storage_budget_community(&G, TOTAL, TOTAL), 0, "full partition");
    // A partial page in `used` counts as a whole page.
    CHECK_EQ(storage_budget_community(&G, TOTAL, 1),
             storage_budget_community(&G, TOTAL, 1019), "used rounds up to pages");

    // The budget never grows when more is on flash.
    uint64_t prev = storage_budget_community(&G, TOTAL, 0);
    bool monotonic = true;
    for (uint64_t used = 1019; used <= TOTAL; used += 1019) {
        uint64_t b = storage_budget_community(&G, TOTAL, used);
        if (b > prev) monotonic = false;
        prev = b;
    }
    CHECK(monotonic, "budget non-increasing in used");

    // A larger reserve shrinks the budget: 6 more blocks = 18 pages.
    storage_geometry_t g2 = G;
    g2.reserve_blocks = 12;
    CHECK(storage_budget_community(&g2, TOTAL, 120 * 1019)
          < storage_budget_community(&G, TOTAL, 120 * 1019),
          "more reserve, smaller budget");
}

static void test_asset_fits(void)
{
    printf("asset fits\n");
    // Asset and community are budgeted alike, so the largest asset that
    // fits is exactly the community budget for the same partition state.
    uint64_t used = 120 * 1019;
    uint64_t limit = storage_budget_community(&G, TOTAL, used);
    CHECK(storage_budget_asset_fits(&G, TOTAL, used, limit), "fits at the limit");
    CHECK(!storage_budget_asset_fits(&G, TOTAL, used, limit + 1),
          "one byte over does not fit");
    CHECK(storage_budget_asset_fits(&G, TOTAL, 0, 240 * 1024),
          "max own recording fits an empty partition");
    // A full recording next to typical assets breaks the budget — that is
    // when storage_make_room() evicts the community list.
    CHECK(!storage_budget_asset_fits(&G, TOTAL, 160 * 1019, 240 * 1024),
          "max own recording next to assets and a list does not fit");
    CHECK(storage_budget_asset_fits(&G, TOTAL, 0, 0), "empty file fits");
}

// --- LittleFS ----------------------------------------------------------------

static const storage_lfs_geometry_t L = {
    .block_size = 4096, .reserve_blocks = STORAGE_LFS_RESERVE_BLOCKS,
};

#define C3_TOTAL 655360ull           // partitions-c3.csv: 0xA0000, 160 blocks
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
    // Empty C3 partition: 160 - 2 root - 8 reserve = 150 blocks,
    // 614400 - 4 * (298 - popcount(149)) = 613224 bytes.
    CHECK_EQ(storage_budget_lfs_community(&L, C3_TOTAL, ROOT), 613224, "empty C3 partition");
    CHECK(storage_budget_lfs_file_blocks(&L, 613224) == 150 &&
          storage_budget_lfs_file_blocks(&L, 613225) == 151,
          "the budget is exactly what the free blocks hold");
    CHECK_EQ(storage_budget_lfs_community(&L, C3_TOTAL, C3_TOTAL), 0, "full partition");
    CHECK_EQ(storage_budget_lfs_community(&L, C3_TOTAL, C3_TOTAL - 8 * 4096), 0,
             "only the reserve left");
    CHECK_EQ(storage_budget_lfs_community(&L, C3_TOTAL, ROOT + 1),
             storage_budget_lfs_community(&L, C3_TOTAL, ROOT + 4096),
             "used rounds up to blocks");

    // The reserve is exactly STORAGE_LFS_RESERVE_BLOCKS blocks.
    storage_lfs_geometry_t none = L;
    none.reserve_blocks = 0;
    CHECK_EQ(storage_budget_lfs_file_blocks(&none,
                 storage_budget_lfs_community(&none, C3_TOTAL, ROOT))
             - storage_budget_lfs_file_blocks(&L,
                 storage_budget_lfs_community(&L, C3_TOTAL, ROOT)),
             STORAGE_LFS_RESERVE_BLOCKS, "reserve blocks kept free");
    CHECK_EQ(STORAGE_LFS_RESERVE_BLOCKS, 8, "reserve: 2 split + 2 relocation + 4 unbudgeted");

    uint64_t prev = storage_budget_lfs_community(&L, C3_TOTAL, 0);
    bool monotonic = true;
    for (uint64_t used = 4096; used <= C3_TOTAL; used += 4096) {
        uint64_t b = storage_budget_lfs_community(&L, C3_TOTAL, used);
        if (b > prev) monotonic = false;
        prev = b;
    }
    CHECK(monotonic, "budget non-increasing in used");
}

static void test_lfs_asset_fits(void)
{
    printf("littlefs asset fits\n");
    uint64_t used = ROOT + 20 * 4096;
    uint64_t limit = storage_budget_lfs_community(&L, C3_TOTAL, used);
    CHECK(storage_budget_lfs_asset_fits(&L, C3_TOTAL, used, limit), "fits at the limit");
    CHECK(!storage_budget_lfs_asset_fits(&L, C3_TOTAL, used, limit + 1),
          "one byte over does not fit");
    CHECK(storage_budget_lfs_asset_fits(&L, C3_TOTAL, used, 0), "empty file fits");
    // The largest own recording next to the packs and a 128 KB list on the C3.
    uint64_t ann_mode = ROOT + blocks(52 * 1024) + blocks(4096) + blocks(4096)
                        + blocks(128 * 1024);
    CHECK(storage_budget_lfs_asset_fits(&L, C3_TOTAL, ann_mode, 240 * 1024),
          "own recording next to packs and a 128 KB list (C3)");
}

// The dialog tiers: S on SPIFFS (storage.c), on LittleFS by the partition's
// size; the C3's M derivation in numbers (storage_budget.h).
static void test_dialog(void)
{
    printf("dialog tiers\n");
    CHECK(STORAGE_DIALOG_TIER_S == 128 * 1024, "S is 128 KiB");
    CHECK(STORAGE_DIALOG_TIER_S < STORAGE_DIALOG_TIER_M &&
          STORAGE_DIALOG_TIER_M < STORAGE_DIALOG_PLAIN_FS_MAX, "S < M < L ceiling");

    // C3: 160 - 8 reserve - 2 root - 33 community - (14 + 1 + 1 + 3) packs,
    // list, index - 14 (a pack update's second copy) = 84 blocks for the set.
    CHECK_EQ(storage_budget_lfs_dialog_room(&L, C3_TOTAL), 343416, "C3 room for the set");
    CHECK(STORAGE_DIALOG_TIER_M <= storage_budget_lfs_dialog_room(&L, C3_TOTAL) &&
          STORAGE_DIALOG_TIER_M + 16 * 1024 > storage_budget_lfs_dialog_room(&L, C3_TOTAL) &&
          STORAGE_DIALOG_TIER_M % (16 * 1024) == 0,
          "M is the C3 room rounded down to 16 KiB");
    CHECK_EQ(storage_budget_lfs_dialog(&L, C3_TOTAL), STORAGE_DIALOG_TIER_M, "C3 takes M");
    CHECK_EQ(storage_budget_lfs_dialog(&L, S3_TOTAL), STORAGE_DIALOG_PLAIN_FS_MAX,
             "S3 takes the L ceiling");
    CHECK_EQ(storage_budget_lfs_dialog(&L, 64 * 4096), STORAGE_DIALOG_TIER_S,
             "too small for M: S");
    CHECK_EQ(storage_budget_lfs_dialog(&L, 0), STORAGE_DIALOG_TIER_S, "nothing known: S");

    // The budget never shrinks as the partition grows.
    uint64_t prev = 0;
    bool monotonic = true;
    for (uint64_t t = 0; t < 16ull * 1024 * 1024; t += 4096) {
        uint64_t b = storage_budget_lfs_dialog(&L, t);
        if (b < prev) monotonic = false;
        prev = b;
    }
    CHECK(monotonic, "dialog budget non-decreasing in the partition size");

    // Everyday C3 in dialog mode: an M set and the packs on flash. The
    // community list keeps (at least) the 128 KB bucket, and next to such a
    // list a pack update's second copy still fits — no eviction.
    uint64_t c3 = ROOT + blocks(52 * 1024) + blocks(4096) + blocks(4096) + blocks(8192)
                  + blocks(STORAGE_DIALOG_TIER_M);
    uint64_t c3_community = storage_budget_lfs_community(&L, C3_TOTAL, c3);
    printf("    C3 community budget next to an M set: %llu B\n",
           (unsigned long long) c3_community);
    CHECK(c3_community >= 128 * 1024 && c3_community < 256 * 1024,
          "C3 with an M set: the 128 KB bucket");
    CHECK(storage_budget_lfs_asset_fits(&L, C3_TOTAL, c3 + blocks(128 * 1024), 52 * 1024),
          "C3: a pack update fits next to the 128 KB list");
    // The set's replacement is delete-first: the new set fits where the old
    // one was, next to the list.
    CHECK(storage_budget_lfs_asset_fits(&L, C3_TOTAL,
                                        c3 - blocks(STORAGE_DIALOG_TIER_M) + blocks(128 * 1024),
                                        STORAGE_DIALOG_TIER_M),
          "C3: a new M set fits next to the 128 KB list");

    // S3: an L set at the ceiling and the packs leave a community budget far
    // above the server's largest bucket (512 KB).
    uint64_t s3 = ROOT + blocks(52 * 1024) + blocks(4096) + blocks(4096) + blocks(8192)
                  + blocks(STORAGE_DIALOG_PLAIN_FS_MAX);
    uint64_t s3_community = storage_budget_lfs_community(&L, S3_TOTAL, s3);
    printf("    S3 community budget next to a 2 MB set: %llu B\n",
           (unsigned long long) s3_community);
    CHECK(s3_community > 512 * 1024, "S3: every bucket");
}

int main(void)
{
    test_footprint();
    test_overhead();
    test_community();
    test_asset_fits();
    test_lfs_file_blocks();
    test_lfs_community();
    test_lfs_asset_fits();
    test_dialog();

    printf("\n%d checks, %d failed\n", tests_run, tests_failed);
    return tests_failed ? 1 : 0;
}
