// Host test for heap_guard_scan.c — the block-sanity predicate behind the
// heap sentinel's deliberate panic.
//
// Two properties matter and are tested from both sides:
//   * it must never accuse a healthy block, because a false positive reboots a
//     working dongle (and would do so every few seconds); and
//   * it must catch the step that actually crashed dongle 1.5.1, whose figures
//     are pinned in test_regression_1_5_1() straight from the core dump.

#include <assert.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>

#include "../main/heap_guard_scan.h"

static int tests_run, tests_failed;
#define CHECK(cond, msg) do {                                   \
    tests_run++;                                                \
    if (!(cond)) { tests_failed++;                              \
        printf("  FAIL: %s (%s:%d)\n", msg, __FILE__, __LINE__);\
    } else printf("  ok: %s\n", msg);                           \
} while (0)

// Bounds in the shape the ESP32 reports them: the internal DRAM heap of the
// dongle that produced the 1.5.1 dump started at .dram0.heap_start.
static const hg_pool_t POOL = { 0x3ffbf3c0u, 0x3ffe0000u };

static void test_healthy(void)
{
    printf("test_healthy\n");
    CHECK(hg_classify(POOL, 0x3ffd0008u, 64) == HG_OK, "ordinary block");
    CHECK(hg_classify(POOL, POOL.start, 16) == HG_OK, "block at heap start");
    CHECK(hg_classify(POOL, 0x3ffd0008u, 4) == HG_OK, "smallest aligned block");

    // A block that reaches exactly to the last byte of the heap is legal — the
    // final allocation in a pool looks like this, and accusing it would panic
    // every few seconds on a healthy device.
    uintptr_t ptr = 0x3ffdf000u;
    CHECK(hg_classify(POOL, ptr, (size_t)(POOL.end - ptr)) == HG_OK,
          "block ending exactly at heap end");

    // Sweep a synthetic pool the way the walker would, to be sure nothing in
    // the ordinary range trips the predicate.
    int bad = 0;
    for (uintptr_t p = POOL.start; p + 128 < POOL.end; p += 128)
        if (hg_classify(POOL, p, 128) != HG_OK) bad++;
    CHECK(bad == 0, "sweep of aligned blocks yields no verdict");
}

static void test_regression_1_5_1(void)
{
    printf("test_regression_1_5_1\n");
    // From the core dump: tlsf_walk_pool stepped to block 0xb26c6190 and
    // faulted reading its size (excvaddr 0xb26c6194, LoadProhibited). That
    // successor is prev + prev_size, so the block reported just before the
    // fault carried this size. The predicate has to reject it *before* the
    // walker takes the step.
    uintptr_t ptr  = 0x3ffd0008u;
    size_t    size = 0xb26c6190u - 0x3ffd0000u;
    CHECK(hg_classify(POOL, ptr, size) == HG_BAD_BOUNDS,
          "1.5.1 wild successor rejected as out of bounds");

    // And the evidence window around it stays inside the heap.
    uintptr_t from; size_t len;
    hg_window(POOL, ptr, 192, 64, &from, &len);
    CHECK(from >= POOL.start && from + len <= POOL.end,
          "1.5.1 evidence window stays within the heap");
    CHECK(len == 192 + 64, "1.5.1 evidence window is full size");
}

static void test_rejections(void)
{
    printf("test_rejections\n");
    CHECK(hg_classify(POOL, POOL.start - 4, 16) == HG_BAD_PTR, "below heap");
    CHECK(hg_classify(POOL, POOL.end, 16) == HG_BAD_PTR, "at heap end");
    CHECK(hg_classify(POOL, 0x8d513e90u, 16) == HG_BAD_PTR, "wild pointer");
    CHECK(hg_classify(POOL, 0x3ffd0008u, 0) == HG_BAD_SIZE, "zero size");

    uintptr_t ptr = 0x3ffdf000u;
    CHECK(hg_classify(POOL, ptr, (size_t)(POOL.end - ptr) + 4) == HG_BAD_BOUNDS,
          "one aligned step past heap end");

    // A wild size must be rejected, not wrapped back into range by overflow.
    CHECK(hg_classify(POOL, 0x3ffdfff0u, SIZE_MAX) == HG_BAD_BOUNDS,
          "SIZE_MAX does not wrap into range");

    CHECK(hg_classify(POOL, 0x3ffd0009u, 64) == HG_BAD_ALIGN, "misaligned ptr");
    CHECK(hg_classify(POOL, 0x3ffd0008u, 63) == HG_BAD_ALIGN, "misaligned size");
}

static void test_window(void)
{
    printf("test_window\n");
    uintptr_t from; size_t len;

    hg_window(POOL, 0x3ffd0000u, 192, 64, &from, &len);
    CHECK(from == 0x3ffd0000u - 192 && len == 256, "window centred in heap");

    // Clamped at the start: a block near the bottom of the heap has less
    // preceding data than we asked for.
    hg_window(POOL, POOL.start + 8, 192, 64, &from, &len);
    CHECK(from == POOL.start && len == 8 + 64, "window clamped at heap start");

    // Clamped at the end.
    hg_window(POOL, POOL.end - 16, 192, 64, &from, &len);
    CHECK(from == POOL.end - 16 - 192 && len == 192 + 16,
          "window clamped at heap end");

    hg_window(POOL, POOL.start, 192, 64, &from, &len);
    CHECK(from == POOL.start && len == 64, "window at exact heap start");

    // Nothing safe to copy when the anchor itself is wild — the caller then
    // falls back to the last good block.
    hg_window(POOL, 0xb26c6190u, 192, 64, &from, &len);
    CHECK(len == 0, "wild centre yields an empty window");
    hg_window(POOL, POOL.end, 192, 64, &from, &len);
    CHECK(len == 0, "centre at heap end yields an empty window");
}

static void test_verdict_strings(void)
{
    printf("test_verdict_strings\n");
    // Every verdict needs a distinct label: it is copied into the core dump's
    // summary line, which is how the crash is triaged.
    const hg_verdict_t all[] = { HG_OK, HG_BAD_PTR, HG_BAD_SIZE,
                                 HG_BAD_BOUNDS, HG_BAD_ALIGN };
    int ok = 1;
    for (size_t i = 0; i < sizeof(all) / sizeof(all[0]); i++) {
        if (!hg_verdict_str(all[i]) || !hg_verdict_str(all[i])[0]) ok = 0;
        for (size_t j = i + 1; j < sizeof(all) / sizeof(all[0]); j++)
            if (strcmp(hg_verdict_str(all[i]), hg_verdict_str(all[j])) == 0) ok = 0;
    }
    CHECK(ok, "all verdicts have distinct non-empty labels");
}

int main(void)
{
    test_healthy();
    test_regression_1_5_1();
    test_rejections();
    test_window();
    test_verdict_strings();

    printf("\n%d checks, %d failed\n", tests_run, tests_failed);
    return tests_failed ? 1 : 0;
}
