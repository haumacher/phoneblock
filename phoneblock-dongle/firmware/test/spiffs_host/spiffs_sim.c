// RAM-backed SPIFFS for the host storage simulation — see spiffs_sim.h.

#include "spiffs_sim.h"

#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "spiffs.h"
#include "spiffs_nucleus.h"

#include "esp_err.h"
#include "esp_log.h"
#include "esp_spiffs.h"

#define SIM_MAX_FILES 2   // announcement_init(): .max_files = 2

static uint8_t s_flash[SIM_PART_SIZE];
static spiffs  s_fs;
static spiffs_config s_cfg;
static uint8_t s_work[2 * CONFIG_SPIFFS_PAGE_SIZE];
static uint8_t s_fds[SIM_MAX_FILES * sizeof(spiffs_fd)];
static uint8_t s_cache[sizeof(spiffs_cache)
                       + SIM_MAX_FILES * (sizeof(spiffs_cache_page)
                                          + CONFIG_SPIFFS_PAGE_SIZE)];
static int  s_last_err;
static int  s_gc_errors;
static long s_erases;
static uint32_t s_clock;   // fake mtime, advances per open

bool sim_log_echo = false;
static int s_log_counts[256];

// ---------------------------------------------------------------------------
// NOR-flash HAL: a write can only clear bits, an erase sets a whole sector
// back to 0xFF — the property SPIFFS' page-state bookkeeping relies on.
// ---------------------------------------------------------------------------

static s32_t hal_read(spiffs *fs, u32_t addr, u32_t size, u8_t *dst)
{
    (void)fs;
    if (addr + size > SIM_PART_SIZE) return SPIFFS_ERR_INTERNAL;
    memcpy(dst, s_flash + addr, size);
    return SPIFFS_OK;
}

static s32_t hal_write(spiffs *fs, u32_t addr, u32_t size, u8_t *src)
{
    (void)fs;
    if (addr + size > SIM_PART_SIZE) return SPIFFS_ERR_INTERNAL;
    for (u32_t i = 0; i < size; i++) s_flash[addr + i] &= src[i];
    return SPIFFS_OK;
}

static s32_t hal_erase(spiffs *fs, u32_t addr, u32_t size)
{
    (void)fs;
    if (addr + size > SIM_PART_SIZE || addr % SIM_BLOCK_SIZE) {
        return SPIFFS_ERR_INTERNAL;
    }
    memset(s_flash + addr, 0xFF, size);
    s_erases++;
    return SPIFFS_OK;
}

// SPIFFS_LOCK/UNLOCK hooks (spiffs_config.h); single-threaded here.
void spiffs_api_lock(struct spiffs_t *fs)   { (void)fs; }
void spiffs_api_unlock(struct spiffs_t *fs) { (void)fs; }

static s32_t do_mount(void)
{
    return SPIFFS_mount(&s_fs, &s_cfg, s_work, s_fds, sizeof(s_fds),
                        s_cache, sizeof(s_cache), NULL);
}

void sim_init(void)
{
    memset(s_flash, 0xFF, sizeof(s_flash));
    memset(&s_fs, 0, sizeof(s_fs));
    memset(&s_cfg, 0, sizeof(s_cfg));
    // Exactly the fields esp_spiffs_init() sets.
    s_cfg.hal_erase_f      = hal_erase;
    s_cfg.hal_read_f       = hal_read;
    s_cfg.hal_write_f      = hal_write;
    s_cfg.log_block_size   = SIM_BLOCK_SIZE;
    s_cfg.log_page_size    = CONFIG_SPIFFS_PAGE_SIZE;
    s_cfg.phys_addr        = 0;
    s_cfg.phys_erase_block = SIM_BLOCK_SIZE;
    s_cfg.phys_size        = SIM_PART_SIZE;

    // esp_spiffs_init() with format_if_mount_failed = true: the first mount
    // of blank flash fails, the partition is formatted and mounted again.
    if (do_mount() != SPIFFS_OK) {
        SPIFFS_clearerr(&s_fs);
        if (SPIFFS_format(&s_fs) != SPIFFS_OK || do_mount() != SPIFFS_OK) {
            fprintf(stderr, "spiffs_sim: format/mount failed (%d)\n",
                    (int)SPIFFS_errno(&s_fs));
            exit(2);
        }
    }
    s_last_err  = 0;
    s_gc_errors = 0;
    s_erases    = 0;
    s_clock     = 1700000000u;
}

int sim_last_err(void) { return s_last_err; }

static bool fail(void)
{
    s_last_err = SPIFFS_errno(&s_fs);
    SPIFFS_clearerr(&s_fs);
    return false;
}

// esp_spiffs.c: vfs_spiffs_open() + vfs_spiffs_update_mtime().
static spiffs_file open_for_write(const char *path)
{
    spiffs_file fd = SPIFFS_open(&s_fs, path,
                                 SPIFFS_O_CREAT | SPIFFS_O_TRUNC | SPIFFS_O_WRONLY, 0);
    if (fd < 0) return fd;
    spiffs_stat st;
    if (SPIFFS_fstat(&s_fs, fd, &st) == SPIFFS_OK) {
        uint32_t t = s_clock++;
        memcpy(st.meta, &t, sizeof(t));
        if (SPIFFS_fupdate_meta(&s_fs, fd, st.meta) != SPIFFS_OK) {
            SPIFFS_clearerr(&s_fs);   // IDF only warns; the open stands
        }
    } else {
        SPIFFS_clearerr(&s_fs);
    }
    return fd;
}

bool sim_write_file(const char *path, size_t size, size_t chunk,
                    size_t *written)
{
    static uint8_t buf[16384];
    size_t done = 0;
    if (written) *written = 0;
    s_last_err = 0;
    if (chunk == 0 || chunk > sizeof(buf)) return false;

    spiffs_file fd = open_for_write(path);
    if (fd < 0) return fail();

    while (done < size) {
        size_t n = size - done < chunk ? size - done : chunk;
        // Synthetic, position-dependent content (no real data involved).
        for (size_t i = 0; i < n; i++) buf[i] = (uint8_t)((done + i) * 131u + 7u);
        s32_t w = SPIFFS_write(&s_fs, fd, buf, (s32_t)n);
        if (w < 0) {
            fail();
            int err = s_last_err;
            SPIFFS_close(&s_fs, fd);
            SPIFFS_clearerr(&s_fs);
            s_last_err = err;
            if (written) *written = done;
            return false;
        }
        done += (size_t)w;
    }
    if (written) *written = done;
    if (SPIFFS_close(&s_fs, fd) < 0) return fail();
    return true;
}

bool sim_unlink(const char *path)
{
    if (SPIFFS_remove(&s_fs, path) < 0) return fail();
    return true;
}

bool sim_rename(const char *from, const char *to)
{
    if (SPIFFS_rename(&s_fs, from, to) < 0) return fail();
    return true;
}

long sim_file_size(const char *path)
{
    spiffs_stat st;
    if (SPIFFS_stat(&s_fs, path, &st) < 0) {
        SPIFFS_clearerr(&s_fs);
        return -1;
    }
    return (long)st.size;
}

void sim_pages(sim_pages_t *out)
{
    int data_pages = (int)((SPIFFS_PAGES_PER_BLOCK(&s_fs) - SPIFFS_OBJ_LOOKUP_PAGES(&s_fs))
                           * s_fs.block_count);
    out->free_blocks   = (int)s_fs.free_blocks;
    out->pages_used    = (int)s_fs.stats_p_allocated;
    out->pages_deleted = (int)s_fs.stats_p_deleted;
    out->pages_free    = data_pages - out->pages_used - out->pages_deleted;
}

int  sim_gc_error_count(void) { return s_gc_errors; }
long sim_erase_count(void)    { return s_erases; }

// ---------------------------------------------------------------------------
// esp_spiffs.h stand-ins, line for line what esp_spiffs.c does.
// ---------------------------------------------------------------------------

esp_err_t esp_spiffs_info(const char *partition_label, size_t *total_bytes,
                          size_t *used_bytes)
{
    (void)partition_label;
    u32_t total = 0, used = 0;
    SPIFFS_info(&s_fs, &total, &used);
    *total_bytes = total;
    *used_bytes  = used;
    return ESP_OK;
}

esp_err_t esp_spiffs_gc(const char *partition_label, size_t size_to_gc)
{
    (void)partition_label;
    int res = SPIFFS_gc(&s_fs, (u32_t)size_to_gc);
    if (res != SPIFFS_OK) {
        s_gc_errors++;
        ESP_LOGE("SPIFFS", "SPIFFS_gc failed, %d", res);
        SPIFFS_clearerr(&s_fs);
        if (res == SPIFFS_ERR_FULL) {
            return ESP_ERR_NOT_FINISHED;
        }
        return ESP_FAIL;
    }
    return ESP_OK;
}

const char *esp_err_to_name(esp_err_t code)
{
    switch (code) {
    case ESP_OK:               return "ESP_OK";
    case ESP_FAIL:             return "ESP_FAIL";
    case ESP_ERR_NOT_FINISHED: return "ESP_ERR_NOT_FINISHED";
    default:                   return "ESP_ERR_?";
    }
}

// ---------------------------------------------------------------------------
// esp_log.h stand-in.
// ---------------------------------------------------------------------------

void sim_log(char level, const char *tag, const char *fmt, ...)
{
    s_log_counts[(unsigned char)level]++;
    if (!sim_log_echo) return;
    va_list ap;
    va_start(ap, fmt);
    fprintf(stderr, "%c %s: ", level, tag);
    vfprintf(stderr, fmt, ap);
    fputc('\n', stderr);
    va_end(ap);
}

int  sim_log_count(char level) { return s_log_counts[(unsigned char)level]; }
void sim_log_reset(void)       { memset(s_log_counts, 0, sizeof(s_log_counts)); }
