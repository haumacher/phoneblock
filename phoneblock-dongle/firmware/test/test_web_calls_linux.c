#define _POSIX_C_SOURCE 200809L

// /api/calls through the real web server of the Linux build: each call
// carries its absolute time (epoch_s, #644) next to its age, and the two
// add up to the wall clock at the time of the request.

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

int main(void)
{
    char config_path[256];
    use_temp_config(config_path, sizeof(config_path));
    stats_setup();
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
    cJSON_Delete(root);

    web_stop();
    unlink(config_path);
    puts("test_web_calls_linux: all tests passed");
    return 0;
}
