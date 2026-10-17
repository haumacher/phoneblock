#define _GNU_SOURCE

// Linux platform services backed by libcurl and OpenSSL: HTTP, TLS, hashes,
// ECDSA verification and base64. Kept apart from platform_linux.c so only
// the Linux firmware build and its network tests need libcurl4-openssl-dev
// and libssl-dev (`make test-linux`, see test/README.md); the default host
// test suite links platform_linux.c alone.

#include "platform.h"

#include <fcntl.h>
#include <limits.h>
#include <netdb.h>
#include <curl/curl.h>
#include <openssl/err.h>
#include <openssl/evp.h>
#include <openssl/ssl.h>
#include <openssl/x509.h>
#include <poll.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <unistd.h>

struct pb_tls {
    SSL_CTX *context;
    SSL *session;
    int fd;
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

int pb_md5(const void *input, size_t input_len, uint8_t output[16])
{
    unsigned int output_len = 0;
    return input && output
        && EVP_Digest(input, input_len, output, &output_len, EVP_md5(), NULL) == 1
        && output_len == 16 ? PB_OK : PB_FAIL;
}

int pb_sha1(const void *input, size_t input_len, uint8_t output[20])
{
    unsigned int output_len = 0;
    return input && output
        && EVP_Digest(input, input_len, output, &output_len, EVP_sha1(), NULL) == 1
        && output_len == 20 ? PB_OK : PB_FAIL;
}

int pb_ecdsa_p256_verify(const uint8_t *public_key_der, size_t public_key_len,
                         const uint8_t hash[32], const uint8_t *signature,
                         size_t signature_len, int *native)
{
    if (native) *native = 0;
    if (!public_key_der || public_key_len > LONG_MAX || !hash || !signature
            || signature_len == 0) return PB_SIG_MISMATCH;
    ERR_clear_error();
    const unsigned char *cursor = public_key_der;
    EVP_PKEY *key = d2i_PUBKEY(NULL, &cursor, (long)public_key_len);
    int verdict = PB_SIG_BAD_KEY;
    if (key && EVP_PKEY_base_id(key) == EVP_PKEY_EC && EVP_PKEY_bits(key) == 256) {
        verdict = PB_SIG_MISMATCH;
        EVP_PKEY_CTX *context = EVP_PKEY_CTX_new(key, NULL);
        if (context && EVP_PKEY_verify_init(context) > 0
                && EVP_PKEY_CTX_set_signature_md(context, EVP_sha256()) > 0
                && EVP_PKEY_verify(context, signature, signature_len,
                                   hash, 32) == 1) {
            verdict = PB_SIG_VALID;
        }
        EVP_PKEY_CTX_free(context);
    }
    EVP_PKEY_free(key);
    if (native && verdict != PB_SIG_VALID)
        *native = -(int)ERR_GET_REASON(ERR_get_error());
    return verdict;
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

int pb_sha256_update(pb_sha256_t *context, const void *data, size_t length)
{
    return context && !context->finished && data
        && EVP_DigestUpdate(context->context, data, length) == 1
        ? PB_OK : PB_FAIL;
}

int pb_sha256_finish(pb_sha256_t *context, uint8_t output[32])
{
    if (!context || !output || context->finished) return PB_FAIL;
    unsigned int length = 0;
    context->finished = true;
    return EVP_DigestFinal_ex(context->context, output, &length) == 1
        && length == 32 ? PB_OK : PB_FAIL;
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
        return PB_FAIL;
    size_t encoded_len = 4 * ((src_len + 2) / 3);
    if (encoded_len >= dst_len) return PB_ERR_INVALID_SIZE;
    int result = EVP_EncodeBlock(dst, src, (int)src_len);
    if (result < 0) return PB_FAIL;
    *output_len = (size_t)result;
    return PB_OK;
}

int pb_base64_decode(unsigned char *dst, size_t dst_len, size_t *output_len,
                     const unsigned char *src, size_t src_len)
{
    if (!dst || !output_len || (!src && src_len) || src_len > INT_MAX
            || src_len % 4 != 0) {
        return PB_FAIL;
    }
    if (src_len / 4 * 3 > dst_len) return PB_ERR_INVALID_SIZE;
    int result = EVP_DecodeBlock(dst, src, (int)src_len);
    if (result < 0) return PB_FAIL;
    size_t padding = src_len > 0 && src[src_len - 1] == '=' ? 1 : 0;
    if (src_len > 1 && src[src_len - 2] == '=') padding++;
    *output_len = (size_t)result - padding;
    return PB_OK;
}

pb_tls_t *pb_tls_connect(const char *host, int port, const char *server_name,
                         uint32_t timeout_ms)
{
    if (!host || !host[0] || port <= 0 || port > 65535) return NULL;

    char port_text[8];
    snprintf(port_text, sizeof(port_text), "%d", port);
    // AF_UNSPEC: the registrar may be reachable over IPv6 only.
    struct addrinfo hints = { .ai_family = AF_UNSPEC,
                              .ai_socktype = SOCK_STREAM };
    struct addrinfo *addresses = NULL;
    if (getaddrinfo(host, port_text, &hints, &addresses) != 0) return NULL;

    int fd = -1;
    for (struct addrinfo *address = addresses; address; address = address->ai_next) {
        fd = socket(address->ai_family, address->ai_socktype,
                    address->ai_protocol);
        if (fd < 0) continue;

        // connect() with a deadline: a blocking connect() ignores timeout_ms
        // entirely while the kernel retries an unreachable peer.
        int flags = fcntl(fd, F_GETFL, 0);
        if (flags < 0 || fcntl(fd, F_SETFL, flags | O_NONBLOCK) != 0) {
            close(fd);
            fd = -1;
            continue;
        }
        int rc = connect(fd, address->ai_addr, address->ai_addrlen);
        if (rc != 0 && errno == EINPROGRESS) {
            struct pollfd pfd = { .fd = fd, .events = POLLOUT };
            rc = poll(&pfd, 1, (int)timeout_ms);
            int error = 0;
            socklen_t error_len = sizeof(error);
            if (rc > 0
                    && getsockopt(fd, SOL_SOCKET, SO_ERROR, &error, &error_len) == 0
                    && error == 0) {
                rc = 0;
            } else {
                rc = -1;
            }
        }
        if (rc == 0) {
            // TLS operations get their per-call deadline from the socket
            // timeouts set below; restore blocking mode for them.
            fcntl(fd, F_SETFL, flags);
            break;
        }
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

// Builds a curl handle for one request with the options shared by
// pb_http_request() and pb_http_stream_open(); the header list goes to
// *curl_headers (free with curl_slist_free_all after the transfer). NULL when
// the handle or the header list cannot be built.
static CURL *pb_curl_create(const char *url, const char *method,
                            const pb_http_header_t *headers,
                            size_t header_count, uint32_t timeout_ms,
                            struct curl_slist **curl_headers)
{
    *curl_headers = NULL;
    CURL *curl = curl_easy_init();
    if (!curl) return NULL;
    for (size_t i = 0; i < header_count; i++) {
        if (!headers[i].name || !headers[i].value) continue;
        size_t line_len = strlen(headers[i].name) + strlen(headers[i].value) + 3;
        char *line = malloc(line_len);
        if (!line) goto fail;
        snprintf(line, line_len, "%s: %s", headers[i].name, headers[i].value);
        struct curl_slist *updated = curl_slist_append(*curl_headers, line);
        free(line);
        if (!updated) goto fail;
        *curl_headers = updated;
    }

    curl_easy_setopt(curl, CURLOPT_URL, url);
    curl_easy_setopt(curl, CURLOPT_CUSTOMREQUEST, method);
    curl_easy_setopt(curl, CURLOPT_NOSIGNAL, 1L);
    // The ESP32's timeout_ms is per socket operation, not a cap on the whole
    // transfer: map it to a connect deadline plus a stall limit (no bytes for
    // the timeout), so a long download is not cut off mid-stream.
    curl_easy_setopt(curl, CURLOPT_CONNECTTIMEOUT_MS, (long)timeout_ms);
    curl_easy_setopt(curl, CURLOPT_LOW_SPEED_LIMIT, 1L);
    curl_easy_setopt(curl, CURLOPT_LOW_SPEED_TIME,
                     (long)(timeout_ms / 1000u + (timeout_ms % 1000u != 0)));
    // Follow redirects (esp_http_client does by default), bounded, and never
    // from https down to plain http: the Authorization header would follow.
    curl_easy_setopt(curl, CURLOPT_FOLLOWLOCATION, 1L);
    curl_easy_setopt(curl, CURLOPT_MAXREDIRS, 5L);
    curl_easy_setopt(curl, CURLOPT_REDIR_PROTOCOLS_STR,
                     strncmp(url, "https:", 6) == 0 ? "https" : "http,https");
    // Loopback never goes through a proxy, so the host tests work with
    // http_proxy set.
    curl_easy_setopt(curl, CURLOPT_NOPROXY, "localhost,127.0.0.1,::1");
    if (*curl_headers) curl_easy_setopt(curl, CURLOPT_HTTPHEADER, *curl_headers);
    return curl;

fail:
    curl_slist_free_all(*curl_headers);
    *curl_headers = NULL;
    curl_easy_cleanup(curl);
    return NULL;
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
    if (s_curl_init_result != CURLE_OK) return (int)s_curl_init_result;

    struct curl_slist *curl_headers = NULL;
    CURL *curl = pb_curl_create(url, method, headers, header_count,
                                timeout_ms, &curl_headers);
    if (!curl) return PB_HTTP_ERROR;

    pb_http_response_buffer_t output = {
        .buffer = response,
        .capacity = response_cap,
        .on_data = on_data,
        .data_context = data_context,
    };
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, pb_http_write);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, &output);
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

    // CURLcode values are positive, so they never collide with PB_HTTP_ERROR.
    return result == CURLE_OK ? PB_HTTP_OK : (int)result;
}

const char *pb_http_err_name(int result)
{
    if (result == PB_HTTP_OK) return "OK";
    if (result == PB_HTTP_ERROR) return "HTTP client setup failed";
    return curl_easy_strerror((CURLcode)result);
}

bool pb_http_is_connect_error(int result)
{
    // Same scope as ESP_ERR_HTTP_CONNECT on the ESP32: the TCP connect
    // itself failed. A timeout is not retried — it already took the full
    // request timeout.
    return result == CURLE_COULDNT_CONNECT;
}

// Pull reads on top of libcurl's push model: the multi interface runs the
// transfer only while pb_http_stream_read() pumps it, the write callback
// parks each chunk in `pending`, and a chunk that does not fit pauses the
// transfer (CURL_WRITEFUNC_PAUSE) until the reader drained the buffer — so
// at most one chunk is held, however large the body, and no thread is needed.
struct pb_http_stream {
    CURLM *multi;
    CURL *curl;
    struct curl_slist *headers;
    char *pending;
    size_t pending_cap;
    size_t pending_start;
    size_t pending_len;
    bool paused;
    bool done;
    CURLcode result;
};

static size_t pb_http_stream_write(void *data, size_t size, size_t count,
                                   void *opaque)
{
    pb_http_stream_t *stream = opaque;
    size_t length = size * count;
    if (stream->pending_len > 0) {
        // Not drained yet: curl delivers this chunk again after the unpause.
        stream->paused = true;
        return CURL_WRITEFUNC_PAUSE;
    }
    if (length > stream->pending_cap) {
        // Normally at most CURL_MAX_WRITE_SIZE; grow for anything larger
        // (older libcurl versions deliver buffered data in one piece).
        char *grown = realloc(stream->pending, length);
        if (!grown) return CURL_WRITEFUNC_ERROR;
        stream->pending = grown;
        stream->pending_cap = length;
    }
    memcpy(stream->pending, data, length);
    stream->pending_start = 0;
    stream->pending_len = length;
    return length;
}

// Advances the transfer until a body chunk is pending or it has finished.
static void pb_http_stream_pump(pb_http_stream_t *stream)
{
    while (stream->pending_len == 0 && !stream->done) {
        if (stream->paused) {
            stream->paused = false;
            curl_easy_pause(stream->curl, CURLPAUSE_CONT);
            continue;
        }
        int running = 0;
        CURLMcode code = curl_multi_perform(stream->multi, &running);
        CURLMsg *message;
        int queued;
        while ((message = curl_multi_info_read(stream->multi, &queued))) {
            if (message->msg == CURLMSG_DONE) {
                stream->done = true;
                stream->result = message->data.result;
            }
        }
        if (code != CURLM_OK && !stream->done) {
            stream->done = true;
            stream->result = CURLE_FAILED_INIT;
        }
        if (stream->pending_len == 0 && !stream->done) {
            // Stalls end through CURLOPT_LOW_SPEED_TIME; the poll timeout
            // only bounds one wait so that check gets to run.
            curl_multi_poll(stream->multi, NULL, 0, 1000, NULL);
        }
    }
}

int pb_http_stream_open(const char *url,
                        const pb_http_header_t *headers, size_t header_count,
                        uint32_t timeout_ms, pb_http_stream_t **stream,
                        int *http_status, int64_t *content_length)
{
    if (stream) *stream = NULL;
    if (http_status) *http_status = 0;
    if (content_length) *content_length = 0;
    if (!url || !stream) return PB_HTTP_ERROR;
    pthread_once(&s_curl_once, pb_curl_init);
    if (s_curl_init_result != CURLE_OK) return (int)s_curl_init_result;

    pb_http_stream_t *result = calloc(1, sizeof(*result));
    if (!result) return PB_HTTP_ERROR;
    result->multi = curl_multi_init();
    result->curl = pb_curl_create(url, "GET", headers, header_count,
                                  timeout_ms, &result->headers);
    if (!result->multi || !result->curl) {
        pb_http_stream_close(result);
        return PB_HTTP_ERROR;
    }
    curl_easy_setopt(result->curl, CURLOPT_WRITEFUNCTION, pb_http_stream_write);
    curl_easy_setopt(result->curl, CURLOPT_WRITEDATA, result);
    if (curl_multi_add_handle(result->multi, result->curl) != CURLM_OK) {
        curl_easy_cleanup(result->curl);
        result->curl = NULL;
        pb_http_stream_close(result);
        return PB_HTTP_ERROR;
    }

    // The headers are complete once the first body byte is pending or the
    // transfer ended (a response without body, or a failure).
    pb_http_stream_pump(result);
    if (result->done && result->result != CURLE_OK
            && result->pending_len == 0) {
        int failure = (int)result->result;
        pb_http_stream_close(result);
        return failure;
    }
    long status = 0;
    curl_off_t length = -1;
    curl_easy_getinfo(result->curl, CURLINFO_RESPONSE_CODE, &status);
    curl_easy_getinfo(result->curl, CURLINFO_CONTENT_LENGTH_DOWNLOAD_T, &length);
    if (http_status) *http_status = (int)status;
    if (content_length) *content_length = length > 0 ? (int64_t)length : 0;
    *stream = result;
    return PB_HTTP_OK;
}

int pb_http_stream_read(pb_http_stream_t *stream, void *buffer,
                        size_t capacity)
{
    if (!stream || !buffer || capacity == 0) return -1;
    if (capacity > INT_MAX) capacity = INT_MAX;
    pb_http_stream_pump(stream);
    if (stream->pending_len == 0) {
        return stream->result == CURLE_OK ? 0 : -1;
    }
    size_t copied = stream->pending_len < capacity
                  ? stream->pending_len : capacity;
    memcpy(buffer, stream->pending + stream->pending_start, copied);
    stream->pending_start += copied;
    stream->pending_len -= copied;
    return (int)copied;
}

void pb_http_stream_close(pb_http_stream_t *stream)
{
    if (!stream) return;
    if (stream->curl) {
        if (stream->multi) curl_multi_remove_handle(stream->multi, stream->curl);
        curl_easy_cleanup(stream->curl);
    }
    if (stream->multi) curl_multi_cleanup(stream->multi);
    curl_slist_free_all(stream->headers);
    free(stream->pending);
    free(stream);
}
