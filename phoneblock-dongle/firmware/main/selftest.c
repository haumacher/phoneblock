#include "selftest.h"

#include <string.h>

#include "esp_log.h"

#include "api.h"
#include "config.h"
#include "logreport.h"

static const char *TAG = "selftest";

void selftest_run(void)
{
    // Skip until the device is provisioned; an unconfigured dongle has
    // no token to validate.
    if (strlen(config_phoneblock_token()) == 0) return;
    ESP_LOGI(TAG, "scheduled token self-test");
    phoneblock_selftest(NULL);
    // Piggyback the daily wakeup: ship any new WARN/ERROR log lines so a
    // running-but-misbehaving dongle (e.g. one that lost SIP registration
    // without crashing) becomes visible server-side instead of only on
    // the local web UI. No-op when there's nothing new or the user opted
    // out.
    logreport_flush();
}
