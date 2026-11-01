// Probe for the persistent call list (#589): compositions the feature tests
// do not combine — late updates to calls that wrapped out of the ring, the
// daily mail across "clear list" and a reboot, sequence numbers after a clear
// and a reboot, and a damaged record in the middle of the file.
#include <assert.h>
#include <stdio.h>

#include "calls_test_util.h"
#include "mail_calls.h"

static void number(char *buf, size_t cap, int i)
{
    snprintf(buf, cap, "+4930123%05d", i);   // synthetic
}

static void record(int i, verdict_t v)
{
    char n[32];
    number(n, sizeof(n), i);
    stats_record_call(n, "", v);
}

// A spam call's end (webhook/duration path) arrives after the call has left
// the ring: ignored; the newest call's duration and a rating of a number that
// is listed several times persist across a reboot.
static void test_late_updates_after_wrap(void)
{
    calls_test_open();
    record(0, VERDICT_SPAM);
    uint32_t first = stats_last_call_seq();
    for (int i = 1; i <= STATS_CALL_CAPACITY + 5; i++) record(i % 7, VERDICT_SPAM);
    uint32_t last = stats_last_call_seq();

    stats_call_t c;
    assert(!stats_get_call(first, &c));
    stats_call_ended(first, 9000000);            // must be a harmless no-op
    stats_call_ended(last, 42000000);

    char n3[32];
    number(n3, sizeof(n3), 3);
    int updated = stats_set_assessment(n3, PB_ASSESS_LEGITIMATE);
    assert(updated > 1);

    assert(calls_test_reboot());
    assert(stats_get_call(last, &c));
    assert(c.duration_s == 42);
    pb_assessment_t a;
    assert(stats_assessment_for_number(n3, &a) && a == PB_ASSESS_LEGITIMATE);

    stats_calls_info_t info;
    stats_calls_info(&info);
    assert(info.count == STATS_CALL_CAPACITY);
    assert(info.newest_seq == last);
    assert(s_log_errors == 0);
    calls_test_cleanup();
    printf("  ok: late updates after wrap-around, persisted over a reboot\n");
}

// Mail mark, then "clear list", reboot, two new calls: the next mail lists
// exactly those two and reports no lost calls; sequence numbers keep rising.
static void test_mail_after_clear_and_reboot(void)
{
    calls_test_open();
    for (int i = 0; i < 5; i++) record(i, VERDICT_SPAM);
    mail_calls_window_t w;
    mail_calls_window(false, false, &w);
    assert(w.new_calls == 5 && w.not_listed == 0);
    mail_calls_mark_sent(&w);
    uint32_t before_clear = stats_last_call_seq();

    stats_clear_calls();
    assert(calls_test_reboot());
    record(100, VERDICT_SPAM);
    record(101, VERDICT_LEGITIMATE);
    assert(stats_last_call_seq() > before_clear);

    mail_calls_window(false, false, &w);
    assert(w.new_calls == 2);
    assert(w.not_listed == 0);
    mail_calls_window(true, false, &w);      // spam only
    assert(w.new_calls == 1);

    // Calls lost to the ring between two mails are counted.
    mail_calls_window(false, false, &w);
    mail_calls_mark_sent(&w);
    for (int i = 0; i < STATS_CALL_CAPACITY + 3; i++) record(200 + i, VERDICT_SPAM);
    assert(calls_test_reboot());
    mail_calls_window(false, false, &w);
    assert(w.new_calls == STATS_CALL_CAPACITY);
    assert(w.not_listed == 3);
    calls_test_cleanup();
    printf("  ok: mail window across clear, reboot and wrap-around\n");
}

// One damaged record in the middle of the file: the list comes back empty
// with a single WARN, keeps working, and survives the next reboot.
static void test_damaged_record(void)
{
    calls_test_open();
    for (int i = 0; i < 20; i++) record(i, VERDICT_SPAM);
    stats_calls_close();

    FILE *f = fopen(s_calls_file, "r+b");
    assert(f);
    assert(fseek(f, 32 + 10 * 240, SEEK_SET) == 0);   // header + 10 records
    unsigned char junk[16];
    memset(junk, 0xA5, sizeof(junk));
    assert(fwrite(junk, 1, sizeof(junk), f) == sizeof(junk));
    fclose(f);

    int warns = s_log_warns;
    assert(stats_calls_open(s_calls_file));
    assert(s_log_warns - warns <= 1);
    record(50, VERDICT_SPAM);
    assert(calls_test_reboot());
    stats_calls_info_t info;
    stats_calls_info(&info);
    assert(info.count >= 1);
    stats_call_t c;
    assert(stats_snapshot_calls(&c, 1) == 1);
    char n50[32];
    number(n50, sizeof(n50), 50);
    assert(strcmp(c.number, n50) == 0);
    printf("  damaged record: %d WARN, %d call(s) listed after recovery\n",
           s_log_warns - warns, info.count);
    calls_test_cleanup();
    printf("  ok: a damaged record does not break the list\n");
}

int main(void)
{
    printf("test_calls_probe\n");
    test_late_updates_after_wrap();
    test_mail_after_clear_and_reboot();
    test_damaged_record();
    printf("test_calls_probe: all tests passed\n");
    return 0;
}
