# Dongle: display on the LilyGO T-Display V1.1 (#708)

## Problem

The LilyGO T-Display V1.1 is a classic ESP32 (external flash) with a 1.14"
ST7789 TFT, 135x240. The browser installer flashes the normal ESP32 image on
it, which runs fine but has no display code: `display.c` was built for the
ESP32-S3 only and wired to the T-Dongle-S3's pins. The screen stays dark.

## Decision: one ESP32 image, display switched on in the web UI ("Variante A")

The display code goes into the normal ESP32 image. It uses the board
settings from #730/#733 (`board.h`): the `display` setting ("1"/"0", "" =
detected) switches it on, and on the ESP32 the detected default is off. The
user picks the "LilyGO T-Display V1.1" preset under System > Board (LED
`none`, display on) or ticks "Display einschalten". No extra image, no new
entry in the installer, the OTA manifests or the CDN layout. The C3 build
carries no display code, as before.

- **Board table** (`display.c`): one `board_t` per build with the controller
  pins, SPI host, size, RAM window offsets per orientation, MADCTL mirroring,
  colour order (BGR on both boards) and backlight pin/polarity. The S3 entry
  holds the values the T-Dongle-S3 code used before. The T-Display also gets
  the ST7789V power and gamma registers from LilyGO's init, which ESP-IDF's
  driver does not set.
- **Layout from the panel size** (`dm_layout()` in `display_model.c`): the
  positions of logo, badge, name, number and rating band follow from width
  and height. For 160x80 they are exactly the T-Dongle-S3's previous
  positions (pinned in `test_display_model.c`). On 240x135 the same fonts are
  drawn at scale 2, every pixel a 2x2 block, because at their own size they
  are too small to read there (13 large cells per line instead of 17). A
  caller name wider than one line wraps onto a second line (at the last space
  that fits, else after the code point that fills the line), and the rating
  band shrinks to 30 px (instead of 44 at scale 2) to make room. The badge
  stays at scale 1. The 160x80 logo is scaled by 3/2 (nearest neighbour) to
  240x120, so no second bitmap is needed.
- **Backlight:** the S3 keeps its LEDC dimming. The ESP32 build switches the
  T-Display's backlight by GPIO instead (off a minute after boot or a
  call), which keeps the LEDC driver out of its app slot. On both boards the
  caller stays on the screen after a call until then, so a short call can
  still be read; the logo is drawn right before the backlight goes down.
- **On and off at run time** through `display_set_enabled()` from #733, as
  on the S3. Once started, the display's pins stay reserved until the next
  restart (`display_was_started()`), so the status LED cannot take them.

## Safety: not on chips with embedded flash

The T-Display's panel uses GPIO 16 (DC). On an ESP32 with flash inside the
package (the EGBO PICO-D4 dongle) GPIO 16/17 carry that flash, and driving
GPIO 16 would crash the dongle. `board.c` already lists those pins as
forbidden on such a chip; `board_display_fits()` (`board_hw.c`,
host-tested) refuses a display when one of its pins is forbidden. So
`board_display_supported()` is false there: the web UI hides the display
checkbox and the T-Display preset, `POST /api/config` rejects `display=1`,
and `display_start()` refuses with a warning in the log.

## Cost for ESP32 dongles without a display

Measured against master when the display was added, same toolchain and
settings (the text scaling and the name wrap came later and add some code):

- App image +52 KB (108 KB of the 1664 KB slot stay free).
- The larger display state (layout, call, drawing scratch, ~1.3 KB) is
  allocated in `display_start()` only when a display is driven: static DRAM
  +272 B.
- `sdkconfig.defaults.esp32` turns off `CONFIG_SPI_MASTER_ISR_IN_IRAM`:
  nothing else on this target uses the SPI master driver, so its interrupt
  handler stays in flash. Static IRAM +1.3 KB instead of +7.3 KB. With a
  display, a transfer waits while the flash is written (NVS, OTA).

## Wire format

No new fields. The `hardware` object and the `display` setting from #733
carry it: on the classic ESP32, `display_supported` is now true unless the
chip has embedded flash.

## Out of scope

- The T-Display's two buttons (GPIO 0 and 35), e.g. display on/off: a
  separate change once the use is agreed on.
- The T-Display-S3 (ESP32-S3, 8-bit parallel ST7789, 320x170, #725): needs an
  i80 bus driver and its own board entry in the S3 build.

## Verification on hardware

The first test on #727 found washed-out colours with red and blue swapped
(the colour order is BGR; LilyGO's power and gamma registers were added on
the way), text too small (scale 2) and names cut after 12 characters (the
second name line). The
window offsets (40/53 normal, 40/52 rotated) and the mirroring are still
TFT_eSPI's values for this panel; a tester with a T-Display uploads the ESP32
test image through the dongle's firmware upload and reports.
