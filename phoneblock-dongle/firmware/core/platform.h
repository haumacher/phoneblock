#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

// --- Logging -----------------------------------------------------------
//
// On ESP-IDF these are the ESP_LOGx macros themselves: they add the
// "I (1234) tag: " prefix and the newline, and main/log_capture.c relies on
// that prefix to mirror WARN/ERROR lines into the web UI's "Protokoll"
// panel. They also keep the compile-time LOG_LOCAL_LEVEL filtering and
// printf format checking. (esp_log_writev() alone does neither prefix nor
// newline.)
#ifdef ESP_PLATFORM
#include "esp_log.h"
#define pb_log_info(tag, ...) ESP_LOGI(tag, __VA_ARGS__)
#define pb_log_warn(tag, ...) ESP_LOGW(tag, __VA_ARGS__)
#define pb_log_err(tag, ...)  ESP_LOGE(tag, __VA_ARGS__)
#else
// Linux prints the same layout as ESP-IDF ("W (<ms>) <tag>: <message>"), so
// console logs of both builds read and grep alike, and the shared parser in
// main/log_capture.c (host-tested) applies unchanged. Feeding WARN/ERROR into
// the web UI's error ring on Linux should not go through that parser, though:
// these functions know level, tag and message already and can hand them to
// the ring directly (stats_record_error() + log_capture_suppressed()). The
// text parser only exists because ESP-IDF's own libraries deliver nothing but
// formatted lines through the vprintf hook.
void pb_log_info(const char *tag, const char *fmt, ...)
    __attribute__((format(printf, 2, 3)));
void pb_log_warn(const char *tag, const char *fmt, ...)
    __attribute__((format(printf, 2, 3)));
void pb_log_err(const char *tag, const char *fmt, ...)
    __attribute__((format(printf, 2, 3)));
#endif

// --- Time, randomness, watchdog -----------------------------------------

uint64_t pb_monotonic_us(void);
const char *pb_firmware_version(void);
uint32_t pb_random_u32(void);
void pb_random_fill(void *out, size_t len);
bool pb_md5(const void *input, size_t input_len, uint8_t output[16]);
bool pb_sha1(const void *input, size_t input_len, uint8_t output[20]);
bool pb_ecdsa_p256_verify(const uint8_t *public_key_der, size_t public_key_len,
                          const uint8_t hash[32], const uint8_t *signature,
                          size_t signature_len);
typedef struct pb_sha256 pb_sha256_t;
pb_sha256_t *pb_sha256_create(void);
bool pb_sha256_update(pb_sha256_t *context, const void *data, size_t length);
bool pb_sha256_finish(pb_sha256_t *context, uint8_t output[32]);
void pb_sha256_destroy(pb_sha256_t *context);
int pb_base64_encode(unsigned char *dst, size_t dst_len, size_t *output_len,
                     const unsigned char *src, size_t src_len);
int pb_base64_decode(unsigned char *dst, size_t dst_len, size_t *output_len,
                     const unsigned char *src, size_t src_len);
int pb_watchdog_is_subscribed(void);
void pb_watchdog_subscribe(void);
void pb_watchdog_reset(void);

// --- Tasks ---------------------------------------------------------------

typedef enum {
    PB_PRIO_BACKGROUND,  // housekeeping work (scheduler)
    PB_PRIO_NORMAL,  // protocol / background work (SIP task)
    PB_PRIO_MEDIA,   // real-time media pacing, above PB_PRIO_NORMAL (RTP)
} pb_task_prio_t;

// Starts fn(arg) as a detached task that ends when fn returns.
//
// stack_bytes is the ESP32 stack budget in bytes, tuned on the hardware.
// The ESP32 implementation passes it straight through (ESP-IDF's FreeRTOS
// counts stack depth in bytes). Linux treats it as a hint only and applies a
// generous floor: frames are 64-bit, glibc/musl and their libraries need far
// more stack, PTHREAD_STACK_MIN differs per target, and musl's default thread
// stack is small — while an unused Linux stack costs only virtual memory.
//
// Returns false if the task could not be created. There is deliberately no
// handle: a task may finish before the creator could store one. Callers that
// need a "running" flag set it before creating and clear it in the task.
bool pb_task_create(void (*fn)(void *), void *arg, const char *name,
                    size_t stack_bytes, pb_task_prio_t prio);

typedef struct pb_event pb_event_t;

// A coalescing bit event. wait() returns and clears all pending bits, or 0
// when the timeout expires. A zero timeout polls without blocking.
pb_event_t *pb_event_create(void);
void pb_event_destroy(pb_event_t *event);
void pb_event_set(pb_event_t *event, uint32_t bits);
uint32_t pb_event_wait(pb_event_t *event, uint32_t timeout_ms);

typedef struct pb_tls pb_tls_t;

enum {
    PB_TLS_WANT_READ = -2,
    PB_TLS_WANT_WRITE = -3,
};

pb_tls_t *pb_tls_connect(const char *host, int port, const char *server_name,
                         uint32_t timeout_ms);
int pb_tls_fd(pb_tls_t *tls);
int pb_tls_pending(pb_tls_t *tls);
int pb_tls_read(pb_tls_t *tls, void *buffer, size_t capacity);
int pb_tls_write(pb_tls_t *tls, const void *buffer, size_t length);
void pb_tls_destroy(pb_tls_t *tls);

typedef struct {
    const char *name;
    const char *value;
} pb_http_header_t;

typedef void (*pb_http_data_fn)(const void *data, size_t length, void *context);

typedef struct {
    uint64_t connect_us;
    uint64_t request_us;
    uint64_t wait_us;
    uint64_t download_us;
    uint64_t total_us;
    bool valid;
} pb_http_timing_t;

typedef struct pb_kv pb_kv_t;

enum {
    PB_KV_OK = 0,
    PB_KV_NOT_FOUND = 1,
    PB_KV_ERROR = -1,
};

int pb_kv_open(const char *name_space, bool writable, pb_kv_t **store);
void pb_kv_close(pb_kv_t *store);
int pb_kv_get_string(pb_kv_t *store, const char *key,
                     char *value, size_t *value_len);
int pb_kv_get_i32(pb_kv_t *store, const char *key, int32_t *value);
int pb_kv_set_string(pb_kv_t *store, const char *key, const char *value);
int pb_kv_set_i32(pb_kv_t *store, const char *key, int32_t value);
int pb_kv_erase_key(pb_kv_t *store, const char *key);
int pb_kv_erase_all(pb_kv_t *store);
int pb_kv_commit(pb_kv_t *store);

enum {
    PB_HTTP_OK = 0,
    PB_HTTP_CONNECT_ERROR = -1,
    PB_HTTP_ERROR = -2,
};

enum {
    PB_OK = 0,
    PB_FAIL = -1,
    PB_ERR_NO_MEM = -2,
    PB_ERR_INVALID_ARG = -3,
    PB_ERR_INVALID_SIZE = -4,
};

// Bounded synchronous HTTP request. A nonzero HTTP status is still a
// completed request; transport/setup failures use the PB_HTTP_* result.
int pb_http_request(const char *method, const char *url,
                    const pb_http_header_t *headers, size_t header_count,
                    const void *body, size_t body_len, uint32_t timeout_ms,
                    char *response, size_t response_cap,
                    size_t *response_len, int *http_status,
                    pb_http_data_fn on_data, void *data_context,
                    pb_http_timing_t *timing);

// Sleeps at least `ms` milliseconds. On the ESP32 the sleep is rounded to
// whole FreeRTOS ticks (10 ms at the project's 100 Hz): a request below one
// tick becomes a plain yield, so don't build a polling loop on short sleeps.
void pb_task_sleep_ms(uint32_t ms);
void pb_task_yield(void);

// Minimum free stack the calling task has had so far (high-water mark), in
// bytes; -1 where the platform can't tell (Linux).
int pb_task_stack_free(void);

// Fixed-rate pacing without drift (RTP frames). Opaque: FreeRTOS ticks on the
// ESP32 (vTaskDelayUntil, tick-exact), CLOCK_MONOTONIC nanoseconds on Linux.
// Like vTaskDelayUntil, a missed deadline returns immediately and the next
// one still advances by one interval.
typedef struct {
    uint64_t at;
} pb_deadline_t;

void pb_deadline_init(pb_deadline_t *deadline);
void pb_task_delay_until(pb_deadline_t *deadline, uint32_t interval_ms);

// --- Mutex ---------------------------------------------------------------

typedef struct pb_mutex pb_mutex_t;

pb_mutex_t *pb_mutex_create(void);
void pb_mutex_lock(pb_mutex_t *mutex);
void pb_mutex_unlock(pb_mutex_t *mutex);

// --- Identity ------------------------------------------------------------

// MAC address identifying this device. On Linux: the globally administered
// (burned-in) address with the lowest interface index, skipping loopback.
// Locally administered addresses (Docker bridges, veth, VPN, randomized Wi-Fi
// MACs) only count when no burned-in one exists, so the result doesn't change
// when such interfaces come and go. Returns 0 on success, -1 if none found.
int pb_get_mac(uint8_t mac[6]);
