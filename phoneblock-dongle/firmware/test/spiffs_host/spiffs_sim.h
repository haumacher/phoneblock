// Host simulation of the dongle's "storage" partition: the unmodified ESP-IDF
// SPIFFS core (components/spiffs/spiffs/src/*.c) on a RAM-backed NOR-flash
// HAL, mounted with the geometry esp_spiffs.c uses on the device:
//
//   physical size   SIM_PART_SIZE   (storage line of partitions.csv)
//   logical block   SIM_BLOCK_SIZE  (= flash erase sector, 4 KB)
//   logical page    CONFIG_SPIFFS_PAGE_SIZE (sdkconfig.defaults)
//   max_files       2               (announcement_init)
//
// The file helpers mirror what esp_spiffs.c's VFS layer turns the firmware's
// stdio calls into (open with O_CREAT|O_TRUNC plus the mtime meta update,
// SPIFFS_write per stdio-buffer flush, SPIFFS_remove, SPIFFS_rename), and
// esp_spiffs_info()/esp_spiffs_gc() (esp_spiffs.h stand-in) behave like the
// IDF originals, including the "SPIFFS_gc failed" ERROR line.
#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifndef SIM_PART_SIZE
#  error "SIM_PART_SIZE missing: the Makefile derives it from partitions.csv"
#endif

// ESP32/ESP32-C3 SPI flash erase sector; esp_spiffs.c uses it as the
// logical block size (g_rom_flashchip.sector_size).
#define SIM_BLOCK_SIZE 4096u

// Formats a zeroed (all-0xFF) flash image and mounts it.
void sim_init(void);

// Raw SPIFFS error code of the last failing helper call (0 if none).
int sim_last_err(void);

// Writes a file of `size` synthetic bytes the way fopen("wb") + fwrite +
// fclose does on the device with a stdio buffer of `chunk` bytes: one
// SPIFFS_write per full buffer, the remainder on close. Returns true on
// success; on failure *written holds the bytes SPIFFS accepted and
// sim_last_err() the SPIFFS error. The (partial) file is left in place, as
// on the device.
bool sim_write_file(const char *path, size_t size, size_t chunk,
                    size_t *written);

// unlink() / rename(); true on success.
bool sim_unlink(const char *path);
bool sim_rename(const char *from, const char *to);

// Size of an existing file, or -1.
long sim_file_size(const char *path);

// Page accounting straight from the SPIFFS core, for diagnostics.
typedef struct {
    int free_blocks;    // fully erased blocks
    int pages_free;     // erased data pages
    int pages_deleted;  // deleted, not yet reclaimed by GC
    int pages_used;     // allocated (live) data pages
} sim_pages_t;
void sim_pages(sim_pages_t *out);

// Number of ERROR lines esp_spiffs_gc() would have logged since sim_init().
int sim_gc_error_count(void);

// Number of block erases since sim_init() (flash wear).
long sim_erase_count(void);

// Log lines captured through the esp_log.h stand-in since the last reset.
int  sim_log_count(char level);
void sim_log_reset(void);
// When true, captured lines are also printed (debugging a test).
extern bool sim_log_echo;
