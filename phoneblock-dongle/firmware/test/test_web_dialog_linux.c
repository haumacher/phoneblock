#define _POSIX_C_SOURCE 200809L

// The dialog settings and the own dialog set upload (#429) through the
// Linux build's web server, as the web UI talks to it:
//  * an invalid dialog setting (dialog_speaker, ab_mode, the dialog tuning)
//    refuses the whole POST /api/config with a 400 naming the field, and
//    nothing of it is applied;
//  * POST /api/dialog takes a set larger than the generic request cap (the
//    route's max_body), refuses one below a header as "not_a_set" and one
//    above the dialog budget as a JSON 413 — each with a WARN in the ring.
// Nothing here writes the data directory: every request is refused before.

#include <arpa/inet.h>
#include <netinet/in.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>

#include "config.h"
#include "linux_test_util.h"
#include "log_capture.h"
#include "stats.h"
#include "storage.h"
#include "web.h"
#include "web_http.h"

static uint16_t s_port;

// One request; the response (headers and body) lands in `response`.
static void http(const char *head, const void *body, size_t body_len,
                 char *response, size_t cap)
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
    size_t sent = 0;
    while (sent < body_len) {
        ssize_t n = send(client, (const char *)body + sent, body_len - sent, MSG_NOSIGNAL);
        if (n <= 0) break;   // a refused body: the server may close early
        sent += (size_t)n;
    }
    size_t got = 0;
    while (got + 1 < cap) {
        ssize_t n = recv(client, response + got, cap - 1 - got, 0);
        if (n <= 0) break;
        got += (size_t)n;
    }
    response[got] = '\0';
    close(client);
}

// POST /api/config with a form body.
static void post_config(const char *form, char *response, size_t cap)
{
    char head[256];
    snprintf(head, sizeof(head),
             "POST /api/config HTTP/1.1\r\nHost: localhost\r\n"
             "Content-Type: application/x-www-form-urlencoded\r\n"
             "Content-Length: %zu\r\nConnection: close\r\n\r\n", strlen(form));
    http(head, form, strlen(form), response, cap);
}

// POST /api/dialog with `n` bytes (a 'P' pattern; never a valid set).
static void post_dialog(size_t n, char *response, size_t cap)
{
    char head[256];
    snprintf(head, sizeof(head),
             "POST /api/dialog HTTP/1.1\r\nHost: localhost\r\n"
             "Content-Type: application/octet-stream\r\n"
             "Content-Length: %zu\r\nConnection: close\r\n\r\n", n);
    char *body = malloc(n ? n : 1);
    REQUIRE(body != NULL);
    memset(body, 'P', n);
    http(head, body, n, response, cap);
    free(body);
}

static bool ring_contains(const char *text)
{
    stats_error_t errors[STATS_MAX_ERRORS];
    int n = stats_snapshot_errors(errors, STATS_MAX_ERRORS);
    for (int i = 0; i < n; i++) {
        if (strstr(errors[i].message, text)) return true;
    }
    return false;
}

static void expect_refused(const char *form, const char *field)
{
    char response[4096];
    post_config(form, response, sizeof(response));
    if (!strstr(response, "400 Bad Request") || !strstr(response, "\"code\":\"invalid\"")
            || !strstr(response, field)) {
        fprintf(stderr, "form %s:\n%s\n", form, response);
    }
    REQUIRE(strstr(response, "400 Bad Request") != NULL);
    REQUIRE(strstr(response, "\"code\":\"invalid\"") != NULL);
    REQUIRE(strstr(response, field) != NULL);
    REQUIRE(strstr(response, "Saved.") == NULL);
}

int main(void)
{
    char config_path[256];
    use_temp_config(config_path, sizeof(config_path));
    stats_setup();
    log_capture_start();
    config_load();

    web_configure_listener(0, "127.0.0.1");
    web_start();
    s_port = web_listening_port();
    REQUIRE(s_port != 0);

    char response[4096];

    // A new installation starts in dialog mode; announcement mode here, so
    // the own set upload below is refused for its mode and never touches the
    // data directory.
    config_update_t mode = { .ab_mode = "announcement" };
    REQUIRE(config_update(&mode) == 0);

    // --- invalid dialog settings refuse the whole request -----------------
    REQUIRE(config_set_dialog_speaker("roger") == 0);
    int vad_before = config_vad_silence_db();
    int vad_other = vad_before == -40 ? -41 : -40;
    char form[160];
    const char *bad_speakers[] = {
        "Roger", "..%2Fx", "abcdefghijklmnopq", "-x", "1abc", "a+b", "a%20b", "a_b",
    };
    for (size_t i = 0; i < sizeof(bad_speakers) / sizeof(bad_speakers[0]); i++) {
        // A valid field next to the bad one is not applied either.
        snprintf(form, sizeof(form), "vad_silence_db=%d&dialog_speaker=%s",
                 vad_other, bad_speakers[i]);
        expect_refused(form, "dialog_speaker");
        REQUIRE(strcmp(config_dialog_speaker(), "roger") == 0);
        REQUIRE(config_vad_silence_db() == vad_before);
    }
    REQUIRE(ring_contains("settings refused"));

    snprintf(form, sizeof(form), "vad_silence_db=%d&ab_mode=Dialog", vad_other);
    expect_refused(form, "ab_mode");
    REQUIRE(strcmp(config_ab_mode(), "announcement") == 0);
    REQUIRE(config_vad_silence_db() == vad_before);

    // Several bad fields: all named.
    expect_refused("ab_mode=x&vad_silence_db=5&dialog_speaker=X", "vad_silence_db");
    expect_refused("ab_mode=x&vad_silence_db=5&dialog_speaker=X", "ab_mode");

    // A valid request still saves.
    snprintf(form, sizeof(form), "vad_silence_db=%d", vad_other);
    post_config(form, response, sizeof(response));
    REQUIRE(strstr(response, "200 OK") != NULL);
    REQUIRE(strstr(response, "Saved.") != NULL);
    REQUIRE(config_vad_silence_db() == vad_other);

    // --- POST /api/dialog size refusals ------------------------------------
    // Below a header: not a set (not "too large").
    post_dialog(0, response, sizeof(response));
    REQUIRE(strstr(response, "400 Bad Request") != NULL);
    REQUIRE(strstr(response, "\"code\":\"not_a_set\"") != NULL);
    post_dialog(8, response, sizeof(response));
    REQUIRE(strstr(response, "\"code\":\"not_a_set\"") != NULL);
    REQUIRE(ring_contains("own dialog set refused: Not a dialog set"));

    // Over the generic request cap but within the dialog budget: the HTTP
    // layer lets it through to the handler, which (announcement mode here)
    // answers wrong_mode — not a 413.
    REQUIRE(storage_dialog_budget() > WEB_HTTP_MAX_BODY);
    post_dialog(WEB_HTTP_MAX_BODY + 200000, response, sizeof(response));
    REQUIRE(strstr(response, "409 Conflict") != NULL);
    REQUIRE(strstr(response, "\"code\":\"wrong_mode\"") != NULL);

    // Over the dialog budget: a JSON 413 and a WARN, from the HTTP layer
    // before any of it is read.
    post_dialog((size_t)storage_dialog_budget() + 1, response, sizeof(response));
    REQUIRE(strstr(response, "413") != NULL);
    REQUIRE(strstr(response, "\"code\":\"too_large\"") != NULL);
    REQUIRE(ring_contains("/api/dialog: request body of"));

    web_stop();
    unlink(config_path);
    puts("test_web_dialog_linux: all tests passed");
    return 0;
}
