#define _POSIX_C_SOURCE 200809L

// /api/i18n/ui through the real web server of the Linux build: without a
// downloaded pack it serves the baked-in English one, and ?fallback=1 asks
// for that English pack explicitly (#721), which the page keeps as the last
// fallback for keys a downloaded pack lacks. Needs the installed web assets
// (ui_en.json) of the same build.

#include <arpa/inet.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>

#include "cJSON.h"
#include "config.h"
#include "linux_test_util.h"
#include "web.h"

static size_t request(uint16_t port, const char *message,
                      char *response, size_t capacity)
{
    int client = socket(AF_INET, SOCK_STREAM, 0);
    REQUIRE(client >= 0);
    struct sockaddr_in address = {
        .sin_family = AF_INET,
        .sin_addr.s_addr = htonl(INADDR_LOOPBACK),
        .sin_port = htons(port),
    };
    REQUIRE(connect(client, (struct sockaddr *)&address, sizeof(address)) == 0);

    size_t message_length = strlen(message);
    size_t sent = 0;
    while (sent < message_length) {
        ssize_t count = send(client, message + sent, message_length - sent, 0);
        REQUIRE(count > 0);
        sent += (size_t)count;
    }

    size_t received = 0;
    while (received + 1 < capacity) {
        ssize_t count = recv(client, response + received,
                             capacity - received - 1, 0);
        if (count == 0) break;
        REQUIRE(count > 0);
        received += (size_t)count;
    }
    response[received] = '\0';
    close(client);
    return received;
}

// GET `uri`; the parsed JSON body (caller deletes).
static cJSON *get_json(uint16_t port, const char *uri)
{
    static char response[262144];
    char message[256];
    snprintf(message, sizeof(message),
             "GET %s HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n", uri);
    REQUIRE(request(port, message, response, sizeof(response)) > 0);
    REQUIRE(strstr(response, "200 OK") != NULL);
    const char *body = strstr(response, "\r\n\r\n");
    REQUIRE(body != NULL);
    cJSON *root = cJSON_Parse(body + 4);
    REQUIRE(root != NULL);
    return root;
}

// The served pack is the baked-in English one: right locale, and a key
// every UI build has.
static void require_english(cJSON *pack)
{
    cJSON *locale = cJSON_GetObjectItem(pack, "@@locale");
    REQUIRE(cJSON_IsString(locale) && strcmp(locale->valuestring, "en") == 0);
    REQUIRE(cJSON_IsString(cJSON_GetObjectItem(pack, "duration.minutes")));
}

int main(void)
{
    char config_path[256];
    use_temp_config(config_path, sizeof(config_path));
    config_load();
    // The installed web assets of this build (ui_en.json).
    REQUIRE(setenv("PHONEBLOCK_WEB_DIR", "../build/linux-host/web", 1) == 0);
    web_configure_listener(0, "127.0.0.1");
    web_start();
    uint16_t port = web_listening_port();
    REQUIRE(port != 0);

    // A German device without a downloaded pack serves English.
    config_update_t u = { .ui_lang = "de" };
    REQUIRE(config_update(&u) == 0);
    cJSON *pack = get_json(port, "/api/i18n/ui");
    require_english(pack);
    cJSON_Delete(pack);

    // ?fallback=1 serves the English pack (no auth needed, like the pack).
    pack = get_json(port, "/api/i18n/ui?fallback=1");
    require_english(pack);
    cJSON_Delete(pack);

    // Other query strings keep the plain behaviour.
    pack = get_json(port, "/api/i18n/ui?x=1");
    require_english(pack);
    cJSON_Delete(pack);

    web_stop();
    unlink(config_path);
    printf("test_web_i18n_linux: all tests passed\n");
    return 0;
}
