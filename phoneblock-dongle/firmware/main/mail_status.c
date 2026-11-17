#include "mail_status.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#include "config.h"
#include "mail_calls.h"
#include "mail_html.h"
#include "mail_i18n.h"
#include "platform.h"
#include "stats.h"
#include "time_sync.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

// Room the call table leaves for the log section when the mail has one.
#define MAIL_LOG_RESERVE    5120

// Call rows read from the call list at a time, on the heap.
#define MAIL_CALLS_PAGE     4

// --- body assembly (shared by the daily flush and the test mail) -----
//
// The body is a single text/html document. The pure string builders
// (append_str / append_html_escaped / append_url_encoded) live in
// mail_html.c so the injection-guarding escaping is host-tested; the
// helpers below add the device-specific formatting on top. All are bounded
// by `cap` and never write past it, so a full body truncates cleanly.

// Render a call/log instant as local wall-clock once SNTP is valid, else the
// "+Ns since boot" fallback. at_us is esp_timer uptime; see append_log_html.
static void format_event_time(int64_t at_us, char *out, size_t cap)
{
    if (time_sync_valid()) {
        int64_t now_us    = (int64_t)pb_monotonic_us();
        time_t  now_epoch = (time_t)time_sync_now_epoch();
        time_t  ev = now_epoch - (time_t)((now_us - at_us) / 1000000);
        struct tm lt;
        localtime_r(&ev, &lt);
        strftime(out, cap, "%Y-%m-%d %H:%M:%S", &lt);
    } else {
        snprintf(out, cap, "+%llds", (long long)(at_us / 1000000));
    }
}

// A call's time: its stored wall-clock time (local), else — a call of this
// boot before the clock was set — "+Ns since boot", else (an earlier boot
// without a time) "?".
static void format_call_time(const stats_call_t *c, char *out, size_t cap)
{
    if (c->epoch_s) {
        time_t ev = (time_t)c->epoch_s;
        struct tm lt;
        time_sync_tz_lock();
        localtime_r(&ev, &lt);
        time_sync_tz_unlock();
        strftime(out, cap, "%Y-%m-%d %H:%M:%S", &lt);
    } else if (c->this_boot) {
        snprintf(out, cap, "+%llds", (long long)(c->at_us / 1000000));
    } else {
        snprintf(out, cap, "%s", "?");
    }
}

// mail_render() (ICU {name} substitution for the localized strings) lives in
// mail_html.c so it is host-tested (test/test_mail_html.c).

// Verdict label matching the web UI's wording (status.calls.verdict.* in
// index.html). Both surfaces render the same pb_assessment_t; the wording
// lives in mail_i18n (embedded English fallback + downloaded pack) and must
// stay in lock-step with the UI i18n strings.
static void verdict_label(const stats_call_t *c, char *out, size_t cap)
{
    const char *scope = c->wildcard ? mail_i18n_str("verdict.scope.range")
                                    : mail_i18n_str("verdict.scope.number");
    char direct[12], range[12];
    snprintf(direct, sizeof(direct), "%d", c->direct_votes);
    snprintf(range,  sizeof(range),  "%d", c->range_votes);
    switch (c->assessment) {
    case PB_ASSESS_BLACKLIST:
        mail_render(out, cap, mail_i18n_str("verdict.spam_blacklist"),
                    "scope", scope, (const char *)NULL);
        break;
    case PB_ASSESS_SPAM_LIST:
        mail_render(out, cap, mail_i18n_str("verdict.spam_blocklist"),
                    "scope", scope, (const char *)NULL);
        break;
    case PB_ASSESS_NAME_PATTERN:
        mail_render(out, cap, mail_i18n_str("verdict.spam_name_pattern"),
                    (const char *)NULL);
        break;
    case PB_ASSESS_TEST:
        mail_render(out, cap, mail_i18n_str("verdict.test"), (const char *)NULL);
        break;
    case PB_ASSESS_SPAM:
        // A verdict without counts (both 0) — show plain "SPAM".
        if (c->direct_votes == 0 && c->range_votes == 0)
            mail_render(out, cap, mail_i18n_str("verdict.spam"), (const char *)NULL);
        else
            mail_render(out, cap, mail_i18n_str("verdict.spam_votes"),
                        "direct", direct, "range", range, (const char *)NULL);
        break;
    case PB_ASSESS_SUSPECT:
        mail_render(out, cap, mail_i18n_str("verdict.spam_suspect"),
                    "direct", direct, "range", range, (const char *)NULL);
        break;
    case PB_ASSESS_LEGITIMATE:
        mail_render(out, cap, mail_i18n_str("verdict.legitimate"), (const char *)NULL);
        break;
    case PB_ASSESS_ERROR:
        mail_render(out, cap, mail_i18n_str("verdict.error"), (const char *)NULL);
        break;
    case PB_ASSESS_UNKNOWN:
    default:
        mail_render(out, cap, mail_i18n_str("verdict.unknown"), (const char *)NULL);
        break;
    }
}

// Append the summary block (device id, uptime, call counters) as HTML.
static size_t append_summary_html(char *body, size_t cap, size_t len,
                                  const stats_counters_t *cnt)
{
    int64_t up_s = (int64_t)pb_monotonic_us() / 1000000;
    char hours[16], minutes[16], total[16], blocked[16], passed[16];
    snprintf(hours,   sizeof(hours),   "%lld", (long long)(up_s / 3600));
    snprintf(minutes, sizeof(minutes), "%lld", (long long)((up_s % 3600) / 60));
    snprintf(total,   sizeof(total),   "%u", (unsigned)cnt->total_calls);
    snprintf(blocked, sizeof(blocked), "%u", (unsigned)cnt->spam_blocked);
    snprintf(passed,  sizeof(passed),  "%u", (unsigned)cnt->legitimate);
    char uptime[64], counts[192];
    mail_render(uptime, sizeof(uptime), mail_i18n_str("sum.uptime"),
                "hours", hours, "minutes", minutes, (const char *)NULL);
    mail_render(counts, sizeof(counts), mail_i18n_str("sum.calls"),
                "total", total, "blocked", blocked, "passed", passed,
                (const char *)NULL);
    char line[400];
    snprintf(line, sizeof(line),
        "<p style=\"font-size:14px;line-height:1.5\">"
        "%s <b>%s</b><br>%s<br>%s</p>",
        mail_i18n_str("sum.device"), config_device_id(), uptime, counts);
    len = append_str(body, cap, len, line);
    return append_str(body, cap, len, "\n");
}

// Append the table of the calls in `w` (mail_calls_read: newest first,
// filtered), read from the call list a few at a time — the list can hold
// STATS_CALL_CAPACITY calls, no copy of it is ever made. Rows stop at
// `limit` bytes of body; *listed receives how many made it. Dialable numbers
// link to their PhoneBlock detail page (<base>/nums/<number>); the server
// normalises the number and redirects to its canonical form, exactly as the
// web UI's links do. Renders nothing when there are no calls.
static size_t append_calls_html(char *body, size_t cap, size_t len,
                                const mail_calls_window_t *w, size_t limit,
                                int *listed)
{
    *listed = 0;
    if (w->new_calls <= 0) return len;
    stats_call_t *page = malloc(MAIL_CALLS_PAGE * sizeof(*page));
    if (!page) return len;
    const char *base = config_phoneblock_base_url();

    char heading[320];
    snprintf(heading, sizeof(heading),
        "<h3 style=\"font-size:15px;margin:1.2em 0 .3em\">%s</h3>\n"
        "<table cellpadding=\"5\" cellspacing=\"0\" "
        "style=\"border-collapse:collapse;font-size:14px\">\n"
        "<tr style=\"text-align:left;color:#555;border-bottom:1px solid #ccc\">\n",
        mail_i18n_str("calls.heading"));
    len = append_str(body, cap, len, heading);
    char hrow[192];
    snprintf(hrow, sizeof(hrow),
        "<th>%s</th><th>%s</th><th>%s</th><th>%s</th></tr>\n",
        mail_i18n_str("calls.time"), mail_i18n_str("calls.number"),
        mail_i18n_str("calls.name"), mail_i18n_str("calls.rating"));
    len = append_str(body, cap, len, hrow);

    uint32_t cursor = 0;
    int n = 0, i = 0;
    while (len < limit) {
        if (i == n) {
            n = mail_calls_read(w, &cursor, page, MAIL_CALLS_PAGE);
            i = 0;
            if (n == 0) break;
        }
        const stats_call_t *c = &page[i++];
        (*listed)++;
        char when[24]; format_call_time(c, when, sizeof(when));
        char vl[64];   verdict_label(c, vl, sizeof(vl));
        // API-checked entries carry a PhoneBlock label/location; phone-book
        // and internal-code entries fall back to the raw number/display.
        bool checked     = c->label[0] != '\0';
        const char *num  = checked ? c->label    : c->number;
        const char *name = checked ? c->location : c->display;
        bool dialable = c->number[0] == '+' ||
                        (c->number[0] >= '0' && c->number[0] <= '9');

        len = mail_html_append_call_row(body, cap, len, when, base,
                                        c->number, dialable, num, name, vl);
    }
    free(page);
    len = append_str(body, cap, len, "</table>");
    return append_str(body, cap, len, "\n");
}

// Append the "Neue Meldungen im Protokoll" section as an HTML <pre> block:
// every WARN/ERROR in `errs` (newest first) with at_us > since_us. The header
// is emitted only if at least one entry qualifies. Updates *newest_us to the
// newest at_us appended, which the daily flush uses as its high-water mark.
//
// Log entries store esp_timer uptime (at_us), not wall-clock; format_event_time
// recovers the real instant once SNTP is valid (time() and esp_timer share the
// same monotonic source), far more useful in a mail read minutes-to-days later.
static size_t append_log_html(char *body, size_t cap, size_t len,
                              const stats_error_t *errs, int n,
                              int64_t since_us, int64_t *newest_us)
{
    bool header = false;
    for (int i = n - 1; i >= 0 && len < cap - 220; i--) {
        const stats_error_t *e = &errs[i];
        if (e->at_us <= since_us) continue;
        char lvl = (e->level == STATS_LOG_LEVEL_ERROR) ? 'E'
                 : (e->level == STATS_LOG_LEVEL_WARN)  ? 'W' : 0;
        if (!lvl) continue;
        if (!header) {
            char hd[256];
            snprintf(hd, sizeof(hd),
                "<h3 style=\"font-size:15px;margin:1.2em 0 .3em\">%s</h3>\n"
                "<pre style=\"font-size:13px;white-space:pre-wrap;word-break:break-word;margin:0\">",
                mail_i18n_str("log.heading"));
            len = append_str(body, cap, len, hd);
            header = true;
        }
        char when[24];
        format_event_time(e->at_us, when, sizeof(when));
        char prefix[56];
        snprintf(prefix, sizeof(prefix), "%c %s %s: ", lvl, when, e->tag);
        len = append_html_escaped(body, cap, len, prefix);   // tag is ours, but cheap to escape
        len = append_html_escaped(body, cap, len, e->message);
        len = append_str(body, cap, len, "\n");
        if (e->at_us > *newest_us) *newest_us = e->at_us;
    }
    if (header) {
        len = append_str(body, cap, len, "</pre>");
        len = append_str(body, cap, len, "\n");
    }
    return len;
}

// Assemble the full text/html status body. `include_log` gates the log
// section (the daily flush only attaches it once a new ERROR has fired;
// the test mail always shows the whole ring). `calls` (NULL: no call table)
// are the calls to list; with `since_line` the "since the last mail" line
// precedes them, worded for blocked calls when calls->blocked_only
// (config_mail_spam_only) narrowed the list to them. Calls of the window
// that are no longer stored or did not fit get a line of their own.
// Returns the assembled length.
size_t mail_status_build_html(char *body, size_t cap,
                              const stats_counters_t *cnt,
                              const mail_calls_window_t *calls, bool since_line,
                              const stats_error_t *errs, int nerr,
                              int64_t since_us, int64_t *newest_us,
                              bool include_log)
{
    int  new_calls = calls && since_line ? calls->new_calls : 0;
    bool spam_only = calls && calls->blocked_only;
    char intro[224];
    snprintf(intro, sizeof(intro),
        "<html><body style=\"font-family:Arial,Helvetica,sans-serif;color:#222\">\n"
        "<p>%s</p>\n", mail_i18n_str("intro"));
    size_t len = append_str(body, cap, 0, intro);
    len = append_summary_html(body, cap, len, cnt);
    if (new_calls > 0) {
        char sentence[192], line[224];
        if (new_calls == 1) {
            mail_render(sentence, sizeof(sentence),
                        mail_i18n_str(spam_only ? "newspam.one" : "newcalls.one"),
                        (const char *)NULL);
        } else {
            char count[12];
            snprintf(count, sizeof(count), "%d", new_calls);
            mail_render(sentence, sizeof(sentence),
                        mail_i18n_str(spam_only ? "newspam.many" : "newcalls.many"),
                        "count", count, (const char *)NULL);
        }
        snprintf(line, sizeof(line), "<p>%s</p>", sentence);
        len = append_str(body, cap, len, line);
        len = append_str(body, cap, len, "\n");
    }
    if (calls) {
        size_t limit = cap - 400 - (include_log ? MAIL_LOG_RESERVE : 0);
        int listed;
        len = append_calls_html(body, cap, len, calls, limit, &listed);
        int missing = calls->not_listed + (calls->new_calls - listed);
        if (missing > 0) {
            char count[12], sentence[224], line[256];
            snprintf(count, sizeof(count), "%d", missing);
            mail_render(sentence, sizeof(sentence), mail_i18n_str("calls.not_listed"),
                        "count", count, (const char *)NULL);
            snprintf(line, sizeof(line), "<p style=\"font-size:14px\">%s</p>", sentence);
            len = append_str(body, cap, len, line);
            len = append_str(body, cap, len, "\n");
        }
    }
    if (include_log)
        len = append_log_html(body, cap, len, errs, nerr, since_us, newest_us);
    return append_str(body, cap, len, "</body></html>\n");
}
