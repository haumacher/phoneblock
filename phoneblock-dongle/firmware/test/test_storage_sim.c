// Host simulation of the dongle's storage partition over many days — the
// unmodified ESP-IDF SPIFFS core on a RAM flash with the dongle's geometry
// (spiffs_host/spiffs_sim.c), driven through the firmware's own storage.c /
// storage_budget.c.
//
// Two policies run the same daily cycle the firmware runs (i18n assets after
// an update, community + personal blocklist every day, now and then an
// upload of the user's own announcement):
//
//  * OLD — the 1.6.0 / 1.7.0-beta3 code, re-enacted: community budget =
//    free - 48 KB, asset downloads through 1 KB writes behind a 48 KB margin
//    check, no eviction. It must reproduce the field failures: SPIFFS_ERR_FULL
//    on writes while a large part of the partition is reported free, and the
//    "SPIFFS_gc failed" ERROR lines (issues #529, #542).
//
//  * NEW — storage.c: the shared budget, the 4 KB write buffer, precedence of
//    the rarely written files (eviction of the community cache), the user's
//    recording superseding the localized announcement, one GC + retry. It
//    must run 60 days without a single failed write, including the day the
//    i18n assets are re-downloaded and the day the user uploads a recording.
//
// File contents are synthetic; sizes are the CDN's real asset sizes.
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "esp_spiffs.h"
#include "spiffs.h"
#include "spiffs_sim.h"
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

// "/spiffs/x" (VFS path) -> "/x" (SPIFFS object name), as esp_spiffs.c does.
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
    bool legacy;      // flash state left behind by 1.6.0 / 1.7.0-beta3
    bool upload;      // the user records a new announcement on UPLOAD_DAY
} device_t;

typedef struct {
    int  write_failures;     // writes SPIFFS refused (SPIFFS_ERR_FULL etc.)
    int  full_errors;        // ... of which SPIFFS_ERR_FULL
    int  refusals;           // community deliberately not downloaded (no room)
    int  gc_errors;          // "SPIFFS_gc failed" ERROR lines
    int  min_free_pct_at_full; // lowest reported free share after an ERR_FULL
    long community_bytes;    // community list on flash at the end
} outcome_t;

static outcome_t s_out;

static size_t fs_free(void)
{
    size_t t, u;
    esp_spiffs_info("storage", &t, &u);
    return t - u;
}

// Call after the partial file was removed: the free share is then what the
// dongle's status page reports next to the error (#542: 188 of 472 KB).
static void note_write_failure(int err)
{
    s_out.write_failures++;
    if (err == SPIFFS_ERR_FULL) {
        size_t t, u;
        esp_spiffs_info("storage", &t, &u);
        int pct = (int) ((t - u) * 100 / t);
        if (s_out.full_errors == 0 || pct < s_out.min_free_pct_at_full) {
            s_out.min_free_pct_at_full = pct;
        }
        s_out.full_errors++;
    }
}

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

// --- NEW policy: what the firmware does now --------------------------------

typedef struct {
    const char *tmp;
    const char *final_vfs;   // NULL: no precedence (blocklist files)
    long        size;
    bool        gc_first;
} attempt_t;

static storage_dl_t sim_attempt(void *ctx, size_t *bytes, char *err, size_t cap)
{
    const attempt_t *a = ctx;
    if (a->final_vfs) storage_make_room((size_t) a->size, a->final_vfs);
    if (a->gc_first)  storage_gc(a->tmp, (size_t) a->size);
    size_t w;
    if (sim_write_file(a->tmp, (size_t) a->size, STORAGE_WRITE_BUF, &w)) {
        return STORAGE_DL_OK;
    }
    snprintf(err, cap, "fwrite short at %zu: SPIFFS %d", w, sim_last_err());
    int e = sim_last_err();
    sim_unlink(a->tmp);
    *bytes = (size_t) a->size;
    return e == SPIFFS_ERR_FULL ? STORAGE_DL_FULL : STORAGE_DL_FAILED;
}

// Download into "<path>.tmp", then replace `path` (blocklist_sync.c /
// i18n_sync.c: storage_download + unlink + rename).
static bool new_download(const char *path, const char *final_vfs, long size)
{
    char tmp[48];
    snprintf(tmp, sizeof(tmp), "%s.tmp", path);
    sim_unlink(tmp);
    attempt_t a = { tmp, final_vfs, size, true };
    char err[64];
    if (!storage_download(tmp, sim_attempt, &a, err, sizeof(err))) {
        note_write_failure(sim_last_err());
        return false;
    }
    sim_unlink(path);
    sim_rename(tmp, path);
    return true;
}

static void new_i18n(const device_t *d, long delta)
{
    bool custom = sim_file_size("/announcement.alaw") > 0;
    if (custom) sim_unlink("/announcement-de.alaw");   // recording supersedes
    new_download("/ui-de.json", "/spiffs/ui-de.json", d->ui + delta);
    new_download("/mail-de.json", "/spiffs/mail-de.json", 2 * KB + delta / 8);
    if (!custom) {
        new_download("/announcement-de.alaw", "/spiffs/announcement-de.alaw",
                     d->ann + delta);
    }
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
}

static void new_upload(long size)
{
    // announcement_write_begin(): drop the localized one, claim the room,
    // then write the live file in place through its 8 KB buffer.
    sim_unlink("/announcement-de.alaw");
    storage_make_room((size_t) size, "/spiffs/announcement.alaw");
    size_t w;
    if (!sim_write_file("/announcement.alaw", (size_t) size, 8192, &w)) {
        int e = sim_last_err();
        sim_unlink("/announcement.alaw");
        note_write_failure(e);
    }
}

// --- OLD policy: 1.6.0 / 1.7.0-beta3, re-enacted ----------------------------

#define OLD_MARGIN (48 * KB)

static bool old_asset(const char *path, long size)
{
    char tmp[48];
    snprintf(tmp, sizeof(tmp), "%s.tmp", path);
    if ((size_t) (size + OLD_MARGIN) > fs_free()) {
        return false;                          // "no space" pre-check
    }
    size_t w;
    if (!sim_write_file(tmp, (size_t) size, 1024, &w)) {   // no stdio buffer
        int e = sim_last_err();                // no GC, no retry
        sim_unlink(tmp);
        note_write_failure(e);
        return false;
    }
    sim_unlink(path);
    sim_rename(tmp, path);
    return true;
}

static void old_i18n(const device_t *d, long delta)
{
    old_asset("/ui-de.json", d->ui + delta);
    old_asset("/mail-de.json", 2 * KB + delta / 8);
    old_asset("/announcement-de.alaw", d->ann + delta);
}

static bool old_list(const char *path, long size)
{
    char tmp[48];
    snprintf(tmp, sizeof(tmp), "%s.tmp", path);
    sim_unlink(tmp);
    for (int attempt = 0; attempt < 2; attempt++) {
        if ((size_t) (size + OLD_MARGIN) > fs_free()) return false;
        esp_spiffs_gc("storage", (size_t) (size + OLD_MARGIN));  // force_gc
        size_t w;
        if (sim_write_file(tmp, (size_t) size, 4096, &w)) {
            sim_rename(tmp, path);
            return true;
        }
        int e = sim_last_err();
        sim_unlink(tmp);
        note_write_failure(e);
        if (e != SPIFFS_ERR_FULL) return false;
        esp_spiffs_gc("storage", (size_t) (size + OLD_MARGIN));  // retry gc
    }
    return false;
}

static void old_blocklist(const device_t *d)
{
    sim_unlink("/community.bin");
    long budget = (long) fs_free() - OLD_MARGIN;
    if (budget < 0) budget = 0;
    old_list("/community.bin", server_list_size(d->list, budget));
    sim_unlink("/personal.bin");
    old_list("/personal.bin", 2 * KB);
}

static void old_upload(long size)
{
    size_t w;
    if (!sim_write_file("/announcement.alaw", (size_t) size, 8192, &w)) {
        int e = sim_last_err();
        sim_unlink("/announcement.alaw");
        note_write_failure(e);
    }
}

// --- The cycle ---------------------------------------------------------------

#define DAYS        60
#define I18N_DAY    30    // a release with changed assets: all re-downloaded
#define UPLOAD_DAY  20    // the user records a new announcement

typedef enum { POLICY_OLD, POLICY_NEW } policy_t;

static outcome_t run(const device_t *d, policy_t p)
{
    memset(&s_out, 0, sizeof(s_out));
    s_evictions = 0;
    sim_init();
    sim_log_reset();

    size_t w;
    if (d->custom) sim_write_file("/announcement.alaw", (size_t) d->custom, 8192, &w);
    if (d->legacy) {
        // As 1.6.0 left it: assets through 1 KB writes, community list at
        // free - 48 KB.
        old_i18n(d, 0);
        old_blocklist(d);
        memset(&s_out, 0, sizeof(s_out));
    }
    int gc_errors_before = sim_gc_error_count();
    sim_log_reset();

    for (int day = 0; day < DAYS; day++) {
        if (day == 0 || day == I18N_DAY) {
            // After an update the i18n sync runs first (30 s after boot),
            // the blocklist two minutes later.
            long delta = day ? 1500 : 0;
            if (p == POLICY_NEW) new_i18n(d, delta); else old_i18n(d, delta);
        }
        if (day == UPLOAD_DAY && d->upload) {
            long size = d->custom ? d->custom + 20 * KB : 120 * KB;
            if (size > 240 * KB) size = 240 * KB;
            if (p == POLICY_NEW) new_upload(size); else old_upload(size);
        }
        if (p == POLICY_NEW) new_blocklist(d); else old_blocklist(d);
    }
    s_out.gc_errors = sim_gc_error_count() - gc_errors_before;
    s_out.community_bytes = sim_file_size("/community.bin");
    return s_out;
}

static const char *describe(const device_t *d)
{
    static char buf[128];
    snprintf(buf, sizeof(buf), "own %3ld KB, ann %2ld KB, ui %2ld KB, list %4ld KB%s",
             d->custom / KB, d->ann / KB, d->ui / KB, d->list / KB,
             d->legacy ? ", 1.6.0 flash" : "");
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
    esp_spiffs_info("storage", &t, &u);
    // (160 blocks - 2 spare) * 3 data pages + 1 emergency page, 1019 B each:
    // the units storage_budget.c computes in.
    CHECK(t == 484025 && u == 0, "partition reports %zu B total, %zu used", t, u);
}

static void test_storage_download(void);

static void test_old_reproduces_field_failures(void)
{
    printf("old policy (1.7.0-beta3) reproduces the field failures\n");

    // #529: plain dongle, a user with low thresholds (list larger than any
    // budget). The list fills the partition up to 48 KB below full every day.
    device_t d529 = { 0, 79 * KB, 29 * KB, 1000 * KB, false, false };
    outcome_t o = run(&d529, POLICY_OLD);
    printf("    %s: %d failed writes (%d SPIFFS_ERR_FULL, at >= %d %% free), "
           "%d \"SPIFFS_gc failed\"\n", describe(&d529), o.write_failures,
           o.full_errors, o.min_free_pct_at_full, o.gc_errors);
    CHECK(o.full_errors > 0, "#529: SPIFFS_ERR_FULL on writes");
    CHECK(o.gc_errors > 0, "#529: \"SPIFFS_gc failed\" logged");

    // #542: own recording + localized announcement + the default 40 KB list.
    // The budget is not even binding — the write fails anyway.
    device_t d542 = { 175 * KB, 79 * KB, 29 * KB, 40 * KB, true, false };
    o = run(&d542, POLICY_OLD);
    printf("    %s: %d failed writes (%d SPIFFS_ERR_FULL, at >= %d %% free), "
           "%d \"SPIFFS_gc failed\"\n", describe(&d542), o.write_failures,
           o.full_errors, o.min_free_pct_at_full, o.gc_errors);
    CHECK(o.full_errors >= DAYS / 2, "#542: community write fails on most days");
    CHECK(o.min_free_pct_at_full >= 25,
          "#542: ERR_FULL while >= 25 %% is reported free (lowest %d %%)",
          o.min_free_pct_at_full);
    CHECK(o.gc_errors > 0, "#542: \"SPIFFS_gc failed\" logged");
}

static void test_new_policy(void)
{
    printf("new policy: %d days without a failed write\n", DAYS);
    static const device_t devices[] = {
        // custom    ann       ui       list        legacy upload
        {   0,       79 * KB,  29 * KB,   40 * KB,  false, true  }, // default
        {   0,       79 * KB,  29 * KB, 1000 * KB,  false, false }, // #529
        {   0,       79 * KB,  29 * KB, 1000 * KB,  false, true  },
        {   0,       96 * KB,  49 * KB, 1000 * KB,  false, true  }, // largest assets
        {   0,       79 * KB,  29 * KB, 1000 * KB,  true,  true  }, // #529, after OTA
        {  60 * KB,  73 * KB,  27 * KB, 1000 * KB,  false, true  },
        { 120 * KB,  96 * KB,  49 * KB,   40 * KB,  false, true  },
        { 175 * KB,  79 * KB,  29 * KB,   40 * KB,  true,  false }, // #542
        { 175 * KB,  79 * KB,  29 * KB, 1000 * KB,  true,  true  },
        { 220 * KB,  96 * KB,  49 * KB, 1000 * KB,  true,  true  }, // max recording
    };
    for (size_t i = 0; i < sizeof(devices) / sizeof(devices[0]); i++) {
        const device_t *d = &devices[i];
        outcome_t o = run(d, POLICY_NEW);
        CHECK(o.write_failures == 0 && o.gc_errors == 0 && sim_log_count('W') == 0
              && sim_log_count('E') == 0,
              "%s: %d failed writes, %d gc errors, community %ld KB, "
              "%d days without list, %d evictions",
              describe(d), o.write_failures, o.gc_errors,
              o.community_bytes > 0 ? o.community_bytes / KB : 0,
              o.refusals, s_evictions);
    }

    // Without an own recording the community cache must not be starved: a
    // default list is kept every day, a large one gets a real budget.
    device_t plain = { 0, 79 * KB, 29 * KB, 40 * KB, false, false };
    outcome_t o = run(&plain, POLICY_NEW);
    CHECK(o.refusals == 0 && o.community_bytes == 40 * KB,
          "default list cached every day (%d days without)", o.refusals);
    device_t big = { 0, 79 * KB, 29 * KB, 1000 * KB, false, false };
    o = run(&big, POLICY_NEW);
    CHECK(o.refusals == 0 && o.community_bytes == 128 * KB,
          "large list gets the 128 KB bucket (%ld KB)", o.community_bytes / KB);
}

static void test_self_healing(void)
{
    printf("self-healing on update\n");
    // A dongle coming from 1.6.0 holds an oversized community list. The
    // first sync deletes it before computing the budget, so the very first
    // list it asks for already fits.
    device_t d = { 0, 79 * KB, 29 * KB, 1000 * KB, true, false };
    sim_init();
    old_i18n(&d, 0);
    // Larger than the new budget allows; the old one (free - 48 KB) offered
    // 323 KB.
    size_t w;
    bool wrote = sim_write_file("/community.bin", 192 * KB, 4096, &w);
    long before = sim_file_size("/community.bin");
    memset(&s_out, 0, sizeof(s_out));
    new_blocklist(&d);
    long after = sim_file_size("/community.bin");
    CHECK(wrote && before == 192 * KB && after == 128 * KB && s_out.write_failures == 0,
          "1.6.0 list %ld KB -> %ld KB on the first sync", before / KB, after / KB);
}

// storage_download(): one GC + exactly one retry, only for out-of-space.
static int s_calls;
static storage_dl_t s_script[2];

static storage_dl_t scripted(void *ctx, size_t *bytes, char *err, size_t cap)
{
    (void) ctx;
    storage_dl_t r = s_script[s_calls++];
    if (r != STORAGE_DL_OK) snprintf(err, cap, "attempt %d", s_calls);
    if (r == STORAGE_DL_FULL) *bytes = 4096;
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


// Probe: the fleet transition. Real dongles reach 1.7.0 after weeks of the
// old policy, on a partition the failing syncs have fragmented, not after a
// single old sync on a fresh one. The new policy must recover on that same
// filesystem (no reformat) within its first sync and stay clean afterwards.
static void test_transition_after_weeks_of_old_policy(void)
{
    printf("transition: %d days old policy, then new policy on the same flash\n", DAYS);
    static const device_t devices[] = {
        {   0,       79 * KB,  29 * KB, 1000 * KB,  false, false }, // #529
        { 175 * KB,  79 * KB,  29 * KB,   40 * KB,  false, false }, // #542
        { 220 * KB,  96 * KB,  49 * KB, 1000 * KB,  false, false }, // max recording
    };
    for (size_t i = 0; i < sizeof(devices) / sizeof(devices[0]); i++) {
        const device_t *d = &devices[i];
        memset(&s_out, 0, sizeof(s_out));
        s_evictions = 0;
        sim_init();
        sim_log_reset();
        size_t w;
        if (d->custom) sim_write_file("/announcement.alaw", (size_t) d->custom, 8192, &w);
        for (int day = 0; day < DAYS; day++) {
            if (day == 0) old_i18n(d, 0);
            old_blocklist(d);
        }
        int old_failures = s_out.write_failures;

        // OTA to the new firmware: i18n sync first, then the daily blocklist.
        memset(&s_out, 0, sizeof(s_out));
        int gc_before = sim_gc_error_count();
        sim_log_reset();
        new_i18n(d, 1500);
        for (int day = 0; day < 30; day++) {
            new_blocklist(d);
        }
        int gc_errors = sim_gc_error_count() - gc_before;
        CHECK(s_out.write_failures == 0 && gc_errors == 0 && sim_log_count('E') == 0,
              "%s: %d old failures, then %d failed writes, %d gc errors, "
              "%d days without list, community %ld KB",
              describe(d), old_failures, s_out.write_failures, gc_errors,
              s_out.refusals, sim_file_size("/community.bin") / KB);
    }
}

int main(void)
{
    test_geometry();
    test_storage_download();
    test_old_reproduces_field_failures();
    test_new_policy();
    test_self_healing();
    test_transition_after_weeks_of_old_policy();

    printf("\n%d checks, %d failed\n", tests_run, tests_failed);
    return tests_failed ? 1 : 0;
}
