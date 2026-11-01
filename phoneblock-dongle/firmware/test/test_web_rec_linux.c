#define _DEFAULT_SOURCE
#define _POSIX_C_SOURCE 200809L

// The call recordings (#663) through the real web server of the Linux build:
// the opt-in switch in /api/status and /api/config, a call recorded by the
// real writer task, its length in /api/calls (rec_s, with the call's seq),
// GET /api/recording streaming the stored bytes exactly — also a recording
// larger than the server's response buffer — POST /api/recording/delete, the
// 404s for an unknown call, and the rec.js asset. Synthetic numbers only.

#include <arpa/inet.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>

#include "api.h"
#include "call_rec.h"
#include "cJSON.h"
#include "config.h"
#include "linux_test_util.h"
#include "stats.h"
#include "time_sync.h"
#include "web.h"
#include "web_http.h"

// One request; the whole response (binary-safe) into a static buffer.
static char   s_resp[4 * 1024 * 1024 + 65536];
static size_t s_resp_len;

static void request(uint16_t port, const char *message)
{
    int client = socket(AF_INET, SOCK_STREAM, 0);
    REQUIRE(client >= 0);
    struct sockaddr_in address = {
        .sin_family = AF_INET,
        .sin_addr.s_addr = htonl(INADDR_LOOPBACK),
        .sin_port = htons(port),
    };
    REQUIRE(connect(client, (struct sockaddr *)&address, sizeof(address)) == 0);
    size_t length = strlen(message), sent = 0;
    while (sent < length) {
        ssize_t n = send(client, message + sent, length - sent, 0);
        REQUIRE(n > 0);
        sent += (size_t)n;
    }
    s_resp_len = 0;
    for (;;) {
        REQUIRE(s_resp_len + 1 < sizeof(s_resp));
        ssize_t n = recv(client, s_resp + s_resp_len, sizeof(s_resp) - s_resp_len - 1, 0);
        if (n == 0) break;
        REQUIRE(n > 0);
        s_resp_len += (size_t)n;
    }
    s_resp[s_resp_len] = '\0';
    close(client);
}

static const char *body(size_t *len)
{
    const char *b = strstr(s_resp, "\r\n\r\n");
    REQUIRE(b != NULL);
    b += 4;
    if (len) *len = s_resp_len - (size_t)(b - s_resp);
    return b;
}

static int status_code(void)
{
    int code = 0;
    REQUIRE(sscanf(s_resp, "HTTP/1.%*d %d", &code) == 1);
    return code;
}

// Value of a response header, "" if absent.
static const char *header(const char *name)
{
    static char value[128];
    value[0] = '\0';
    const char *end = strstr(s_resp, "\r\n\r\n");
    size_t nl = strlen(name);
    for (const char *line = strstr(s_resp, "\r\n"); line && line < end;
         line = strstr(line + 2, "\r\n")) {
        const char *h = line + 2;
        if (strncasecmp(h, name, nl) == 0 && h[nl] == ':') {
            h += nl + 1;
            while (*h == ' ') h++;
            size_t n = strcspn(h, "\r\n");
            if (n >= sizeof(value)) n = sizeof(value) - 1;
            memcpy(value, h, n);
            value[n] = '\0';
            break;
        }
    }
    return value;
}

static void get(uint16_t port, const char *uri)
{
    char message[256];
    snprintf(message, sizeof(message),
             "GET %s HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n", uri);
    request(port, message);
}

static void post(uint16_t port, const char *uri, const char *form)
{
    char message[512];
    snprintf(message, sizeof(message),
             "POST %s HTTP/1.1\r\nHost: localhost\r\n"
             "Content-Type: application/x-www-form-urlencoded\r\n"
             "Content-Length: %zu\r\nConnection: close\r\n\r\n%s",
             uri, strlen(form), form);
    request(port, message);
}

static cJSON *get_json(uint16_t port, const char *uri)
{
    get(port, uri);
    REQUIRE(status_code() == 200);
    cJSON *root = cJSON_Parse(body(NULL));
    REQUIRE(root != NULL);
    return root;
}

// The call with `seq` in /api/calls (caller deletes `*root`).
static cJSON *listed_call(uint16_t port, uint32_t seq, cJSON **root)
{
    *root = get_json(port, "/api/calls");
    cJSON *c;
    cJSON_ArrayForEach(c, cJSON_GetObjectItem(*root, "calls")) {
        cJSON *s = cJSON_GetObjectItem(c, "seq");
        if (cJSON_IsNumber(s) && (uint32_t)s->valuedouble == seq) return c;
    }
    return NULL;
}

static char s_rec_dir[400];

// The stored file of call `seq`, whole (test-side, not through the API).
static unsigned char *read_rec(uint32_t seq, size_t *len)
{
    char path[512];
    snprintf(path, sizeof(path), "%s/%u.al", s_rec_dir, (unsigned)seq);
    FILE *f = fopen(path, "rb");
    if (!f) return NULL;
    static unsigned char buf[4 * 1024 * 1024];
    *len = fread(buf, 1, sizeof(buf), f);
    fclose(f);
    return buf;
}

int main(void)
{
    char config_path[256], calls_dir[256], calls_path[300];
    use_temp_config(config_path, sizeof(config_path));
    stats_setup();
    stats_set_clock(time_sync_now_epoch);
    use_temp_calls(calls_dir, sizeof(calls_dir), calls_path, sizeof(calls_path));
    snprintf(s_rec_dir, sizeof(s_rec_dir), "%s/rec", calls_dir);
    config_load();
    REQUIRE(call_rec_init(s_rec_dir));
    // The installed web assets of this build (rec.js).
    REQUIRE(setenv("PHONEBLOCK_WEB_DIR", "../build/linux-host/web", 1) == 0);
    web_configure_listener(0, "127.0.0.1");
    web_start();
    uint16_t port = web_listening_port();
    REQUIRE(port != 0);

    // Available on Linux, off by default; the switch is saved and reported.
    cJSON *st = get_json(port, "/api/status");
    cJSON *rec = cJSON_GetObjectItem(st, "recording");
    REQUIRE(cJSON_IsTrue(cJSON_GetObjectItem(rec, "available")));
    REQUIRE(cJSON_IsFalse(cJSON_GetObjectItem(rec, "enabled")));
    REQUIRE(cJSON_GetObjectItem(rec, "count")->valueint == 0);
    REQUIRE(cJSON_GetObjectItem(rec, "max_s")->valueint == 30 * 60);
    cJSON_Delete(st);
    REQUIRE(!config_rec_enabled());
    post(port, "/api/config", "rec_enabled=1");
    REQUIRE(status_code() == 200 && config_rec_enabled());
    st = get_json(port, "/api/status");
    REQUIRE(cJSON_IsTrue(cJSON_GetObjectItem(cJSON_GetObjectItem(st, "recording"), "enabled")));
    cJSON_Delete(st);

    // A call recorded by the real writer task: 3 s, fed at about real pace
    // so the 2 s ring never fills.
    stats_record_call("0301234560", "", VERDICT_SPAM);
    uint32_t seq = stats_last_call_seq();
    call_rec_t *r = call_rec_begin(seq);
    REQUIRE(r != NULL);
    uint8_t frame[CALL_REC_FRAME];
    uint8_t rx[CALL_REC_FRAME];
    for (int i = 0; i < CALL_REC_FRAME; i++) rx[i] = (uint8_t)(0x10 + i);
    for (uint32_t n = 0; n < 150; n++) {
        for (int i = 0; i < CALL_REC_FRAME; i++) frame[i] = (uint8_t)(n + (uint32_t)i);
        if (n % 3 == 0) call_rec_rx(r, n * CALL_REC_FRAME, rx, CALL_REC_FRAME);
        call_rec_tick(r, frame);
        if (n % 10 == 9) usleep(20000);
    }
    call_rec_end(r);
    for (int i = 0; i < 100 && call_rec_seconds(seq) == 0; i++) usleep(50000);
    REQUIRE(call_rec_seconds(seq) == 3);

    // /api/calls: the call's seq and the recording's length; a call without
    // one has rec_s 0. The existing fields stay.
    stats_record_call("0301234561", "", VERDICT_LEGITIMATE);
    uint32_t plain = stats_last_call_seq();
    cJSON *root;
    cJSON *c = listed_call(port, seq, &root);
    REQUIRE(c != NULL);
    REQUIRE(cJSON_GetObjectItem(c, "rec_s")->valueint == 3);
    REQUIRE(strcmp(cJSON_GetObjectItem(c, "number")->valuestring, "0301234560") == 0);
    REQUIRE(cJSON_IsNumber(cJSON_GetObjectItem(c, "duration_s")));
    REQUIRE(cJSON_IsNumber(cJSON_GetObjectItem(c, "age_s")));
    cJSON_Delete(root);
    c = listed_call(port, plain, &root);
    REQUIRE(c != NULL && cJSON_GetObjectItem(c, "rec_s")->valueint == 0);
    cJSON_Delete(root);

    // GET /api/recording: the stored bytes, exactly.
    size_t want_len = 0;
    unsigned char *want = read_rec(seq, &want_len);
    REQUIRE(want != NULL && want_len == 150u * CALL_REC_FRAME);
    char uri[64];
    snprintf(uri, sizeof(uri), "/api/recording?seq=%u", (unsigned)seq);
    get(port, uri);
    size_t got_len = 0;
    const char *got = body(&got_len);
    REQUIRE(status_code() == 200);
    REQUIRE(strcmp(header("Content-Type"), "audio/x-alaw-basic") == 0);
    REQUIRE(got_len == want_len && memcmp(got, want, want_len) == 0);

    // Unknown or missing seq: 404 (GET), the JSON refusal (POST).
    get(port, "/api/recording?seq=999999");
    REQUIRE(status_code() == 404);
    get(port, "/api/recording");
    REQUIRE(status_code() == 404);
    snprintf(uri, sizeof(uri), "/api/recording?seq=%u", (unsigned)plain);
    get(port, uri);
    REQUIRE(status_code() == 404);
    post(port, "/api/recording/delete?seq=999999", "");
    REQUIRE(status_code() == 404);
    cJSON *refusal = cJSON_Parse(body(NULL));
    REQUIRE(refusal && cJSON_IsFalse(cJSON_GetObjectItem(refusal, "ok"))
            && strcmp(cJSON_GetObjectItem(refusal, "code")->valuestring, "not_found") == 0);
    cJSON_Delete(refusal);

    // A recording larger than the server buffers a response (2 MB) streams
    // from its file: a 3 MB one, put there as a crash-free earlier run would
    // have left it, picked up at start.
    stats_record_call("0301234562", "", VERDICT_SPAM);
    uint32_t big = stats_last_call_seq();
    {
        char path[512];
        snprintf(path, sizeof(path), "%s/%u.al", s_rec_dir, (unsigned)big);
        FILE *f = fopen(path, "wb");
        REQUIRE(f != NULL);
        for (size_t i = 0; i < 3u * 1024 * 1024; i++) fputc((int)((i * 13) & 0xFF), f);
        REQUIRE(fclose(f) == 0);
    }
    REQUIRE(call_rec_init(s_rec_dir));
    REQUIRE(call_rec_seconds(seq) == 3 && call_rec_seconds(big) == 393);
    REQUIRE(3u * 1024 * 1024 > WEB_HTTP_MAX_RESPONSE);
    snprintf(uri, sizeof(uri), "/api/recording?seq=%u", (unsigned)big);
    get(port, uri);
    REQUIRE(status_code() == 200);
    got = body(&got_len);
    want = read_rec(big, &want_len);
    REQUIRE(got_len == 3u * 1024 * 1024 && want_len == got_len
            && memcmp(got, want, got_len) == 0);

    // Delete: gone from the API and the directory; the call stays listed.
    snprintf(uri, sizeof(uri), "/api/recording/delete?seq=%u", (unsigned)seq);
    post(port, uri, "");
    REQUIRE(status_code() == 200);
    cJSON *ok = cJSON_Parse(body(NULL));
    REQUIRE(ok && cJSON_IsTrue(cJSON_GetObjectItem(ok, "ok")));
    cJSON_Delete(ok);
    REQUIRE(read_rec(seq, &want_len) == NULL && call_rec_seconds(seq) == 0);
    c = listed_call(port, seq, &root);
    REQUIRE(c != NULL && cJSON_GetObjectItem(c, "rec_s")->valueint == 0);
    cJSON_Delete(root);
    post(port, uri, "");
    REQUIRE(status_code() == 404);

    // "Clear list" takes the rest with it.
    post(port, "/api/calls/clear", "");
    REQUIRE(status_code() == 200 && call_rec_seconds(big) == 0
            && read_rec(big, &want_len) == NULL);

    // The recordings' UI, served gzipped like the page.
    get(port, "/rec.js");
    REQUIRE(status_code() == 200);
    REQUIRE(strcmp(header("Content-Encoding"), "gzip") == 0);
    size_t js_len = 0;
    const unsigned char *js = (const unsigned char *)body(&js_len);
    REQUIRE(js_len > 100 && js[0] == 0x1f && js[1] == 0x8b);

    post(port, "/api/config", "rec_enabled=0");
    REQUIRE(status_code() == 200 && !config_rec_enabled());

    web_stop();
    rmdir(s_rec_dir);
    unlink(config_path);
    unlink(calls_path);
    rmdir(calls_dir);
    puts("test_web_rec_linux: all tests passed");
    return 0;
}
