#define _GNU_SOURCE   // strcasestr

// The device-code link (#621) through the Linux build's real web server and
// poll task, against a fake of phoneblock.net's /api/link endpoints:
//  * /api/status reports token_link only under ingress (X-Ingress-Path);
//  * POST /api/link/start → pending, slow_down (the interval grows by 5 s),
//    approved → the token is stored by the shared post-token function
//    (self-test with the new token, country seeded from /api/account);
//  * a denied link stores no token; a link whose server stays unreachable
//    expires locally; a second start replaces the running link;
//  * the server refusing the start (429) answers 502 with the reason;
//  * the redirect flow (/register-start → /token-callback) still stores the
//    token through the same function.
// One protocol second is shortened to 20 ms (link_set_second_ms()).

#include <arpa/inet.h>
#include <netinet/in.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <time.h>
#include <unistd.h>

#include "api.h"
#include "cJSON.h"
#include "config.h"
#include "link.h"
#include "linux_test_util.h"
#include "platform.h"
#include "stats.h"
#include "web.h"

#define SECOND_MS 20
#define USER_CODE "BCDF-GHJK"
#define LINK_TOKEN "link-test-token-0123"
#define CALLBACK_TOKEN "callback-test-token-4567"

// --- fake phoneblock.net -------------------------------------------------

static int s_fake_fd;
static pthread_mutex_t s_fake_lock = PTHREAD_MUTEX_INITIALIZER;

// The script of the current link: poll answers in order (the last one
// repeats), the start's expiresIn, or a refused start.
static const char *s_script[8];
static int s_script_len;
static int s_expires_in = 600;
static bool s_refuse_start;
// Per start a fresh device code "devcode<N>"; every poll is recorded.
static int s_starts;
static int s_polls;
static char s_poll_codes[64][32];
static uint64_t s_poll_times[64];
static int s_script_pos;
static char s_last_bearer[64];
static atomic_bool s_body_ok = true;

static void fake_script(const char *const *answers, int n, int expires_in)
{
    pthread_mutex_lock(&s_fake_lock);
    for (int i = 0; i < n; i++) s_script[i] = answers[i];
    s_script_len = n;
    s_script_pos = 0;
    s_expires_in = expires_in;
    s_refuse_start = false;
    pthread_mutex_unlock(&s_fake_lock);
}

static void recv_request(int client, char *request, size_t cap)
{
    size_t got = 0;
    for (;;) {
        ssize_t n = recv(client, request + got, cap - 1 - got, 0);
        REQUIRE(n > 0);
        got += (size_t)n;
        request[got] = '\0';
        const char *end = strstr(request, "\r\n\r\n");
        if (!end) continue;
        const char *length = strcasestr(request, "Content-Length: ");
        size_t body = length ? (size_t)atoi(length + 16) : 0;
        if (got >= (size_t)(end + 4 - request) + body) return;
    }
}

static void reply(int client, const char *status, const char *body)
{
    char response[1024];
    int n = snprintf(response, sizeof(response),
        "HTTP/1.1 %s\r\nContent-Type: application/json\r\nContent-Length: %zu\r\n"
        "Connection: close\r\n\r\n%s", status, strlen(body), body);
    REQUIRE(n > 0 && (size_t)n < sizeof(response));
    REQUIRE(send(client, response, (size_t)n, MSG_NOSIGNAL) == n);
}

static void *fake_server(void *unused)
{
    (void)unused;
    for (;;) {
        int client = accept(s_fake_fd, NULL, NULL);
        if (client < 0) return NULL;
        char request[4096];
        recv_request(client, request, sizeof(request));
        const char *body = strstr(request, "\r\n\r\n") + 4;
        if (!strstr(request, "User-Agent: PhoneBlock-Dongle/")) s_body_ok = false;
        char answer[512];
        pthread_mutex_lock(&s_fake_lock);
        if (strncmp(request, "POST /api/link/start ", 21) == 0) {
            // Anonymous, with the redirect flow's app id and label.
            if (strstr(request, "Authorization:")
                    || !strstr(body, "\"appId\":\"PhoneBlockDongle\"")
                    || !strstr(body, "\"label\":\"PhoneBlock-Dongle\"")) {
                s_body_ok = false;
            }
            if (s_refuse_start) {
                pthread_mutex_unlock(&s_fake_lock);
                reply(client, "429 Too Many Requests",
                      "{\"message\":\"too many open links\"}");
                close(client);
                continue;
            }
            s_starts++;
            snprintf(answer, sizeof(answer),
                "{\"deviceCode\":\"devcode%d\",\"userCode\":\"" USER_CODE "\","
                "\"verifyUrl\":\"http://phoneblock.test/link?code=" USER_CODE "\","
                "\"interval\":1,\"expiresIn\":%d}", s_starts, s_expires_in);
            pthread_mutex_unlock(&s_fake_lock);
            reply(client, "200 OK", answer);
        } else if (strncmp(request, "POST /api/link/poll ", 20) == 0) {
            cJSON *root = cJSON_Parse(body);
            const cJSON *code = cJSON_GetObjectItem(root, "deviceCode");
            if (s_polls < 64) {
                snprintf(s_poll_codes[s_polls], sizeof(s_poll_codes[0]), "%s",
                         cJSON_IsString(code) ? code->valuestring : "");
                s_poll_times[s_polls] = pb_monotonic_us();
                s_polls++;
            }
            char current[32];
            snprintf(current, sizeof(current), "devcode%d", s_starts);
            bool stale = !cJSON_IsString(code) || strcmp(code->valuestring, current) != 0;
            cJSON_Delete(root);
            // A replaced link stays pending and does not consume the script.
            const char *a = "{\"status\":\"pending\"}";
            if (!stale && s_script_len) {
                a = s_script[s_script_pos < s_script_len ? s_script_pos : s_script_len - 1];
                if (s_script_pos < s_script_len) s_script_pos++;
            }
            pthread_mutex_unlock(&s_fake_lock);
            if (strcmp(a, "503") == 0) {
                reply(client, "503 Service Unavailable", "{}");
            } else {
                reply(client, "200 OK", a);
            }
        } else if (strncmp(request, "GET /api/test ", 14) == 0
                   || strncmp(request, "GET /api/account ", 17) == 0) {
            const char *auth = strstr(request, "Authorization: Bearer ");
            if (auth) {
                const char *end = strstr(auth, "\r\n");
                snprintf(s_last_bearer, sizeof(s_last_bearer), "%.*s",
                         (int)(end - auth - 22), auth + 22);
            }
            pthread_mutex_unlock(&s_fake_lock);
            if (request[5] == 't') {
                reply(client, "200 OK", "ok");
            } else {
                reply(client, "200 OK", "{\"dialPrefix\":\"+41\"}");
            }
        } else {
            pthread_mutex_unlock(&s_fake_lock);
            reply(client, "404 Not Found", "{}");
        }
        close(client);
    }
}

// --- the device's web server ---------------------------------------------

static uint16_t s_port;

static void http(const char *head, char *response, size_t cap)
{
    int client = socket(AF_INET, SOCK_STREAM, 0);
    REQUIRE(client >= 0);
    struct sockaddr_in address = {
        .sin_family = AF_INET,
        .sin_addr.s_addr = htonl(INADDR_LOOPBACK),
        .sin_port = htons(s_port),
    };
    REQUIRE(connect(client, (struct sockaddr *)&address, sizeof(address)) == 0);
    size_t length = strlen(head);
    REQUIRE(send(client, head, length, 0) == (ssize_t)length);
    size_t got = 0;
    while (got + 1 < cap) {
        ssize_t n = recv(client, response + got, cap - 1 - got, 0);
        if (n <= 0) break;
        got += (size_t)n;
    }
    response[got] = '\0';
    close(client);
}

static cJSON *get_json(const char *path, const char *extra_header)
{
    char head[256];
    snprintf(head, sizeof(head),
             "GET %s HTTP/1.1\r\nHost: localhost\r\n%sConnection: close\r\n\r\n",
             path, extra_header ? extra_header : "");
    static char response[16384];
    http(head, response, sizeof(response));
    REQUIRE(strstr(response, "200 OK") != NULL);
    cJSON *root = cJSON_Parse(strstr(response, "\r\n\r\n") + 4);
    REQUIRE(root != NULL);
    return root;
}

// POST /api/link/start; returns the response (status line and body).
static void post_start(char *response, size_t cap)
{
    http("POST /api/link/start HTTP/1.1\r\nHost: localhost\r\n"
         "X-Ingress-Path: /api/hassio_ingress/test\r\n"
         "Content-Length: 0\r\nConnection: close\r\n\r\n", response, cap);
}

static void sleep_ms(long ms)
{
    struct timespec t = { .tv_sec = ms / 1000, .tv_nsec = (ms % 1000) * 1000000L };
    nanosleep(&t, NULL);
}

// Polls /api/link/status until the state leaves "pending" (at most 5 s).
static cJSON *wait_terminal(void)
{
    for (int i = 0; i < 500; i++) {
        cJSON *st = get_json("/api/link/status", NULL);
        const char *state = cJSON_GetObjectItem(st, "state")->valuestring;
        if (strcmp(state, "pending") != 0) return st;
        cJSON_Delete(st);
        sleep_ms(10);
    }
    REQUIRE(!"link did not end");
    return NULL;
}

static const char *state_of(cJSON *st)
{
    return cJSON_GetObjectItem(st, "state")->valuestring;
}

static void clear_token(void)
{
    config_update_t u = { .phoneblock_token = "" };
    REQUIRE(config_update(&u) == PB_KV_OK);
    REQUIRE(config_phoneblock_token()[0] == '\0');
}

int main(void)
{
    char config_path[256];
    use_temp_config(config_path, sizeof(config_path));
    stats_setup();
    config_load();

    s_fake_fd = socket(AF_INET, SOCK_STREAM, 0);
    REQUIRE(s_fake_fd >= 0);
    struct sockaddr_in address = {
        .sin_family = AF_INET,
        .sin_addr.s_addr = htonl(INADDR_LOOPBACK),
        .sin_port = 0,
    };
    REQUIRE(bind(s_fake_fd, (struct sockaddr *)&address, sizeof(address)) == 0);
    REQUIRE(listen(s_fake_fd, 8) == 0);
    socklen_t address_len = sizeof(address);
    REQUIRE(getsockname(s_fake_fd, (struct sockaddr *)&address, &address_len) == 0);
    char base_url[64];
    snprintf(base_url, sizeof(base_url), "http://127.0.0.1:%u",
             (unsigned)ntohs(address.sin_port));
    config_update_t update = { .phoneblock_base_url = base_url };
    REQUIRE(config_update(&update) == PB_KV_OK);

    phoneblock_api_init();
    pthread_t fake;
    REQUIRE(pthread_create(&fake, NULL, fake_server, NULL) == 0);

    web_configure_listener(0, "127.0.0.1");
    web_start();
    s_port = web_listening_port();
    REQUIRE(s_port != 0);
    link_set_second_ms(SECOND_MS);

    // token_link only under ingress; the redirect sign-in only without it.
    cJSON *status = get_json("/api/status", "X-Ingress-Path: /api/hassio_ingress/test\r\n");
    cJSON *pb = cJSON_GetObjectItem(status, "phoneblock");
    REQUIRE(cJSON_IsTrue(cJSON_GetObjectItem(pb, "token_link")));
    REQUIRE(cJSON_IsFalse(cJSON_GetObjectItem(pb, "token_signin")));
    cJSON_Delete(status);
    status = get_json("/api/status", NULL);
    pb = cJSON_GetObjectItem(status, "phoneblock");
    REQUIRE(cJSON_GetObjectItem(pb, "token_link") == NULL);
    REQUIRE(cJSON_IsTrue(cJSON_GetObjectItem(pb, "token_signin")));
    cJSON_Delete(status);

    cJSON *st = get_json("/api/link/status", NULL);
    REQUIRE(strcmp(state_of(st), "idle") == 0);
    cJSON_Delete(st);

    // 1. pending, slow_down, approved.
    static const char *const approve[] = {
        "{\"status\":\"pending\"}",
        "{\"status\":\"slow_down\"}",
        "{\"status\":\"approved\",\"token\":\"" LINK_TOKEN "\"}",
    };
    fake_script(approve, 3, 600);
    char response[4096];
    post_start(response, sizeof(response));
    REQUIRE(strstr(response, "200 OK") != NULL);
    // The device code never reaches the browser.
    REQUIRE(strstr(response, "devcode") == NULL);
    cJSON *started = cJSON_Parse(strstr(response, "\r\n\r\n") + 4);
    REQUIRE(started != NULL);
    REQUIRE(strcmp(cJSON_GetObjectItem(started, "userCode")->valuestring, USER_CODE) == 0);
    REQUIRE(strcmp(cJSON_GetObjectItem(started, "verifyUrl")->valuestring,
                   "http://phoneblock.test/link?code=" USER_CODE) == 0);
    REQUIRE(cJSON_GetObjectItem(started, "expiresIn")->valueint == 600);
    cJSON_Delete(started);

    st = wait_terminal();
    REQUIRE(strcmp(state_of(st), "approved") == 0);
    REQUIRE(strcmp(cJSON_GetObjectItem(st, "userCode")->valuestring, USER_CODE) == 0);
    char *printed = cJSON_PrintUnformatted(st);
    REQUIRE(strstr(printed, "devcode") == NULL && strstr(printed, LINK_TOKEN) == NULL);
    cJSON_free(printed);
    cJSON_Delete(st);
    // Stored by phoneblock_adopt_token(): self-test with the new token and
    // the country from the account.
    REQUIRE(strcmp(config_phoneblock_token(), LINK_TOKEN) == 0);
    REQUIRE(strcmp(s_last_bearer, LINK_TOKEN) == 0);
    REQUIRE(strcmp(config_dial_prefix(), "+41") == 0);
    pthread_mutex_lock(&s_fake_lock);
    REQUIRE(s_polls == 3);
    for (int i = 0; i < 3; i++) REQUIRE(strcmp(s_poll_codes[i], "devcode1") == 0);
    // Interval 1 s, after slow_down 6 s (protocol seconds of SECOND_MS).
    uint64_t gap1 = s_poll_times[1] - s_poll_times[0];
    uint64_t gap2 = s_poll_times[2] - s_poll_times[1];
    pthread_mutex_unlock(&s_fake_lock);
    REQUIRE(gap1 >= 1 * SECOND_MS * 1000ULL);
    REQUIRE(gap2 >= 6 * SECOND_MS * 1000ULL);
    REQUIRE(gap2 > gap1);

    // 2. denied: no token stored.
    clear_token();
    static const char *const deny[] = {
        "{\"status\":\"pending\"}",
        "{\"status\":\"denied\"}",
    };
    fake_script(deny, 2, 600);
    post_start(response, sizeof(response));
    REQUIRE(strstr(response, "200 OK") != NULL);
    st = wait_terminal();
    REQUIRE(strcmp(state_of(st), "denied") == 0);
    REQUIRE(cJSON_GetObjectItem(st, "message")->valuestring[0] != '\0');
    cJSON_Delete(st);
    REQUIRE(config_phoneblock_token()[0] == '\0');

    // 3. The server stays unavailable: keep polling, then the local expiry.
    static const char *const down[] = { "503" };
    fake_script(down, 1, 3);
    int polls_before = s_polls;
    post_start(response, sizeof(response));
    REQUIRE(strstr(response, "200 OK") != NULL);
    st = wait_terminal();
    REQUIRE(strcmp(state_of(st), "expired") == 0);
    REQUIRE(strstr(cJSON_GetObjectItem(st, "message")->valuestring, "HTTP 503") != NULL);
    cJSON_Delete(st);
    REQUIRE(s_polls - polls_before >= 2);
    REQUIRE(config_phoneblock_token()[0] == '\0');

    // 4. A second start replaces the running link: its polls stop.
    static const char *const pending[] = { "{\"status\":\"pending\"}" };
    fake_script(pending, 1, 600);
    post_start(response, sizeof(response));
    REQUIRE(strstr(response, "200 OK") != NULL);
    sleep_ms(3 * SECOND_MS);
    fake_script(approve + 2, 1, 600);
    post_start(response, sizeof(response));
    REQUIRE(strstr(response, "200 OK") != NULL);
    pthread_mutex_lock(&s_fake_lock);
    int replaced_at = s_polls;
    char replaced_code[32];
    snprintf(replaced_code, sizeof(replaced_code), "devcode%d", s_starts);
    pthread_mutex_unlock(&s_fake_lock);
    st = wait_terminal();
    REQUIRE(strcmp(state_of(st), "approved") == 0);
    cJSON_Delete(st);
    sleep_ms(5 * SECOND_MS);
    pthread_mutex_lock(&s_fake_lock);
    // At most one poll of the old link was already under way.
    int stale = 0;
    for (int i = replaced_at; i < s_polls; i++) {
        if (strcmp(s_poll_codes[i], replaced_code) != 0) stale++;
    }
    REQUIRE(stale <= 1);
    REQUIRE(strcmp(s_poll_codes[s_polls - 1], replaced_code) == 0);
    pthread_mutex_unlock(&s_fake_lock);
    REQUIRE(strcmp(config_phoneblock_token(), LINK_TOKEN) == 0);

    // 5. The server refuses the start: 502 with its reason.
    pthread_mutex_lock(&s_fake_lock);
    s_refuse_start = true;
    pthread_mutex_unlock(&s_fake_lock);
    post_start(response, sizeof(response));
    REQUIRE(strstr(response, "502 Bad Gateway") != NULL);
    REQUIRE(strstr(response, "429") != NULL);
    REQUIRE(strstr(response, "too many open links") != NULL);
    st = get_json("/api/link/status", NULL);
    REQUIRE(strcmp(state_of(st), "error") == 0);
    cJSON_Delete(st);

    // 6. The redirect flow, unchanged: /register-start → /token-callback
    // stores the token through the same function (self-test included).
    clear_token();
    http("GET /register-start HTTP/1.1\r\nHost: dongle.test\r\nConnection: close\r\n\r\n",
         response, sizeof(response));
    REQUIRE(strstr(response, "302 Found") != NULL);
    const char *location = strstr(response, "Location: ");
    REQUIRE(location != NULL);
    char expected_prefix[256];
    snprintf(expected_prefix, sizeof(expected_prefix),
             "Location: %s/mobile/login?appId=PhoneBlockDongle&tokenLabel=PhoneBlock-Dongle"
             "&callback=http%%3A%%2F%%2Fdongle.test%%2Ftoken-callback&state=", base_url);
    REQUIRE(strncmp(location, expected_prefix, strlen(expected_prefix)) == 0);
    char state[40];
    snprintf(state, sizeof(state), "%.32s", location + strlen(expected_prefix));
    char head[256];
    snprintf(head, sizeof(head),
             "GET /token-callback?loginToken=" CALLBACK_TOKEN "&state=%s HTTP/1.1\r\n"
             "Host: dongle.test\r\nConnection: close\r\n\r\n", state);
    http(head, response, sizeof(response));
    REQUIRE(strstr(response, "200 OK") != NULL);
    REQUIRE(strstr(response, "window.close()") != NULL);
    REQUIRE(strcmp(config_phoneblock_token(), CALLBACK_TOKEN) == 0);
    REQUIRE(strcmp(s_last_bearer, CALLBACK_TOKEN) == 0);
    // A replayed callback is refused (single-use state), the token stays.
    http(head, response, sizeof(response));
    REQUIRE(strstr(response, "400 Bad Request") != NULL);
    REQUIRE(strcmp(config_phoneblock_token(), CALLBACK_TOKEN) == 0);

    REQUIRE(atomic_load(&s_body_ok));
    web_stop();
    shutdown(s_fake_fd, SHUT_RDWR);
    close(s_fake_fd);
    REQUIRE(config_erase() == 0);
    unlink(config_path);
    puts("test_link_linux: all tests passed");
    return 0;
}
