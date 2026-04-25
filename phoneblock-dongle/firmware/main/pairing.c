#include "pairing.h"

#include <string.h>

#include "esp_log.h"
#include "esp_partition.h"

#include "pairing_parse.h"

static const char *TAG = "pairing";

bool pairing_load(uint8_t out_secret[PAIRING_SECRET_LEN])
{
    const esp_partition_t *part = esp_partition_find_first(
            ESP_PARTITION_TYPE_DATA, 0x40, "pairing");
    if (part == NULL) {
        ESP_LOGI(TAG, "no pairing partition (OTA-only image or older flash layout)");
        return false;
    }

    uint8_t buf[PAIRING_HEADER_LEN];
    esp_err_t err = esp_partition_read(part, 0, buf, sizeof(buf));
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "esp_partition_read: %s", esp_err_to_name(err));
        return false;
    }

    if (!pairing_parse(buf, sizeof(buf), out_secret)) {
        // Erased flash (all 0xFF) is the expected first-line case here
        // for OTA-only dongles, so log at INFO not WARN.
        ESP_LOGI(TAG, "pairing partition empty or invalid — skipping handshake");
        return false;
    }

    ESP_LOGI(TAG, "pairing secret loaded (16 bytes)");
    return true;
}
