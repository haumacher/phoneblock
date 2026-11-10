#include "board_s3.h"

#include <stdint.h>

#include "esp_efuse.h"
#include "esp_efuse_table.h"
#include "esp_log.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

static const char *TAG = "board";

bool board_is_t_dongle_s3(void)
{
    static int s_tdongle = -1;   // -1: not decided yet
    if (s_tdongle < 0) {
        // PSRAM_CAP and PSRAM_VENDOR (2 bits each): both zero means no
        // PSRAM in the package. The vendor covers capacities newer than
        // IDF 5.3's two-bit capacity field knows.
        uint8_t cap = 0, vendor = 0;
        esp_efuse_read_field_blob(ESP_EFUSE_PSRAM_CAP, &cap, 2);
        esp_efuse_read_field_blob(ESP_EFUSE_PSRAM_VENDOR, &vendor, 2);
        s_tdongle = (cap == 0 && vendor == 0) ? 1 : 0;
        ESP_LOGI(TAG, "ESP32-S3 %s PSRAM (eFuse cap %u, vendor %u) → %s",
                 s_tdongle ? "without" : "with", (unsigned)cap, (unsigned)vendor,
                 s_tdongle ? "LilyGO T-Dongle-S3 (display, APA102 LED)"
                           : "generic S3 board (no display, WS2812 LED on GPIO 48)");
    }
    return s_tdongle == 1;
}
