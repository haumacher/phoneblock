# Dongle firmware

ESP-IDF firmware for the PhoneBlock dongle (ESP32). Build with
`idf.py build` after `source ~/tools/esp/esp-idf/export.sh`. See
`README.md` for the full setup, QEMU recipes and on-device flashing.

## Conventions

### Warnings/errors are mirrored to the web UI

`main/log_capture.c` installs a global `esp_log_set_vprintf` hook that
copies every `ESP_LOGW`/`ESP_LOGE` line — ours and the ESP-IDF
libraries' — into the stats error ring, shown as the "Protokoll" panel on
the dongle's web page. This is the field-diagnosis path: customers read
problems off the web UI instead of needing a serial console.

Consequence for new code: **log an unusual condition at WARN or ERROR and
it shows up on the UI automatically** — no per-call-site plumbing. Keep
`ESP_LOGI` for normal progress; don't `ESP_LOGW` things that recur every
few seconds (they'd flush the 32-entry ring). The parsing of the formatted
log line is host-tested in `test/test_log_capture.c`.

Some ESP-IDF libraries WARN on routine, self-recovering conditions they
have no INFO path for (e.g. `httpd_txrx` logs `error in recv` every time a
browser drops a keep-alive socket). Those aren't faults but would alarm
users on the panel. The `k_suppress` denylist in `log_capture.c` drops
such lines from the *ring only* — they still reach the serial console.
Keep entries narrow (exact tag + message substring + level) so a genuine
error from the same component still surfaces; add a host test alongside.
Prefer this over `esp_log_level_set(tag, …)`, which also kills the serial
line and hides the whole tag.

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

## Analysing a field crash dump

Devices upload core dumps to the server after a panic (see
`firmware_update.c` / the coredump upload path). To turn one into a
backtrace:

1. **Get the matching ELF from the CDN over HTTPS — not sftp.** The
   unstripped ELF ships next to the `.bin` for every release and is
   served publicly:

   ```bash
   curl -O https://cdn.phoneblock.net/dongle/firmware/<version>/phoneblock_dongle.elf
   ```

   (The sftp host has no shell and is only for *uploading* releases; for
   a read, plain `curl` is simpler and works inside the sandbox.)

2. **The dump is the raw binary coredump format, not ELF.** Its first
   four bytes are the little-endian length, which equals the file size
   (e.g. `0x000071A4` = 29092 bytes). So pass `--core-format raw`; with
   `elf` (or `auto`) the tool tries to parse a `\x7fELF` magic and dies
   with a `ConstError`:

   ```bash
   source ~/tools/esp/esp-idf/export.sh
   esp-coredump info_corefile -c <dump> --core-format raw phoneblock_dongle.elf
   ```

3. **A SHA256 mismatch is expected for field-built firmware — bypass it.**
   `esp-coredump` guards on the ELF's `app_elf_sha256` matching the one
   recorded in the dump. That hash covers the *whole* ELF, including the
   build timestamp embedded in `esp_app_desc` — so a rebuild from
   identical source (or a user's own build of the same tag) gets a
   different hash while the *code addresses* line up. If you trust the
   ELF is the same source, comment out the
   `if core_sha_trimmed != app_sha_trimmed:` raise in the installed
   `esp_coredump/corefile/loader.py`, run the decode, then restore it.
   Our own frames (`tr064.c`, `web.c`, …) then resolve correctly; only
   the ESP-IDF library frames may be slightly misattributed by the
   address offset.
