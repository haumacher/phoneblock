#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "esp_err.h"

// User-overridable voice announcement. Serves exactly one G.711 A-law
// stream (8 kHz, mono, no header), either the default baked into the
// firmware binary or a custom one uploaded through the web UI and
// stored in the SPIFFS partition.
//
// Lifecycle:
//   - announcement_init() must be called once during boot. It mounts
//     the SPIFFS partition (formatting it on first boot).
//   - announcement_get() hands out a pointer + length to the bytes
//     that should be streamed next. If a custom SPIFFS file exists
//     it is loaded into a heap cache on first call and kept there.
//     Otherwise the embedded default (flash RODATA) is returned.
//   - announcement_write() replaces the current SPIFFS file with the
//     provided bytes and invalidates the cache.
//   - announcement_reset() deletes the SPIFFS file; the next get()
//     falls back to the embedded default.
//
// All bytes returned by get() remain valid until the next
// write()/reset() call — the caller does not free them.

#define ANNOUNCEMENT_MAX_BYTES (240 * 1024)   // ~30 s at 8 kB/s A-law

esp_err_t   announcement_init(void);

// Returns ESP_OK and fills *buf/*len with the current announcement.
// *len == 0 is legal (means: no audio, caller should go straight
// to BYE without streaming).
esp_err_t   announcement_get(const uint8_t **buf, size_t *len);

// Streaming write API — lets the web handler spool the upload
// directly from HTTP into SPIFFS without a 240 KB heap buffer.
//
//   announcement_write_begin(total) → writes to a temp file
//   announcement_write_append(...)  → append a received chunk
//   announcement_write_commit()     → atomic rename to live file
//   announcement_write_abort()      → clean up on error
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

// True if the current announcement comes from SPIFFS (not the
// embedded default). For the dashboard.
bool        announcement_is_custom(void);

// Byte length the dashboard reports alongside is_custom.
size_t      announcement_length(void);
