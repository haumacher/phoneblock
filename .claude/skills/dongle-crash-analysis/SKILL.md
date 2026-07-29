---
name: dongle-crash-analysis
description: Decode and analyze an ESP32 dongle crash report (uploaded *.coredump). Strips the 24-byte flash header, pulls the exact-version unstripped ELF from the CDN, symbolizes with esp-coredump + xtensa gdb, and interprets the backtrace. Use when asked to "analyze the crash report", look at a .coredump, or resolve a dongle backtrace.
allowed-tools: Bash, Read, Glob, Grep
---

# Dongle crash-report analysis

The dongle firmware (`phoneblock-dongle/firmware/`, an ESP32-PICO-D4 / IDF v5.3
app) writes an ESP core dump to its `coredump` flash partition on a panic, and
on the next boot `crashreport_upload_async()` (`main/crashreport.c`) POSTs the
raw image to `${BASE}/api/dongle/coredump` with the firmware version as a query
param. The server stores them per-version, e.g.
`phoneblock/tmp/crash-reports/<version>/<device-uuid>-<YYYYMMDD>-<HHMMSS>.coredump`.

This skill turns one of those files into a symbolized backtrace and a root-cause
read. Do the whole flow — a decode against the wrong ELF is worse than useless
(see step 2).

## 0. Find the files

```bash
find / -type d -name '<version>' -path '*crash-reports*' 2>/dev/null   # e.g. .../crash-reports/1.5.0
ls -la <dir>
```
Those directories are sometimes owned by `root` with mode `0750`. If you get
"Permission denied" and `sudo` needs a password, ask the user to run
`sudo chown -R $USER:$USER <dir>` (or `chmod -R a+rX`) themselves — don't burn
turns retrying.

## 1. Strip the 24-byte flash header

The `.coredump` is **not** a bare ELF. It carries a 24-byte ESP flash core-dump
header (`data_len` u32 = full file size, `version` u32 ≈ 0x0102, …). The real
ELF magic `\x7fELF` begins at **offset 24**. Confirm, then strip:

```bash
f=<file>.coredump
grep -aboF $'\x7fELF' "$f" | head -1          # must print "24:..."
tail -c +25 "$f" > "$SCRATCH/core.elf"        # SCRATCH = your scratchpad dir
xxd -l 8 "$SCRATCH/core.elf"                   # sanity: starts with 7f45 4c46
```

## 2. Get the EXACT-version unstripped ELF from the CDN

**Critical.** Symbolization is only trustworthy against the same build that
produced the dump. A local checkout that is even a few commits past the
`dongle-vX.Y.Z` tag mis-resolves every address — IDF-internal frames come out as
nonsense (`esp_startup_start_app_other_cores`, `wifi_transmit`, …) that *look*
plausible and will send you down a rabbit hole.

`release.sh` uploads the unstripped ELF to the CDN **specifically for this**
(look for the `phoneblock_dongle.elf` copy + the `espcoredump.py` comment in
`firmware/scripts/release.sh`). Pull it by version:

```bash
curl -fsSL -o "$SCRATCH/dongle-<ver>.elf" \
  https://cdn.phoneblock.net/dongle/firmware/<ver>/phoneblock_dongle.elf
file "$SCRATCH/dongle-<ver>.elf"                       # "with debug_info, not stripped"
strings "$SCRATCH/dongle-<ver>.elf" | grep -Eo '<ver>' | head   # confirm version baked in
```

The CDN layout (from `release.sh`): base `https://cdn.phoneblock.net/dongle/firmware/`,
then `<version>/` holds `phoneblock_dongle.{bin,elf}`, `bootloader.bin`,
`partition-table.bin`, `ota_data_initial.bin`, `manifest.json`; the `stable/`
and `beta/` dirs hold the channel `manifest.json` that flips per release.

## 3. Decode with esp-coredump + the xtensa GDB

`esp-coredump` needs the Xtensa GDB, not system gdb. `--chip` is a **global**
flag, before the subcommand.

```bash
source ~/.espressif/python_env/idf5.3_py3.10_env/bin/activate
GDB=~/.espressif/tools/xtensa-esp-elf-gdb/*/xtensa-esp-elf-gdb/bin/xtensa-esp32-elf-gdb
esp-coredump --chip esp32 info_corefile --gdb $GDB \
    --core "$SCRATCH/core.elf" --core-format elf \
    "$SCRATCH/dongle-<ver>.elf" > "$SCRATCH/decoded-<ver>.txt" 2>/dev/null
```

**Match check:** in a correct decode the IDF frames resolve to the release build
host path `/home/bhu/...` and the app frames to `/home/bhu/git/phoneblock/...`.
If instead you see local `/home/haui/...` paths *and* absurd IDF frames, the ELF
is the wrong build — go back to step 2. `dbg_corefile` (instead of
`info_corefile`) drops you into an interactive gdb on the dump for deeper poking.

## 4. Read the dump

Key fields at the top:
- **Crashed task** name + whether it was in interrupt context.
- **exccause**: `0x1c LoadProhibitedCause` / `0x1d StoreProhibited` = deref of an
  invalid pointer; `0x02 InstrFetchProhibited` = jumped through a bad function
  pointer; `0x09 LoadStoreAlignment`; etc.
- **excvaddr**: the bad address that was accessed. A value outside DRAM
  (`0x3ffb0000–0x40000000`) or IRAM is a wild pointer.
- **a0–a15**: check for ASCII text in supposedly-pointer registers (bytes in the
  `0x20–0x7e` range) — a fingerprint of string/HTTP data written over a struct or
  heap metadata.
- **Stack usage table**: `USED/FREE` per task. A task at/near its limit ⇒ stack
  overflow (a different failure mode than heap corruption). Healthy free stack
  everywhere rules that out.

## 5. Interpret — messenger vs. culprit

The top frame is often the *victim*, not the bug. The signature seen in 1.5.0:

> `httpd` serving `GET /api/status` → `add_system_load` (`web.c`) →
> `heap_caps_get_largest_free_block` → `tlsf_walk_pool` → `block_size` faults on
> a wild block pointer (`0x8d513e9…`).

`add_system_load` only *reads* heap metadata; it crashed because it was the first
code to walk an **already-corrupted** heap. A crash inside `tlsf_*` /
`multi_heap_*` / `heap_caps_*` almost always means **heap corruption that
happened earlier** — a use-after-free or buffer overflow whose write already
returned. ASCII bytes sitting in the TLSF block header confirm an overrun. The
originating write is NOT in the backtrace; you have to hunt it (step 6).

General rule: when the fault is deep in an allocator, ring buffer, or scheduler
that "can't" be buggy, treat it as a corruption *detector* and look for who
wrote out of bounds — don't file a bug against the IDF component.

## 6. Root-causing heap corruption

Single dumps prove corruption reached the field but rarely name the write. To
catch it at the source:
- **`CONFIG_HEAP_POISONING_COMPREHENSIVE`** (Component config → Heap memory
  debugging) on a test/beta build. It brackets every allocation with head/tail
  canaries and fills freed memory; the *next* heap op over a smashed region
  aborts with the offending allocation's info instead of crashing later
  elsewhere. Add periodic `heap_caps_check_integrity_all(true)` to shorten the
  gap between the bad write and the abort. (Comprehensive = every free byte is
  also poisoned, so a use-after-free write is caught too; the lighter
  `CONFIG_HEAP_POISONING_LIGHT` only checks canaries on alloc/free.)
- Grep the suspect subsystem (the crashed task's normal work) for fixed-size
  buffers filled from external data — `strcpy`/`memcpy`/`sprintf`/`snprintf`
  whose bound isn't tied to the destination size. For the 1.5.0 httpd signature,
  that's the HTTP request path (header values, URI, query, POST body).
- `CONFIG_HEAP_TRACING` (leaks / alloc history) and, on Xtensa, a watchpoint in
  `dbg_corefile`'s gdb can corner a repeating offender.

## Caveats

- **Don't over-claim from one dump.** State the confirmed fault, name the most
  likely cause, and flag it as a single data point. ASCII-in-metadata hints at
  the payload's origin but doesn't prove the specific write site.
- Verify addresses against the memory map before calling a pointer "wild"; note
  the `<optimized out>` frames are inlined, not missing.

Save the decoded `.txt` and the pulled ELF to the scratchpad so re-reads are free.
