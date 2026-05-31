# Dongle firmware

ESP-IDF firmware for the PhoneBlock dongle (ESP32). Build with
`idf.py build` after `source ~/tools/esp/esp-idf/export.sh`. See
`README.md` for the full setup, QEMU recipes and on-device flashing.

## Conventions

### Long-running timer intervals

`pdMS_TO_TICKS(ms)` multiplies its argument by `configTICK_RATE_HZ` in
32-bit `TickType_t`. At the project's 100 Hz tick rate, any millisecond
value above ~42_949_672 (≈12 h) overflows the multiplication and wraps
to a much shorter delay — a 24 h interval becomes ~8.3 min, which is
how issue #348 leaked.

The fix is the unit, not wider arithmetic: in seconds, even a 24 h delay
stays comfortably inside `uint32_t`. Use the helpers in
`main/ticks_util.h` for anything bigger than a minute or two:

- `seconds_to_ticks(uint32_t seconds)` — takes the interval in seconds,
  so the `* configTICK_RATE_HZ` multiplication can't overflow for any
  delay short enough to fit in a `TickType_t` at all.
- `pb_ms_to_ticks(ms)` — drop-in replacement for `pdMS_TO_TICKS` that
  refuses to compile if a *constant* `ms` would overflow. Use this
  whenever you'd reach for `pdMS_TO_TICKS` with a literal, so the
  build catches the gotcha for you.

Plain `pdMS_TO_TICKS` is fine for short, obviously-safe intervals
(seconds to a few minutes).

This is a temporary workaround: FreeRTOS-Kernel PR #866 widened
`pdMS_TO_TICKS` to `uint64_t` upstream and shipped in kernel V11.0.0
(Dec 2023). ESP-IDF v5.3 still bundles V10.5.1 with the 32-bit
multiplication. When we upgrade to ESP-IDF >= v5.4 (kernel V11+),
plain `pdMS_TO_TICKS` becomes safe again and `ticks_util.h` (plus the
`seconds_to_ticks` / `pb_ms_to_ticks` call sites) can be retired.
