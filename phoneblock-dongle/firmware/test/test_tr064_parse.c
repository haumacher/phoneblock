// Host-side tests for the TR-064 parser helpers. No ESP-IDF
// dependencies — plain gcc build, see Makefile.

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "tr064_parse.h"

static int failures = 0;

#define CHECK(cond) do {                                                  \
    if (!(cond)) {                                                        \
        fprintf(stderr, "FAIL %s:%d: %s\n", __FILE__, __LINE__, #cond);   \
        failures++;                                                       \
    }                                                                     \
} while (0)

#define CHECK_STR(got, expected) do {                                     \
    if (strcmp((got), (expected)) != 0) {                                 \
        fprintf(stderr, "FAIL %s:%d: got \"%s\", expected \"%s\"\n",      \
                __FILE__, __LINE__, (got), (expected));                   \
        failures++;                                                       \
    }                                                                     \
} while (0)

static void test_find_text_simple(void)
{
    char buf[64];
    int n = tr064_xml_find_text("<Foo>hello</Foo>", "Foo", buf, sizeof(buf));
    CHECK(n == 5);
    CHECK_STR(buf, "hello");
}

static void test_find_text_namespace_prefix(void)
{
    char buf[64];
    int n = tr064_xml_find_text(
        "<s:Body><u:Foo xmlns:u=\"x\">bar</u:Foo></s:Body>",
        "Foo", buf, sizeof(buf));
    CHECK(n == 3);
    CHECK_STR(buf, "bar");
}

static void test_find_text_with_attributes(void)
{
    char buf[64];
    int n = tr064_xml_find_text(
        "<Username last_user=\"1\">fritz9344</Username>",
        "Username", buf, sizeof(buf));
    CHECK(n == 9);
    CHECK_STR(buf, "fritz9344");
}

static void test_find_text_self_closing(void)
{
    char buf[32];
    strcpy(buf, "sentinel");
    int n = tr064_xml_find_text("<Empty/>", "Empty", buf, sizeof(buf));
    CHECK(n == 0);
    CHECK_STR(buf, "");
}

static void test_find_text_missing_tag(void)
{
    char buf[32];
    strcpy(buf, "sentinel");
    int n = tr064_xml_find_text("<Foo>x</Foo>", "Missing", buf, sizeof(buf));
    CHECK(n == -1);
    // Buffer untouched on miss.
    CHECK_STR(buf, "sentinel");
}

static void test_find_text_nested_same_name_not_confused(void)
{
    char buf[64];
    // Our minimal parser doesn't support true nesting, but we do care
    // that the first sibling is returned unmolested.
    int n = tr064_xml_find_text(
        "<List><Username>alice</Username><Username>bob</Username></List>",
        "Username", buf, sizeof(buf));
    CHECK(n == 5);
    CHECK_STR(buf, "alice");
}

static void test_find_text_truncation(void)
{
    char buf[4];   // room for 3 chars + NUL
    int n = tr064_xml_find_text("<Foo>abcdef</Foo>", "Foo", buf, sizeof(buf));
    CHECK(n == 3);
    CHECK_STR(buf, "abc");
}

static void test_unescape_inplace_all(void)
{
    char s[64];
    strcpy(s, "&amp;&lt;&gt;&quot;&apos;x&amp;y");
    tr064_xml_unescape_inplace(s);
    CHECK_STR(s, "&<>\"'x&y");
}

static void test_unescape_inplace_keeps_unknown(void)
{
    char s[64];
    strcpy(s, "a&nbsp;b&#x41;c");
    tr064_xml_unescape_inplace(s);
    CHECK_STR(s, "a&nbsp;b&#x41;c");
}

static void test_unescape_inplace_userlist(void)
{
    char s[256];
    strcpy(s,
        "&lt;List&gt;&lt;Username last_user=&quot;1&quot;&gt;"
        "fritz9344&lt;/Username&gt;&lt;/List&gt;");
    tr064_xml_unescape_inplace(s);
    CHECK_STR(s,
        "<List><Username last_user=\"1\">fritz9344</Username></List>");
}

static void test_escape_roundtrip(void)
{
    char esc[64];
    tr064_xml_escape("a&b<c>d\"e'f", esc, sizeof(esc));
    CHECK_STR(esc, "a&amp;b&lt;c&gt;d&quot;e&apos;f");
    tr064_xml_unescape_inplace(esc);
    CHECK_STR(esc, "a&b<c>d\"e'f");
}

static void test_escape_truncation(void)
{
    // Capacity 6: not enough for any escape; should produce "" or just
    // whatever fits without overflowing.
    char out[6];
    tr064_xml_escape("<&>", out, sizeof(out));
    CHECK(strlen(out) < sizeof(out));
    // Must always NUL-terminate.
    CHECK(out[sizeof(out) - 1] == '\0'
          || out[strlen(out)]  == '\0');
}

static void test_pick_default_user_attribute_form(void)
{
    char list[256];
    strcpy(list,
        "<List><Username last_user=\"1\">fritz9344</Username></List>");
    char out[64];
    CHECK(tr064_pick_default_user(list, out, sizeof(out)));
    CHECK_STR(out, "fritz9344");
}

static void test_pick_default_user_attribute_single_quoted(void)
{
    char list[256];
    strcpy(list,
        "<List><Username last_user='1'>einzel</Username></List>");
    char out[64];
    CHECK(tr064_pick_default_user(list, out, sizeof(out)));
    CHECK_STR(out, "einzel");
}

static void test_pick_default_user_picks_marked(void)
{
    char list[256];
    strcpy(list,
        "<List>"
        "<Username>first</Username>"
        "<Username last_user=\"1\">flagged</Username>"
        "<Username>third</Username>"
        "</List>");
    char out[64];
    CHECK(tr064_pick_default_user(list, out, sizeof(out)));
    CHECK_STR(out, "flagged");
}

static void test_pick_default_user_legacy_wrapper(void)
{
    char list[256];
    strcpy(list,
        "<List>"
        "<User><Username>alice</Username><LastUser>0</LastUser></User>"
        "<User><Username>bob</Username><LastUser>1</LastUser></User>"
        "</List>");
    char out[64];
    CHECK(tr064_pick_default_user(list, out, sizeof(out)));
    CHECK_STR(out, "bob");
}

static void test_pick_default_user_fallback_first(void)
{
    char list[256];
    // No marker at all: the first <Username> is the fallback.
    strcpy(list,
        "<List>"
        "<Username>alpha</Username>"
        "<Username>beta</Username>"
        "</List>");
    char out[64];
    CHECK(tr064_pick_default_user(list, out, sizeof(out)));
    CHECK_STR(out, "alpha");
}

static void test_pick_default_user_empty(void)
{
    char list[64];
    strcpy(list, "<List></List>");
    char out[64];
    CHECK(!tr064_pick_default_user(list, out, sizeof(out)));
}

static void test_pick_default_user_single_entry(void)
{
    // The shape observed on real hardware.
    char list[256];
    strcpy(list,
        "<List><Username last_user=\"1\">fritz9344</Username></List>");
    char out[64];
    CHECK(tr064_pick_default_user(list, out, sizeof(out)));
    CHECK_STR(out, "fritz9344");
}

int main(void)
{
    test_find_text_simple();
    test_find_text_namespace_prefix();
    test_find_text_with_attributes();
    test_find_text_self_closing();
    test_find_text_missing_tag();
    test_find_text_nested_same_name_not_confused();
    test_find_text_truncation();

    test_unescape_inplace_all();
    test_unescape_inplace_keeps_unknown();
    test_unescape_inplace_userlist();

    test_escape_roundtrip();
    test_escape_truncation();

    test_pick_default_user_attribute_form();
    test_pick_default_user_attribute_single_quoted();
    test_pick_default_user_picks_marked();
    test_pick_default_user_legacy_wrapper();
    test_pick_default_user_fallback_first();
    test_pick_default_user_empty();
    test_pick_default_user_single_entry();

    if (failures) {
        fprintf(stderr, "%d test(s) failed\n", failures);
        return 1;
    }
    printf("OK — all tr064_parse tests passed\n");
    return 0;
}
