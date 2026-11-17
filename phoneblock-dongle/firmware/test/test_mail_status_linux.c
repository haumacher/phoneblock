#define _POSIX_C_SOURCE 200809L

#include <stdio.h>
#include <string.h>
#include <unistd.h>

#include "config.h"
#include "linux_test_util.h"
#include "mail_html.h"
#include "mail_status.h"
#include "platform.h"
#include "smtp_body.h"
#include "stats.h"
#include "time_sync.h"

#define BODY_CAP (16 * 1024)

// Provide the same embedded English pack symbols as target_add_binary_data().
__asm__(".section .rodata\n"
        ".global _binary_mail_en_json_start\n"
        "_binary_mail_en_json_start:\n"
        ".incbin \"../i18n/l10n/mail/mail_en.arb\"\n"
        ".byte 0\n"
        ".global _binary_mail_en_json_end\n"
        "_binary_mail_en_json_end:\n"
        ".previous\n");

static char s_wire[2 * BODY_CAP + 1];
static size_t s_wire_len;
static size_t s_line_len;
static size_t s_max_line;
static char s_last_char;

void sim_log(char level, const char *tag, const char *fmt, ...)
{
    (void)level;
    (void)tag;
    (void)fmt;
}

static int check_wire_line(void *ctx, const char *data, size_t len)
{
    (void)ctx;
    REQUIRE(s_wire_len + len < sizeof(s_wire));
    memcpy(s_wire + s_wire_len, data, len);
    s_wire_len += len;
    for (size_t i = 0; i < len; i++) {
        if (data[i] == '\n') {
            REQUIRE(s_line_len > 0 && s_last_char == '\r');
            size_t line_len = s_line_len - 1;
            REQUIRE(line_len <= 998);
            if (line_len > s_max_line) s_max_line = line_len;
            s_line_len = 0;
        } else {
            s_line_len++;
        }
        s_last_char = data[i];
    }
    return 0;
}

static int count_rows(const char *body)
{
    const char *row = "<tr style=\"border-bottom:1px solid #eee\">";
    int count = 0;
    while ((body = strstr(body, row)) != NULL) {
        count++;
        body++;
    }
    return count;
}

int main(void)
{
    char config_path[256], calls_dir[256], calls_path[300];
    use_temp_config(config_path, sizeof(config_path));
    stats_setup();
    stats_set_clock(time_sync_now_epoch);
    use_temp_calls(calls_dir, sizeof(calls_dir), calls_path, sizeof(calls_path));
    config_load();

    char base[128];
    memset(base, 'x', sizeof(base) - 1);
    base[sizeof(base) - 1] = '\0';
    config_update_t update = { .phoneblock_base_url = base };
    REQUIRE(config_update(&update) == PB_KV_OK);
    REQUIRE(strlen(config_phoneblock_base_url()) == sizeof(base) - 1);

    char number[STATS_NUMBER_LEN];
    number[0] = '0';
    memset(number + 1, '"', sizeof(number) - 2);
    number[sizeof(number) - 1] = '\0';
    char display[STATS_DISPLAY_LEN];
    memset(display, '"', sizeof(display) - 1);
    display[sizeof(display) - 1] = '\0';
    for (int i = 0; i < STATS_CALL_CAPACITY - 1; i++)
        stats_record_call(number, display, VERDICT_SPAM);
    pb_check_result_t checked = { .verdict = VERDICT_SPAM,
                                  .assessment = PB_ASSESS_SPAM };
    memset(checked.label, '"', sizeof(checked.label) - 1);
    memset(checked.location, '"', sizeof(checked.location) - 1);
    stats_record_call_checked(number, "", &checked);

    for (int i = 0; i < STATS_MAX_ERRORS; i++)
        stats_record_error(STATS_LOG_LEVEL_WARN, "smtp_test", "SMTP command failed");

    mail_calls_window_t calls;
    mail_calls_window(false, true, &calls);
    REQUIRE(calls.new_calls == STATS_CALL_CAPACITY);

    stats_counters_t counters;
    stats_snapshot_counters(&counters);
    stats_error_t errors[STATS_MAX_ERRORS];
    int nerr = stats_snapshot_errors(errors, STATS_MAX_ERRORS);
    REQUIRE(nerr == STATS_MAX_ERRORS);

    char body[BODY_CAP];
    int64_t newest_us = 0;
    size_t body_len = mail_status_build_html(body, sizeof(body), &counters,
        &calls, false, errors, nerr, 0, &newest_us, true);
    REQUIRE(body_len < sizeof(body));
    REQUIRE(body[body_len] == '\0');
    REQUIRE(strstr(body, "<table") != NULL);
    REQUIRE(strstr(body, "<pre") != NULL);
    REQUIRE(count_rows(body) > 0);
    char escaped_location[STATS_LOCATION_LEN * 6];
    size_t location_len = append_html_escaped(escaped_location,
        sizeof(escaped_location), 0, checked.location);
    escaped_location[location_len] = '\0';
    REQUIRE(strstr(body, escaped_location) != NULL);
    REQUIRE(strstr(body, "%22") != NULL);

    s_wire_len = 0;
    s_line_len = 0;
    s_max_line = 0;
    REQUIRE(smtp_encode_body(body, check_wire_line, NULL) == 0);
    REQUIRE(s_line_len == 0);
    s_wire[s_wire_len] = '\0';
    REQUIRE(s_max_line == 573);

    const char *row = "<tr style=\"border-bottom:1px solid #eee\">";
    int rows = 0;
    for (char *p = s_wire; (p = strstr(p, row)) != NULL; p++) {
        REQUIRE(p > s_wire && p[-1] == '\n');
        rows++;
    }
    REQUIRE(rows == count_rows(body));
    int row_ends = 0;
    for (char *p = s_wire; (p = strstr(p, "</tr>\r\n")) != NULL; p++)
        row_ends++;
    REQUIRE(row_ends == rows + 1);

    printf("test_mail_status_linux: %d calls in window, %d rows listed, "
           "%d log entries, max SMTP line %zu bytes\n",
           calls.new_calls, rows, nerr, s_max_line);

    stats_calls_close();
    unlink(config_path);
    unlink(calls_path);
    rmdir(calls_dir);
    return 0;
}
