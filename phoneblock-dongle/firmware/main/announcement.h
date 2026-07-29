#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>

#include "esp_err.h"

// Voice announcement served to answered spam callers as a single G.711
// A-law stream (8 kHz, mono, no header). No announcement is baked into the
// firmware (issue #460 — a single German recording does not scale to the
// UI's languages). The active announcement is resolved at open() time, in
// order:
//   1. a custom file the user uploaded through the web UI
//      (/spiffs/announcement.alaw), else
//   2. the localized file downloaded from the CDN for the active ui_lang
//      (/spiffs/announcement-<lang>.alaw, written by i18n_sync.c), else
//   3. nothing — open() returns an empty source and the caller answers
//      silently and hangs up. A missing announcement is not fatal: the
//      dongle's job is to occupy the spam call, not to say anything.
//
// Lifecycle:
//   - announcement_init() must be called once during boot. It mounts
//     the SPIFFS partition (formatting it on first boot).
//   - announcement_open()/read()/close() stream the active announcement
//     sequentially. The SPIFFS file is read straight from flash, never
//     loaded into a single heap block (the device has no PSRAM, so a
//     ~240 KB contiguous malloc fails once the heap is fragmented).
//   - announcement_write_*() replaces the custom SPIFFS file.
//   - announcement_reset() deletes the custom file; the next open()
//     falls back to the localized download, or to silence.

#define ANNOUNCEMENT_MAX_BYTES (240 * 1024)   // ~30 s at 8 kB/s A-law

esp_err_t   announcement_init(void);

// A handle for streaming the active announcement frame-by-frame.
// Either an in-memory source (the embedded default, mapped from flash)
// or an open SPIFFS file (a custom announcement). Treat as opaque:
// fill it with announcement_open(), drain it with announcement_read(),
// release it with announcement_close().
typedef struct {
    FILE          *file;   // non-NULL → read sequentially via fread
    const uint8_t *mem;    // non-NULL → read directly from flash RODATA
    size_t         pos;    // read cursor for the mem source
    size_t         len;    // total announcement length in bytes
} announcement_src_t;

// Open the active announcement for sequential streaming and fill *src.
// Always returns ESP_OK with a usable source (falls back to the
// embedded default if no custom file can be opened). src->len == 0 is
// legal (means: no audio, caller should go straight to BYE).
// The caller MUST release the handle with announcement_close().
esp_err_t   announcement_open(announcement_src_t *src);

// Build the SPIFFS path of the downloaded announcement for a locale:
// "/spiffs/announcement-<lang>.alaw". lang must be a config_lang_code_valid()
// code. Shared by announcement_open() and i18n_sync.c so the naming
// convention lives in one place. A 48-byte buffer is always enough.
void        announcement_localized_path(char *out, size_t cap, const char *lang);

// Read up to max bytes sequentially from src into out. Returns the
// number of bytes produced (0 at end-of-stream or on read error).
size_t      announcement_read(announcement_src_t *src, uint8_t *out, size_t max);

// Release any resources held by src (closes the SPIFFS file, if any).
// Safe to call on an already-closed or all-memory source.
void        announcement_close(announcement_src_t *src);

// Streaming write API — lets the web handler spool the upload
// directly from HTTP into SPIFFS without a 240 KB heap buffer.
//
//   announcement_write_begin(total) → truncates the live file in place
//   announcement_write_append(...)  → append a received chunk
//   announcement_write_commit()     → verify the byte count
//   announcement_write_abort()      → drop the partial file on error
//
// The write goes straight into the live file (no temp + rename), so the
// partition never needs room for two copies. The trade-off is that an
// interrupted write leaves no custom announcement and the embedded
// default takes over until the user re-uploads (see write_begin).
//
// Only one write session at a time. Not thread-safe — call from a
// single request handler.
esp_err_t   announcement_write_begin(size_t total_bytes);
esp_err_t   announcement_write_append(const uint8_t *buf, size_t len);
esp_err_t   announcement_write_commit(void);
void        announcement_write_abort(void);

// Discard any user-provided announcement; the next get() returns the
// embedded default.
esp_err_t   announcement_reset(void);

// True if a user-uploaded custom announcement is present. For the
// dashboard (distinguishes a hand-uploaded file from a localized download).
bool        announcement_is_custom(void);

// Which announcement open() would serve right now, for the dashboard:
//   "custom"    — user-uploaded file
//   "localized" — CDN download for the active ui_lang
//   "none"      — nothing; answered calls are silent
const char *announcement_source(void);

// Byte length of the announcement open() would serve, or 0 when none is
// available. The dashboard reports it alongside announcement_source().
size_t      announcement_length(void);
