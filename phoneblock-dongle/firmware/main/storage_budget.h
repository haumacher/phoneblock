// Storage budget for the shared "storage" SPIFFS partition — pure
// arithmetic, no ESP-IDF dependencies, host-tested (test/test_storage_budget.c,
// test/test_storage_sim.c). storage.c applies it to the live partition.
//
// One budget covers the whole partition. The rarely written files — the
// localized announcement, the UI and mail packs (i18n_sync.c) and the user's
// own recorded announcement (announcement.c) — have precedence; the daily
// community blocklist gets what remains, which the dongle expresses to the
// server as a smaller &maxBytes (blocklist_sync.c).
//
// Derivation. "Free" as esp_spiffs_info() reports it is not "writable": the
// old budget (free space minus 48 KB, every day) drove the partition into a
// state where SPIFFS fails 4 KB writes with SPIFFS_ERR_FULL while 40 % of it
// is reported free (issues #529, #542). Four terms, all in data pages (the
// unit esp_spiffs_info() counts in: page size minus the 5-byte page header;
// with the dongle's 1024-byte pages and 4 KB blocks a block holds one lookup
// page and three data pages, and SPIFFS keeps two blocks spare):
//
//  1. The files themselves (esp_spiffs_info's `used`).
//
//  2. Write amplification. Every SPIFFS write call rewrites the file's
//     object index page and leaves the previous copy behind, deleted. A file
//     written through a W-byte stdio buffer leaves one stale page per W bytes
//     (25 % with W = 4 KB, 100 % with the 1 KB writes i18n_sync used before).
//     Those pages sit one per block between live data pages, and collecting
//     such a block gains nothing — moving its live pages costs a fresh index
//     page — so for as long as the file lives they are as good as used.
//
//  3. GC rotation. SPIFFS picks the block to collect by erase age (weight 50
//     per erase) far more than by deleted pages (weight 5 per page, at most
//     15 with three data pages per block). Before it reaches the blocks the
//     deleted community file left behind, it relocates the older blocks —
//     those of the long-lived files — and each relocation costs one page for
//     the rewritten object index. The free space has to fund one page per
//     occupied block; without it the GC ends in its "crammed" mode, where it
//     alternates between gaining and losing a page and never collects the
//     five pages a 4 KB write call needs.
//
//  4. A fixed reserve of blocks for the write in flight (storage.c,
//     RESERVE_BLOCKS).
//
// The community file is budgeted like any other: its own stale and rotation
// pages count while it is on flash, since other files are written next to it.
#pragma once

#include <stdbool.h>
#include <stdint.h>

typedef struct {
    // File bytes per SPIFFS data page: logical page size minus the 5-byte
    // spiffs_page_header.
    uint32_t page_payload;
    // Data pages per logical block: pages per block minus the object lookup
    // page(s).
    uint32_t data_pages_per_block;
    // Bytes handed to SPIFFS per write call, i.e. the stdio buffer every file
    // is written through (STORAGE_WRITE_BUF).
    uint32_t write_buf;
    // Blocks kept free on top of the derived terms.
    uint32_t reserve_blocks;
} storage_geometry_t;

// Pages a file of `bytes` occupies once written: its data pages, its index
// header page and the stale index pages its write calls leave behind.
uint32_t storage_budget_footprint_pages(const storage_geometry_t *g,
                                        uint64_t bytes);

// Pages the partition must keep free beyond `live_pages` pages of files (as
// esp_spiffs_info's `used` counts them): their stale index pages, one
// rotation page per block they occupy, and the fixed block reserve.
uint32_t storage_budget_overhead_pages(const storage_geometry_t *g,
                                       uint32_t live_pages);

// Largest community file, in bytes, that fits next to everything on the
// partition now. `total` and `used` are esp_spiffs_info()'s numbers, taken
// after the old community file was deleted. Returns 0 if nothing fits.
uint64_t storage_budget_community(const storage_geometry_t *g, uint64_t total,
                                  uint64_t used);

// Whether a file of `bytes` can be added next to what is on the partition now
// (`total`, `used` as above) within the budget. An old copy the file will
// replace is still counted.
bool storage_budget_asset_fits(const storage_geometry_t *g, uint64_t total,
                               uint64_t used, uint64_t bytes);
