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
// The partition holds LittleFS on every board: 640 KB on the classic ESP32
// (partitions.csv, whose old SPIFFS the first boot of 1.7 reformats,
// storage.c) and on the ESP32-C3 (partitions-c3.csv), ~9.6 MB on the
// ESP32-S3 (partitions-16mb.csv). The dialog tiers at the end cover those
// and the plain filesystem of the Linux port.
#pragma once

#include <stdbool.h>
#include <stdint.h>

// --- LittleFS ----------------------------------------------------------------
//
// LittleFS (the joltwallet/littlefs component) has no object index pages
// that go stale and no garbage collection by erase age, as the classic
// ESP32's SPIFFS had up to 1.7.0-beta7 (issues #499, #529, #542) — a deleted
// file's blocks are free again the moment the deletion is committed.
// esp_littlefs_info() reports `total` = blocks * block size and `used` = the
// blocks in use (lfs_fs_size(), metadata pairs included) * block size, so the
// budget counts whole blocks:
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
//     that old copy. What is left:
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
// There is nothing to collect, so nothing in the write path waits for a
// collection: a write fails with ENOSPC only once the blocks are gone.

// See item 3: 32 KB, 5 % of the 640 KB partitions, 0.3 % of the S3's.
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
// The dialog's clip set is published in up to two size tiers per locale
// (scripts/audio-assets.sh, which reads the M limit below from this file):
// each the longest prefix of a fixed clip priority order that fits the
// tier's limit, header included. A device takes the largest tier within its
// dialog budget, which follows from its partition:
//
//   M  LittleFS on the 640 KB partitions of the classic ESP32
//      (partitions.csv) and the C3 (partitions-c3.csv).
//   L  unlimited, everything the locale has, held to
//      STORAGE_DIALOG_PLAIN_FS_MAX: LittleFS on the S3's ~9.6 MB partition
//      (partitions-16mb.csv) and the plain filesystem of the Linux port.
//
// On LittleFS the tier is computed from the partition's size
// (storage_budget_lfs_dialog()): the largest tier whose replacement peak
// fits (storage_budget_lfs_dialog_room()). (Up to 1.7.0-beta7 the classic
// ESP32 kept SPIFFS, whose stale pages and GC rotation left room for an S
// tier of 128 KiB only; no released firmware read the dialog tiers.)
//
// M is derived, for LittleFS on a 640 KB partition. Every file there
// takes whole blocks, freed again once its unlink is committed, and the set
// is replaced delete-first (i18n_sync.c sync_dialog() removes the old set
// before it downloads the new one), so a set needs room for one copy: the
// partition minus the reserve, the root metadata pair, everything else a
// dongle in dialog mode keeps (STORAGE_LFS_DIALOG_PEERS: the largest UI pack,
// the mail pack, the personal list, the speakers index), a community list
// in the server's 128 KB bucket (STORAGE_LFS_DIALOG_COMMUNITY) — and a
// second copy of the largest pack: the packs are still replaced
// old-copy-kept (download_verify_rename(), storage_make_room() counts the
// old one), and a pack update must not cost the 128 KB list either. On the
// 640 KB partition that leaves 84 blocks, 343416 bytes
// (storage_budget_lfs_dialog_room()); M is that rounded down to 16 KiB,
// 320 KiB. Next to an M set the community budget is then ~200 KB, the
// 128 KB bucket, with a pack update's second copy still fitting beside it
// (test_storage_budget.c checks all of these numbers; test_storage_sim.c
// runs 60 days of it on the real littlefs core without a failed write).
#define STORAGE_DIALOG_TIER_M 327680u

// The dialog budget where the partition does not limit it (LittleFS on the
// S3's ~9.6 MB, the Linux port's plain filesystem): this ceiling does — above the largest published tier (`l`, 450–530 KB) with room for a
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
// tier — STORAGE_DIALOG_PLAIN_FS_MAX or M — not above
// storage_budget_lfs_dialog_room(); 0 if none is (no published set fits).
uint64_t storage_budget_lfs_dialog(const storage_lfs_geometry_t *g,
                                   uint64_t total);
