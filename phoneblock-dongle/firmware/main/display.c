#include "display.h"

#include <stdio.h>
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
#include "display_model.h"
#include "lcd_font.h"
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

// The caller screen's fallback: a legitimate call is declined by the dongle
// after a few seconds (#380) while the other phones keep ringing, and the
// Fritz!Box tells the dongle nothing more about it — so the screen ends on
// this timeout unless a CANCEL, the spam call's teardown or the next call
// ends it first. Long enough to cover the ringing phase.
#define CALL_TIMEOUT_US (60LL * 1000 * 1000)

// Offset of the 80x160 panel inside the controller's 132x162 RAM, in the
// swapped (landscape) axes, per orientation (LilyGO: 1/26 for normal).
// Turning the image by 180° mirrors both axes, which may move the visible
// window to the opposite edge of the RAM; adjust the flipped pair if the
// rotated logo appears shifted on the hardware.
#define GAP_NORMAL_X   1
#define GAP_NORMAL_Y   26
#define GAP_FLIPPED_X  1
#define GAP_FLIPPED_Y  26

// Frames are composed and sent in horizontal strips from one transient
// DMA buffer (5 KB, freed after each frame) instead of a 25.6 KB frame
// buffer: the S3 has no PSRAM. A full frame is 25.6 KB on a 40 MHz bus,
// about 5 ms of transfer.
#define STRIP_ROWS   16
#define STRIP_PIXELS (DM_WIDTH * STRIP_ROWS)

// RGB565 with the bytes swapped into the order the panel reads over SPI —
// the encoding scripts/gen-lcd-logo.py uses for the logo.
#define RGB565(r, g, b) ((uint16_t)((((r) & 0xF8) << 8) | (((g) & 0xFC) << 3) | ((b) >> 3)))
#define COLOR(r, g, b)  ((uint16_t)((RGB565(r, g, b) << 8) | (RGB565(r, g, b) >> 8)))

#define C_BACKGROUND  COLOR(16, 24, 40)     // the logo's background
#define C_WHITE       COLOR(255, 255, 255)
#define C_BLACK       COLOR(0, 0, 0)
#define C_NUMBER      COLOR(180, 190, 205)  // number below a name
#define C_SPAM        COLOR(220, 30, 30)
#define C_SUSPECT     COLOR(245, 160, 0)
#define C_LEGIT       COLOR(30, 150, 60)
#define C_NEUTRAL     COLOR(70, 80, 100)
#define C_BADGE       COLOR(230, 40, 40)

// Caller screen layout (what goes into the lines: dm_call_screen()): a
// dark area with the name over the number, or the number alone, and the
// rating band below it in the class colour.
#define BAND_Y         58                   // band: rows 58..79
#define NAME_Y         10                   // large name, cell top
#define NAME_NUMBER_Y  35                   // small number below a name
#define NUMBER_LARGE_Y 20                   // number alone, large font
#define NUMBER_SMALL_Y 22                   // number alone, small font
#define BAND_TEXT_Y    62                   // small font in the band

// Spam badge on the idle screen: a red pill in the top-right corner, in
// the logo's empty area above the word mark (rows 0..17 are background
// from x = 80 on).
#define BADGE_TOP      1
#define BADGE_H        17
#define BADGE_RIGHT    158                  // last column
#define BADGE_TEXT_Y   3                    // small digits centred in it

// Set once display_start() got the panel up; NULL means "no display", and
// every entry point then does nothing.
static esp_lcd_panel_handle_t s_panel;
static SemaphoreHandle_t s_done;    // given by on_trans_done
static SemaphoreHandle_t s_lock;    // serialises drawing (timer vs. httpd)
static SemaphoreHandle_t s_state;   // guards the call state below (short holds)

static esp_timer_handle_t s_redraw_timer;
static esp_timer_handle_t s_dim_timer;
static esp_timer_handle_t s_call_timer;

// The call on screen, written by the notifiers under s_state.
static stats_call_t s_call;
// The badge's count, recounted by display_calls_changed() in the task that
// changed the list: the call list is a file (#589), and reading it is no
// job for the esp_timer task the drawing runs on (3.5 KB stack, and every
// other timer waits meanwhile).
static int          s_badge;
static bool         s_call_active;
static uint32_t     s_call_gen;          // bumped per call
static int64_t      s_call_started_us;

// What is on the panel, and the drawing scratch space — static rather than
// on the esp_timer task's stack. All under s_lock.
static bool             s_drawn_valid;
static bool             s_drawn_call;
static uint32_t         s_drawn_gen;
static int              s_drawn_badge;
static stats_call_t     s_draw_call;
static dm_call_screen_t s_screen;
static bool             s_draw_failure_logged;

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

// --- backlight --------------------------------------------------------

static void set_backlight(uint32_t duty)
{
    ledc_set_duty(LEDC_LOW_SPEED_MODE, BL_CHANNEL, duty);
    ledc_update_duty(LEDC_LOW_SPEED_MODE, BL_CHANNEL);
}

static void dim_backlight(void *arg)
{
    (void)arg;
    xSemaphoreTake(s_state, portMAX_DELAY);
    bool calling = s_call_active;
    xSemaphoreGive(s_state);
    if (!calling) set_backlight(BL_DIMMED);
}

// Full brightness now, dimmed BL_DIM_AFTER_US later (unless a call is on
// screen by then).
static void brighten_for_a_while(void)
{
    set_backlight(BL_FULL);
    esp_timer_stop(s_dim_timer);
    esp_timer_start_once(s_dim_timer, BL_DIM_AFTER_US);
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

// --- composing strips ---------------------------------------------------

// Expands the run-length encoded logo (lcd_logo.h) strip by strip.
typedef struct {
    size_t   next;       // index of the next (count, colour) pair
    uint32_t left;       // pixels left in the current run
    uint16_t colour;
} logo_cursor_t;

// Fills `n` pixels from the logo. False if the runs end early (corrupt
// data); runs beyond one frame are simply never read.
static bool logo_fill(logo_cursor_t *c, uint16_t *out, size_t n)
{
    while (n > 0) {
        if (c->left == 0) {
            if (c->next + 1 >= lcd_logo_rle_len) return false;
            c->left   = lcd_logo_rle[c->next];
            c->colour = lcd_logo_rle[c->next + 1];
            c->next  += 2;
            continue;
        }
        size_t k = c->left < n ? c->left : n;
        for (size_t i = 0; i < k; i++) *out++ = c->colour;
        c->left -= k;
        n -= k;
    }
    return true;
}

// Draws the set pixels of glyph `index` with its cell's top-left corner at
// (x, y), clipped to the strip holding rows y0 .. y0 + STRIP_ROWS - 1.
static void draw_glyph(uint16_t *strip, int y0, const lcd_font_t *font,
                       int index, int x, int y, uint16_t fg)
{
    const uint8_t *bits = font->bits + (size_t)index * font->bytes_per_glyph;
    for (int gy = 0; gy < font->h; gy++) {
        int sy = y + gy - y0;
        if (sy < 0 || sy >= STRIP_ROWS) continue;
        for (int gx = 0; gx < font->w; gx++) {
            int sx = x + gx;
            if (sx < 0 || sx >= DM_WIDTH) continue;
            int bit = gy * font->w + gx;
            if (bits[bit >> 3] & (0x80 >> (bit & 7))) {
                strip[sy * DM_WIDTH + sx] = fg;
            }
        }
    }
}

// Draws UTF-8 `text` from (x, y); a code point the fonts lack shows as '?'.
static void draw_text(uint16_t *strip, int y0, const lcd_font_t *font,
                      const char *text, int x, int y, uint16_t fg)
{
    if (y + font->h <= y0 || y >= y0 + STRIP_ROWS) return;
    uint32_t cp;
    while ((cp = dm_utf8_next(&text)) != 0) {
        int index = cp == DM_UTF8_INVALID ? -1 : dm_glyph_index(cp);
        if (index < 0) index = dm_glyph_index('?');
        draw_glyph(strip, y0, font, index, x, y, fg);
        x += font->w;
    }
}

// Draws a line centred horizontally with its cell top at y.
static void draw_line(uint16_t *strip, int y0, const dm_line_t *line,
                      int y, uint16_t fg)
{
    if (!line->font || !line->text[0]) return;
    draw_text(strip, y0, line->font, line->text,
              (DM_WIDTH - line->width) / 2, y, fg);
}

// The badge: a pill BADGE_H high, right-aligned at BADGE_RIGHT, wide
// enough for `text` (a circle for one digit), with the count in white.
static void draw_badge(uint16_t *strip, int y0, const char *text)
{
    const lcd_font_t *font = &lcd_font_small;
    int text_w = dm_text_width(font, text);
    int w = text_w + 8 > BADGE_H ? text_w + 8 : BADGE_H;
    int x0 = BADGE_RIGHT + 1 - w;
    // In doubled coordinates, so the cap centres and the radius (half the
    // height) fall on whole numbers; pixel centres are at odd values.
    int r2 = BADGE_H;
    int left_cx = 2 * x0 + r2;
    int right_cx = 2 * (BADGE_RIGHT + 1) - r2;
    int cy = 2 * BADGE_TOP + r2;
    for (int y = BADGE_TOP; y < BADGE_TOP + BADGE_H; y++) {
        int sy = y - y0;
        if (sy < 0 || sy >= STRIP_ROWS) continue;
        int dy = 2 * y + 1 - cy;
        for (int x = x0; x <= BADGE_RIGHT; x++) {
            int px = 2 * x + 1;
            int dx = px < left_cx ? left_cx - px : px > right_cx ? px - right_cx : 0;
            if (dx * dx + dy * dy <= r2 * r2) strip[sy * DM_WIDTH + x] = C_BADGE;
        }
    }
    draw_text(strip, y0, font, text, x0 + (w - text_w) / 2, BADGE_TEXT_Y, C_WHITE);
}

static uint16_t band_colour(dm_class_t cls)
{
    switch (cls) {
        case DM_CLASS_SPAM:    return C_SPAM;
        case DM_CLASS_SUSPECT: return C_SUSPECT;
        case DM_CLASS_LEGIT:   return C_LEGIT;
        default:               return C_NEUTRAL;
    }
}

static void compose_call(uint16_t *strip, int y0, const dm_call_screen_t *scr)
{
    uint16_t band = band_colour(scr->cls);
    for (int r = 0; r < STRIP_ROWS; r++) {
        uint16_t c = y0 + r < BAND_Y ? C_BACKGROUND : band;
        uint16_t *row = strip + r * DM_WIDTH;
        for (int x = 0; x < DM_WIDTH; x++) row[x] = c;
    }
    if (scr->named) {
        draw_line(strip, y0, &scr->main, NAME_Y, C_WHITE);
        draw_line(strip, y0, &scr->sub, NAME_NUMBER_Y, C_NUMBER);
    } else {
        int y = scr->main.font == &lcd_font_large ? NUMBER_LARGE_Y : NUMBER_SMALL_Y;
        draw_line(strip, y0, &scr->main, y, C_WHITE);
    }
    draw_line(strip, y0, &scr->band, BAND_TEXT_Y,
              scr->cls == DM_CLASS_SUSPECT ? C_BLACK : C_WHITE);
}

// Composes and sends one frame: the caller screen if `call`, else the logo
// with `badge` (hidden if 0). False (nothing logged) on failure.
static bool send_frame(bool call, int badge)
{
    uint16_t *strip = heap_caps_malloc(STRIP_PIXELS * sizeof(uint16_t),
                                       MALLOC_CAP_DMA | MALLOC_CAP_INTERNAL);
    if (!strip) return false;
    char badge_text[8];
    snprintf(badge_text, sizeof(badge_text), "%d", badge);
    logo_cursor_t logo = { 0 };
    bool ok = true;
    for (int y0 = 0; ok && y0 < DM_HEIGHT; y0 += STRIP_ROWS) {
        if (call) {
            compose_call(strip, y0, &s_screen);
        } else {
            ok = logo_fill(&logo, strip, STRIP_PIXELS);
            if (ok && badge > 0) draw_badge(strip, y0, badge_text);
        }
        // The transfer runs from the strip buffer by DMA; reuse (or free)
        // it only once the panel IO reports it done.
        ok = ok && esp_lcd_panel_draw_bitmap(s_panel, 0, y0, DM_WIDTH,
                                             y0 + STRIP_ROWS, strip) == ESP_OK
                && xSemaphoreTake(s_done, pdMS_TO_TICKS(1000)) == pdTRUE;
    }
    free(strip);
    return ok;
}

// Brings the panel up to date with the current state; with `force` even if
// it already shows it (new orientation). Caller holds s_lock.
static bool draw_screen(bool force)
{
    xSemaphoreTake(s_state, portMAX_DELAY);
    bool call = s_call_active;
    uint32_t gen = s_call_gen;
    if (call) s_draw_call = s_call;
    int badge = call ? 0 : s_badge;
    xSemaphoreGive(s_state);

    if (!force && s_drawn_valid && s_drawn_call == call
        && (call ? s_drawn_gen == gen : s_drawn_badge == badge)) {
        return true;
    }
    if (call) dm_call_screen(&s_draw_call, &s_screen);
    if (!send_frame(call, badge)) {
        s_drawn_valid = false;
        if (!s_draw_failure_logged) {
            s_draw_failure_logged = true;
            ESP_LOGW(TAG, "drawing failed (out of memory or panel error)");
        }
        return false;
    }
    s_drawn_valid = true;
    s_drawn_call  = call;
    s_drawn_gen   = gen;
    s_drawn_badge = badge;
    return true;
}

static void redraw(void *arg)
{
    (void)arg;
    xSemaphoreTake(s_lock, portMAX_DELAY);
    if (s_panel) draw_screen(false);
    xSemaphoreGive(s_lock);
}

// Hands the redraw to the esp_timer task, so a notifier (SIP task, web
// server) never waits for the panel. Notifications arriving while one is
// pending fold into it: the redraw reads the latest state.
static void schedule_redraw(void)
{
    esp_timer_start_once(s_redraw_timer, 1);   // ESP_ERR_INVALID_STATE: already pending
}

// --- notifications ------------------------------------------------------

// Every entry point checks s_redraw_timer: it is the last thing
// display_start() sets up, so it is non-NULL only once the panel showed the
// logo — before that, and on a board without a working display, the
// notifications do nothing.

void display_call_started(const stats_call_t *call)
{
    if (!s_redraw_timer) return;
    xSemaphoreTake(s_state, portMAX_DELAY);
    s_call = *call;
    s_call_active = true;
    s_call_gen++;
    s_call_started_us = esp_timer_get_time();
    xSemaphoreGive(s_state);
    esp_timer_stop(s_dim_timer);
    set_backlight(BL_FULL);
    esp_timer_stop(s_call_timer);
    esp_timer_start_once(s_call_timer, CALL_TIMEOUT_US);
    schedule_redraw();
}

static void end_call(bool timeout)
{
    xSemaphoreTake(s_state, portMAX_DELAY);
    bool was_active = s_call_active;
    // A timeout that raced with a new call must not end that one.
    if (timeout && esp_timer_get_time() - s_call_started_us < CALL_TIMEOUT_US - 1000) {
        was_active = false;
    }
    if (was_active) s_call_active = false;
    xSemaphoreGive(s_state);
    if (!was_active) return;
    if (!timeout) esp_timer_stop(s_call_timer);
    brighten_for_a_while();
    schedule_redraw();
}

static void call_timeout(void *arg)
{
    (void)arg;
    end_call(true);
}

void display_call_ended(void)
{
    if (!s_redraw_timer) return;
    end_call(false);
}

void display_calls_changed(void)
{
    if (!s_redraw_timer) return;
    // Called by stats.c outside its lock, from the task that changed the list.
    int badge = stats_count_calls(dm_is_listed_spam, DM_BADGE_CALLS);
    xSemaphoreTake(s_state, portMAX_DELAY);
    s_badge = badge;
    xSemaphoreGive(s_state);
    schedule_redraw();
}

void display_set_flipped(bool flipped)
{
    if (!s_lock) return;
    xSemaphoreTake(s_lock, portMAX_DELAY);
    if (s_panel) {
        esp_err_t err = set_orientation(flipped);
        if (err == ESP_OK) {
            draw_screen(true);
            ESP_LOGI(TAG, "orientation %s", flipped ? "flipped" : "normal");
        } else {
            ESP_LOGW(TAG, "orientation: %s", esp_err_to_name(err));
        }
    }
    xSemaphoreGive(s_lock);
}

static bool create_timer(esp_timer_cb_t cb, const char *name, esp_timer_handle_t *out)
{
    esp_timer_create_args_t args = { .callback = cb, .name = name };
    return esp_timer_create(&args, out) == ESP_OK;
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
        .max_transfer_sz = STRIP_PIXELS * sizeof(uint16_t),
    };
    esp_err_t err = spi_bus_initialize(LCD_HOST, &bus, SPI_DMA_CH_AUTO);
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "spi_bus_initialize: %s", esp_err_to_name(err));
        return;
    }

    s_done = xSemaphoreCreateBinary();
    s_lock = xSemaphoreCreateMutex();
    s_state = xSemaphoreCreateMutex();
    if (!s_done || !s_lock || !s_state) return;
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

    esp_timer_handle_t redraw_timer = NULL;
    if (!create_timer(dim_backlight, "lcd_dim", &s_dim_timer)
        || !create_timer(call_timeout, "lcd_call", &s_call_timer)
        || !create_timer(redraw, "lcd_draw", &redraw_timer)) {
        ESP_LOGW(TAG, "timer setup failed");
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
    // No calls listed yet at boot: the logo, without a badge.
    bool shown = err == ESP_OK && draw_screen(true);
    if (shown) esp_lcd_panel_disp_on_off(panel, true);
    else s_panel = NULL;            // stays dark; no redraws on a broken panel
    xSemaphoreGive(s_lock);
    if (!shown) return;

    // Calls may be shown from now on.
    s_redraw_timer = redraw_timer;
    brighten_for_a_while();
    ESP_LOGI(TAG, "logo shown");
}
