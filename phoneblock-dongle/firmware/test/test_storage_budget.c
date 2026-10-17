// Host test for storage_budget.c — the arithmetic behind the shared budget of
// the "storage" SPIFFS partition (see storage_budget.h for the derivation).
//
// The geometry is the dongle's: 1024-byte logical pages (1019 bytes of file
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

int main(void)
{
    test_footprint();
    test_overhead();
    test_community();
    test_asset_fits();

    printf("\n%d checks, %d failed\n", tests_run, tests_failed);
    return tests_failed ? 1 : 0;
}
