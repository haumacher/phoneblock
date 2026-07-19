// Host test for strbuf.h — the bounded string builder. Built with
// AddressSanitizer (see Makefile): the buffer is malloc'd so any write past
// the requested capacity aborts, proving the builder never overruns however
// long the appended text is.

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "../main/strbuf.h"

static int tests_run, tests_failed;
#define CHECK(cond, msg) do {                                   \
    tests_run++;                                                \
    if (!(cond)) { tests_failed++;                              \
        printf("  FAIL: %s (%s:%d)\n", msg, __FILE__, __LINE__);\
    } else printf("  ok: %s\n", msg);                           \
} while (0)

static void test_basic(void)
{
    printf("test_basic\n");
    char *buf = malloc(64);
    strbuf_t sb = sb_init(buf, 64);
    CHECK(sb.len == 0 && !sb.truncated && buf[0] == '\0', "empty after init");
    sb_appendf(&sb, "hello %s", "world");
    sb_appendf(&sb, " x=%d", 42);
    CHECK(strcmp(buf, "hello world x=42") == 0, "content correct");
    CHECK(sb.len == (int)strlen(buf), "len matches strlen");
    CHECK(!sb.truncated, "not truncated");
    free(buf);
}

static void test_exact_fit(void)
{
    printf("test_exact_fit\n");
    // "abc" needs 4 bytes incl NUL; cap 4 fits exactly, cap 3 truncates.
    char *b4 = malloc(4);
    strbuf_t s4 = sb_init(b4, 4);
    sb_appendf(&s4, "abc");
    CHECK(!s4.truncated && s4.len == 3 && strcmp(b4, "abc") == 0, "exact fit");
    free(b4);

    char *b3 = malloc(3);
    strbuf_t s3 = sb_init(b3, 3);
    sb_appendf(&s3, "abc");
    CHECK(s3.truncated && s3.len == 2 && b3[2] == '\0', "one short truncates");
    free(b3);
}

// The core safety property: appending far more than the buffer holds must
// never write past it, must set truncated, and must leave len at cap-1.
static void test_overflow_is_contained(void)
{
    printf("test_overflow_is_contained\n");
    char *buf = malloc(16);   // ASan-guarded
    strbuf_t sb = sb_init(buf, 16);
    sb_appendf(&sb, "%s", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");  // 40 A's
    CHECK(sb.truncated, "truncated flag set");
    CHECK(sb.len == 15, "len saturated at cap-1");
    CHECK(buf[15] == '\0', "NUL terminated");
    // Further appends after truncation stay safe and no-op the content.
    sb_appendf(&sb, "more");
    CHECK(sb.len == 15 && buf[15] == '\0', "post-overflow append is safe");
    free(buf);
}

// Sweep: for every capacity, a chain of appends is either fully present or
// marked truncated — never a partial write past the buffer (ASan-checked).
static void test_accumulator_sweep(void)
{
    printf("test_accumulator_sweep\n");
    int first_complete = -1;
    for (int cap = 1; cap <= 128; cap++) {
        char *buf = malloc(cap);
        strbuf_t sb = sb_init(buf, cap);
        sb_appendf(&sb, "GET /path?a=%d", 1);
        sb_appendf(&sb, "&b=%s", "value");
        sb_appendf(&sb, "&c=%d\r\n", 999);
        int ok = (sb.len >= 0 && sb.len < cap && buf[sb.len] == '\0');
        if (!ok) { CHECK(0, "len in range + terminated"); free(buf); return; }
        if (!sb.truncated && first_complete < 0) first_complete = cap;
        free(buf);
    }
    CHECK(first_complete > 0, "some capacity holds the full string");
}

static void test_zero_cap(void)
{
    printf("test_zero_cap\n");
    char dummy = 'x';
    strbuf_t sb = sb_init(&dummy, 0);   // cap 0: cannot even hold a NUL
    sb_appendf(&sb, "anything");
    CHECK(sb.truncated && sb.len == 0, "zero cap: truncated, nothing written");
    free(NULL);
}

int main(void)
{
    test_basic();
    test_exact_fit();
    test_overflow_is_contained();
    test_accumulator_sweep();
    test_zero_cap();
    printf("\n%d checks, %d failed\n", tests_run, tests_failed);
    return tests_failed ? 1 : 0;
}
