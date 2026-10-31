// Storage budget for the shared "storage" partition — pure arithmetic, no
// ESP-IDF dependencies, host-tested (test/test_storage_budget.c,
// test/test_storage_sim.c). storage.c applies it to the live partition.
//
// One budget covers the whole partition. The rarely written files — the
// localized announcement or the dialog clips, the UI and mail packs
// (i18n_sync.c) and the user's own recorded announcement (announcement.c) —
// have precedence; the daily community blocklist gets what remains, which
// the dongle expresses to the server as a smaller &maxBytes
// (blocklist_sync.c).
//
// The partition holds one of two filesystems (CONFIG_PHONEBLOCK_STORAGE_LITTLEFS,
// main/Kconfig.projbuild): SPIFFS on the classic ESP32 (partitions.csv, the
// dongles in the field) and LittleFS on the ESP32-C3 (partitions-c3.csv, the
// same 640 KB) and the ESP32-S3 (partitions-16mb.csv, ~9.6 MB). Each has its
// own arithmetic below; the dialog tiers at the end cover both and the plain
// filesystem of the Linux port.
//
// --- SPIFFS ------------------------------------------------------------------
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

// --- LittleFS ----------------------------------------------------------------
//
// LittleFS (the joltwallet/littlefs component, ESP-IDF's data subtype 0x83)
// has none of the SPIFFS terms: no object index pages that go stale, no
// garbage collection by erase age — a deleted file's blocks are free again
// the moment the deletion is committed. esp_littlefs_info() reports
// `total` = blocks * block size and `used` = the blocks in use (lfs_fs_size(),
// metadata pairs included) * block size, so the budget counts whole blocks:
//
//  1. The files themselves (`used`).
//
//  2. A file's own blocks: LittleFS stores a file of more than one block as a
//     backwards CTZ skip list — block i (i >= 1) begins with ctz(i) + 1
//     pointers of 4 bytes — so n blocks hold n * B - 4 * (2(n-1) -
//     popcount(n-1)) bytes (littlefs DESIGN.md, "CTZ skip-lists").
//     storage_budget_lfs_file_blocks(). Files small enough to be inlined
//     into their directory's metadata are counted as one block anyway.
//
//  3. A reserve of blocks for what copy-on-write needs in flight
//     (STORAGE_LFS_RESERVE_BLOCKS below). The file being written goes into
//     fresh blocks (its own footprint, item 2), and a file it replaces keeps
//     its blocks until the new one is committed — storage_make_room() counts
//     that old copy, as on SPIFFS. What is left:
//       2  a metadata pair split: a commit (file created, closed, renamed,
//          removed) that no longer fits its directory's pair after
//          compaction allocates a new pair;
//       2  a metadata pair relocation: after CONFIG_LITTLEFS_BLOCK_CYCLES
//          (512) erases a pair moves to two fresh blocks, released only
//          once the move is committed — possibly in the same commit as a
//          split;
//       4  the small files written without a budget check: the personal
//          list (a few KB, re-downloaded after its old copy was removed, so
//          only its growth counts) and the dialog marker (dialog_store.c),
//          each at least one block unless inlined.
//     The community list needs nothing extra: blocklist_sync.c removes the
//     old copy before it asks for the budget, so it never has two copies.
//
// There is nothing to collect: storage_gc() is a no-op on LittleFS, as on
// the Linux port.

// See item 3: 32 KB, 5 % of the C3's partition, 0.3 % of the S3's.
#define STORAGE_LFS_RESERVE_BLOCKS 8u

typedef struct {
    // Bytes per LittleFS block: the flash erase sector (4096, fixed by
    // esp_littlefs.c).
    uint32_t block_size;
    // Blocks kept free for the write in flight.
    uint32_t reserve_blocks;
} storage_lfs_geometry_t;

// Blocks a file of `bytes` occupies on LittleFS (CTZ skip list; at least one
// block for a non-empty file).
uint64_t storage_budget_lfs_file_blocks(const storage_lfs_geometry_t *g,
                                        uint64_t bytes);

// Largest community file, in bytes, that fits next to everything on the
// partition now. `total` and `used` are esp_littlefs_info()'s numbers, taken
// after the old community file was deleted. Returns 0 if nothing fits.
uint64_t storage_budget_lfs_community(const storage_lfs_geometry_t *g,
                                      uint64_t total, uint64_t used);

// Whether a file of `bytes` can be added next to what is on the partition now
// (`total`, `used` as above) within the budget. An old copy the file will
// replace is still counted.
bool storage_budget_lfs_asset_fits(const storage_lfs_geometry_t *g,
                                   uint64_t total, uint64_t used,
                                   uint64_t bytes);

// --- The dialog clip set (#429) ----------------------------------------------
//
// The dialog's clip set is published in up to three size tiers per locale
// (scripts/audio-assets.sh, which reads the S and M limits below from this
// file): each the longest prefix of a fixed clip priority order that fits
// the tier's limit, header included. A device takes the largest tier within
// its dialog budget, which follows from its filesystem and partition:
//
//   S  SPIFFS, the classic ESP32's 640 KB partition (partitions.csv):
//      always S (storage.c).
//   M  LittleFS on the C3's 640 KB partition (partitions-c3.csv).
//   L  unlimited, everything the locale has, held to
//      STORAGE_DIALOG_PLAIN_FS_MAX: LittleFS on the S3's ~9.6 MB partition
//      (partitions-16mb.csv) and the plain filesystem of the Linux port.
//
// On LittleFS the tier is computed from the partition's size
// (storage_budget_lfs_dialog()): the largest tier whose replacement peak
// fits (storage_budget_lfs_dialog_room()).
//
// S is validated by test_storage_sim.c on its layout: 60 days next to the
// largest localized assets without a failed write, a GC error or a
// WARN/ERROR line, a community list on every day — as asked for (a 146 KB
// set already costs the community cache on some rows).
//
// M is derived, for LittleFS on the C3's 640 KB partition. Every file there
// takes whole blocks, freed again once its unlink is committed, and the set
// is replaced delete-first (i18n_sync.c sync_dialog() removes the old set
// before it downloads the new one), so a set needs room for one copy: the
// partition minus the reserve, the root metadata pair, everything else a
// C3 in dialog mode keeps (STORAGE_LFS_DIALOG_PEERS: the largest UI pack,
// the mail pack, the personal list, the speakers index), a community list
// in the server's 128 KB bucket (STORAGE_LFS_DIALOG_COMMUNITY) — and a
// second copy of the largest pack: the packs are still replaced
// old-copy-kept (download_verify_rename(), storage_make_room() counts the
// old one), and a pack update must not cost the 128 KB list either. On the
// 640 KB partition that leaves 84 blocks, 343416 bytes
// (storage_budget_lfs_dialog_room()); M is that rounded down to 16 KiB,
// 320 KiB. Next to an M set the C3's community budget is then ~200 KB, the
// 128 KB bucket, with a pack update's second copy still fitting beside it
// (test_storage_budget.c checks all of these numbers).
#define STORAGE_DIALOG_TIER_S 131072u
#define STORAGE_DIALOG_TIER_M 327680u

// The dialog budget on a filesystem without the SPIFFS limits (LittleFS, the
// Linux port's plain filesystem): no partition limits it, so this ceiling
// does — above the largest published tier (`l`, 450–530 KB) with room for a
// larger own set, and within what the Linux HTTP layer buffers
// (WEB_HTTP_MAX_RESPONSE, web_http.h; checked in web.c).
#define STORAGE_DIALOG_PLAIN_FS_MAX (2U * 1024U * 1024U)

// The community bucket an M set must leave room for (the server's buckets:
// 64, 128, 256, 384, 512 KB).
#define STORAGE_LFS_DIALOG_COMMUNITY (128u * 1024u)

// What a LittleFS partition in dialog mode holds besides the set and the
// community list, at most, in bytes: the largest UI pack (49 KB on the CDN),
// the mail pack (~2 KB), the personal list (a few KB) and the speakers index
// (DIALOG_INDEX_MAX_BYTES, dialog_manifest.h). The dialog marker is in the
// reserve. No announcement: dialog mode removes it and the own recording.
#define STORAGE_LFS_DIALOG_PEERS { 52u * 1024u, 4096u, 4096u, 8192u }

// Largest dialog set file, in bytes, that fits next to
// STORAGE_LFS_DIALOG_PEERS, a second copy of the largest of them (a pack
// update in flight) and a STORAGE_LFS_DIALOG_COMMUNITY list on a LittleFS
// partition of `total` bytes (esp_littlefs_info(); the root metadata pair
// counted as used) within the reserve. 0 if not even that fits.
uint64_t storage_budget_lfs_dialog_room(const storage_lfs_geometry_t *g,
                                        uint64_t total);

// The dialog budget of a LittleFS partition of `total` bytes: the largest
// tier — STORAGE_DIALOG_PLAIN_FS_MAX, M, S — not above
// storage_budget_lfs_dialog_room(); S if none is.
uint64_t storage_budget_lfs_dialog(const storage_lfs_geometry_t *g,
                                   uint64_t total);
