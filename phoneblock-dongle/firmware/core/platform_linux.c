#define _GNU_SOURCE

#include "platform.h"

#include <errno.h>
#include <fcntl.h>
#include <ifaddrs.h>
#include <net/if.h>
#include <netpacket/packet.h>
#include <pthread.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/random.h>
#include <time.h>
#include <unistd.h>

struct pb_mutex {
    pthread_mutex_t mutex;
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

int pb_watchdog_is_subscribed(void)
{
    return 0;
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

bool pb_task_create(void (*fn)(void *), void *arg, const char *name,
                    size_t stack_bytes, pb_task_prio_t prio)
{
    (void)name;
    (void)prio;  // regular threads; the host scheduler has no RT need here
    if (!fn) return false;

    pb_task_start_t *start = malloc(sizeof(*start));
    if (!start) return false;
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
        return false;
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
        return false;
    }
    return true;
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
