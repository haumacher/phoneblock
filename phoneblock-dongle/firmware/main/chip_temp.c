#include "chip_temp.h"

#include "soc/soc_caps.h"

#if SOC_TEMP_SENSOR_SUPPORTED

#include "driver/temperature_sensor.h"
#include "esp_log.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

static const char *TAG = "chip_temp";

static temperature_sensor_handle_t s_tsens;

void chip_temp_init(void)
{
    // 20..100 °C: the measurement range is selected per install and the
    // error grows outside it. A die in a closed case near a router sits in
    // the 30s to 60s; the board this is for (C3 Super Mini) runs hot.
    temperature_sensor_config_t cfg = TEMPERATURE_SENSOR_CONFIG_DEFAULT(20, 100);
    esp_err_t err = temperature_sensor_install(&cfg, &s_tsens);
    if (err == ESP_OK) err = temperature_sensor_enable(s_tsens);
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "temperature sensor unavailable: %s", esp_err_to_name(err));
        s_tsens = NULL;
    }
}

bool chip_temp_read(float *celsius)
{
    return s_tsens && temperature_sensor_get_celsius(s_tsens, celsius) == ESP_OK;
}

#else

void chip_temp_init(void) {}

bool chip_temp_read(float *celsius)
{
    (void)celsius;
    return false;
}

#endif
