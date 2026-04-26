// Host-side tests for api_scan.c — the streaming /api/check-prefix
// JSON scanner. Covers happy-path matches, JSON quirks (whitespace,
// escapes, special characters in strings), arbitrary chunk
// boundaries, and explicit error conditions (per-entry overflow,
// per-entry malformed JSON).
//
// The scanner is the dongle's only line of defence against silently
// misclassifying SPAM when the response gets unusual — every branch
// here exists because it could fail-open in production.

#include <assert.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "../main/api_scan.h"

static int total_checks = 0;

#define CHECK(cond) do {                                            \
    total_checks++;                                                 \
    if (!(cond)) {                                                  \
        fprintf(stderr, "  FAIL: %s:%d: %s\n",                      \
                __FILE__, __LINE__, #cond);                         \
        assert(cond);                                               \
    }                                                               \
} while (0)

// Feed the entire body at once.
static void feed_all(api_scan_t *s, const char *body)
{
    api_scan_feed(s, body, (int)strlen(body));
}

// Feed the body in fixed-size chunks. chunk_size==0 means byte-by-byte.
static void feed_chunks(api_scan_t *s, const char *body, int chunk_size)
{
    int len = (int)strlen(body);
    int step = chunk_size > 0 ? chunk_size : 1;
    for (int i = 0; i < len; i += step) {
        int n = (i + step <= len) ? step : (len - i);
        api_scan_feed(s, body + i, n);
    }
}

// ------------------------- A. Top-level structure -------------------------

static void test_empty_arrays(void)
{
    api_scan_t s;
    api_scan_init(&s, "+49301234567");
    feed_all(&s, "{\"numbers\":[],\"range10\":[],\"range100\":[]}");

    CHECK(!s.error);
    CHECK(s.direct_votes == 0);
    CHECK(s.v10 == 0 && s.c10 == 0);
    CHECK(s.v100 == 0 && s.c100 == 0);
    printf("  empty arrays: ok\n");
}

static void test_only_numbers_key_present(void)
{
    // Server may legally omit empty arrays. Scanner must tolerate.
    api_scan_t s;
    api_scan_init(&s, "+49301234567");
    feed_all(&s, "{\"numbers\":[]}");

    CHECK(!s.error);
    CHECK(s.direct_votes == 0);
    printf("  only numbers key present: ok\n");
}

static void test_unknown_top_level_key_ignored(void)
{
    // Forward compat: a future server build may add new top-level
    // fields (string, number, object, array). None of them must
    // confuse the scanner.
    api_scan_t s;
    api_scan_init(&s, "+49301234567");
    feed_all(&s,
        "{\"meta\":\"v2\",\"count\":7,\"flags\":{\"a\":true},"
        "\"future\":[1,2,3],"
        "\"numbers\":[{\"phone\":\"+49301234567\",\"votes\":42}],"
        "\"range10\":[],\"range100\":[]}");

    CHECK(!s.error);
    CHECK(s.direct_votes == 42);
    printf("  unknown top-level key ignored: ok\n");
}

// ----------------------------- B. Direct match -----------------------------

static void test_direct_match_single(void)
{
    api_scan_t s;
    api_scan_init(&s, "+49301234567");
    feed_all(&s,
        "{\"numbers\":[{\"phone\":\"+49301234567\",\"votes\":42}],"
        "\"range10\":[],\"range100\":[]}");
    CHECK(!s.error);
    CHECK(s.direct_votes == 42);
    printf("  direct match single: ok\n");
}

static void test_direct_no_match(void)
{
    api_scan_t s;
    api_scan_init(&s, "+49301234567");
    feed_all(&s,
        "{\"numbers\":[{\"phone\":\"+49301111111\",\"votes\":99}],"
        "\"range10\":[],\"range100\":[]}");
    CHECK(!s.error);
    CHECK(s.direct_votes == 0);
    printf("  direct no match: ok\n");
}

static void test_direct_match_among_many(void)
{
    api_scan_t s;
    api_scan_init(&s, "+49301234567");
    feed_all(&s,
        "{\"numbers\":["
            "{\"phone\":\"+49301111111\",\"votes\":1},"
            "{\"phone\":\"+49302222222\",\"votes\":2},"
            "{\"phone\":\"+49301234567\",\"votes\":17},"
            "{\"phone\":\"+49303333333\",\"votes\":3}"
        "],\"range10\":[],\"range100\":[]}");
    CHECK(!s.error);
    CHECK(s.direct_votes == 17);
    printf("  direct match among many: ok\n");
}

static void test_direct_match_field_order_swapped(void)
{
    api_scan_t s;
    api_scan_init(&s, "+49301234567");
    feed_all(&s,
        "{\"numbers\":[{\"votes\":42,\"phone\":\"+49301234567\"}],"
        "\"range10\":[],\"range100\":[]}");
    CHECK(!s.error);
    CHECK(s.direct_votes == 42);
    printf("  field order swapped: ok\n");
}

static void test_direct_match_with_unknown_fields(void)
{
    // Forward-compat for new PhoneInfo fields like rating/label/etc.
    api_scan_t s;
    api_scan_init(&s, "+49301234567");
    feed_all(&s,
        "{\"numbers\":[{"
            "\"phone\":\"+49301234567\","
            "\"votes\":42,"
            "\"votesWildcard\":120,"
            "\"rating\":\"C_POLL\","
            "\"whiteListed\":false,"
            "\"blackListed\":false,"
            "\"archived\":false,"
            "\"dateAdded\":1700000000000,"
            "\"lastUpdate\":1710000000000,"
            "\"label\":\"(DE) 030 12345678\","
            "\"location\":\"Berlin\","
            "\"newField\":42"
        "}],\"range10\":[],\"range100\":[]}");
    CHECK(!s.error);
    CHECK(s.direct_votes == 42);
    printf("  direct match with unknown fields: ok\n");
}

// ----------------------------- C. Range matches ----------------------------

static void test_range10_match(void)
{
    // phone = "+49301234567" → range10 prefix is the first 11 chars.
    api_scan_t s;
    api_scan_init(&s, "+49301234567");
    feed_all(&s,
        "{\"numbers\":[],"
        "\"range10\":[{\"prefix\":\"+4930123456\",\"votes\":24,\"cnt\":5}],"
        "\"range100\":[]}");
    CHECK(!s.error);
    CHECK(s.v10 == 24);
    CHECK(s.c10 == 5);
    CHECK(s.v100 == 0);
    printf("  range10 match: ok\n");
}

static void test_range100_match(void)
{
    api_scan_t s;
    api_scan_init(&s, "+49301234567");
    feed_all(&s,
        "{\"numbers\":[],\"range10\":[],"
        "\"range100\":[{\"prefix\":\"+493012345\",\"votes\":78,\"cnt\":12}]}");
    CHECK(!s.error);
    CHECK(s.v100 == 78);
    CHECK(s.c100 == 12);
    printf("  range100 match: ok\n");
}

static void test_both_ranges_match(void)
{
    api_scan_t s;
    api_scan_init(&s, "+49301234567");
    feed_all(&s,
        "{\"numbers\":[],"
        "\"range10\":[{\"prefix\":\"+4930123456\",\"votes\":24,\"cnt\":5}],"
        "\"range100\":[{\"prefix\":\"+493012345\",\"votes\":78,\"cnt\":12}]}");
    CHECK(!s.error);
    CHECK(s.v10 == 24 && s.c10 == 5);
    CHECK(s.v100 == 78 && s.c100 == 12);
    printf("  both ranges match: ok\n");
}

static void test_range10_wrong_length_ignored(void)
{
    // Server-side hash buckets can return aggregations with shorter
    // or longer prefixes that happen to share the hash bucket.
    // Ours-or-not is decided by length AND startsWith.
    api_scan_t s;
    api_scan_init(&s, "+49301234567");
    feed_all(&s,
        "{\"numbers\":[],"
        "\"range10\":["
            "{\"prefix\":\"+493012345\",\"votes\":111,\"cnt\":7}"   // too short
        "],\"range100\":[]}");
    CHECK(!s.error);
    CHECK(s.v10 == 0 && s.c10 == 0);
    printf("  range10 wrong length ignored: ok\n");
}

static void test_range10_no_prefix_match(void)
{
    api_scan_t s;
    api_scan_init(&s, "+49301234567");
    feed_all(&s,
        "{\"numbers\":[],"
        "\"range10\":[{\"prefix\":\"+4940999888\",\"votes\":111,\"cnt\":7}],"
        "\"range100\":[]}");
    CHECK(!s.error);
    CHECK(s.v10 == 0 && s.c10 == 0);
    printf("  range10 no prefix match: ok\n");
}

static void test_range10_picks_correct_match_in_array(void)
{
    api_scan_t s;
    api_scan_init(&s, "+49301234567");
    feed_all(&s,
        "{\"numbers\":[],"
        "\"range10\":["
            "{\"prefix\":\"+4940000000\",\"votes\":1,\"cnt\":1},"
            "{\"prefix\":\"+4930123456\",\"votes\":24,\"cnt\":5},"
            "{\"prefix\":\"+4933333333\",\"votes\":2,\"cnt\":1}"
        "],\"range100\":[]}");
    CHECK(!s.error);
    CHECK(s.v10 == 24 && s.c10 == 5);
    printf("  range10 picks correct match: ok\n");
}

// ------------------------------ D. JSON quirks -----------------------------

static void test_pretty_printed(void)
{
    api_scan_t s;
    api_scan_init(&s, "+49301234567");
    feed_all(&s,
        "{\n"
        "  \"numbers\": [\n"
        "    {\n"
        "      \"phone\": \"+49301234567\",\n"
        "      \"votes\": 42\n"
        "    }\n"
        "  ],\n"
        "  \"range10\": [],\n"
        "  \"range100\": []\n"
        "}\n");
    CHECK(!s.error);
    CHECK(s.direct_votes == 42);
    printf("  pretty-printed: ok\n");
}

static void test_utf8_in_label(void)
{
    api_scan_t s;
    api_scan_init(&s, "+49891234567");
    feed_all(&s,
        "{\"numbers\":[{"
            "\"phone\":\"+49891234567\","
            "\"votes\":7,"
            "\"label\":\"(DE) München Innenstadt\","
            "\"location\":\"München\""
        "}],\"range10\":[],\"range100\":[]}");
    CHECK(!s.error);
    CHECK(s.direct_votes == 7);
    printf("  utf-8 in label: ok\n");
}

static void test_escaped_quote_in_label(void)
{
    // The label contains an escaped quote (\"). The scanner must NOT
    // close the string on the embedded quote — otherwise depth
    // tracking goes haywire and the entry boundary is lost.
    api_scan_t s;
    api_scan_init(&s, "+49301234567");
    feed_all(&s,
        "{\"numbers\":[{"
            "\"phone\":\"+49301234567\","
            "\"label\":\"foo \\\"bar\\\" baz\","
            "\"votes\":5"
        "}],\"range10\":[],\"range100\":[]}");
    CHECK(!s.error);
    CHECK(s.direct_votes == 5);
    printf("  escaped quote in label: ok\n");
}

static void test_escaped_backslash(void)
{
    api_scan_t s;
    api_scan_init(&s, "+49301234567");
    feed_all(&s,
        "{\"numbers\":[{"
            "\"phone\":\"+49301234567\","
            "\"label\":\"a\\\\b\","
            "\"votes\":3"
        "}],\"range10\":[],\"range100\":[]}");
    CHECK(!s.error);
    CHECK(s.direct_votes == 3);
    printf("  escaped backslash: ok\n");
}

static void test_special_chars_inside_string(void)
{
    // Curly braces and brackets inside string values must not change
    // the scanner's depth state.
    api_scan_t s;
    api_scan_init(&s, "+49301234567");
    feed_all(&s,
        "{\"numbers\":[{"
            "\"phone\":\"+49301234567\","
            "\"label\":\"weird {nested} [stuff]: ,here\","
            "\"votes\":11"
        "}],\"range10\":[],\"range100\":[]}");
    CHECK(!s.error);
    CHECK(s.direct_votes == 11);
    printf("  special chars inside string: ok\n");
}

static void test_label_with_pseudo_phone_field(void)
{
    // Adversarial: a label that itself contains the substring
    // "phone":"+49…" must NOT cause a false match — only the real
    // top-level field of the entry counts.
    api_scan_t s;
    api_scan_init(&s, "+49999999999");  // does not match
    feed_all(&s,
        "{\"numbers\":[{"
            "\"phone\":\"+49301234567\","
            "\"label\":\"\\\"phone\\\":\\\"+49999999999\\\"\","
            "\"votes\":99"
        "}],\"range10\":[],\"range100\":[]}");
    CHECK(!s.error);
    CHECK(s.direct_votes == 0);  // pseudo-field in label must not match
    printf("  label with pseudo phone field: ok\n");
}

// ---------------------------- E. Streaming chunks --------------------------

static const char *CHUNK_BODY =
    "{\n"
    "  \"numbers\": [\n"
    "    {\"phone\":\"+49301111111\",\"votes\":1,\"label\":\"a\"},\n"
    "    {\"phone\":\"+49301234567\",\"votes\":42,\"label\":\"b{c\\\"d\"},\n"
    "    {\"phone\":\"+49303333333\",\"votes\":3}\n"
    "  ],\n"
    "  \"range10\": [{\"prefix\":\"+4930123456\",\"votes\":24,\"cnt\":5}],\n"
    "  \"range100\": [{\"prefix\":\"+493012345\",\"votes\":78,\"cnt\":12}]\n"
    "}\n";

static void test_one_shot(void)
{
    api_scan_t s;
    api_scan_init(&s, "+49301234567");
    feed_all(&s, CHUNK_BODY);
    CHECK(!s.error);
    CHECK(s.direct_votes == 42);
    CHECK(s.v10 == 24 && s.c10 == 5);
    CHECK(s.v100 == 78 && s.c100 == 12);
    printf("  one-shot full body: ok\n");
}

static void test_byte_by_byte(void)
{
    api_scan_t s;
    api_scan_init(&s, "+49301234567");
    feed_chunks(&s, CHUNK_BODY, 1);
    CHECK(!s.error);
    CHECK(s.direct_votes == 42);
    CHECK(s.v10 == 24 && s.c10 == 5);
    CHECK(s.v100 == 78 && s.c100 == 12);
    printf("  byte-by-byte: ok\n");
}

static void test_various_chunk_sizes(void)
{
    int sizes[] = { 1, 2, 3, 5, 7, 13, 17, 31, 64, 128, 256 };
    for (size_t i = 0; i < sizeof(sizes) / sizeof(sizes[0]); i++) {
        api_scan_t s;
        api_scan_init(&s, "+49301234567");
        feed_chunks(&s, CHUNK_BODY, sizes[i]);
        CHECK(!s.error);
        CHECK(s.direct_votes == 42);
        CHECK(s.v10 == 24 && s.c10 == 5);
        CHECK(s.v100 == 78 && s.c100 == 12);
    }
    printf("  various chunk sizes: ok\n");
}

static void test_split_inside_escape(void)
{
    // Pathological split: chunk boundary lands between the backslash
    // and the escaped char. escape_next state must persist across
    // chunk boundaries.
    api_scan_t s;
    api_scan_init(&s, "+49301234567");
    const char *part1 =
        "{\"numbers\":[{\"phone\":\"+49301234567\","
        "\"label\":\"x\\";  // ends right after the backslash
    const char *part2 =
        "\"y\",\"votes\":42}],\"range10\":[],\"range100\":[]}";
    api_scan_feed(&s, part1, (int)strlen(part1));
    api_scan_feed(&s, part2, (int)strlen(part2));
    CHECK(!s.error);
    CHECK(s.direct_votes == 42);
    printf("  split inside escape: ok\n");
}

static void test_split_inside_string_with_brace(void)
{
    // Chunk boundary inside a string that contains '{'. brace_depth
    // must NOT be incremented by the embedded char.
    api_scan_t s;
    api_scan_init(&s, "+49301234567");
    const char *part1 =
        "{\"numbers\":[{\"phone\":\"+49301234567\","
        "\"label\":\"foo{ba";
    const char *part2 =
        "r}baz\",\"votes\":42}],\"range10\":[],\"range100\":[]}";
    api_scan_feed(&s, part1, (int)strlen(part1));
    api_scan_feed(&s, part2, (int)strlen(part2));
    CHECK(!s.error);
    CHECK(s.direct_votes == 42);
    printf("  split inside string with brace: ok\n");
}

// ------------------------------ F. Error paths -----------------------------

static void test_per_entry_overflow(void)
{
    // Build an entry whose JSON is much larger than ENTRY_BUF_SIZE.
    // The scanner must set error and not call the entry as a match.
    api_scan_t s;
    api_scan_init(&s, "+49301234567");

    // Allocate enough for a label that comfortably exceeds the buffer.
    int label_size = API_SCAN_ENTRY_BUF_SIZE * 2;
    char *body = malloc(label_size + 256);
    assert(body);
    int n = sprintf(body,
        "{\"numbers\":[{"
            "\"phone\":\"+49301234567\","
            "\"votes\":42,"
            "\"label\":\"");
    for (int i = 0; i < label_size; i++) body[n++] = 'X';
    n += sprintf(body + n,
            "\"}],\"range10\":[],\"range100\":[]}");
    body[n] = '\0';

    api_scan_feed(&s, body, n);
    free(body);

    CHECK(s.error);
    CHECK(s.error_reason != NULL);
    // Direct votes must NOT have been recorded — the entry was
    // truncated, so we cannot trust any field we may have parsed.
    CHECK(s.direct_votes == 0);
    printf("  per-entry overflow: ok\n");
}

static void test_per_entry_malformed(void)
{
    // The entry is syntactically broken JSON (missing quote on the
    // value). cJSON_Parse fails on the per-entry buffer; the scanner
    // must surface that as error, not silently treat it as no-match.
    api_scan_t s;
    api_scan_init(&s, "+49301234567");
    feed_all(&s,
        "{\"numbers\":[{"
            "\"phone\":+49301234567,"        // value not quoted
            "\"votes\":42"
        "}],\"range10\":[],\"range100\":[]}");
    CHECK(s.error);
    CHECK(s.error_reason != NULL);
    printf("  per-entry malformed: ok\n");
}

// ------------------------------- G. Edge cases -----------------------------

static void test_short_phone_no_range_lookup(void)
{
    // Phone of length 1 → expected_len would be 0 (range10) or -1
    // (range100). Both lookups must be guarded out: even a prefix
    // that would technically startsWith() must not produce a match.
    api_scan_t s;
    api_scan_init(&s, "+");

    feed_all(&s,
        "{\"numbers\":[],"
        "\"range10\":[{\"prefix\":\"\",\"votes\":1,\"cnt\":99}],"
        "\"range100\":[{\"prefix\":\"\",\"votes\":1,\"cnt\":99}]}");

    CHECK(!s.error);
    CHECK(s.v10 == 0);
    CHECK(s.v100 == 0);
    printf("  short phone no range lookup: ok\n");
}

static void test_phone_field_not_string(void)
{
    // Server bug or future schema: phone is not a string. Must not
    // match (and must not crash).
    api_scan_t s;
    api_scan_init(&s, "+49301234567");
    feed_all(&s,
        "{\"numbers\":[{\"phone\":12345,\"votes\":42}],"
        "\"range10\":[],\"range100\":[]}");
    CHECK(!s.error);
    CHECK(s.direct_votes == 0);
    printf("  phone field not string: ok\n");
}

static void test_range_prefix_not_string(void)
{
    api_scan_t s;
    api_scan_init(&s, "+49301234567");
    feed_all(&s,
        "{\"numbers\":[],"
        "\"range10\":[{\"prefix\":12345,\"votes\":1,\"cnt\":1}],"
        "\"range100\":[]}");
    CHECK(!s.error);
    CHECK(s.v10 == 0);
    printf("  range prefix not string: ok\n");
}

static void test_numbers_entry_missing_votes(void)
{
    // Schema oversight: phone matches but votes field is absent.
    // direct_votes stays at 0 (legitimate verdict), no error.
    api_scan_t s;
    api_scan_init(&s, "+49301234567");
    feed_all(&s,
        "{\"numbers\":[{\"phone\":\"+49301234567\"}],"
        "\"range10\":[],\"range100\":[]}");
    CHECK(!s.error);
    CHECK(s.direct_votes == 0);
    printf("  numbers entry missing votes: ok\n");
}

// ---------------------------------- main ----------------------------------

int main(void)
{
    test_empty_arrays();
    test_only_numbers_key_present();
    test_unknown_top_level_key_ignored();

    test_direct_match_single();
    test_direct_no_match();
    test_direct_match_among_many();
    test_direct_match_field_order_swapped();
    test_direct_match_with_unknown_fields();

    test_range10_match();
    test_range100_match();
    test_both_ranges_match();
    test_range10_wrong_length_ignored();
    test_range10_no_prefix_match();
    test_range10_picks_correct_match_in_array();

    test_pretty_printed();
    test_utf8_in_label();
    test_escaped_quote_in_label();
    test_escaped_backslash();
    test_special_chars_inside_string();
    test_label_with_pseudo_phone_field();

    test_one_shot();
    test_byte_by_byte();
    test_various_chunk_sizes();
    test_split_inside_escape();
    test_split_inside_string_with_brace();

    test_per_entry_overflow();
    test_per_entry_malformed();

    test_short_phone_no_range_lookup();
    test_phone_field_not_string();
    test_range_prefix_not_string();
    test_numbers_entry_missing_votes();

    printf("%d checks, 0 failures\n", total_checks);
    return 0;
}
