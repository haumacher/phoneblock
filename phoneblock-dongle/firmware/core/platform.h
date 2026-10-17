#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

// --- Conventions ---------------------------------------------------------
//
// Result codes. Every fallible function returns int:
//
//   0         success (PB_OK / PB_KV_OK / PB_HTTP_OK / PB_SIG_VALID)
//   positive  completed with a benign domain outcome — key not found,
//             signature mismatch, bad key; not an error
//   negative  failure (PB_FAIL / PB_KV_ERROR / PB_HTTP_ERROR / PB_ERR_*)
//
// Where a failure's cause belongs in the log, the result may carry the
// platform's native code, and the matching companion names it:
// pb_kv_err_name(), pb_http_err_name(), or the `native` out-param of
// pb_ecdsa_p256_verify(). Never compare results against raw backend codes
// (esp_err_t, CURLcode, mbedTLS) directly. Generic operations whose only
// outcomes are "done" or "failed" use the PB_OK/PB_FAIL/PB_ERR_* family.
//
// bool is only for pure questions that cannot fail
// (pb_watchdog_is_subscribed). Functions that produce a value instead of a
// status return it directly and document their sentinel: pb_event_wait_s
// returns 0 on timeout, pb_task_stack_free returns -1 where the platform
// cannot tell, pb_tls_read/write return a byte count or PB_TLS_WANT_*.
//
// Buffers. The caller owns every buffer: output parameters get a capacity
// and, where text is produced, are always NUL-terminated on success; *_len
// out-params report the bytes written or needed. Context objects
// (pb_sha256_t, pb_tls_t, pb_kv_t, pb_event_t, pb_mutex_t) are owned by
// their creator and freed with the matching destroy/close call.
//
// Thread safety. Logging, pb_random_*, pb_monotonic_us, mutexes and events
// are task-safe. pb_http_request opens a fresh connection per call and holds
// no shared state, so concurrent calls are safe. Everything else is
// single-owner: a pb_tls_t connection, a pb_sha256_t context, and a pb_kv_t
// store must not be shared between tasks without external locking.

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
#define pb_log_debug(tag, ...) ESP_LOGD(tag, __VA_ARGS__)
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
// Compiled out, like ESP_LOGD at the firmware's default INFO log level; the
// dead call keeps the format string checked.
#define pb_log_debug(tag, ...) do { if (0) pb_log_info(tag, __VA_ARGS__); } while (0)
#endif

// --- Time, randomness, watchdog -----------------------------------------

uint64_t pb_monotonic_us(void);
const char *pb_firmware_version(void);
uint32_t pb_random_u32(void);
void pb_random_fill(void *out, size_t len);
// One-shot digests: PB_OK on success, PB_FAIL otherwise.
int pb_md5(const void *input, size_t input_len, uint8_t output[16]);
int pb_sha1(const void *input, size_t input_len, uint8_t output[20]);

// ECDSA-P256 verification of a SHA-256 hash against a DER
// SubjectPublicKeyInfo key. Returns PB_SIG_VALID, PB_SIG_BAD_KEY (key not
// parsable or not P-256) or PB_SIG_MISMATCH (signature doesn't verify).
// *native, if given, receives the backend's negative error code for the log
// (printed as "-0x%04x" of its negation): the mbedTLS return value on the
// ESP32 (e.g. -0x4e80 MBEDTLS_ERR_ECP_VERIFY_FAILED), the negated OpenSSL
// reason code on Linux; 0 when the backend reported none.
enum {
    PB_SIG_VALID = 0,
    PB_SIG_BAD_KEY = 1,
    PB_SIG_MISMATCH = 2,
};
int pb_ecdsa_p256_verify(const uint8_t *public_key_der, size_t public_key_len,
                         const uint8_t hash[32], const uint8_t *signature,
                         size_t signature_len, int *native);

typedef struct pb_sha256 pb_sha256_t;
// NULL when the context cannot be created; free with pb_sha256_destroy.
pb_sha256_t *pb_sha256_create(void);
// PB_OK on success, PB_FAIL otherwise (also when the context is finished).
int pb_sha256_update(pb_sha256_t *context, const void *data, size_t length);
int pb_sha256_finish(pb_sha256_t *context, uint8_t output[32]);
void pb_sha256_destroy(pb_sha256_t *context);
// Canonical padded Base64 in and out. PB_OK on success,
// PB_ERR_INVALID_SIZE when dst is too small, PB_FAIL on malformed input.
int pb_base64_encode(unsigned char *dst, size_t dst_len, size_t *output_len,
                     const unsigned char *src, size_t src_len);
int pb_base64_decode(unsigned char *dst, size_t dst_len, size_t *output_len,
                     const unsigned char *src, size_t src_len);
// Pure query: true when the calling task is subscribed to the watchdog.
bool pb_watchdog_is_subscribed(void);
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
// Returns PB_OK, or PB_FAIL if the task could not be created. There is
// deliberately no handle: a task may finish before the creator could store
// one. Callers that need a "running" flag set it before creating and clear
// it in the task.
int pb_task_create(void (*fn)(void *), void *arg, const char *name,
                   size_t stack_bytes, pb_task_prio_t prio);

typedef struct pb_event pb_event_t;

// A coalescing bit event. wait() returns and clears all pending bits, or 0
// when the timeout expires. A zero timeout polls without blocking.
//
// The timeout is in seconds, not milliseconds: the scheduler waits up to a
// day, and a millisecond count of that converted to FreeRTOS ticks overflows
// 32 bits (#348, see main/ticks_util.h). In seconds even a 24 h wait stays
// far inside uint32_t on both platforms.
pb_event_t *pb_event_create(void);
void pb_event_destroy(pb_event_t *event);
void pb_event_set(pb_event_t *event, uint32_t bits);
uint32_t pb_event_wait_s(pb_event_t *event, uint32_t timeout_s);

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

// Results of the pb_kv_* functions: PB_KV_OK, PB_KV_NOT_FOUND, or any other
// value for an error. Errors carry the platform's native code (esp_err_t
// from NVS on the ESP32, where PB_KV_ERROR coincides with ESP_FAIL), so
// logs can name the actual cause via pb_kv_err_name().
enum {
    PB_KV_OK = 0,
    PB_KV_NOT_FOUND = 1,
    PB_KV_ERROR = -1,
};

const char *pb_kv_err_name(int result);

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

// Result of pb_http_request(): PB_HTTP_OK once a response arrived (whatever
// its status), PB_HTTP_ERROR for bad arguments or a client that could not be
// set up, otherwise the platform's native transport error (esp_err_t such as
// ESP_ERR_HTTP_CONNECT on the ESP32, CURLcode on Linux).
// pb_http_err_name() names any result for logs and user-facing messages;
// pb_http_is_connect_error() tells whether the connection itself failed —
// the case worth a short retry (see tr064.c).
enum {
    PB_HTTP_OK = 0,
    PB_HTTP_ERROR = -1,
};

const char *pb_http_err_name(int result);
bool pb_http_is_connect_error(int result);

enum {
    PB_OK = 0,
    PB_FAIL = -1,
    PB_ERR_NO_MEM = -2,
    PB_ERR_INVALID_ARG = -3,
    PB_ERR_INVALID_SIZE = -4,
};

// Bounded synchronous HTTP request. A nonzero HTTP status is still a
// completed request; transport/setup failures are reported as described
// above. The platform adds no User-Agent of its own: callers pass
// http_util_user_agent() (main/http_util.h) in `headers`, so both builds
// identify as "PhoneBlock-Dongle/<version> (<device id>)". On the ESP32,
// HTTPS URLs are verified against the ESP-IDF certificate bundle.
int pb_http_request(const char *method, const char *url,
                    const pb_http_header_t *headers, size_t header_count,
                    const void *body, size_t body_len, uint32_t timeout_ms,
                    char *response, size_t response_cap,
                    size_t *response_len, int *http_status,
                    pb_http_data_fn on_data, void *data_context,
                    pb_http_timing_t *timing);

// Pull-style HTTP GET for downloads that are written out while they arrive
// (blocklist, i18n assets). The caller drives the transfer: open sends the
// request and reads the response headers, read hands out the body piece by
// piece, close ends it at any point — so a refusal (bad status, over budget,
// full filesystem) needs no abort protocol. On the ESP32 these are the
// esp_http_client_open / _fetch_headers / _read / _close calls themselves.
typedef struct pb_http_stream pb_http_stream_t;

// Opens a GET of `url` and reads the response headers. Returns PB_HTTP_OK
// with *stream set (close it with pb_http_stream_close() whatever the
// status), or a transport/setup error as for pb_http_request() with *stream
// NULL. *http_status is the response status (0 if none could be read);
// *content_length the announced body size, <= 0 when unknown (chunked).
// `timeout_ms` bounds each network wait, not the whole transfer.
int pb_http_stream_open(const char *url,
                        const pb_http_header_t *headers, size_t header_count,
                        uint32_t timeout_ms, pb_http_stream_t **stream,
                        int *http_status, int64_t *content_length);

// Reads up to `capacity` body bytes. Returns the byte count, 0 at the end of
// the body, or a negative value when the transfer failed.
int pb_http_stream_read(pb_http_stream_t *stream, void *buffer,
                        size_t capacity);

// Ends the transfer (also mid-body) and frees the stream. NULL is a no-op.
void pb_http_stream_close(pb_http_stream_t *stream);

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
