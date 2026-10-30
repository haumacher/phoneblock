#define _POSIX_C_SOURCE 200809L

// The call webhook (#644) end to end on the Linux build: a listed call is
// posted, through the report_queue worker, to a receiver on 127.0.0.1 with
// the /api/calls JSON of the call; nothing goes out while no URL is set; a
// host outside the home network is refused with a WARN in the log ring.

#include <arpa/inet.h>
#include <netinet/in.h>
#include <poll.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>

#include "api.h"
#include "cJSON.h"
#include "config.h"
#include "linux_test_util.h"
#include "log_capture.h"
#include "platform.h"
#include "report_queue.h"
#include "stats.h"
#include "web.h"
#include "webhook.h"

static int listener;

// Waits up to `timeout_ms` for one POST and answers it with 204. Returns
// false when none arrived; otherwise `request` holds headers and body.
static bool receive_post(char *request, size_t cap, int timeout_ms)
{
    struct pollfd p = { .fd = listener, .events = POLLIN };
    if (poll(&p, 1, timeout_ms) != 1) return false;
    int client = accept(listener, NULL, NULL);
    REQUIRE(client >= 0);

    size_t got = 0;
    for (;;) {
        ssize_t n = recv(client, request + got, cap - 1 - got, 0);
        REQUIRE(n > 0);
        got += (size_t)n;
        request[got] = '\0';
        const char *end = strstr(request, "\r\n\r\n");
        if (!end) continue;
        const char *length = strstr(request, "Content-Length: ");
        REQUIRE(length != NULL);
        if (got >= (size_t)(end + 4 - request) + (size_t)atoi(length + 16)) break;
    }
    static const char reply[] =
        "HTTP/1.1 204 No Content\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
    REQUIRE(send(client, reply, sizeof(reply) - 1, 0) == (ssize_t)(sizeof(reply) - 1));
    close(client);
    return true;
}

// One request to the dongle's own web server; the response lands in
// `response`.
static void http(uint16_t port, const char *message, char *response, size_t cap)
{
    int client = socket(AF_INET, SOCK_STREAM, 0);
    REQUIRE(client >= 0);
    struct sockaddr_in address = {
        .sin_family = AF_INET,
        .sin_addr.s_addr = htonl(INADDR_LOOPBACK),
        .sin_port = htons(port),
    };
    REQUIRE(connect(client, (struct sockaddr *)&address, sizeof(address)) == 0);
    size_t length = strlen(message);
    REQUIRE(send(client, message, length, 0) == (ssize_t)length);
    size_t got = 0;
    while (got + 1 < cap) {
        ssize_t n = recv(client, response + got, cap - 1 - got, 0);
        if (n == 0) break;
        REQUIRE(n > 0);
        got += (size_t)n;
    }
    response[got] = '\0';
    close(client);
}

// Saves the URL the way the web UI does (POST /api/config, form-encoded).
static void save_url(uint16_t port, const char *encoded_url)
{
    char message[512], response[2048];
    snprintf(message, sizeof(message),
             "POST /api/config HTTP/1.1\r\nHost: localhost\r\n"
             "Content-Type: application/x-www-form-urlencoded\r\n"
             "Content-Length: %zu\r\nConnection: close\r\n\r\nwebhook_url=%s",
             strlen("webhook_url=") + strlen(encoded_url), encoded_url);
    http(port, message, response, sizeof(response));
    REQUIRE(strstr(response, "200 OK") != NULL);
}

// The URL /api/status reports, as the web UI prefills it.
static void status_url(uint16_t port, char *out, size_t cap)
{
    static char response[16384];
    http(port, "GET /api/status HTTP/1.1\r\nHost: localhost\r\n"
               "Connection: close\r\n\r\n", response, sizeof(response));
    cJSON *root = cJSON_Parse(strstr(response, "\r\n\r\n") + 4);
    REQUIRE(root != NULL);
    cJSON *url = cJSON_GetObjectItem(cJSON_GetObjectItem(root, "webhook"), "url");
    REQUIRE(cJSON_IsString(url));
    snprintf(out, cap, "%s", url->valuestring);
    cJSON_Delete(root);
}

// Checks the posted JSON against what was recorded.
static void expect_call(const char *request, const char *number,
                        const char *display, const char *assessment,
                        int duration_s)
{
    REQUIRE(strncmp(request, "POST /hook HTTP/1.1\r\n", 21) == 0);
    REQUIRE(strstr(request, "Content-Type: application/json\r\n") != NULL);
    REQUIRE(strstr(request, "User-Agent: PhoneBlock-Dongle/") != NULL);
    cJSON *call = cJSON_Parse(strstr(request, "\r\n\r\n") + 4);
    REQUIRE(call != NULL);
    REQUIRE(strcmp(cJSON_GetObjectItem(call, "number")->valuestring, number) == 0);
    REQUIRE(strcmp(cJSON_GetObjectItem(call, "display")->valuestring, display) == 0);
    REQUIRE(strcmp(cJSON_GetObjectItem(call, "assessment")->valuestring,
                   assessment) == 0);
    REQUIRE(cJSON_GetObjectItem(call, "age_s")->valuedouble >= 0);
    REQUIRE(cJSON_GetObjectItem(call, "epoch_s")->valuedouble > 0);
    REQUIRE(cJSON_GetObjectItem(call, "duration_s")->valueint == duration_s);
    cJSON_Delete(call);
}

static bool error_ring_contains(const char *text)
{
    stats_error_t errors[STATS_MAX_ERRORS];
    int n = stats_snapshot_errors(errors, STATS_MAX_ERRORS);
    for (int i = 0; i < n; i++) {
        if (strstr(errors[i].message, text)) return true;
    }
    return false;
}

int main(void)
{
    char config_path[256];
    use_temp_config(config_path, sizeof(config_path));
    stats_setup();
    log_capture_start();
    config_load();

    listener = socket(AF_INET, SOCK_STREAM, 0);
    REQUIRE(listener >= 0);
    struct sockaddr_in address = {
        .sin_family = AF_INET,
        .sin_addr.s_addr = htonl(INADDR_LOOPBACK),
        .sin_port = 0,
    };
    REQUIRE(bind(listener, (struct sockaddr *)&address, sizeof(address)) == 0);
    REQUIRE(listen(listener, 4) == 0);
    socklen_t address_len = sizeof(address);
    REQUIRE(getsockname(listener, (struct sockaddr *)&address, &address_len) == 0);
    char url[64];
    snprintf(url, sizeof(url), "http://127.0.0.1:%u/hook",
             (unsigned)ntohs(address.sin_port));
    REQUIRE(webhook_url_valid(url));

    report_queue_start();
    webhook_start();
    web_configure_listener(0, "127.0.0.1");
    web_start();
    uint16_t web_port = web_listening_port();
    REQUIRE(web_port != 0);
    char request[4096];
    char stored[CONFIG_WEBHOOK_URL_CAP];

    // Off: no URL, no POST.
    status_url(web_port, stored, sizeof(stored));
    REQUIRE(stored[0] == '\0');
    stats_record_call("0301234567", "Test", VERDICT_LEGITIMATE);
    REQUIRE(!receive_post(request, sizeof(request), 1000));

    // Saved through the web UI's endpoint and reported back by /api/status.
    char encoded[96];
    snprintf(encoded, sizeof(encoded), "http%%3A%%2F%%2F127.0.0.1%%3A%u%%2Fhook",
             (unsigned)ntohs(address.sin_port));
    save_url(web_port, encoded);
    status_url(web_port, stored, sizeof(stored));
    REQUIRE(strcmp(stored, url) == 0);

    // https is refused on storing; the URL stays as it was.
    save_url(web_port, "https%3A%2F%2Fhomeassistant.local%2Fhook");
    status_url(web_port, stored, sizeof(stored));
    REQUIRE(strcmp(stored, url) == 0);

    // A legitimate call goes out at once.
    stats_record_call("0301234567", "Test", VERDICT_LEGITIMATE);
    REQUIRE(receive_post(request, sizeof(request), 5000));
    expect_call(request, "0301234567", "Test", "legitimate", 0);

    // A spam call is answered: it is posted when it ends, as sip_register.c
    // reports it, with its duration (here a caller who hung up after 4 s).
    stats_record_call("0309876543", "", VERDICT_SPAM);
    REQUIRE(!receive_post(request, sizeof(request), 1000));
    stats_call_ended(stats_last_call_seq(), 4200000);
    REQUIRE(receive_post(request, sizeof(request), 5000));
    expect_call(request, "0309876543", "", "spam", 4);

    // TEST-NET-1 is neither private nor on our subnet: refused before any
    // connection, with the reason in the web UI's log ring.
    save_url(web_port, "http%3A%2F%2F192.0.2.1%2Fhook");
    stats_record_call("0301234567", "Test", VERDICT_LEGITIMATE);
    bool refused = false;
    for (int i = 0; i < 50 && !refused; i++) {
        pb_task_sleep_ms(100);
        refused = error_ring_contains("not in the home network");
    }
    REQUIRE(refused);
    REQUIRE(!receive_post(request, sizeof(request), 200));

    // What the web UI may store.
    REQUIRE(webhook_url_valid(""));
    REQUIRE(webhook_url_valid("http://homeassistant.local:8123/api/webhook/abc"));
    REQUIRE(!webhook_url_valid("https://homeassistant.local/api/webhook/abc"));
    char long_url[CONFIG_WEBHOOK_URL_CAP + 1];
    memset(long_url, 'a', sizeof(long_url) - 1);
    memcpy(long_url, "http://h/", 9);
    long_url[sizeof(long_url) - 1] = '\0';
    REQUIRE(!webhook_url_valid(long_url));
    long_url[CONFIG_WEBHOOK_URL_CAP - 1] = '\0';
    REQUIRE(webhook_url_valid(long_url));

    // Empty switches it off again.
    save_url(web_port, "");
    status_url(web_port, stored, sizeof(stored));
    REQUIRE(stored[0] == '\0');

    web_stop();
    close(listener);
    unlink(config_path);
    puts("test_webhook_linux: all tests passed");
    return 0;
}
