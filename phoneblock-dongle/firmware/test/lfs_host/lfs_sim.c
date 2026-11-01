// RAM-backed LittleFS for the host storage simulation — see lfs_sim.h.

#include "lfs_sim.h"

#include <stdarg.h>
#include <stdio.h>
#include <string.h>

#include "lfs.h"

#include "esp_err.h"
#include "esp_littlefs.h"
#include "esp_log.h"
#include "esp_partition.h"

#define SIM_BLOCKS (SIM_PART_SIZE / SIM_BLOCK_SIZE)
#define SIM_CACHE  512u   // CONFIG_LITTLEFS_CACHE_SIZE
#define SIM_LABEL  "storage"

static uint8_t s_flash[SIM_PART_SIZE];
static lfs_t   s_lfs;
static bool    s_mounted;
static int     s_formats;
static int     s_last_err;
static long    s_erases;
static int64_t s_clock;   // fake mtime, advances per open

bool sim_log_echo = false;
static int  s_log_counts[256];
static char s_last_msg[256][160];

// ---------------------------------------------------------------------------
// NOR-flash block device: a program can only clear bits, an erase sets a
// whole sector back to 0xFF.
// ---------------------------------------------------------------------------

static int bd_read(const struct lfs_config *c, lfs_block_t block, lfs_off_t off,
                   void *buffer, lfs_size_t size)
{
    memcpy(buffer, s_flash + block * c->block_size + off, size);
    return 0;
}

static int bd_prog(const struct lfs_config *c, lfs_block_t block, lfs_off_t off,
                   const void *buffer, lfs_size_t size)
{
    uint8_t *dst = s_flash + block * c->block_size + off;
    const uint8_t *src = buffer;
    for (lfs_size_t i = 0; i < size; i++) dst[i] &= src[i];
    return 0;
}

static int bd_erase(const struct lfs_config *c, lfs_block_t block)
{
    memset(s_flash + block * c->block_size, 0xFF, c->block_size);
    s_erases++;
    return 0;
}

static int bd_sync(const struct lfs_config *c)
{
    (void) c;
    return 0;
}

// esp_littlefs.c, esp_littlefs_init_efs() for a flash partition, with the
// component's Kconfig defaults (CONFIG_LITTLEFS_READ_SIZE, _WRITE_SIZE,
// _CACHE_SIZE, _LOOKAHEAD_SIZE, _BLOCK_CYCLES).
static const struct lfs_config s_cfg = {
    .read           = bd_read,
    .prog           = bd_prog,
    .erase          = bd_erase,
    .sync           = bd_sync,
    .read_size      = 128,
    .prog_size      = 128,
    .block_size     = SIM_BLOCK_SIZE,
    .block_count    = SIM_BLOCKS,
    .block_cycles   = 512,
    .cache_size     = SIM_CACHE,
    .lookahead_size = 128,
};

// ---------------------------------------------------------------------------
// Logging.
// ---------------------------------------------------------------------------

static void record(char level, const char *msg)
{
    s_log_counts[(unsigned char) level]++;
    snprintf(s_last_msg[(unsigned char) level], sizeof(s_last_msg[0]), "%s", msg);
    if (sim_log_echo) printf("    [%c] %s\n", level, msg);
}

void sim_log(char level, const char *tag, const char *fmt, ...)
{
    (void) tag;
    char msg[160];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(msg, sizeof(msg), fmt, ap);
    va_end(ap);
    record(level, msg);
}

void sim_lfs_log(char level, const char *fmt, ...)
{
    char msg[160];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(msg, sizeof(msg), fmt, ap);
    va_end(ap);
    record(level, msg);
}

int sim_log_count(char level) { return s_log_counts[(unsigned char) level]; }

const char *sim_last_log(char level) { return s_last_msg[(unsigned char) level]; }

void sim_log_reset(void)
{
    memset(s_log_counts, 0, sizeof(s_log_counts));
    memset(s_last_msg, 0, sizeof(s_last_msg));
}

const char *esp_err_to_name(esp_err_t code)
{
    switch (code) {
    case ESP_OK:                return "ESP_OK";
    case ESP_FAIL:              return "ESP_FAIL";
    case ESP_ERR_NOT_FOUND:     return "ESP_ERR_NOT_FOUND";
    case ESP_ERR_INVALID_STATE: return "ESP_ERR_INVALID_STATE";
    default:                    return "ESP_ERR_?";
    }
}

// ---------------------------------------------------------------------------
// The flash and the mount.
// ---------------------------------------------------------------------------

void sim_erase(void)
{
    if (s_mounted) lfs_unmount(&s_lfs);
    s_mounted = false;
    memset(s_flash, 0xFF, sizeof(s_flash));
    s_formats = 0;
    s_erases = 0;
    s_last_err = 0;
}

void sim_init(void)
{
    sim_erase();
    if (lfs_format(&s_lfs, &s_cfg) != 0 || lfs_mount(&s_lfs, &s_cfg) != 0) {
        fprintf(stderr, "lfs_sim: format/mount of a fresh flash failed\n");
        return;
    }
    s_mounted = true;
}

void sim_write_raw(uint32_t offset, const void *data, size_t n)
{
    if (offset + n > sizeof(s_flash)) return;
    const uint8_t *src = data;
    for (size_t i = 0; i < n; i++) s_flash[offset + i] &= src[i];
}

void sim_unmount(void)
{
    if (s_mounted) lfs_unmount(&s_lfs);
    s_mounted = false;
}

bool sim_mounted(void) { return s_mounted; }
int  sim_format_count(void) { return s_formats; }
int  sim_last_err(void) { return s_last_err; }
long sim_erase_count(void) { return s_erases; }

// --- esp_partition.h ---------------------------------------------------------

static const esp_partition_t s_partition = { SIM_PART_SIZE, SIM_LABEL };

const esp_partition_t *esp_partition_find_first(esp_partition_type_t type,
                                                esp_partition_subtype_t subtype,
                                                const char *label)
{
    (void) type;
    (void) subtype;
    return label && strcmp(label, SIM_LABEL) == 0 ? &s_partition : NULL;
}

esp_err_t esp_partition_read(const esp_partition_t *p, size_t offset,
                             void *dst, size_t size)
{
    if (p != &s_partition || offset + size > sizeof(s_flash)) return ESP_FAIL;
    memcpy(dst, s_flash + offset, size);
    return ESP_OK;
}

// --- esp_littlefs.h ----------------------------------------------------------

esp_err_t esp_littlefs_format(const char *partition_label)
{
    if (!partition_label || strcmp(partition_label, SIM_LABEL) != 0) {
        return ESP_ERR_NOT_FOUND;
    }
    bool was_mounted = s_mounted;
    sim_unmount();
    s_formats++;
    if (lfs_format(&s_lfs, &s_cfg) != 0) return ESP_FAIL;
    if (was_mounted) {
        if (lfs_mount(&s_lfs, &s_cfg) != 0) return ESP_FAIL;
        s_mounted = true;
    }
    return ESP_OK;
}

esp_err_t esp_vfs_littlefs_register(const esp_vfs_littlefs_conf_t *conf)
{
    if (!conf->partition_label || strcmp(conf->partition_label, SIM_LABEL) != 0) {
        return ESP_ERR_NOT_FOUND;
    }
    if (s_mounted) return ESP_ERR_INVALID_STATE;   // "Partition already used"
    int res = lfs_mount(&s_lfs, &s_cfg);
    if (res != 0 && conf->format_if_mount_failed) {
        sim_log('W', "esp_littlefs", "mount failed, (%d). formatting...", res);
        s_formats++;
        if (lfs_format(&s_lfs, &s_cfg) != 0) {
            sim_log('E', "esp_littlefs", "format failed");
            return ESP_FAIL;
        }
        res = lfs_mount(&s_lfs, &s_cfg);
    }
    if (res != 0) {
        sim_log('E', "esp_littlefs", "mount failed, (%d)", res);
        return ESP_FAIL;
    }
    s_mounted = true;
    return ESP_OK;
}

esp_err_t esp_littlefs_info(const char *partition_label, size_t *total,
                            size_t *used)
{
    if (!s_mounted || !partition_label || strcmp(partition_label, SIM_LABEL) != 0) {
        return ESP_ERR_INVALID_STATE;
    }
    lfs_ssize_t n = lfs_fs_size(&s_lfs);
    if (n < 0) return ESP_FAIL;
    *total = (size_t) s_cfg.block_count * s_cfg.block_size;
    *used  = (size_t) n * s_cfg.block_size;
    return ESP_OK;
}

// --- File helpers ------------------------------------------------------------

bool sim_write_file(const char *path, size_t size, size_t chunk,
                    size_t *written)
{
    *written = 0;
    s_last_err = 0;
    if (!s_mounted) { s_last_err = LFS_ERR_INVAL; return false; }

    // vfs_littlefs_open(): the file's own cache and the mtime attribute,
    // committed with every sync; a sync right after the open frees the old
    // content's blocks of a file opened with O_TRUNC.
    static uint8_t cache[SIM_CACHE];
    int64_t mtime = ++s_clock;
    struct lfs_attr attr = { 't', &mtime, sizeof(mtime) };
    struct lfs_file_config fcfg = { .buffer = cache, .attrs = &attr, .attr_count = 1 };
    lfs_file_t f;
    int res = lfs_file_opencfg(&s_lfs, &f, path,
                               LFS_O_WRONLY | LFS_O_CREAT | LFS_O_TRUNC, &fcfg);
    if (res < 0) { s_last_err = res; return false; }
    res = lfs_file_sync(&s_lfs, &f);
    if (res < 0) { s_last_err = res; lfs_file_close(&s_lfs, &f); return false; }

    static uint8_t buf[16384];
    if (chunk > sizeof(buf)) chunk = sizeof(buf);
    bool ok = true;
    size_t done = 0;
    while (done < size) {
        size_t n = size - done < chunk ? size - done : chunk;
        for (size_t i = 0; i < n; i++) buf[i] = (uint8_t) ((done + i) * 131u + 7u);
        lfs_ssize_t w = lfs_file_write(&s_lfs, &f, buf, (lfs_size_t) n);
        if (w < 0) { s_last_err = (int) w; ok = false; break; }
        done += (size_t) w;
        if ((size_t) w != n) { s_last_err = LFS_ERR_NOSPC; ok = false; break; }
    }
    *written = done;
    // vfs_littlefs_close(): the final sync, where the last blocks and the
    // metadata commit land.
    res = lfs_file_close(&s_lfs, &f);
    if (ok && res < 0) { s_last_err = res; ok = false; }
    return ok;
}

bool sim_update_file(const char *path, size_t offset, size_t size)
{
    s_last_err = 0;
    if (!s_mounted) { s_last_err = LFS_ERR_INVAL; return false; }
    // fopen(path, "r+b") + fseek + one fwrite (unbuffered) + fclose, as
    // stats.c writes a call record: O_RDWR, no truncation.
    static uint8_t cache[SIM_CACHE];
    int64_t mtime = ++s_clock;
    struct lfs_attr attr = { 't', &mtime, sizeof(mtime) };
    struct lfs_file_config fcfg = { .buffer = cache, .attrs = &attr, .attr_count = 1 };
    lfs_file_t f;
    int res = lfs_file_opencfg(&s_lfs, &f, path, LFS_O_RDWR, &fcfg);
    if (res < 0) { s_last_err = res; return false; }
    bool ok = lfs_file_seek(&s_lfs, &f, (lfs_soff_t) offset, LFS_SEEK_SET) >= 0;
    static uint8_t buf[512];
    size_t done = 0;
    while (ok && done < size) {
        size_t n = size - done < sizeof(buf) ? size - done : sizeof(buf);
        for (size_t i = 0; i < n; i++) buf[i] = (uint8_t) ((offset + done + i) * 37u + (uint8_t) mtime);
        lfs_ssize_t w = lfs_file_write(&s_lfs, &f, buf, (lfs_size_t) n);
        if (w < 0) { s_last_err = (int) w; ok = false; break; }
        if ((size_t) w != n) { s_last_err = LFS_ERR_NOSPC; ok = false; break; }
        done += n;
    }
    res = lfs_file_close(&s_lfs, &f);
    if (ok && res < 0) { s_last_err = res; ok = false; }
    return ok;
}

bool sim_unlink(const char *path)
{
    return s_mounted && lfs_remove(&s_lfs, path) == 0;
}

bool sim_rename(const char *from, const char *to)
{
    return s_mounted && lfs_rename(&s_lfs, from, to) == 0;
}

long sim_file_size(const char *path)
{
    struct lfs_info info;
    if (!s_mounted || lfs_stat(&s_lfs, path, &info) != 0) return -1;
    return (long) info.size;
}
