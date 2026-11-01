// Host simulation of the dongle's storage partition over many days — the
// littlefs core the firmware links (managed_components/joltwallet__littlefs)
// on a RAM flash with the 640 KB partition of the 4 MB boards
// (lfs_host/lfs_sim.c), driven through the firmware's own storage.c /
// storage_budget.c.
//
//  * Mount. What storage_mount() does with the partition it finds: an erased
//    one is formatted quietly, a LittleFS is mounted as it is, and anything
//    else — the classic ESP32's SPIFFS on the first boot of 1.7 — is
//    reformatted with exactly one WARN, the line the web UI's log panel
//    shows (STORAGE_REFORMAT_WARNING).
//
//  * The daily cycle the firmware runs (i18n assets after an update,
//    community + personal blocklist every day, now and then an upload of the
//    user's own announcement, in dialog mode the clip set at the partition's
//    tier): the shared budget, the 4 KB write buffer, precedence of the
//    rarely written files (eviction of the community cache), the user's
//    recording superseding the localized announcement, the delete-first
//    replacement of the dialog set, one retry — and the call list (#589):
//    created at full size at the first boot, then a few calls a day written
//    into it in place right after the blocklist sync left the partition at
//    its fullest, and the daily mail's mark in its header. It must run 60
//    days without a single failed write or a WARN/ERROR line, including the
//    day the i18n assets are re-downloaded and the day the user uploads a
//    recording.
//
// File contents are synthetic; sizes are the CDN's real asset sizes.
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "lfs.h"
#include "lfs_sim.h"
#include "stats.h"
#include "storage.h"
#include "storage_budget.h"
#include "blocklist_sync.h"

#define KB 1024L

static int tests_run, tests_failed;

#define CHECK(cond, ...) do {                                   \
    tests_run++;                                                \
    if (cond) { printf("  ok: "); printf(__VA_ARGS__); printf("\n"); } \
    else { tests_failed++; printf("  FAIL (%s:%d): ", __FILE__, __LINE__); \
           printf(__VA_ARGS__); printf("\n"); }                 \
} while (0)

// ---------------------------------------------------------------------------
// Glue: storage.c's two outside dependencies, mapped onto the simulation.
// ---------------------------------------------------------------------------

// "/spiffs/x" (VFS path) -> "/x" (littlefs path), as esp_littlefs.c does.
static const char *obj(const char *path)
{
    return strncmp(path, "/spiffs/", 8) == 0 ? path + 7 : path;
}

int sim_vfs_unlink(const char *path)
{
    return sim_unlink(obj(path)) ? 0 : -1;
}

static int s_evictions;

bool blocklist_sync_evict_community(void)
{
    if (!sim_unlink("/community.bin")) return false;
    s_evictions++;
    return true;
}

// ---------------------------------------------------------------------------
// One simulated device.
// ---------------------------------------------------------------------------

typedef struct {
    long custom;      // user's own recording, 0 = none
    long ann;         // localized announcement (CDN sizes: 72-97 KB)
    long ui;          // UI pack (27-49 KB)
    long list;        // community list the server would send unbounded
    bool upload;      // the user records a new announcement on UPLOAD_DAY
    bool dialog;      // ab_mode=dialog: the German dialog clip set (#429)
} device_t;

// The dialog clip set (#429): one file, clip table in its header, at the
// largest size the partition's tier admits (storage_dialog_budget()) — the
// set i18n_sync.c picks is never larger. Plus the marker naming the complete
// set's locale ("de\n").
#define DIALOG_INDEX 3L

static long dialog_bytes(void)
{
    return (long) storage_dialog_budget();
}

// The community bucket a large list gets.
#define SIM_BUCKET  (128 * KB)

typedef struct {
    int  write_failures;     // writes littlefs refused (LFS_ERR_NOSPC etc.)
    int  refusals;           // community deliberately not downloaded (no room)
    long community_bytes;    // community list on flash at the end
    int  community_days;     // days the community list was on flash
    bool dialog_present;     // dialog set on flash at the end (#429)
} outcome_t;

static outcome_t s_out;

// The server snaps &maxBytes down to 64/128/256/384/512 KB — never below the
// smallest bucket (BlocklistServlet.MAX_BYTES_OPTIONS).
static long server_list_size(long list, long max_bytes)
{
    static const long buckets[] = { 65536, 131072, 262144, 393216, 524288 };
    long b = buckets[0];
    for (size_t i = 0; i < sizeof(buckets) / sizeof(buckets[0]); i++) {
        if (buckets[i] <= max_bytes) b = buckets[i];
    }
    return list < b ? list : b;
}

typedef struct {
    const char *tmp;
    const char *final_vfs;   // NULL: no precedence (blocklist files)
    long        size;
} attempt_t;

static storage_dl_t sim_attempt(void *ctx, char *err, size_t cap)
{
    const attempt_t *a = ctx;
    if (a->final_vfs) storage_make_room((size_t) a->size, a->final_vfs);
    size_t w;
    if (sim_write_file(a->tmp, (size_t) a->size, STORAGE_WRITE_BUF, &w)) {
        return STORAGE_DL_OK;
    }
    int e = sim_last_err();
    snprintf(err, cap, "fwrite short at %zu: littlefs %d", w, e);
    sim_unlink(a->tmp);
    return e == LFS_ERR_NOSPC ? STORAGE_DL_FULL : STORAGE_DL_FAILED;
}

// Download into "<path>.tmp", then replace `path` (blocklist_sync.c /
// i18n_sync.c: storage_download + rename, which replaces atomically).
static bool new_download(const char *path, const char *final_vfs, long size)
{
    char tmp[48];
    snprintf(tmp, sizeof(tmp), "%s.tmp", path);
    sim_unlink(tmp);
    attempt_t a = { tmp, final_vfs, size };
    char err[64];
    if (!storage_download(tmp, sim_attempt, &a, err, sizeof(err))) {
        s_out.write_failures++;
        printf("    write failed: %s (%s)\n", tmp, err);
        return false;
    }
    if (!sim_rename(tmp, path)) {
        s_out.write_failures++;
        printf("    rename failed: %s\n", tmp);
        return false;
    }
    return true;
}

// i18n_sync.c sync_dialog(): the set's one file through storage_download
// with the precedence of every rarely written asset, the marker after it. A
// changed release (`delta`: a few clips shorter, so still within the tier)
// replaces both, the old set file removed before the download.
static long s_dialog_have = -1;   // size of the set on flash, -1: none

static void new_dialog(long delta)
{
    long bytes = dialog_bytes() - delta / 8;
    if (s_dialog_have == bytes) return;              // up to date
    sim_unlink("/dialog.idx");                       // the set changed
    sim_unlink("/dialog-de.alaw");                   // delete-first (i18n_sync.c)
    s_dialog_have = -1;
    if (!new_download("/dialog-de.alaw", "/spiffs/dialog-de.alaw", bytes)) return;
    size_t w;
    if (!sim_write_file("/dialog.idx", DIALOG_INDEX, STORAGE_WRITE_BUF, &w)) {
        sim_unlink("/dialog.idx");
        s_out.write_failures++;
        return;
    }
    s_dialog_have = bytes;
}

static void new_i18n(const device_t *d, long delta)
{
    bool custom = sim_file_size("/announcement.alaw") > 0;
    // An own recording supersedes the localized announcement, and so does the
    // dialog (#429), which never plays it (and whose switch deleted the own
    // recording, web.c).
    if (custom || d->dialog) sim_unlink("/announcement-de.alaw");
    new_download("/ui-de.json", "/spiffs/ui-de.json", d->ui + delta);
    new_download("/mail-de.json", "/spiffs/mail-de.json", 2 * KB + delta / 8);
    if (!custom && !d->dialog) {
        new_download("/announcement-de.alaw", "/spiffs/announcement-de.alaw",
                     d->ann + delta);
    }
    if (d->dialog) new_dialog(delta);
}

static void new_blocklist(const device_t *d)
{
    // sync_one(): old file first, then the budget of what is left.
    sim_unlink("/community.bin");
    int64_t budget = storage_community_budget();
    long content = budget > 0 ? server_list_size(d->list, (long) budget) : 0;
    if (budget <= 0 || content > budget) {
        s_out.refusals++;
    } else {
        new_download("/community.bin", NULL, content);
    }
    sim_unlink("/personal.bin");
    new_download("/personal.bin", NULL, 2 * KB);
    if (sim_file_size("/community.bin") > 0) s_out.community_days++;
}

static void new_upload(long size)
{
    // announcement_write_begin(): drop the localized one, claim the room,
    // then write the live file in place through its 8 KB buffer.
    sim_unlink("/announcement-de.alaw");
    storage_make_room((size_t) size, "/spiffs/announcement.alaw");
    size_t w;
    if (!sim_write_file("/announcement.alaw", (size_t) size, 8192, &w)) {
        sim_unlink("/announcement.alaw");
        s_out.write_failures++;
    }
}

// --- The call list (#589) ------------------------------------------------------

// The 4 MB boards' call list (stats.h), as stats.c writes it: created record
// by record at full size, each call one record written in place, a spam
// call's duration a second write into the same record, the mail's mark the
// header.
#define CALLS_CAP   STATS_CALL_CAPACITY_SMALL
#define CALLS_BYTES STATS_CALLS_FILE_BYTES(CALLS_CAP)
#define CALLS_PER_DAY 5

static unsigned s_call_seq;

static void calls_create(void)
{
    size_t w;
    if (!sim_write_file("/calls.bin", CALLS_BYTES, STATS_CALL_RECORD_BYTES, &w)) {
        s_out.write_failures++;
        printf("    call list not created (littlefs %d)\n", sim_last_err());
    }
    s_call_seq = 0;
}

static void calls_write(size_t offset, size_t size)
{
    if (!sim_update_file("/calls.bin", offset, size)) {
        s_out.write_failures++;
        printf("    call list write at %zu failed (littlefs %d)\n", offset, sim_last_err());
    }
}

static void calls_day(void)
{
    for (int i = 0; i < CALLS_PER_DAY; i++) {
        unsigned seq = ++s_call_seq;
        size_t at = STATS_CALL_HEADER_BYTES
                  + (seq % CALLS_CAP) * (size_t) STATS_CALL_RECORD_BYTES;
        calls_write(at, STATS_CALL_RECORD_BYTES);
        if (i % 2 == 0) calls_write(at, STATS_CALL_RECORD_BYTES);   // duration
    }
    calls_write(0, STATS_CALL_HEADER_BYTES);                      // mail mark
}

// --- The cycle ---------------------------------------------------------------

#define DAYS        60
#define I18N_DAY    30    // a release with changed assets: all re-downloaded
#define UPLOAD_DAY  20    // the user records a new announcement

static outcome_t run(const device_t *d)
{
    memset(&s_out, 0, sizeof(s_out));
    s_evictions = 0;
    s_dialog_have = -1;
    sim_init();

    size_t w;
    if (d->custom) sim_write_file("/announcement.alaw", (size_t) d->custom, 8192, &w);
    // A device switched to dialog mode: the switch deleted the own
    // recording (web.c), whatever was there before.
    if (d->dialog) sim_unlink("/announcement.alaw");
    // The first boot of 1.7 creates the call list, before any sync.
    calls_create();
    sim_log_reset();

    for (int day = 0; day < DAYS; day++) {
        if (day == 0 || day == I18N_DAY) {
            // After an update the i18n sync runs first (30 s after boot),
            // the blocklist two minutes later.
            new_i18n(d, day ? 1500 : 0);
        }
        // In dialog mode there is no own recording: the switch deleted it,
        // and the upload API refuses (web.c).
        if (day == UPLOAD_DAY && d->upload && !d->dialog) {
            long size = d->custom ? d->custom + 20 * KB : 120 * KB;
            if (size > 240 * KB) size = 240 * KB;
            new_upload(size);
        }
        new_blocklist(d);
        // The calls of the day, at the partition's fullest: the blocklist
        // sync just took what the budget left.
        calls_day();
    }
    if (sim_file_size("/calls.bin") != CALLS_BYTES) {
        s_out.write_failures++;
        printf("    call list has %ld bytes\n", sim_file_size("/calls.bin"));
    }
    s_out.community_bytes = sim_file_size("/community.bin");
    s_out.dialog_present = sim_file_size("/dialog-de.alaw") == s_dialog_have
                           && s_dialog_have > 0 && sim_file_size("/dialog.idx") > 0;
    return s_out;
}

static const char *describe(const device_t *d)
{
    static char buf[192];
    char set[32] = "";
    if (d->dialog) snprintf(set, sizeof(set), ", dialog %ld KB", dialog_bytes() / KB);
    snprintf(buf, sizeof(buf), "own %3ld KB, ann %2ld KB, ui %2ld KB, list %4ld KB%s",
             d->custom / KB, d->ann / KB, d->ui / KB, d->list / KB, set);
    return buf;
}

// ---------------------------------------------------------------------------
// Tests.
// ---------------------------------------------------------------------------

static void test_geometry(void)
{
    printf("geometry\n");
    sim_init();
    size_t t, u;
    CHECK(storage_info(&t, &u) && t == (size_t) SIM_PART_SIZE && u == 2 * SIM_BLOCK_SIZE,
          "%d KB partition reports %zu B total, %zu used (the root pair)",
          SIM_PART_SIZE / 1024, t, u);
    // The 640 KB partition takes the M tier (storage_budget.h).
    CHECK((long) storage_dialog_budget() == (long) STORAGE_DIALOG_TIER_M,
          "dialog budget %ld KB", (long) storage_dialog_budget() / KB);
    // Not mounted: the tier of the 640 KB partitions as well.
    sim_unmount();
    CHECK((long) storage_dialog_budget() == (long) STORAGE_DIALOG_TIER_M
          && storage_community_budget() == -1,
          "not mounted: dialog budget M, no community budget");
}

// Fills the partition's first blocks the way an older firmware's SPIFFS does:
// every block starts with its object lookup page (one 16-bit object id per
// page of the block, the index-header flag 0x8000 on the first), followed by
// pages of file data.
static void write_spiffs_like(int blocks)
{
    uint8_t page[1024];
    for (int b = 0; b < blocks; b++) {
        memset(page, 0xFF, sizeof(page));
        uint16_t ids[4] = { 0x8000 | (uint16_t) (b + 1), (uint16_t) (b + 1),
                            (uint16_t) (b + 1), (uint16_t) (b + 1) };
        memcpy(page, ids, sizeof(ids));
        sim_write_raw((uint32_t) b * SIM_BLOCK_SIZE, page, sizeof(page));
        for (int p = 1; p < 4; p++) {
            for (size_t i = 0; i < sizeof(page); i++) page[i] = (uint8_t) (i * 7 + b + p);
            sim_write_raw((uint32_t) b * SIM_BLOCK_SIZE + (uint32_t) p * 1024, page, sizeof(page));
        }
    }
}

static void test_mount(void)
{
    printf("storage_mount\n");

    // A factory-fresh partition (USB install): formatted quietly.
    sim_erase();
    sim_log_reset();
    bool ok = storage_mount();
    CHECK(ok && sim_mounted() && sim_format_count() == 1 && sim_log_count('W') == 0
          && sim_log_count('E') == 0 && strstr(sim_last_log('I'), "LittleFS mounted"),
          "erased partition: formatted once, mounted, nothing above INFO");

    // A LittleFS with files (every later boot): mounted as it is.
    size_t w;
    sim_write_file("/announcement.alaw", 80 * KB, 8192, &w);
    sim_write_file("/community.bin", 40 * KB, STORAGE_WRITE_BUF, &w);
    sim_unmount();
    sim_log_reset();
    ok = storage_mount();
    CHECK(ok && sim_mounted() && sim_format_count() == 1 && sim_log_count('W') == 0
          && sim_log_count('E') == 0 && sim_file_size("/announcement.alaw") == 80 * KB
          && sim_file_size("/community.bin") == 40 * KB,
          "LittleFS: mounted without a format, the files kept");

    // The classic ESP32's SPIFFS on the first boot of 1.7: reformatted with
    // exactly one WARN, which says what the user has to do.
    sim_erase();
    write_spiffs_like(40);
    sim_log_reset();
    ok = storage_mount();
    const char *warn = sim_last_log('W');
    CHECK(ok && sim_mounted() && sim_format_count() == 1 && sim_log_count('W') == 1
          && sim_log_count('E') == 0
          && strcmp(warn, STORAGE_REFORMAT_WARNING) == 0,
          "old SPIFFS: formatted once, mounted, one WARN: \"%s\"", warn);
    CHECK(strlen(STORAGE_REFORMAT_WARNING) < STATS_ERROR_MSG_LEN
          && strstr(STORAGE_REFORMAT_WARNING, "announcement") != NULL,
          "the WARN names the own announcement and fits the log panel (%zu of %d)",
          strlen(STORAGE_REFORMAT_WARNING), STATS_ERROR_MSG_LEN - 1);
    size_t t, u;
    CHECK(storage_info(&t, &u) && u == 2 * SIM_BLOCK_SIZE
          && storage_community_budget() == (int64_t) storage_budget_lfs_community(
                 &(storage_lfs_geometry_t){ SIM_BLOCK_SIZE, STORAGE_LFS_RESERVE_BLOCKS },
                 t, 2 * SIM_BLOCK_SIZE),
          "after the reformat the whole partition is free (%zu B used)", u);

    // The next boot: a LittleFS now, no second format, no second WARN.
    sim_write_file("/community.bin", 64 * KB, STORAGE_WRITE_BUF, &w);
    sim_unmount();
    sim_log_reset();
    ok = storage_mount();
    CHECK(ok && sim_format_count() == 1 && sim_log_count('W') == 0
          && sim_log_count('E') == 0 && sim_file_size("/community.bin") == 64 * KB,
          "second boot: mounted as it is, no WARN, the new files kept");

    // A SPIFFS that never held a file (lookup pages all 0xFF) has nothing to
    // lose: the quiet path.
    sim_erase();
    uint8_t magic[2] = { 0x12, 0x34 };   // SPIFFS' magic sits at the end of the lookup page
    sim_write_raw(1022, magic, sizeof(magic));
    sim_log_reset();
    ok = storage_mount();
    CHECK(ok && sim_format_count() == 1 && sim_log_count('W') == 0 && sim_log_count('E') == 0,
          "empty SPIFFS: formatted without a WARN");

    // Mounted state for the tests below.
    sim_init();
}

static void test_policy(void)
{
    printf("daily cycle: %d days without a failed write\n", DAYS);
    static const device_t devices[] = {
        // custom    ann       ui       list        upload dialog
        {   0,       79 * KB,  29 * KB,   40 * KB,  true,  false }, // default
        {   0,       79 * KB,  29 * KB, 1000 * KB,  false, false }, // #529
        {   0,       79 * KB,  29 * KB, 1000 * KB,  true,  false },
        {   0,       96 * KB,  49 * KB, 1000 * KB,  true,  false }, // largest assets
        {  60 * KB,  73 * KB,  27 * KB, 1000 * KB,  true,  false },
        { 120 * KB,  96 * KB,  49 * KB,   40 * KB,  true,  false },
        { 175 * KB,  79 * KB,  29 * KB,   40 * KB,  false, false }, // #542
        { 175 * KB,  79 * KB,  29 * KB, 1000 * KB,  true,  false },
        { 220 * KB,  96 * KB,  49 * KB, 1000 * KB,  true,  false }, // max recording
    };
    for (size_t i = 0; i < sizeof(devices) / sizeof(devices[0]); i++) {
        const device_t *d = &devices[i];
        outcome_t o = run(d);
        CHECK(o.write_failures == 0 && sim_log_count('W') == 0 && sim_log_count('E') == 0,
              "%s: %d failed writes, community %ld KB, %d days without list, "
              "%d evictions", describe(d), o.write_failures,
              o.community_bytes > 0 ? o.community_bytes / KB : 0,
              o.refusals, s_evictions);
    }

    // Dialog mode (#429): the M set in place of the localized announcement
    // and of any own recording (the switch deletes it; `upload` is refused).
    // Every row clean, the set kept, and the community cache gets a list on
    // every day — at least the 128 KB bucket where the server has one.
    printf("daily cycle, dialog mode: %d days\n", DAYS);
    static const device_t dialog_rows[] = {
        // custom ann       ui       list        upload dialog
        {  0,    79 * KB,  29 * KB,   40 * KB,  false, true }, // default list
        {  0,    79 * KB,  29 * KB,   40 * KB,  true,  true }, // upload refused
        {  0,    79 * KB,  29 * KB, 1000 * KB,  true,  true }, // #529
        {  0,    96 * KB,  49 * KB, 1000 * KB,  true,  true }, // largest assets
        // Switched from announcement mode with a maximal own recording: the
        // recording goes with the switch.
        { 220 * KB, 96 * KB, 49 * KB, 1000 * KB, true,  true },
        { 175 * KB, 79 * KB, 29 * KB,   40 * KB, false, true }, // #542
    };
    for (size_t i = 0; i < sizeof(dialog_rows) / sizeof(dialog_rows[0]); i++) {
        const device_t *d = &dialog_rows[i];
        outcome_t o = run(d);
        long want = d->list < SIM_BUCKET ? d->list : SIM_BUCKET;
        CHECK(o.write_failures == 0 && sim_log_count('W') == 0
              && sim_log_count('E') == 0 && o.dialog_present
              && sim_file_size("/announcement-de.alaw") < 0
              && sim_file_size("/announcement.alaw") < 0
              && o.refusals == 0 && o.community_days == DAYS
              && o.community_bytes >= want,
              "%s: %d failed writes, set %s, community %ld KB "
              "(on %d of %d days), %d evictions",
              describe(d), o.write_failures,
              o.dialog_present ? "kept" : "ABSENT",
              o.community_bytes > 0 ? o.community_bytes / KB : 0,
              o.community_days, DAYS, s_evictions);
    }

    // Without an own recording the community cache must not be starved: a
    // default list is kept every day, a large one gets a real budget.
    device_t plain = { 0, 79 * KB, 29 * KB, 40 * KB, false, false };
    outcome_t o = run(&plain);
    CHECK(o.refusals == 0 && o.community_bytes == 40 * KB,
          "default list cached every day (%d days without)", o.refusals);
    device_t big = { 0, 79 * KB, 29 * KB, 1000 * KB, false, false };
    o = run(&big);
    CHECK(o.refusals == 0 && o.community_bytes >= SIM_BUCKET,
          "large list gets at least the %ld KB bucket (%ld KB)", SIM_BUCKET / KB,
          o.community_bytes / KB);
}

static void test_self_healing(void)
{
    printf("an oversized list is replaced by one within the budget\n");
    // A community list larger than the budget allows (left by a firmware
    // with another budget). The sync deletes it before computing the
    // budget, so the very first list it asks for already fits.
    device_t d = { 0, 79 * KB, 29 * KB, 1000 * KB, false, false };
    memset(&s_out, 0, sizeof(s_out));
    sim_init();
    new_i18n(&d, 0);
    long want = server_list_size(d.list, (long) storage_community_budget());
    size_t w;
    bool wrote = sim_write_file("/community.bin", 480 * KB, STORAGE_WRITE_BUF, &w);
    long before = sim_file_size("/community.bin");
    sim_log_reset();
    new_blocklist(&d);
    long after = sim_file_size("/community.bin");
    CHECK(wrote && before == 480 * KB && after == want
          && s_out.write_failures == 0 && sim_log_count('W') == 0,
          "%ld KB list -> %ld KB on the first sync", before / KB, after / KB);
}

// The replacement of a set is delete-first (i18n_sync.c): a new M set fits
// where the old one was, next to the packs and the list — and the budget says
// so before the write, without an eviction.
static void test_dialog_replacement(void)
{
    printf("dialog set replaced delete-first\n");
    device_t d = { 0, 96 * KB, 49 * KB, 1000 * KB, false, true };
    memset(&s_out, 0, sizeof(s_out));
    s_evictions = 0;
    s_dialog_have = -1;
    sim_init();
    calls_create();
    sim_log_reset();
    new_i18n(&d, 0);
    new_blocklist(&d);
    long list = sim_file_size("/community.bin");
    int evictions = s_evictions;
    for (int release = 1; release <= 5; release++) {
        new_dialog(release * 800);
        calls_day();
        new_blocklist(&d);
        calls_day();
    }
    CHECK(s_out.write_failures == 0 && sim_log_count('W') == 0 && sim_log_count('E') == 0
          && s_dialog_have > 0 && s_evictions == evictions
          && sim_file_size("/community.bin") == list,
          "5 releases of the set: no failed write, no eviction, list stays %ld KB",
          list / KB);
}

// rename() onto an existing file replaces it in one commit (i18n_sync.c
// relies on it): the new content in place, the old copy's blocks free again.
static void test_replace(void)
{
    printf("rename replaces\n");
    sim_init();
    size_t w;
    sim_write_file("/ui-de.json", 49 * KB, STORAGE_WRITE_BUF, &w);
    size_t t, before, during, after;
    storage_info(&t, &before);
    sim_write_file("/ui-de.json.tmp", 30 * KB, STORAGE_WRITE_BUF, &w);
    storage_info(&t, &during);
    bool ok = sim_rename("/ui-de.json.tmp", "/ui-de.json");
    storage_info(&t, &after);
    CHECK(ok && sim_file_size("/ui-de.json") == 30 * KB
          && sim_file_size("/ui-de.json.tmp") < 0 && during > before && after < during
          && after == before - (storage_budget_lfs_file_blocks(
                 &(storage_lfs_geometry_t){ SIM_BLOCK_SIZE, 0 }, 49 * KB)
             - storage_budget_lfs_file_blocks(
                 &(storage_lfs_geometry_t){ SIM_BLOCK_SIZE, 0 }, 30 * KB)) * SIM_BLOCK_SIZE,
          "49 KB replaced by 30 KB: %zu -> %zu -> %zu B used", before, during, after);
}

// storage_download(): exactly one retry, only for out-of-space.
static int s_calls;
static storage_dl_t s_script[2];

static storage_dl_t scripted(void *ctx, char *err, size_t cap)
{
    (void) ctx;
    storage_dl_t r = s_script[s_calls++];
    if (r != STORAGE_DL_OK) snprintf(err, cap, "attempt %d", s_calls);
    return r;
}

static bool script(storage_dl_t first, storage_dl_t second)
{
    s_calls = 0;
    s_script[0] = first;
    s_script[1] = second;
    char err[32] = "";
    sim_log_reset();
    return storage_download("test", scripted, NULL, err, sizeof(err));
}

static void test_storage_download(void)
{
    printf("storage_download\n");
    sim_init();
    CHECK(script(STORAGE_DL_OK, STORAGE_DL_FAILED) && s_calls == 1,
          "success: one attempt");
    CHECK(!script(STORAGE_DL_FAILED, STORAGE_DL_OK) && s_calls == 1,
          "other failure: no retry");
    CHECK(script(STORAGE_DL_FULL, STORAGE_DL_OK) && s_calls == 2,
          "out of space: retried once, succeeds");
    CHECK(!script(STORAGE_DL_FULL, STORAGE_DL_FULL) && s_calls == 2,
          "out of space twice: gives up after one retry");
    CHECK(sim_log_count('W') == 0 && sim_log_count('E') == 0,
          "nothing above INFO — the caller's one WARN is the only line");
}

int main(void)
{
    test_geometry();
    test_mount();
    test_storage_download();
    test_replace();
    test_policy();
    test_self_healing();
    test_dialog_replacement();

    printf("\n%d checks, %d failed\n", tests_run, tests_failed);
    return tests_failed ? 1 : 0;
}
