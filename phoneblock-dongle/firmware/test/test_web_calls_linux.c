#define _POSIX_C_SOURCE 200809L

// /api/calls through the real web server of the Linux build: each call
// carries its absolute time (epoch_s, #644) next to its age, and the two
// add up to the wall clock at the time of the request. The list is the
// persistent call file (#589): streamed in pages, "total" counts it, and
// ?limit=N returns the N newest.

#include <arpa/inet.h>
#include <stdio.h>
#include <string.h>
#include <sys/socket.h>
#include <time.h>
#include <unistd.h>

#include "api.h"
#include "cJSON.h"
#include "config.h"
#include "linux_test_util.h"
#include "stats.h"
#include "time_sync.h"
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
    static char response[131072];
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

int main(void)
{
    char config_path[256], calls_dir[256], calls_path[300];
    use_temp_config(config_path, sizeof(config_path));
    stats_setup();
    stats_set_clock(time_sync_now_epoch);
    use_temp_calls(calls_dir, sizeof(calls_dir), calls_path, sizeof(calls_path));
    config_load();
    web_configure_listener(0, "127.0.0.1");
    web_start();
    uint16_t port = web_listening_port();
    REQUIRE(port != 0);

    stats_record_call("0301234567", "", VERDICT_SPAM);

    time_t before = time(NULL);
    char response[8192];
    REQUIRE(request(port,
                    "GET /api/calls HTTP/1.1\r\nHost: localhost\r\n"
                    "Connection: close\r\n\r\n",
                    response, sizeof(response)) > 0);
    time_t after = time(NULL);
    REQUIRE(strstr(response, "200 OK") != NULL);

    const char *body = strstr(response, "\r\n\r\n");
    REQUIRE(body != NULL);
    cJSON *root = cJSON_Parse(body + 4);
    REQUIRE(root != NULL);
    cJSON *calls = cJSON_GetObjectItem(root, "calls");
    REQUIRE(cJSON_GetArraySize(calls) == 1);
    cJSON *call = cJSON_GetArrayItem(calls, 0);
    REQUIRE(strcmp(cJSON_GetObjectItem(call, "number")->valuestring,
                   "0301234567") == 0);
    cJSON *age = cJSON_GetObjectItem(call, "age_s");
    cJSON *epoch = cJSON_GetObjectItem(call, "epoch_s");
    REQUIRE(cJSON_IsNumber(age) && cJSON_IsNumber(epoch));
    REQUIRE(age->valuedouble >= 0);
    // Spam call still on the line (not ended yet): no duration so far.
    REQUIRE(cJSON_GetObjectItem(call, "duration_s")->valueint == 0);
    // The host keeps the wall clock, so it is set: epoch_s is not the
    // "clock not set" 0, and age plus epoch is the time of the request.
    double sum = epoch->valuedouble + age->valuedouble;
    REQUIRE(sum >= (double)before && sum <= (double)after);
    REQUIRE(cJSON_GetObjectItem(root, "total")->valueint == 1);
    cJSON_Delete(root);

    // More calls than one page of the handler: all of them, newest first.
    char number[24];
    for (int i = 0; i < 12; i++) {
        snprintf(number, sizeof(number), "03012345%02d", i);
        stats_record_call(number, "", VERDICT_LEGITIMATE);
    }
    root = get_json(port, "/api/calls");
    calls = cJSON_GetObjectItem(root, "calls");
    REQUIRE(cJSON_GetArraySize(calls) == 13);
    REQUIRE(cJSON_GetObjectItem(root, "total")->valueint == 13);
    REQUIRE(strcmp(cJSON_GetObjectItem(cJSON_GetArrayItem(calls, 0), "number")->valuestring,
                   "0301234511") == 0);
    REQUIRE(strcmp(cJSON_GetObjectItem(cJSON_GetArrayItem(calls, 12), "number")->valuestring,
                   "0301234567") == 0);
    cJSON_Delete(root);

    // ?limit=N: the N newest, the total still all of them (the web UI's
    // "show all (13)").
    root = get_json(port, "/api/calls?limit=10");
    calls = cJSON_GetObjectItem(root, "calls");
    REQUIRE(cJSON_GetArraySize(calls) == 10);
    REQUIRE(cJSON_GetObjectItem(root, "total")->valueint == 13);
    REQUIRE(strcmp(cJSON_GetObjectItem(cJSON_GetArrayItem(calls, 9), "number")->valuestring,
                   "0301234502") == 0);
    cJSON_Delete(root);

    // The list survives a restart: same calls, a call of the earlier run
    // keeps its time and its age follows from it.
    stats_calls_close();
    REQUIRE(stats_calls_open(calls_path));
    root = get_json(port, "/api/calls?limit=1");
    call = cJSON_GetArrayItem(cJSON_GetObjectItem(root, "calls"), 0);
    REQUIRE(strcmp(cJSON_GetObjectItem(call, "number")->valuestring, "0301234511") == 0);
    REQUIRE(cJSON_GetObjectItem(call, "epoch_s")->valuedouble > 0);
    REQUIRE(cJSON_GetObjectItem(call, "age_s")->valuedouble >= 0);
    REQUIRE(cJSON_GetObjectItem(root, "total")->valueint == 13);
    cJSON_Delete(root);

    stats_clear_calls();
    root = get_json(port, "/api/calls");
    REQUIRE(cJSON_GetArraySize(cJSON_GetObjectItem(root, "calls")) == 0);
    REQUIRE(cJSON_GetObjectItem(root, "total")->valueint == 0);
    cJSON_Delete(root);

    web_stop();
    unlink(config_path);
    unlink(calls_path);
    rmdir(calls_dir);
    puts("test_web_calls_linux: all tests passed");
    return 0;
}
