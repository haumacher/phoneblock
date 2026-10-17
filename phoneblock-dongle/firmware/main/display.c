#include "display.h"

#include <stdlib.h>
#include <string.h>

#include "driver/gpio.h"
#include "driver/ledc.h"
#include "driver/spi_master.h"
#include "esp_heap_caps.h"
#include "esp_lcd_panel_io.h"
#include "esp_lcd_panel_ops.h"
#include "esp_lcd_panel_vendor.h"
#include "esp_log.h"
#include "esp_timer.h"
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"

#include "config.h"
#include "lcd_logo.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

static const char *TAG = "display";

// T-Dongle-S3 wiring, as in LilyGO's factory firmware.
#define LCD_HOST      SPI2_HOST
#define PIN_MOSI      3
#define PIN_SCLK      5
#define PIN_CS        4
#define PIN_DC        2
#define PIN_RST       1
#define PIN_BACKLIGHT 38    // active-low
#define LCD_PCLK_HZ   (40 * 1000 * 1000)

// Backlight PWM. Dimmed instead of off: the logo stays readable when
// someone looks at the Fritz!Box, at a fraction of the current and heat.
#define BL_TIMER      LEDC_TIMER_1
#define BL_CHANNEL    LEDC_CHANNEL_1
#define BL_FULL       255
#define BL_DIMMED     24
#define BL_DIM_AFTER_US (60LL * 1000 * 1000)

// Offset of the 80x160 panel inside the controller's 132x162 RAM, in the
// swapped (landscape) axes, per orientation (LilyGO: 1/26 for normal).
// Turning the image by 180° mirrors both axes, which may move the visible
// window to the opposite edge of the RAM; adjust the flipped pair if the
// rotated logo appears shifted on the hardware.
#define GAP_NORMAL_X   1
#define GAP_NORMAL_Y   26
#define GAP_FLIPPED_X  1
#define GAP_FLIPPED_Y  26

// Set once display_start() got the panel up; NULL means "no display", and
// display_set_flipped() then does nothing.
static esp_lcd_panel_handle_t s_panel;
static SemaphoreHandle_t s_done;    // given by on_trans_done
static SemaphoreHandle_t s_lock;    // serialises drawing (boot vs. httpd)

// ST7735 power and gamma setup. The panel is driven through ESP-IDF's
// ST7789 driver (reset, sleep-out, pixel format, address window, MADCTL
// are the same commands); these registers are ST7735-specific. Values from
// LilyGO's T-Dongle-S3 init (esp_lcd_st7735.c, MIT).
typedef struct {
    uint8_t cmd;
    uint8_t len;
    uint8_t data[16];
} lcd_cmd_t;

static const lcd_cmd_t k_st7735_init[] = {
    { 0xB1, 3,  { 0x05, 0x3A, 0x3A } },                          // FRMCTR1
    { 0xB2, 3,  { 0x05, 0x3A, 0x3A } },                          // FRMCTR2
    { 0xB3, 6,  { 0x05, 0x3A, 0x3A, 0x05, 0x3A, 0x3A } },        // FRMCTR3
    { 0xB4, 1,  { 0x03 } },                                      // INVCTR
    { 0xC0, 3,  { 0x62, 0x02, 0x04 } },                          // PWCTR1
    { 0xC1, 1,  { 0xC0 } },                                      // PWCTR2
    { 0xC2, 2,  { 0x0D, 0x00 } },                                // PWCTR3
    { 0xC3, 2,  { 0x8D, 0x6A } },                                // PWCTR4
    { 0xC4, 2,  { 0x8D, 0xEE } },                                // PWCTR5
    { 0xC5, 1,  { 0x0E } },                                      // VMCTR1
    { 0xE0, 16, { 0x10, 0x0E, 0x02, 0x03, 0x0E, 0x07, 0x02, 0x07,
                  0x0A, 0x12, 0x27, 0x37, 0x00, 0x0D, 0x0E, 0x10 } }, // GMCTRP1
    { 0xE1, 16, { 0x10, 0x0E, 0x03, 0x03, 0x0F, 0x06, 0x02, 0x08,
                  0x0A, 0x13, 0x26, 0x36, 0x00, 0x0D, 0x0E, 0x10 } }, // GMCTRN1
};

static void dim_backlight(void *arg)
{
    (void)arg;
    ledc_set_duty(LEDC_LOW_SPEED_MODE, BL_CHANNEL, BL_DIMMED);
    ledc_update_duty(LEDC_LOW_SPEED_MODE, BL_CHANNEL);
}

static bool backlight_init(void)
{
    ledc_timer_config_t timer = {
        .speed_mode      = LEDC_LOW_SPEED_MODE,
        .timer_num       = BL_TIMER,
        .duty_resolution = LEDC_TIMER_8_BIT,
        .freq_hz         = 5000,
        .clk_cfg         = LEDC_AUTO_CLK,
    };
    ledc_channel_config_t channel = {
        .gpio_num   = PIN_BACKLIGHT,
        .speed_mode = LEDC_LOW_SPEED_MODE,
        .channel    = BL_CHANNEL,
        .timer_sel  = BL_TIMER,
        .duty       = 0,                    // dark until the logo is drawn
        .flags.output_invert = 1,           // active-low
    };
    return ledc_timer_config(&timer) == ESP_OK
        && ledc_channel_config(&channel) == ESP_OK;
}

static bool on_trans_done(esp_lcd_panel_io_handle_t io,
                          esp_lcd_panel_io_event_data_t *event, void *ctx)
{
    (void)io;
    (void)event;
    BaseType_t woken = pdFALSE;
    xSemaphoreGiveFromISR((SemaphoreHandle_t)ctx, &woken);
    return woken == pdTRUE;
}

// Expands the run-length encoded logo into a full frame. Returns NULL if
// out of memory or if the runs do not add up to exactly one frame.
static uint16_t *decode_logo(void)
{
    const size_t pixels = (size_t)LCD_LOGO_W * LCD_LOGO_H;
    uint16_t *frame = heap_caps_malloc(pixels * sizeof(uint16_t),
                                       MALLOC_CAP_DMA | MALLOC_CAP_INTERNAL);
    if (!frame) return NULL;
    size_t n = 0;
    for (size_t i = 0; i + 1 < lcd_logo_rle_len; i += 2) {
        size_t count = lcd_logo_rle[i];
        if (count > pixels - n) {
            free(frame);
            return NULL;
        }
        for (size_t k = 0; k < count; k++) frame[n++] = lcd_logo_rle[i + 1];
    }
    if (n != pixels) {
        free(frame);
        return NULL;
    }
    return frame;
}

// Sets the landscape orientation, normal or rotated by 180°.
static esp_err_t set_orientation(bool flipped)
{
    esp_err_t err = flipped
        ? esp_lcd_panel_set_gap(s_panel, GAP_FLIPPED_X, GAP_FLIPPED_Y)
        : esp_lcd_panel_set_gap(s_panel, GAP_NORMAL_X, GAP_NORMAL_Y);
    if (err == ESP_OK) err = esp_lcd_panel_swap_xy(s_panel, true);
    if (err == ESP_OK) err = esp_lcd_panel_mirror(s_panel, flipped, !flipped);
    return err;
}

// Decodes the logo and sends it to the panel. Returns false (after
// logging) if that failed.
static bool draw_logo(void)
{
    uint16_t *frame = decode_logo();
    if (!frame) {
        ESP_LOGW(TAG, "logo: out of memory or corrupt data");
        return false;
    }
    esp_err_t err = esp_lcd_panel_draw_bitmap(s_panel, 0, 0, LCD_LOGO_W, LCD_LOGO_H, frame);
    // The transfer runs from the frame buffer by DMA; free it only once
    // the panel IO reports it done.
    bool sent = err == ESP_OK && xSemaphoreTake(s_done, pdMS_TO_TICKS(1000)) == pdTRUE;
    free(frame);
    if (!sent) {
        ESP_LOGW(TAG, "logo transfer failed: %s", esp_err_to_name(err));
    }
    return sent;
}

void display_set_flipped(bool flipped)
{
    if (!s_lock) return;
    xSemaphoreTake(s_lock, portMAX_DELAY);
    if (s_panel) {
        esp_err_t err = set_orientation(flipped);
        if (err == ESP_OK) {
            draw_logo();
            ESP_LOGI(TAG, "orientation %s", flipped ? "flipped" : "normal");
        } else {
            ESP_LOGW(TAG, "orientation: %s", esp_err_to_name(err));
        }
    }
    xSemaphoreGive(s_lock);
}

void display_start(void)
{
    if (!backlight_init()) {
        ESP_LOGW(TAG, "backlight PWM setup failed");
        return;
    }

    spi_bus_config_t bus = {
        .mosi_io_num     = PIN_MOSI,
        .miso_io_num     = -1,
        .sclk_io_num     = PIN_SCLK,
        .quadwp_io_num   = -1,
        .quadhd_io_num   = -1,
        .max_transfer_sz = LCD_LOGO_W * LCD_LOGO_H * sizeof(uint16_t),
    };
    esp_err_t err = spi_bus_initialize(LCD_HOST, &bus, SPI_DMA_CH_AUTO);
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "spi_bus_initialize: %s", esp_err_to_name(err));
        return;
    }

    s_done = xSemaphoreCreateBinary();
    s_lock = xSemaphoreCreateMutex();
    if (!s_done || !s_lock) return;
    esp_lcd_panel_io_handle_t io = NULL;
    esp_lcd_panel_io_spi_config_t io_cfg = {
        .cs_gpio_num         = PIN_CS,
        .dc_gpio_num         = PIN_DC,
        .spi_mode            = 0,
        .pclk_hz             = LCD_PCLK_HZ,
        .trans_queue_depth   = 10,
        .on_color_trans_done = on_trans_done,
        .user_ctx            = s_done,
        .lcd_cmd_bits        = 8,
        .lcd_param_bits      = 8,
    };
    err = esp_lcd_new_panel_io_spi((esp_lcd_spi_bus_handle_t)LCD_HOST,
                                   &io_cfg, &io);
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "panel io: %s", esp_err_to_name(err));
        return;
    }

    esp_lcd_panel_handle_t panel = NULL;
    esp_lcd_panel_dev_config_t panel_cfg = {
        .reset_gpio_num = PIN_RST,
        .rgb_ele_order  = LCD_RGB_ELEMENT_ORDER_BGR,
        .bits_per_pixel = 16,
    };
    err = esp_lcd_new_panel_st7789(io, &panel_cfg, &panel);
    if (err == ESP_OK) err = esp_lcd_panel_reset(panel);
    if (err == ESP_OK) err = esp_lcd_panel_init(panel);
    for (size_t i = 0; err == ESP_OK && i < sizeof(k_st7735_init) / sizeof(k_st7735_init[0]); i++) {
        err = esp_lcd_panel_io_tx_param(io, k_st7735_init[i].cmd,
                                        k_st7735_init[i].data,
                                        k_st7735_init[i].len);
    }
    if (err == ESP_OK) err = esp_lcd_panel_invert_color(panel, true);
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "panel init: %s", esp_err_to_name(err));
        return;
    }

    // Published under the lock: from here on display_set_flipped() may
    // redraw, but only after the boot draw below has finished.
    xSemaphoreTake(s_lock, portMAX_DELAY);
    s_panel = panel;
    err = set_orientation(config_lcd_flipped());
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "orientation: %s", esp_err_to_name(err));
    }
    bool shown = err == ESP_OK && draw_logo();
    if (shown) esp_lcd_panel_disp_on_off(panel, true);
    else s_panel = NULL;            // stays dark; no redraws on a broken panel
    xSemaphoreGive(s_lock);
    if (!shown) return;

    ledc_set_duty(LEDC_LOW_SPEED_MODE, BL_CHANNEL, BL_FULL);
    ledc_update_duty(LEDC_LOW_SPEED_MODE, BL_CHANNEL);
    esp_timer_handle_t timer = NULL;
    esp_timer_create_args_t args = { .callback = dim_backlight, .name = "lcd_dim" };
    if (esp_timer_create(&args, &timer) == ESP_OK) {
        esp_timer_start_once(timer, BL_DIM_AFTER_US);
    }
    ESP_LOGI(TAG, "logo shown");
}
