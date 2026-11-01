// Implementation of call_rec.h.

#ifndef ESP_PLATFORM
#define _POSIX_C_SOURCE 200809L
#endif

#include "call_rec.h"

#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#ifdef ESP_PLATFORM
#include "esp_heap_caps.h"
#endif

#include "platform.h"
#include "stats.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

static const char *TAG = "rec";

#define FRAME_BYTES CALL_REC_FRAME   // A-law: one byte per sample
#define RING_BYTES  (CALL_REC_RING_FRAMES * FRAME_BYTES)

struct call_rec {
    uint32_t seq;
    // --- the dialog task's side ---
    call_rec_rx_t rx;                 // the caller's audio, by RTP timestamp
    int16_t  rx_frame[CALL_REC_FRAME];
    uint8_t  mixed[CALL_REC_FRAME];
    uint32_t pushed;                  // frames queued
    uint32_t max_frames;              // the per-call maximum
    bool     stopped;                 // no more frames: full ring or maximum
    // --- shared: a single-producer single-consumer ring of frames. `head`
    // is written by the dialog task only, `tail` by the writer only; both
    // count frames and wrap modulo 2^32.
    uint8_t *ring;
    uint32_t head;
    uint32_t tail;
    bool     ended;                   // call_rec_end() was called
    // --- the writer's side ---
    FILE    *f;
    bool     opened;                  // the file open was attempted
    bool     write_failed;
    uint32_t written;                 // frames in the file
    char     tmp[PB_DATA_PATH_CAP];
};

typedef struct {
    uint32_t seq;
    uint32_t bytes;
} rec_entry_t;

static pb_mutex_t  *s_mutex;
static bool         s_ready;
static char         s_dir[PB_DATA_PATH_CAP];
// The stored recordings, ascending by seq (oldest first): at most one per
// listed call.
static rec_entry_t  s_index[STATS_CALL_CAPACITY];
static int          s_count;
static uint64_t     s_used;           // sum of their footprints
static uint64_t     s_audio;          // sum of their bytes
// Every call below this one has left the call list.
static uint32_t     s_keep_from = 1;
static call_rec_t  *s_active;         // the recording in progress
static uint64_t     s_cap = CALL_REC_CAP;
static uint32_t     s_call_max = CALL_REC_CALL_MAX;
static bool         s_manual;

static void lock(void)   { pb_mutex_lock(s_mutex); }
static void unlock(void) { pb_mutex_unlock(s_mutex); }

uint64_t call_rec_footprint(uint64_t bytes)
{
    static const storage_lfs_geometry_t g = { .block_size = 4096, .reserve_blocks = 0 };
    return storage_budget_lfs_file_blocks(&g, bytes) * g.block_size;
}

void call_rec_set_limits(uint64_t cap_bytes, uint32_t call_max_bytes)
{
    s_cap      = cap_bytes ? cap_bytes : CALL_REC_CAP;
    s_call_max = call_max_bytes ? call_max_bytes : CALL_REC_CALL_MAX;
}

void call_rec_set_manual_writer(bool manual)
{
    s_manual = manual;
}

// "<dir>/<seq>.<ext>"; false if it does not fit.
static bool rec_path(char *out, size_t cap, uint32_t seq, const char *ext)
{
    int n = snprintf(out, cap, "%s/%u.%s", s_dir, (unsigned)seq, ext);
    return n > 0 && (size_t)n < cap;
}

static void remove_rec(uint32_t seq)
{
    char path[PB_DATA_PATH_CAP];
    if (rec_path(path, sizeof(path), seq, "al") && unlink(path) != 0 && errno != ENOENT) {
        pb_log_warn(TAG, "recording of call %u cannot be deleted: %s",
                    (unsigned)seq, strerror(errno));
    }
}

// --- The index (under the lock) -----------------------------------------------

static int find_locked(uint32_t seq)
{
    int lo = 0, hi = s_count - 1;
    while (lo <= hi) {
        int mid = (lo + hi) / 2;
        if (s_index[mid].seq == seq) return mid;
        if (s_index[mid].seq < seq) lo = mid + 1;
        else hi = mid - 1;
    }
    return -1;
}

static void drop_at_locked(int i)
{
    s_used  -= call_rec_footprint(s_index[i].bytes);
    s_audio -= s_index[i].bytes;
    memmove(&s_index[i], &s_index[i + 1], (size_t)(s_count - i - 1) * sizeof(s_index[0]));
    s_count--;
}

// False when the index is full (more recordings than listed calls cannot
// happen; a file of a call that is listed twice cannot either).
static bool insert_locked(uint32_t seq, uint32_t bytes)
{
    if (s_count >= (int)(sizeof(s_index) / sizeof(s_index[0]))) return false;
    int i = s_count;
    while (i > 0 && s_index[i - 1].seq > seq) i--;
    if (i > 0 && s_index[i - 1].seq == seq) return false;
    memmove(&s_index[i + 1], &s_index[i], (size_t)(s_count - i) * sizeof(s_index[0]));
    s_index[i].seq   = seq;
    s_index[i].bytes = bytes;
    s_count++;
    s_used  += call_rec_footprint(bytes);
    s_audio += bytes;
    return true;
}

// Takes the oldest recording out of the index if its call has left the
// list; returns its seq (0: none). The file is the caller's to delete, outside the
// lock.
static uint32_t pop_oldest_if_dropped(void)
{
    uint32_t seq = 0;
    lock();
    if (s_count > 0 && s_index[0].seq < s_keep_from) {
        seq = s_index[0].seq;
        drop_at_locked(0);
    }
    unlock();
    return seq;
}

// The call list's drop hook (stats_set_drop_hook): calls below `keep_from`
// have left the list, and so do their recordings.
static void on_calls_dropped(uint32_t keep_from)
{
    lock();
    if (keep_from > s_keep_from) s_keep_from = keep_from;
    unlock();
    for (uint32_t seq; (seq = pop_oldest_if_dropped()) != 0; ) {
        remove_rec(seq);
        pb_log_info(TAG, "recording of call %u deleted with its call", (unsigned)seq);
    }
}

// --- Start-up ------------------------------------------------------------------

// The seq of a recording's file name "<digits>.al", 0 for anything else.
static uint32_t parse_name(const char *name)
{
    uint32_t seq = 0;
    const char *p = name;
    if (*p < '1' || *p > '9') return 0;
    for (; *p >= '0' && *p <= '9'; p++) {
        if (seq > (UINT32_MAX - 9) / 10) return 0;
        seq = seq * 10 + (uint32_t)(*p - '0');
    }
    return strcmp(p, ".al") == 0 ? seq : 0;
}

static bool listed(const stats_calls_info_t *info, uint32_t seq)
{
    return info->count > 0 && seq >= info->oldest_seq && seq <= info->newest_seq;
}

// Size of the regular file `dir`/`name`, -1 if it is none.
static long entry_size(const char *name, char *path, size_t cap)
{
    int n = snprintf(path, cap, "%s/%s", s_dir, name);
    if (n <= 0 || (size_t)n >= cap) return -1;
    struct stat st;
    if (stat(path, &st) != 0 || !S_ISREG(st.st_mode)) return -1;
    return (long)st.st_size;
}

// Removes the first file in the directory that is not the recording of a
// listed call (or does not fit the index). True if it removed one: the
// directory is read again from the start, so no removal skews a running
// readdir().
static bool remove_one_stray(const stats_calls_info_t *info, int *kept)
{
    DIR *d = opendir(s_dir);
    if (!d) return false;
    char path[PB_DATA_PATH_CAP];
    char victim[PB_DATA_PATH_CAP] = "";
    int ok = 0;
    struct dirent *e;
    while ((e = readdir(d)) != NULL) {
        if (strcmp(e->d_name, ".") == 0 || strcmp(e->d_name, "..") == 0) continue;
        uint32_t seq = parse_name(e->d_name);
        long size = entry_size(e->d_name, path, sizeof(path));
        if (seq && size > 0 && listed(info, seq)
                && ok < (int)(sizeof(s_index) / sizeof(s_index[0]))) {
            ok++;
            continue;
        }
        if (size >= 0) {                          // a file (not a directory)
            snprintf(victim, sizeof(victim), "%s", path);
            break;
        }
    }
    closedir(d);
    *kept = ok;
    return victim[0] && unlink(victim) == 0;
}

bool call_rec_init(const char *dir)
{
    if (!s_mutex) s_mutex = pb_mutex_create();
    lock();
    s_ready = false;
    s_count = 0;
    s_used = s_audio = 0;
    s_active = NULL;
    int n = snprintf(s_dir, sizeof(s_dir), "%s", dir ? dir : "");
    unlock();
    if (n <= 0 || (size_t)n >= sizeof(s_dir) - 16) {
        pb_log_warn(TAG, "recordings directory path does not fit: %s", dir ? dir : "(null)");
        return false;
    }
    if (mkdir(s_dir, 0750) != 0 && errno != EEXIST) {
        pb_log_warn(TAG, "recordings directory %s cannot be created: %s — calls "
                         "are not recorded", s_dir, strerror(errno));
        return false;
    }

    stats_calls_info_t info;
    stats_calls_info(&info);

    // Strays first: temp files of a recording a crash interrupted, files
    // whose call has left the list (or a list that was discarded), anything
    // else that is not a recording.
    int removed = 0, kept = 0;
    while (removed < 4 * STATS_CALL_CAPACITY + 64 && remove_one_stray(&info, &kept)) removed++;

    DIR *d = opendir(s_dir);
    if (!d) {
        pb_log_warn(TAG, "recordings directory %s cannot be read: %s — calls are "
                         "not recorded", s_dir, strerror(errno));
        return false;
    }
    char path[PB_DATA_PATH_CAP];
    struct dirent *e;
    lock();
    while ((e = readdir(d)) != NULL) {
        uint32_t seq = parse_name(e->d_name);
        long size = seq ? entry_size(e->d_name, path, sizeof(path)) : -1;
        if (seq && size > 0 && listed(&info, seq)) insert_locked(seq, (uint32_t)size);
    }
    s_keep_from = info.count ? info.oldest_seq : info.floor_seq;
    unlock();
    closedir(d);

    // A smaller cap than the recordings stored (another firmware): the
    // oldest go.
    for (;;) {
        uint32_t seq = 0;
        lock();
        if (s_count > 0 && s_used > s_cap) {
            seq = s_index[0].seq;
            drop_at_locked(0);
        }
        unlock();
        if (!seq) break;
        remove_rec(seq);
        removed++;
    }

    stats_set_drop_hook(on_calls_dropped);
    lock();
    s_ready = true;
    int count = s_count;
    uint64_t used = s_used;
    unlock();
    pb_log_info(TAG, "recordings in %s: %d (%u KB of %u KB)%s", s_dir, count,
                (unsigned)(used / 1024), (unsigned)(s_cap / 1024),
                removed ? ", strays removed" : "");
    return true;
}

// --- Recording (the dialog task) ---------------------------------------------

static void writer_task(void *arg);

call_rec_t *call_rec_begin(uint32_t seq)
{
    if (!s_ready || seq == 0) return NULL;
    stats_calls_info_t info;
    stats_calls_info(&info);
    if (!listed(&info, seq)) {
        pb_log_info(TAG, "call %u is not listed: not recorded", (unsigned)seq);
        return NULL;
    }

    call_rec_t *r = calloc(1, sizeof(*r));
    uint8_t *ring = r ? malloc(RING_BYTES) : NULL;
    if (!ring) {
        free(r);
#ifdef ESP_PLATFORM
        pb_log_warn(TAG, "call %u not recorded: no memory for %u B (heap free %u, "
                         "largest block %u)", (unsigned)seq,
                    (unsigned)(sizeof(*r) + RING_BYTES),
                    (unsigned)heap_caps_get_free_size(MALLOC_CAP_8BIT),
                    (unsigned)heap_caps_get_largest_free_block(MALLOC_CAP_8BIT));
#else
        pb_log_warn(TAG, "call %u not recorded: no memory", (unsigned)seq);
#endif
        return NULL;
    }
    r->seq = seq;
    r->ring = ring;
    r->max_frames = s_call_max / FRAME_BYTES;
    call_rec_rx_init(&r->rx);
    if (!rec_path(r->tmp, sizeof(r->tmp), seq, "tmp")) {
        free(ring);
        free(r);
        return NULL;
    }

    lock();
    bool busy = s_active != NULL;
    if (!busy) s_active = r;
    unlock();
    if (busy) {
        pb_log_info(TAG, "call %u not recorded: the previous recording is still "
                         "being written", (unsigned)seq);
        free(ring);
        free(r);
        return NULL;
    }
    if (!s_manual && pb_task_create(writer_task, r, "rec_writer", CALL_REC_WRITER_STACK,
                                    PB_PRIO_BACKGROUND) != PB_OK) {
        lock();
        s_active = NULL;
        unlock();
        free(ring);
        free(r);
        pb_log_warn(TAG, "call %u not recorded: the writer task cannot be started",
                    (unsigned)seq);
        return NULL;
    }
#ifdef ESP_PLATFORM
    pb_log_info(TAG, "recording call %u (%u B ring; heap free %u, largest block %u)",
                (unsigned)seq, (unsigned)(sizeof(*r) + RING_BYTES),
                (unsigned)heap_caps_get_free_size(MALLOC_CAP_8BIT),
                (unsigned)heap_caps_get_largest_free_block(MALLOC_CAP_8BIT));
#else
    pb_log_info(TAG, "recording call %u", (unsigned)seq);
#endif
    return r;
}

void call_rec_rx(call_rec_t *r, uint32_t ts, const uint8_t *alaw, size_t n)
{
    if (!r || r->stopped) return;
    call_rec_rx_push(&r->rx, ts, alaw, n);
}

void call_rec_tick(call_rec_t *r, const uint8_t tx[CALL_REC_FRAME])
{
    if (!r || r->stopped) return;
    if (r->pushed >= r->max_frames) {
        r->stopped = true;
        pb_log_info(TAG, "recording of call %u reached its maximum of %u s, the "
                         "rest of the call is not recorded", (unsigned)r->seq,
                    (unsigned)(r->max_frames / (CALL_REC_BYTES_PER_S / FRAME_BYTES)));
        return;
    }
    call_rec_rx_pop(&r->rx, r->rx_frame);
    call_rec_mix(r->mixed, tx, r->rx_frame);
    uint32_t tail = __atomic_load_n(&r->tail, __ATOMIC_ACQUIRE);
    if (r->head - tail >= CALL_REC_RING_FRAMES) {
        // The writer is CALL_REC_RING_FRAMES frames behind: the flash stalled.
        // Never wait for it — the call is what matters.
        r->stopped = true;
        pb_log_warn(TAG, "recording of call %u ends after %u s: the flash fell %u s "
                         "behind (the call goes on)", (unsigned)r->seq,
                    (unsigned)(r->pushed / (CALL_REC_BYTES_PER_S / FRAME_BYTES)),
                    (unsigned)(CALL_REC_RING_FRAMES / (CALL_REC_BYTES_PER_S / FRAME_BYTES)));
        return;
    }
    memcpy(r->ring + (size_t)(r->head % CALL_REC_RING_FRAMES) * FRAME_BYTES,
           r->mixed, FRAME_BYTES);
    __atomic_store_n(&r->head, r->head + 1, __ATOMIC_RELEASE);
    r->pushed++;
}

void call_rec_end(call_rec_t *r)
{
    if (!r) return;
    __atomic_store_n(&r->ended, true, __ATOMIC_RELEASE);
}

// --- The writer ----------------------------------------------------------------

// Makes room for the recording in progress to grow to `bytes`: the oldest
// stored recordings go until it fits the cap. False if it cannot (only when
// the per-call maximum exceeds the cap).
static bool make_room(uint64_t bytes)
{
    uint64_t need = call_rec_footprint(bytes);
    for (;;) {
        uint32_t seq = 0;
        bool fits;
        lock();
        fits = s_used + need <= s_cap;
        if (!fits && s_count > 0) {
            seq = s_index[0].seq;
            drop_at_locked(0);
        }
        unlock();
        if (fits) return true;
        if (!seq) return false;
        remove_rec(seq);
        pb_log_info(TAG, "recording of call %u deleted to make room (cap %u KB)",
                    (unsigned)seq, (unsigned)(s_cap / 1024));
    }
}

static void write_frames(call_rec_t *r, const uint8_t *data, uint32_t frames)
{
    if (r->write_failed) return;
    if (!r->opened) {
        r->opened = true;
        r->f = fopen(r->tmp, "wb");
        if (!r->f) {
            r->write_failed = true;
            pb_log_warn(TAG, "recording of call %u: %s cannot be created: %s",
                        (unsigned)r->seq, r->tmp, strerror(errno));
            return;
        }
        // The ring is the buffer: each write is a run of whole frames.
        setvbuf(r->f, NULL, _IONBF, 0);
    }
    uint64_t total = (uint64_t)(r->written + frames) * FRAME_BYTES;
    if (!make_room(total)) {
        r->write_failed = true;
        pb_log_warn(TAG, "recording of call %u ends: it does not fit the cap of %u KB",
                    (unsigned)r->seq, (unsigned)(s_cap / 1024));
        return;
    }
    size_t n = (size_t)frames * FRAME_BYTES;
    if (fwrite(data, 1, n, r->f) != n) {
        r->write_failed = true;
        pb_log_warn(TAG, "recording of call %u ends after %u s: writing failed: %s",
                    (unsigned)r->seq,
                    (unsigned)(r->written / (CALL_REC_BYTES_PER_S / FRAME_BYTES)),
                    strerror(errno));
        return;
    }
    r->written += frames;
}

// The call has ended and every frame is written: close, keep or discard,
// free.
static void finish(call_rec_t *r)
{
    bool close_ok = true;
    if (r->f) {
        close_ok = fflush(r->f) == 0;
        close_ok = fclose(r->f) == 0 && close_ok;
        r->f = NULL;
    }
    char path[PB_DATA_PATH_CAP];
    lock();
    bool still_listed = r->seq >= s_keep_from;
    unlock();
    unsigned secs = (unsigned)((r->written + 25) / (CALL_REC_BYTES_PER_S / FRAME_BYTES));
    bool keep = r->opened && close_ok && still_listed && r->written >= CALL_REC_MIN_FRAMES
             && rec_path(path, sizeof(path), r->seq, "al");
    if (keep && rename(r->tmp, path) != 0) {
        pb_log_warn(TAG, "recording of call %u cannot be stored: %s",
                    (unsigned)r->seq, strerror(errno));
        keep = false;
    }
    if (keep) {
        lock();
        // The call may have left the list during the rename.
        keep = r->seq >= s_keep_from
            && insert_locked(r->seq, r->written * FRAME_BYTES);
        unlock();
        if (!keep) unlink(path);
    } else if (r->opened) {
        unlink(r->tmp);
    }
    if (keep) {
        int stack_free = pb_task_stack_free();      // -1: not measurable here
        if (stack_free >= 0) {
            pb_log_info(TAG, "recording of call %u saved: %u s, %u KB (writer stack "
                             "headroom %d B of %d)", (unsigned)r->seq, secs,
                        (unsigned)(r->written * FRAME_BYTES / 1024),
                        stack_free, CALL_REC_WRITER_STACK);
        } else {
            pb_log_info(TAG, "recording of call %u saved: %u s, %u KB",
                        (unsigned)r->seq, secs,
                        (unsigned)(r->written * FRAME_BYTES / 1024));
        }
    } else if (!close_ok) {
        pb_log_warn(TAG, "recording of call %u lost: closing the file failed",
                    (unsigned)r->seq);
    } else if (!still_listed) {
        pb_log_info(TAG, "recording of call %u dropped: the call has left the list",
                    (unsigned)r->seq);
    } else if (r->written < CALL_REC_MIN_FRAMES && !r->write_failed) {
        pb_log_info(TAG, "recording of call %u discarded: shorter than %u s",
                    (unsigned)r->seq, CALL_REC_MIN_FRAMES / (CALL_REC_BYTES_PER_S / FRAME_BYTES));
    }
    if (r->rx.dropped) {
        pb_log_info(TAG, "recording of call %u: %u ms of the caller's audio came "
                         "too late or too early to be mixed", (unsigned)r->seq,
                    (unsigned)(r->rx.dropped / 8));
    }
    lock();
    if (s_active == r) s_active = NULL;
    unlock();
    free(r->ring);
    free(r);
}

// One pass over `r`; false once it is finished (and freed).
static bool writer_pass(call_rec_t *r)
{
    // `ended` before `head`: the dialog task stores its last frame before it
    // ends the recording, so a drained ring after an ended call is complete.
    bool ended = __atomic_load_n(&r->ended, __ATOMIC_ACQUIRE);
    uint32_t head = __atomic_load_n(&r->head, __ATOMIC_ACQUIRE);
    uint32_t tail = r->tail;
    while (tail != head) {
        uint32_t slot = tail % CALL_REC_RING_FRAMES;
        uint32_t run  = head - tail;
        if (run > CALL_REC_RING_FRAMES - slot) run = CALL_REC_RING_FRAMES - slot;
        write_frames(r, r->ring + (size_t)slot * FRAME_BYTES, run);
        tail += run;
        __atomic_store_n(&r->tail, tail, __ATOMIC_RELEASE);
    }
    if (!ended) return true;
    finish(r);
    return false;
}

bool call_rec_writer_step(void)
{
    lock();
    call_rec_t *r = s_active;
    unlock();
    return r && writer_pass(r);
}

static void writer_task(void *arg)
{
    call_rec_t *r = arg;
    while (writer_pass(r)) pb_task_sleep_ms(CALL_REC_WRITER_MS);
}

// --- The stored recordings -------------------------------------------------------

uint32_t call_rec_seconds(uint32_t seq)
{
    if (!s_mutex) return 0;
    lock();
    int i = find_locked(seq);
    uint32_t bytes = i >= 0 ? s_index[i].bytes : 0;
    unlock();
    if (!bytes) return 0;
    uint32_t s = (bytes + CALL_REC_BYTES_PER_S / 2) / CALL_REC_BYTES_PER_S;
    return s ? s : 1;
}

int call_rec_open(uint32_t seq, size_t *size)
{
    if (!s_mutex) return -1;
    char path[PB_DATA_PATH_CAP];
    lock();
    int i = find_locked(seq);
    bool ok = i >= 0 && rec_path(path, sizeof(path), seq, "al");
    if (ok && size) *size = s_index[i].bytes;
    unlock();
    return ok ? open(path, O_RDONLY) : -1;
}

bool call_rec_delete(uint32_t seq)
{
    if (!s_mutex) return false;
    lock();
    int i = find_locked(seq);
    if (i >= 0) drop_at_locked(i);
    unlock();
    if (i < 0) return false;
    remove_rec(seq);
    pb_log_info(TAG, "recording of call %u deleted", (unsigned)seq);
    return true;
}

void call_rec_delete_all(void)
{
    if (!s_mutex) return;
    for (;;) {
        uint32_t seq = 0;
        lock();
        if (s_count > 0) {
            seq = s_index[0].seq;
            drop_at_locked(0);
        }
        unlock();
        if (!seq) break;
        remove_rec(seq);
    }
}

void call_rec_get_info(call_rec_info_t *out)
{
    memset(out, 0, sizeof(*out));
    out->cap_bytes  = s_cap;
    out->call_max_s = s_call_max / CALL_REC_BYTES_PER_S;
    if (!s_mutex) return;
    lock();
    out->count       = s_count;
    out->audio_bytes = s_audio;
    out->used_bytes  = s_used;
    out->active      = s_active != NULL;
    unlock();
}
