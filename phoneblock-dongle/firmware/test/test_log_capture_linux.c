// Host test for main/log_capture_linux.c: pb_log_* lines reach the WARN/ERROR
// ring directly, with the same level, INFO opt-in and suppression rules as
// the ESP32 hook.

#include <stdio.h>
#include <string.h>
#include <unistd.h>

#include "config.h"
#include "linux_test_util.h"
#include "log_capture.h"
#include "platform.h"
#include "stats.h"

static int snapshot(stats_error_t *out)
{
    return stats_snapshot_errors(out, STATS_MAX_ERRORS);
}

int main(void)
{
    char path[256];
    use_temp_config(path, sizeof(path));

    stats_setup();
    config_load();

    // Before the sink is installed, logging only prints.
    pb_log_err("test", "before start");
    stats_error_t errs[STATS_MAX_ERRORS];
    REQUIRE(snapshot(errs) == 0);

    log_capture_start();

    // WARN and ERROR land in the ring, formatted, newest first.
    pb_log_warn("sip", "registration failed: %d", 403);
    pb_log_err("api", "lookup failed for %s", "+4930123");
    int n = snapshot(errs);
    REQUIRE(n == 2);
    REQUIRE(errs[0].level == STATS_LOG_LEVEL_ERROR);
    REQUIRE(strcmp(errs[0].tag, "api") == 0);
    REQUIRE(strcmp(errs[0].message, "lookup failed for +4930123") == 0);
    REQUIRE(errs[1].level == STATS_LOG_LEVEL_WARN);
    REQUIRE(strcmp(errs[1].tag, "sip") == 0);
    REQUIRE(strcmp(errs[1].message, "registration failed: 403") == 0);

    // INFO stays out by default...
    pb_log_info("sip", "registered");
    REQUIRE(snapshot(errs) == 2);

    // ...and is kept while the user enabled it for troubleshooting.
    config_update_t update = { .log_info = "1" };
    REQUIRE(config_update(&update) == PB_KV_OK);
    stats_clear_errors();   // config_update() logs its own INFO line
    pb_log_info("sip", "registered");
    REQUIRE(snapshot(errs) == 1);
    REQUIRE(errs[0].level == STATS_LOG_LEVEL_INFO);
    REQUIRE(strcmp(errs[0].message, "registered") == 0);

    // The suppression list applies like on the ESP32.
    stats_clear_errors();
    pb_log_warn("httpd_txrx", "httpd_sock_err: error in recv : 104");
    REQUIRE(snapshot(errs) == 0);

    // A NULL tag is recorded under the console's fallback tag.
    pb_log_err(NULL, "untagged");
    REQUIRE(snapshot(errs) == 1);
    REQUIRE(strcmp(errs[0].tag, "phoneblock") == 0);

    unlink(path);
    puts("test_log_capture_linux: all tests passed");
    return 0;
}
