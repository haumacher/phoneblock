#define _GNU_SOURCE

#include "platform.h"

#include <errno.h>
#include <fcntl.h>
#include <ifaddrs.h>
#include <limits.h>
#include <net/if.h>
#include <netdb.h>
#include <netpacket/packet.h>
#include <curl/curl.h>
#include <openssl/evp.h>
#include <openssl/ssl.h>
#include <openssl/x509.h>
#include <pthread.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/random.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/time.h>
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

struct pb_tls {
    SSL_CTX *context;
    SSL *session;
    int fd;
};

typedef struct pb_kv_item {
    char *key;
    char *value;
    struct pb_kv_item *next;
} pb_kv_item_t;

struct pb_kv {
    char *path;
    char *name_space;
    bool writable;
    pb_kv_item_t *items;
};

struct pb_sha256 {
    EVP_MD_CTX *context;
    bool finished;
};

typedef struct {
    char *buffer;
    size_t capacity;
    size_t length;
    pb_http_data_fn on_data;
    void *data_context;
} pb_http_response_buffer_t;

static pthread_once_t s_curl_once = PTHREAD_ONCE_INIT;
static CURLcode s_curl_init_result;

static void pb_curl_init(void)
{
    s_curl_init_result = curl_global_init(CURL_GLOBAL_DEFAULT);
}

static size_t pb_http_write(void *data, size_t size, size_t count, void *opaque)
{
    pb_http_response_buffer_t *response = opaque;
    size_t length = size * count;
    if (response->on_data)
        response->on_data(data, length, response->data_context);
    size_t available = response->capacity > response->length + 1
                     ? response->capacity - response->length - 1 : 0;
    size_t copied = length < available ? length : available;
    if (copied > 0) {
        memcpy(response->buffer + response->length, data, copied);
        response->length += copied;
        response->buffer[response->length] = '\0';
    }
    return length;
}

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

bool pb_md5(const void *input, size_t input_len, uint8_t output[16])
{
    unsigned int output_len = 0;
    return input && output
        && EVP_Digest(input, input_len, output, &output_len, EVP_md5(), NULL) == 1
        && output_len == 16;
}

    bool pb_sha1(const void *input, size_t input_len, uint8_t output[20])
    {
        unsigned int output_len = 0;
        return input && output
        && EVP_Digest(input, input_len, output, &output_len, EVP_sha1(), NULL) == 1
        && output_len == 20;
    }

bool pb_ecdsa_p256_verify(const uint8_t *public_key_der, size_t public_key_len,
                          const uint8_t hash[32], const uint8_t *signature,
                          size_t signature_len)
{
    if (!public_key_der || public_key_len > LONG_MAX || !hash || !signature
            || signature_len == 0) return false;
    const unsigned char *cursor = public_key_der;
    EVP_PKEY *key = d2i_PUBKEY(NULL, &cursor, (long)public_key_len);
    if (!key) return false;
    bool valid = false;
    if (EVP_PKEY_base_id(key) == EVP_PKEY_EC && EVP_PKEY_bits(key) == 256) {
        EVP_PKEY_CTX *context = EVP_PKEY_CTX_new(key, NULL);
        if (context && EVP_PKEY_verify_init(context) > 0
                && EVP_PKEY_CTX_set_signature_md(context, EVP_sha256()) > 0) {
            valid = EVP_PKEY_verify(context, signature, signature_len,
                                    hash, 32) == 1;
        }
        EVP_PKEY_CTX_free(context);
    }
    EVP_PKEY_free(key);
    return valid;
}

pb_sha256_t *pb_sha256_create(void)
{
    pb_sha256_t *context = calloc(1, sizeof(*context));
    if (!context) return NULL;
    context->context = EVP_MD_CTX_new();
    if (!context->context
            || EVP_DigestInit_ex(context->context, EVP_sha256(), NULL) != 1) {
        EVP_MD_CTX_free(context->context);
        free(context);
        return NULL;
    }
    return context;
}

bool pb_sha256_update(pb_sha256_t *context, const void *data, size_t length)
{
    return context && !context->finished && data
        && EVP_DigestUpdate(context->context, data, length) == 1;
}

bool pb_sha256_finish(pb_sha256_t *context, uint8_t output[32])
{
    if (!context || !output || context->finished) return false;
    unsigned int length = 0;
    context->finished = true;
    return EVP_DigestFinal_ex(context->context, output, &length) == 1
        && length == 32;
}

void pb_sha256_destroy(pb_sha256_t *context)
{
    if (!context) return;
    EVP_MD_CTX_free(context->context);
    free(context);
}

int pb_base64_encode(unsigned char *dst, size_t dst_len, size_t *output_len,
                     const unsigned char *src, size_t src_len)
{
    if (!dst || !output_len || (!src && src_len) || src_len > INT_MAX)
        return -1;
    size_t encoded_len = 4 * ((src_len + 2) / 3);
    if (encoded_len >= dst_len) return -1;
    int result = EVP_EncodeBlock(dst, src, (int)src_len);
    if (result < 0) return -1;
    *output_len = (size_t)result;
    return 0;
}

int pb_base64_decode(unsigned char *dst, size_t dst_len, size_t *output_len,
                     const unsigned char *src, size_t src_len)
{
    if (!dst || !output_len || (!src && src_len) || src_len > INT_MAX
            || src_len % 4 != 0 || src_len / 4 * 3 > dst_len) {
        return -1;
    }
    int result = EVP_DecodeBlock(dst, src, (int)src_len);
    if (result < 0) return -1;
    size_t padding = src_len > 0 && src[src_len - 1] == '=' ? 1 : 0;
    if (src_len > 1 && src[src_len - 2] == '=') padding++;
    *output_len = (size_t)result - padding;
    return 0;
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

uint32_t pb_event_wait(pb_event_t *event, uint32_t timeout_ms)
{
    if (!event) return 0;
    pthread_mutex_lock(&event->mutex);
    if (timeout_ms > 0 && event->bits == 0) {
        struct timespec deadline;
        clock_gettime(CLOCK_MONOTONIC, &deadline);
        deadline.tv_sec += timeout_ms / 1000u;
        deadline.tv_nsec += (long)(timeout_ms % 1000u) * 1000000L;
        if (deadline.tv_nsec >= 1000000000L) {
            deadline.tv_sec++;
            deadline.tv_nsec -= 1000000000L;
        }
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

pb_tls_t *pb_tls_connect(const char *host, int port, const char *server_name,
                         uint32_t timeout_ms)
{
    if (!host || !host[0] || port <= 0 || port > 65535) return NULL;

    char port_text[8];
    snprintf(port_text, sizeof(port_text), "%d", port);
    struct addrinfo hints = { .ai_family = AF_INET,
                              .ai_socktype = SOCK_STREAM };
    struct addrinfo *addresses = NULL;
    if (getaddrinfo(host, port_text, &hints, &addresses) != 0) return NULL;

    int fd = -1;
    for (struct addrinfo *address = addresses; address; address = address->ai_next) {
        fd = socket(address->ai_family, address->ai_socktype,
                    address->ai_protocol);
        if (fd < 0) continue;
        if (connect(fd, address->ai_addr, address->ai_addrlen) == 0) break;
        close(fd);
        fd = -1;
    }
    freeaddrinfo(addresses);
    if (fd < 0) return NULL;

    struct timeval timeout = {
        .tv_sec = timeout_ms / 1000u,
        .tv_usec = (timeout_ms % 1000u) * 1000u,
    };
    setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));
    setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof(timeout));

    pb_tls_t *tls = calloc(1, sizeof(*tls));
    if (!tls) {
        close(fd);
        return NULL;
    }
    tls->fd = fd;
    tls->context = SSL_CTX_new(TLS_client_method());
    if (!tls->context
            || SSL_CTX_set_default_verify_paths(tls->context) != 1) {
        pb_tls_destroy(tls);
        return NULL;
    }
    SSL_CTX_set_verify(tls->context, SSL_VERIFY_PEER, NULL);
    tls->session = SSL_new(tls->context);
    const char *verify_name = server_name && server_name[0]
                            ? server_name : host;
    if (!tls->session || SSL_set_fd(tls->session, fd) != 1
            || SSL_set_tlsext_host_name(tls->session, verify_name) != 1
            || SSL_set1_host(tls->session, verify_name) != 1
            || SSL_connect(tls->session) != 1) {
        pb_tls_destroy(tls);
        return NULL;
    }
    return tls;
}

int pb_tls_fd(pb_tls_t *tls)
{
    return tls ? tls->fd : -1;
}

int pb_tls_pending(pb_tls_t *tls)
{
    return tls ? SSL_pending(tls->session) : 0;
}

static int pb_tls_io_result(pb_tls_t *tls, int result)
{
    int error = SSL_get_error(tls->session, result);
    if (error == SSL_ERROR_WANT_READ) return PB_TLS_WANT_READ;
    if (error == SSL_ERROR_WANT_WRITE) return PB_TLS_WANT_WRITE;
    return -1;
}

int pb_tls_read(pb_tls_t *tls, void *buffer, size_t capacity)
{
    if (!tls || !buffer || capacity == 0 || capacity > INT_MAX) return -1;
    int result = SSL_read(tls->session, buffer, (int)capacity);
    if (result > 0 || SSL_get_error(tls->session, result) == SSL_ERROR_ZERO_RETURN)
        return result;
    return pb_tls_io_result(tls, result);
}

int pb_tls_write(pb_tls_t *tls, const void *buffer, size_t length)
{
    if (!tls || (!buffer && length) || length > INT_MAX) return -1;
    int result = SSL_write(tls->session, buffer, (int)length);
    return result > 0 ? result : pb_tls_io_result(tls, result);
}

void pb_tls_destroy(pb_tls_t *tls)
{
    if (!tls) return;
    SSL_free(tls->session);
    SSL_CTX_free(tls->context);
    if (tls->fd >= 0) close(tls->fd);
    free(tls);
}

int pb_http_request(const char *method, const char *url,
                    const pb_http_header_t *headers, size_t header_count,
                    const void *body, size_t body_len, uint32_t timeout_ms,
                    char *response, size_t response_cap,
            size_t *response_len, int *http_status,
            pb_http_data_fn on_data, void *data_context,
            pb_http_timing_t *timing)
{
    if (!method || !url || (!response && !on_data)
        || (response && response_cap == 0)
            || (!body && body_len)) return PB_HTTP_ERROR;
    if (response) response[0] = '\0';
    if (response_len) *response_len = 0;
    if (http_status) *http_status = 0;
    if (timing) memset(timing, 0, sizeof(*timing));
    pthread_once(&s_curl_once, pb_curl_init);
    if (s_curl_init_result != CURLE_OK) return PB_HTTP_ERROR;

    CURL *curl = curl_easy_init();
    if (!curl) return PB_HTTP_ERROR;
    struct curl_slist *curl_headers = NULL;
    for (size_t i = 0; i < header_count; i++) {
        if (!headers[i].name || !headers[i].value) continue;
        size_t line_len = strlen(headers[i].name) + strlen(headers[i].value) + 3;
        char *line = malloc(line_len);
        if (!line) {
            curl_slist_free_all(curl_headers);
            curl_easy_cleanup(curl);
            return PB_HTTP_ERROR;
        }
        snprintf(line, line_len, "%s: %s", headers[i].name, headers[i].value);
        struct curl_slist *updated = curl_slist_append(curl_headers, line);
        free(line);
        if (!updated) {
            curl_slist_free_all(curl_headers);
            curl_easy_cleanup(curl);
            return PB_HTTP_ERROR;
        }
        curl_headers = updated;
    }

    pb_http_response_buffer_t output = {
        .buffer = response,
        .capacity = response_cap,
        .on_data = on_data,
        .data_context = data_context,
    };
    curl_easy_setopt(curl, CURLOPT_URL, url);
    curl_easy_setopt(curl, CURLOPT_CUSTOMREQUEST, method);
    curl_easy_setopt(curl, CURLOPT_NOSIGNAL, 1L);
    curl_easy_setopt(curl, CURLOPT_TIMEOUT_MS, (long)timeout_ms);
    curl_easy_setopt(curl, CURLOPT_USERAGENT, "phoneblock-dongle");
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, pb_http_write);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, &output);
    if (curl_headers) curl_easy_setopt(curl, CURLOPT_HTTPHEADER, curl_headers);
    if (body || body_len) {
        curl_easy_setopt(curl, CURLOPT_POSTFIELDS, body ? body : "");
        curl_easy_setopt(curl, CURLOPT_POSTFIELDSIZE_LARGE, (curl_off_t)body_len);
    }

    CURLcode result = curl_easy_perform(curl);
    long status = 0;
    curl_off_t connect_us = 0, appconnect_us = 0, pretransfer_us = 0;
    curl_off_t first_byte_us = 0, total_us = 0;
    curl_easy_getinfo(curl, CURLINFO_CONNECT_TIME_T, &connect_us);
    curl_easy_getinfo(curl, CURLINFO_APPCONNECT_TIME_T, &appconnect_us);
    curl_easy_getinfo(curl, CURLINFO_PRETRANSFER_TIME_T, &pretransfer_us);
    curl_easy_getinfo(curl, CURLINFO_STARTTRANSFER_TIME_T, &first_byte_us);
    curl_easy_getinfo(curl, CURLINFO_TOTAL_TIME_T, &total_us);
    curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &status);
    if (http_status) *http_status = (int)status;
    if (response_len) *response_len = output.length;
    if (timing) {
        curl_off_t connected = appconnect_us > 0 ? appconnect_us : connect_us;
        timing->connect_us = connected > 0 ? (uint64_t)connected : 0;
        timing->request_us = pretransfer_us > connected
                           ? (uint64_t)(pretransfer_us - connected) : 0;
        timing->wait_us = first_byte_us > pretransfer_us
                        ? (uint64_t)(first_byte_us - pretransfer_us) : 0;
        timing->download_us = total_us > first_byte_us
                            ? (uint64_t)(total_us - first_byte_us) : 0;
        timing->total_us = total_us > 0 ? (uint64_t)total_us : 0;
        timing->valid = result == CURLE_OK;
    }
    curl_slist_free_all(curl_headers);
    curl_easy_cleanup(curl);

    if (result == CURLE_OK) return PB_HTTP_OK;
    if (result == CURLE_COULDNT_CONNECT || result == CURLE_COULDNT_RESOLVE_HOST
            || result == CURLE_OPERATION_TIMEDOUT || result == CURLE_GOT_NOTHING)
        return PB_HTTP_CONNECT_ERROR;
    return PB_HTTP_ERROR;
}

static void pb_kv_clear(pb_kv_t *store)
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
        char *entry = pb_kv_trim(line);
        if (!entry[0] || entry[0] == '#' || entry[0] == ';') continue;
        size_t length = strlen(entry);
        if (entry[0] == '[' && length > 2 && entry[length - 1] == ']') {
            entry[length - 1] = '\0';
            snprintf(section, sizeof(section), "%s", entry + 1);
            continue;
        }
        if (strcmp(section, store->name_space) != 0) continue;
        char *separator = strchr(entry, '=');
        if (!separator) continue;
        *separator++ = '\0';
        char *key = pb_kv_trim(entry);
        char *value = pb_kv_trim(separator);
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

int pb_kv_open(const char *name_space, bool writable, pb_kv_t **store_out)
{
    if (!name_space || !store_out) return PB_KV_ERROR;
    *store_out = NULL;
    pb_kv_t *store = calloc(1, sizeof(*store));
    if (!store) return PB_KV_ERROR;
    store->path = strdup(pb_kv_config_path());
    store->name_space = strdup(name_space);
    store->writable = writable;
    if (!store->path || !store->name_space) {
        pb_kv_close(store);
        return PB_KV_ERROR;
    }
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
    pb_kv_clear(store);
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
    int result = fprintf(output, "[%s]\n", store->name_space) < 0
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
