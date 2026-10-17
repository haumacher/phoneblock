#define _GNU_SOURCE

// Linux platform services that need nothing beyond libc and pthreads:
// logging, time, tasks, events, mutexes, the key-value store and the MAC.
// The parts backed by libcurl / OpenSSL (HTTP, TLS, hashes, signatures,
// base64) live in platform_linux_net.c, so the default host test suite
// builds without those development packages (see test/README.md).

#include "platform.h"

#include <errno.h>
#include <fcntl.h>
#include <ifaddrs.h>
#include <limits.h>
#include <net/if.h>
#include <netpacket/packet.h>
#include <pthread.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/file.h>
#include <sys/random.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>

struct pb_mutex {
    pthread_mutex_t mutex;
};

struct pb_event {
    pthread_mutex_t mutex;
    pthread_cond_t changed;
    uint32_t bits;
};

typedef struct pb_kv_item {
    char *key;
    char *value;
    struct pb_kv_item *next;
} pb_kv_item_t;

typedef struct pb_kv_raw {
    char *line;
    struct pb_kv_raw *next;
} pb_kv_raw_t;

struct pb_kv {
    char *path;
    char *name_space;
    bool writable;
    int lock_fd;
    pb_kv_item_t *items;
    // Lines of every other namespace, verbatim: pb_kv_commit() writes them
    // back, so committing one namespace never deletes the rest of the file.
    pb_kv_raw_t *preserved;
    pb_kv_raw_t *preserved_tail;
};

static void pb_log(const char *level, const char *tag, const char *fmt,
                   va_list args)
{
    // ESP-IDF layout, see platform.h. One fprintf per part under the stream
    // lock keeps lines from concurrent threads intact.
    flockfile(stderr);
    fprintf(stderr, "%s (%llu) %s: ", level,
            (unsigned long long)(pb_monotonic_us() / 1000u),
            tag ? tag : "phoneblock");
    vfprintf(stderr, fmt, args);
    fputc('\n', stderr);
    funlockfile(stderr);
}

void pb_log_info(const char *tag, const char *fmt, ...)
{
    va_list args;
    va_start(args, fmt);
    pb_log("I", tag, fmt, args);
    va_end(args);
}

void pb_log_warn(const char *tag, const char *fmt, ...)
{
    va_list args;
    va_start(args, fmt);
    pb_log("W", tag, fmt, args);
    va_end(args);
}

void pb_log_err(const char *tag, const char *fmt, ...)
{
    va_list args;
    va_start(args, fmt);
    pb_log("E", tag, fmt, args);
    va_end(args);
}

uint64_t pb_monotonic_us(void)
{
    struct timespec now;
    if (clock_gettime(CLOCK_MONOTONIC, &now) != 0) return 0;
    return (uint64_t)now.tv_sec * 1000000u + (uint64_t)now.tv_nsec / 1000u;
}

const char *pb_firmware_version(void)
{
#ifdef PHONEBLOCK_VERSION
    return PHONEBLOCK_VERSION;
#else
    return "linux-dev";
#endif
}

uint32_t pb_random_u32(void)
{
    uint32_t value;
    ssize_t count = getrandom(&value, sizeof(value), 0);
    if (count == (ssize_t)sizeof(value)) return value;

    int fd = open("/dev/urandom", O_RDONLY | O_CLOEXEC);
    if (fd >= 0) {
        count = read(fd, &value, sizeof(value));
        close(fd);
        if (count == (ssize_t)sizeof(value)) return value;
    }

    value = (uint32_t)time(NULL) ^ (uint32_t)getpid();
    return value ^ (uint32_t)(uintptr_t)&value;
}

void pb_random_fill(void *out, size_t len)
{
    uint8_t *cursor = out;
    while (len > 0) {
        ssize_t count = getrandom(cursor, len, 0);
        if (count <= 0) {
            uint32_t fallback = pb_random_u32();
            size_t chunk = len < sizeof(fallback) ? len : sizeof(fallback);
            memcpy(cursor, &fallback, chunk);
            count = (ssize_t)chunk;
        }
        cursor += count;
        len -= (size_t)count;
    }
}

bool pb_watchdog_is_subscribed(void)
{
    return false;
}

void pb_watchdog_subscribe(void)
{
}

void pb_watchdog_reset(void)
{
}

int pb_task_stack_free(void)
{
    return -1;
}

void pb_task_sleep_ms(uint32_t milliseconds)
{
    struct timespec delay = {
        .tv_sec = milliseconds / 1000u,
        .tv_nsec = (long)(milliseconds % 1000u) * 1000000L
    };
    while (nanosleep(&delay, &delay) != 0 && errno == EINTR) {
    }
}

void pb_task_yield(void)
{
    sched_yield();
}

static uint64_t monotonic_ns(void)
{
    struct timespec now;
    if (clock_gettime(CLOCK_MONOTONIC, &now) != 0) return 0;
    return (uint64_t)now.tv_sec * 1000000000u + (uint64_t)now.tv_nsec;
}

void pb_deadline_init(pb_deadline_t *deadline)
{
    deadline->at = monotonic_ns();
}

void pb_task_delay_until(pb_deadline_t *deadline, uint32_t interval_ms)
{
    deadline->at += (uint64_t)interval_ms * 1000000u;
    struct timespec at = {
        .tv_sec  = (time_t)(deadline->at / 1000000000u),
        .tv_nsec = (long)(deadline->at % 1000000000u),
    };
    // Absolute sleep: a deadline already in the past returns at once.
    while (clock_nanosleep(CLOCK_MONOTONIC, TIMER_ABSTIME, &at, NULL) == EINTR) {
    }
}

typedef struct {
    void (*fn)(void *);
    void *arg;
} pb_task_start_t;

static void *pb_task_start(void *opaque)
{
    pb_task_start_t start = *(pb_task_start_t *)opaque;
    free(opaque);
    start.fn(start.arg);
    return NULL;
}

// Floor for thread stacks (see platform.h): well above any target's
// PTHREAD_STACK_MIN and musl's small default; costs only address space.
#define PB_LINUX_STACK_FLOOR (512u * 1024u)

int pb_task_create(void (*fn)(void *), void *arg, const char *name,
                    size_t stack_bytes, pb_task_prio_t prio)
{
    (void)name;
    (void)prio;  // regular threads; the host scheduler has no RT need here
    if (!fn) return PB_FAIL;

    pb_task_start_t *start = malloc(sizeof(*start));
    if (!start) return PB_FAIL;
    start->fn = fn;
    start->arg = arg;

    size_t stack = stack_bytes > PB_LINUX_STACK_FLOOR
                 ? stack_bytes : PB_LINUX_STACK_FLOOR;
    long page = sysconf(_SC_PAGESIZE);
    if (page > 0) {
        stack = (stack + (size_t)page - 1) / (size_t)page * (size_t)page;
    }

    pthread_attr_t attributes;
    if (pthread_attr_init(&attributes) != 0) {
        free(start);
        return PB_FAIL;
    }
    pthread_t thread;
    int rc = pthread_attr_setstacksize(&attributes, stack);
    if (rc == 0) {
        pthread_attr_setdetachstate(&attributes, PTHREAD_CREATE_DETACHED);
        rc = pthread_create(&thread, &attributes, pb_task_start, start);
    }
    pthread_attr_destroy(&attributes);
    if (rc != 0) {
        free(start);
        return PB_FAIL;
    }
    return PB_OK;
}

pb_event_t *pb_event_create(void)
{
    pb_event_t *event = calloc(1, sizeof(*event));
    if (!event) return NULL;
    if (pthread_mutex_init(&event->mutex, NULL) != 0) {
        free(event);
        return NULL;
    }

    pthread_condattr_t attributes;
    if (pthread_condattr_init(&attributes) != 0) {
        pthread_mutex_destroy(&event->mutex);
        free(event);
        return NULL;
    }
    int rc = pthread_condattr_setclock(&attributes, CLOCK_MONOTONIC);
    if (rc == 0) rc = pthread_cond_init(&event->changed, &attributes);
    pthread_condattr_destroy(&attributes);
    if (rc != 0) {
        pthread_mutex_destroy(&event->mutex);
        free(event);
        return NULL;
    }
    return event;
}

void pb_event_destroy(pb_event_t *event)
{
    if (!event) return;
    pthread_cond_destroy(&event->changed);
    pthread_mutex_destroy(&event->mutex);
    free(event);
}

void pb_event_set(pb_event_t *event, uint32_t bits)
{
    if (!event) return;
    pthread_mutex_lock(&event->mutex);
    event->bits |= bits;
    pthread_cond_signal(&event->changed);
    pthread_mutex_unlock(&event->mutex);
}

uint32_t pb_event_wait_s(pb_event_t *event, uint32_t timeout_s)
{
    if (!event) return 0;
    pthread_mutex_lock(&event->mutex);
    if (timeout_s > 0 && event->bits == 0) {
        struct timespec deadline;
        clock_gettime(CLOCK_MONOTONIC, &deadline);
        deadline.tv_sec += (time_t)timeout_s;
        while (event->bits == 0) {
            int rc = pthread_cond_timedwait(&event->changed, &event->mutex,
                                            &deadline);
            if (rc != 0) break;
        }
    }
    uint32_t bits = event->bits;
    event->bits = 0;
    pthread_mutex_unlock(&event->mutex);
    return bits;
}

const char *pb_kv_err_name(int result)
{
    switch (result) {
    case PB_KV_OK:        return "OK";
    case PB_KV_NOT_FOUND: return "not found";
    default:              return "key-value store error";
    }
}

// Drops this namespace's entries; the other namespaces' lines stay.
static void pb_kv_clear_items(pb_kv_t *store)
{
    pb_kv_item_t *item = store->items;
    while (item) {
        pb_kv_item_t *next = item->next;
        free(item->key);
        free(item->value);
        free(item);
        item = next;
    }
    store->items = NULL;
}

static void pb_kv_clear(pb_kv_t *store)
{
    pb_kv_clear_items(store);
    pb_kv_raw_t *raw = store->preserved;
    while (raw) {
        pb_kv_raw_t *next = raw->next;
        free(raw->line);
        free(raw);
        raw = next;
    }
    store->preserved = NULL;
    store->preserved_tail = NULL;
}

static int pb_kv_preserve_line(pb_kv_t *store, const char *line)
{
    pb_kv_raw_t *raw = calloc(1, sizeof(*raw));
    if (!raw) return PB_KV_ERROR;
    raw->line = strdup(line);
    if (!raw->line) {
        free(raw);
        return PB_KV_ERROR;
    }
    if (store->preserved_tail) store->preserved_tail->next = raw;
    else store->preserved = raw;
    store->preserved_tail = raw;
    return PB_KV_OK;
}

static pb_kv_item_t *pb_kv_find(pb_kv_t *store, const char *key)
{
    for (pb_kv_item_t *item = store->items; item; item = item->next)
        if (strcmp(item->key, key) == 0) return item;
    return NULL;
}

static int pb_kv_assign(pb_kv_t *store, const char *key, const char *value)
{
    pb_kv_item_t *item = pb_kv_find(store, key);
    if (!item) {
        item = calloc(1, sizeof(*item));
        if (!item) return PB_KV_ERROR;
        item->key = strdup(key);
        if (!item->key) {
            free(item);
            return PB_KV_ERROR;
        }
        item->next = store->items;
        store->items = item;
    }
    char *copy = strdup(value);
    if (!copy) return PB_KV_ERROR;
    free(item->value);
    item->value = copy;
    return PB_KV_OK;
}

static char *pb_kv_trim(char *text)
{
    while (*text == ' ' || *text == '\t') text++;
    char *end = text + strlen(text);
    while (end > text && (end[-1] == ' ' || end[-1] == '\t'
                          || end[-1] == '\r' || end[-1] == '\n')) end--;
    *end = '\0';
    return text;
}

static int pb_kv_load(pb_kv_t *store)
{
    FILE *input = fopen(store->path, "r");
    if (!input) return errno == ENOENT ? PB_KV_NOT_FOUND : PB_KV_ERROR;
    char section[128] = "";
    char line[4096];
    int result = PB_KV_OK;
    while (fgets(line, sizeof(line), input)) {
        // Strip only the line terminator: a value's spaces are content (a
        // password with a leading or trailing space must round-trip).
        size_t line_len = strlen(line);
        while (line_len > 0 && (line[line_len - 1] == '\n'
                                || line[line_len - 1] == '\r')) {
            line[--line_len] = '\0';
        }
        char probe[4096];
        snprintf(probe, sizeof(probe), "%s", line);
        char *entry = pb_kv_trim(probe);
        size_t length = strlen(entry);
        if (entry[0] == '[' && length > 2 && entry[length - 1] == ']') {
            entry[length - 1] = '\0';
            snprintf(section, sizeof(section), "%s", entry + 1);
        }
        if (strcmp(section, store->name_space) != 0) {
            if (pb_kv_preserve_line(store, line) != PB_KV_OK) {
                result = PB_KV_ERROR;
                break;
            }
            continue;
        }
        if (!entry[0] || entry[0] == '#' || entry[0] == ';') continue;
        if (entry[0] == '[') continue;
        char *separator = strchr(line, '=');
        if (!separator) continue;
        *separator = '\0';
        char *key = pb_kv_trim(line);
        const char *value = separator + 1;
        if (key[0] && pb_kv_assign(store, key, value) != PB_KV_OK) {
            result = PB_KV_ERROR;
            break;
        }
    }
    if (ferror(input)) result = PB_KV_ERROR;
    fclose(input);
    return result;
}

static const char *pb_kv_config_path(void)
{
    const char *path = getenv("PHONEBLOCK_CONFIG_FILE");
    if (path && path[0]) return path;
    const char *xdg = getenv("XDG_CONFIG_HOME");
    if (xdg && xdg[0]) {
        static char xdg_path[4096];
        snprintf(xdg_path, sizeof(xdg_path), "%s/phoneblock/dongle.conf", xdg);
        return xdg_path;
    }
    const char *home = getenv("HOME");
    if (home && home[0]) {
        static char home_path[4096];
        snprintf(home_path, sizeof(home_path), "%s/.config/phoneblock/dongle.conf",
                 home);
        return home_path;
    }
    return "/var/lib/phoneblock/dongle.conf";
}

static int pb_kv_make_parent_dirs(const char *path)
{
    char *copy = strdup(path);
    if (!copy) return PB_KV_ERROR;
    for (char *slash = strchr(copy + 1, '/'); slash;
         slash = strchr(slash + 1, '/')) {
        *slash = '\0';
        if (mkdir(copy, 0750) != 0 && errno != EEXIST) {
            free(copy);
            return PB_KV_ERROR;
        }
        *slash = '/';
    }
    free(copy);
    return PB_KV_OK;
}

static void pb_kv_lock(pb_kv_t *store)
{
    // The settings file is shared between the daemon and any CLI touching
    // it; hold an exclusive lock for writers (shared for readers) so an
    // open→edit→commit sequence cannot interleave with another process.
    store->lock_fd = -1;
    size_t lock_len = strlen(store->path) + sizeof(".lock");
    char *lock_path = malloc(lock_len);
    if (!lock_path) return;
    snprintf(lock_path, lock_len, "%s.lock", store->path);
    int fd = open(lock_path, O_RDWR | O_CREAT | O_CLOEXEC, 0600);
    if (fd < 0) {
        pb_log_warn("kv", "open %s: %s — continuing without the lock",
                    lock_path, strerror(errno));
    } else {
        if (flock(fd, store->writable ? LOCK_EX : LOCK_SH) == 0) {
            store->lock_fd = fd;
        } else {
            pb_log_warn("kv", "flock %s: %s — continuing without the lock",
                        lock_path, strerror(errno));
            close(fd);
        }
    }
    free(lock_path);
}

int pb_kv_open(const char *name_space, bool writable, pb_kv_t **store_out)
{
    if (!name_space || !store_out) return PB_KV_ERROR;
    *store_out = NULL;
    pb_kv_t *store = calloc(1, sizeof(*store));
    if (!store) return PB_KV_ERROR;
    store->lock_fd = -1;
    store->path = strdup(pb_kv_config_path());
    store->name_space = strdup(name_space);
    store->writable = writable;
    if (!store->path || !store->name_space) {
        pb_kv_close(store);
        return PB_KV_ERROR;
    }
    pb_kv_lock(store);
    int result = pb_kv_load(store);
    if (result == PB_KV_NOT_FOUND && writable) result = PB_KV_OK;
    if (result != PB_KV_OK) {
        pb_kv_close(store);
        return result;
    }
    *store_out = store;
    return PB_KV_OK;
}

void pb_kv_close(pb_kv_t *store)
{
    if (!store) return;
    if (store->lock_fd >= 0) {
        flock(store->lock_fd, LOCK_UN);
        close(store->lock_fd);
    }
    pb_kv_clear(store);
    free(store->path);
    free(store->name_space);
    free(store);
}

int pb_kv_get_string(pb_kv_t *store, const char *key,
                     char *value, size_t *value_len)
{
    if (!store || !key || !value || !value_len) return PB_KV_ERROR;
    pb_kv_item_t *item = pb_kv_find(store, key);
    if (!item) return PB_KV_NOT_FOUND;
    size_t required = strlen(item->value) + 1;
    if (*value_len < required) {
        *value_len = required;
        return PB_KV_ERROR;
    }
    memcpy(value, item->value, required);
    *value_len = required;
    return PB_KV_OK;
}

int pb_kv_get_i32(pb_kv_t *store, const char *key, int32_t *value)
{
    if (!store || !key || !value) return PB_KV_ERROR;
    pb_kv_item_t *item = pb_kv_find(store, key);
    if (!item) return PB_KV_NOT_FOUND;
    char *end = NULL;
    long parsed = strtol(item->value, &end, 10);
    if (!end || *end || parsed < INT32_MIN || parsed > INT32_MAX)
        return PB_KV_ERROR;
    *value = (int32_t)parsed;
    return PB_KV_OK;
}

int pb_kv_set_string(pb_kv_t *store, const char *key, const char *value)
{
    if (!store || !store->writable || !key || !value
            || strchr(key, '=') || strchr(key, '\n') || strchr(value, '\n'))
        return PB_KV_ERROR;
    return pb_kv_assign(store, key, value);
}

int pb_kv_set_i32(pb_kv_t *store, const char *key, int32_t value)
{
    char text[16];
    snprintf(text, sizeof(text), "%ld", (long)value);
    return pb_kv_set_string(store, key, text);
}

int pb_kv_erase_key(pb_kv_t *store, const char *key)
{
    if (!store || !store->writable || !key) return PB_KV_ERROR;
    pb_kv_item_t **link = &store->items;
    while (*link) {
        pb_kv_item_t *item = *link;
        if (strcmp(item->key, key) == 0) {
            *link = item->next;
            free(item->key);
            free(item->value);
            free(item);
            return PB_KV_OK;
        }
        link = &item->next;
    }
    return PB_KV_NOT_FOUND;
}

int pb_kv_erase_all(pb_kv_t *store)
{
    if (!store || !store->writable) return PB_KV_ERROR;
    pb_kv_clear_items(store);
    return PB_KV_OK;
}

int pb_kv_commit(pb_kv_t *store)
{
    if (!store || !store->writable) return PB_KV_ERROR;
    if (pb_kv_make_parent_dirs(store->path) != PB_KV_OK) return PB_KV_ERROR;
    size_t path_len = strlen(store->path) + sizeof(".tmpXXXXXX");
    char *temp_path = malloc(path_len);
    if (!temp_path) return PB_KV_ERROR;
    snprintf(temp_path, path_len, "%s.tmpXXXXXX", store->path);
    int fd = mkstemp(temp_path);
    if (fd < 0) {
        free(temp_path);
        return PB_KV_ERROR;
    }
    fchmod(fd, S_IRUSR | S_IWUSR);
    FILE *output = fdopen(fd, "w");
    if (!output) {
        close(fd);
        unlink(temp_path);
        free(temp_path);
        return PB_KV_ERROR;
    }
    int result = PB_KV_OK;
    for (pb_kv_raw_t *raw = store->preserved; result == PB_KV_OK && raw;
         raw = raw->next) {
        if (fprintf(output, "%s\n", raw->line) < 0) result = PB_KV_ERROR;
    }
    if (result == PB_KV_OK)
        result = fprintf(output, "[%s]\n", store->name_space) < 0
               ? PB_KV_ERROR : PB_KV_OK;
    for (pb_kv_item_t *item = store->items; result == PB_KV_OK && item;
         item = item->next) {
        if (strchr(item->key, ']')) {
            result = PB_KV_ERROR;
            break;
        }
        if (fprintf(output, "%s=%s\n", item->key, item->value) < 0)
            result = PB_KV_ERROR;
    }
    if (fflush(output) != 0 || fsync(fd) != 0) result = PB_KV_ERROR;
    if (fclose(output) != 0) result = PB_KV_ERROR;
    if (result == PB_KV_OK && rename(temp_path, store->path) != 0)
        result = PB_KV_ERROR;
    if (result != PB_KV_OK) unlink(temp_path);
    free(temp_path);
    return result;
}

pb_mutex_t *pb_mutex_create(void)
{
    pb_mutex_t *mutex = calloc(1, sizeof(*mutex));
    if (!mutex || pthread_mutex_init(&mutex->mutex, NULL) != 0) {
        free(mutex);
        return NULL;
    }
    return mutex;
}

void pb_mutex_lock(pb_mutex_t *mutex)
{
    if (mutex) pthread_mutex_lock(&mutex->mutex);
}

void pb_mutex_unlock(pb_mutex_t *mutex)
{
    if (mutex) pthread_mutex_unlock(&mutex->mutex);
}

// Locally administered MACs (bit 1 of the first octet) are what Docker
// bridges, veth pairs, VPN taps and randomized Wi-Fi addresses use.
static bool mac_is_local(const uint8_t *mac) { return (mac[0] & 0x02) != 0; }

static bool mac_is_zero(const uint8_t *mac)
{
    for (int i = 0; i < 6; i++) if (mac[i]) return false;
    return true;
}

int pb_get_mac(uint8_t mac[6])
{
    if (!mac) return -1;
    memset(mac, 0, 6);

    struct ifaddrs *interfaces;
    if (getifaddrs(&interfaces) != 0) return -1;

    // getifaddrs() order is not guaranteed; pick by rank, then lowest ifindex:
    // a burned-in address beats a locally administered one.
    int best_rank = -1, best_index = 0;
    for (struct ifaddrs *entry = interfaces; entry; entry = entry->ifa_next) {
        if (!entry->ifa_addr || (entry->ifa_flags & IFF_LOOPBACK)) continue;
        if (entry->ifa_addr->sa_family != AF_PACKET) continue;
        struct sockaddr_ll *address = (struct sockaddr_ll *)entry->ifa_addr;
        if (address->sll_halen != 6 || mac_is_zero(address->sll_addr)) continue;
        int rank = mac_is_local(address->sll_addr) ? 0 : 1;
        if (rank > best_rank
                || (rank == best_rank && address->sll_ifindex < best_index)) {
            best_rank = rank;
            best_index = address->sll_ifindex;
            memcpy(mac, address->sll_addr, 6);
        }
    }
    freeifaddrs(interfaces);
    return best_rank >= 0 ? 0 : -1;
}
