#define _POSIX_C_SOURCE 200809L

// Linux platform services that need no libcurl / OpenSSL
// (core/platform_linux.c); the network and crypto half is covered by
// test_platform_linux_net.c, run by `make test-linux`.

#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "log_capture.h"
#include "config.h"
#include "linux_test_util.h"
#include "platform.h"

static volatile int task_done;

static void mark_done(void *arg)
{
    int *value = arg;
    *value = 1;
    task_done = 1;
}

static void test_time(void)
{
    uint64_t before = pb_monotonic_us();
    pb_task_sleep_ms(2);
    uint64_t after = pb_monotonic_us();
    REQUIRE(after >= before + 2000);
}

static void test_random(void)
{
    uint32_t first = pb_random_u32();
    uint32_t second = pb_random_u32();
    REQUIRE(first != second);
}

static void test_kv_store(void)
{
    char path[256];
    use_temp_config(path, sizeof(path));

    pb_kv_t *store = NULL;
    REQUIRE(pb_kv_open("phoneblock", true, &store) == PB_KV_OK);
    REQUIRE(pb_kv_set_string(store, "sip_host", "fritz.box") == PB_KV_OK);
    REQUIRE(pb_kv_set_i32(store, "sip_port", 5060) == PB_KV_OK);
    REQUIRE(pb_kv_commit(store) == PB_KV_OK);
    pb_kv_close(store);

    REQUIRE(pb_kv_open("phoneblock", false, &store) == PB_KV_OK);
    char host[32];
    size_t host_len = sizeof(host);
    int32_t port = 0;
    REQUIRE(pb_kv_get_string(store, "sip_host", host, &host_len) == PB_KV_OK);
    REQUIRE(strcmp(host, "fritz.box") == 0);
    REQUIRE(pb_kv_get_i32(store, "sip_port", &port) == PB_KV_OK);
    REQUIRE(port == 5060);
    REQUIRE(pb_kv_get_i32(store, "missing", &port) == PB_KV_NOT_FOUND);
    pb_kv_close(store);

    // Erasing an absent key reports PB_KV_NOT_FOUND on both platforms.
    REQUIRE(pb_kv_open("phoneblock", true, &store) == PB_KV_OK);
    REQUIRE(pb_kv_erase_key(store, "missing") == PB_KV_NOT_FOUND);
    REQUIRE(pb_kv_erase_key(store, "sip_port") == PB_KV_OK);
    pb_kv_close(store);

    REQUIRE(strcmp(pb_kv_err_name(PB_KV_OK), "OK") == 0);
    REQUIRE(strcmp(pb_kv_err_name(PB_KV_ERROR), "key-value store error") == 0);
    unlink(path);
    unsetenv("PHONEBLOCK_CONFIG_FILE");
}

static void test_config_store(void)
{
    char path[256];
    use_temp_config(path, sizeof(path));

    config_load();
    REQUIRE(config_sip_port() == 5060);
    REQUIRE(config_sip_host()[0] == '\0');
    REQUIRE(config_device_id()[0] != '\0');

    config_update_t update = {
        .sip_host = "fritz.box",
        .sip_user = "phoneblock-test",
        .has_sip_port = true,
        .sip_port = 5061,
    };
    REQUIRE(config_update(&update) == PB_KV_OK);
    REQUIRE(strcmp(config_sip_host(), "fritz.box") == 0);
    REQUIRE(config_sip_port() == 5061);

    config_load();
    REQUIRE(strcmp(config_sip_user(), "phoneblock-test") == 0);
    REQUIRE(config_sip_port() == 5061);
    REQUIRE(config_set_last_failed_ota("1.2.3") == PB_KV_OK);
    REQUIRE(strcmp(config_last_failed_ota(), "1.2.3") == 0);
    REQUIRE(config_erase() == PB_KV_OK);

    config_load();
    REQUIRE(config_sip_host()[0] == '\0');
    REQUIRE(config_sip_port() == 5060);
    unlink(path);
    unsetenv("PHONEBLOCK_CONFIG_FILE");
}

static void wait_done(void)
{
    for (int attempt = 0; attempt < 1000 && !task_done; attempt++) {
        pb_task_sleep_ms(1);
    }
}

static void test_task(void)
{
    int value = 0;
    task_done = 0;
    REQUIRE(pb_task_create(mark_done, &value, "test", 0, PB_PRIO_NORMAL) == PB_OK);
    wait_done();
    REQUIRE(value == 1);
}

// The stack size is an ESP32 byte budget; Linux must not take it literally.
// A 1 KB hint would be rejected by pthread_attr_setstacksize (below
// PTHREAD_STACK_MIN), and the thread must have room for a 200 KB frame.
static void use_big_stack(void *arg)
{
    volatile char big[200 * 1024];
    memset((char *)big, 0x5a, sizeof(big));
    *(int *)arg = big[sizeof(big) - 1] == 0x5a;
    task_done = 1;
}

static void test_task_stack_floor(void)
{
    int ok = 0;
    task_done = 0;
    REQUIRE(pb_task_create(use_big_stack, &ok, "big", 1024, PB_PRIO_MEDIA) == PB_OK);
    wait_done();
    REQUIRE(ok == 1);
}

static void signal_event(void *arg)
{
    pb_task_sleep_ms(2);
    pb_event_set(arg, 1u << 3);
}

static void test_event(void)
{
    pb_event_t *event = pb_event_create();
    REQUIRE(event != NULL);
    REQUIRE(pb_event_wait_s(event, 0) == 0);

    // The timeout is in seconds (see platform.h).
    uint64_t before = pb_monotonic_us();
    REQUIRE(pb_event_wait_s(event, 1) == 0);
    REQUIRE(pb_monotonic_us() - before >= 1000000);

    pb_event_set(event, (1u << 0) | (1u << 1));
    REQUIRE(pb_event_wait_s(event, 0) == ((1u << 0) | (1u << 1)));
    REQUIRE(pb_event_wait_s(event, 0) == 0);

    REQUIRE(pb_task_create(signal_event, event, "event", 1024,
                          PB_PRIO_NORMAL) == PB_OK);
    REQUIRE(pb_event_wait_s(event, 5) == (1u << 3));
    pb_event_destroy(event);
}

// Fixed-rate pacing must not drift: 10 intervals of 20 ms take ~200 ms
// regardless of the work done between them.
static void test_delay_until(void)
{
    pb_deadline_t deadline;
    uint64_t start = pb_monotonic_us();
    pb_deadline_init(&deadline);
    for (int i = 0; i < 10; i++) {
        pb_task_sleep_ms(5);  // simulated per-frame work
        pb_task_delay_until(&deadline, 20);
    }
    uint64_t elapsed = pb_monotonic_us() - start;
    REQUIRE(elapsed >= 200000);
    REQUIRE(elapsed < 240000);

    // A missed deadline returns at once and the next one still advances by
    // one interval (catch-up, like vTaskDelayUntil).
    pb_deadline_init(&deadline);
    pb_task_sleep_ms(50);
    uint64_t before = pb_monotonic_us();
    pb_task_delay_until(&deadline, 20);
    REQUIRE(pb_monotonic_us() - before < 5000);
}

static void test_mutex(void)
{
    pb_mutex_t *mutex = pb_mutex_create();
    REQUIRE(mutex != NULL);
    pb_mutex_lock(mutex);
    pb_mutex_unlock(mutex);
}

static void test_mac(void)
{
    uint8_t first[6], second[6];
    int result = pb_get_mac(first);
    REQUIRE(result == 0 || result == -1);
    if (result == 0) {
        // Deterministic, not "whatever getifaddrs() listed first".
        REQUIRE(pb_get_mac(second) == 0);
        REQUIRE(memcmp(first, second, 6) == 0);
    }
}

// Linux log lines use the ESP-IDF layout, so the shared parser (which feeds
// the web UI's error ring on the ESP32) reads them unchanged — including a
// message that itself contains ": ".
static void test_log_format(void)
{
    FILE *capture = tmpfile();
    REQUIRE(capture);
    fflush(stderr);
    int saved = dup(fileno(stderr));
    REQUIRE(dup2(fileno(capture), fileno(stderr)) >= 0);
    pb_log_warn("sip", "registrar %s: %d", "fritz.box", 401);
    fflush(stderr);
    REQUIRE(dup2(saved, fileno(stderr)) >= 0);
    close(saved);

    char line[256] = "";
    rewind(capture);
    REQUIRE(fgets(line, sizeof(line), capture));
    fclose(capture);

    char tag[32], msg[128];
    REQUIRE(log_capture_parse(line, tag, sizeof(tag), msg, sizeof(msg)) == 'W');
    REQUIRE(strcmp(tag, "sip") == 0);
    REQUIRE(strcmp(msg, "registrar fritz.box: 401") == 0);
    REQUIRE(pb_task_stack_free() == -1);
}

// A commit writes back the other namespaces verbatim, values keep their
// leading/trailing spaces, and the settings file is locked while open.
static void test_kv_namespaces_and_whitespace(void)
{
    char path[256];
    use_temp_config(path, sizeof(path));

    pb_kv_t *store = NULL;
    REQUIRE(pb_kv_open("alpha", true, &store) == PB_KV_OK);
    char lock_path[300];
    snprintf(lock_path, sizeof(lock_path), "%s.lock", path);
    REQUIRE(access(lock_path, F_OK) == 0);
    REQUIRE(pb_kv_set_string(store, "user", "alice") == PB_KV_OK);
    REQUIRE(pb_kv_commit(store) == PB_KV_OK);
    pb_kv_close(store);

    REQUIRE(pb_kv_open("beta", true, &store) == PB_KV_OK);
    REQUIRE(pb_kv_set_string(store, "token", "xyz") == PB_KV_OK);
    REQUIRE(pb_kv_commit(store) == PB_KV_OK);
    pb_kv_close(store);

    // Committing "beta" must not have deleted "alpha".
    REQUIRE(pb_kv_open("alpha", false, &store) == PB_KV_OK);
    char user[32];
    size_t user_len = sizeof(user);
    REQUIRE(pb_kv_get_string(store, "user", user, &user_len) == PB_KV_OK);
    REQUIRE(strcmp(user, "alice") == 0);
    pb_kv_close(store);

    FILE *f = fopen(path, "r");
    REQUIRE(f != NULL);
    char content[512] = "";
    REQUIRE(fread(content, 1, sizeof(content) - 1, f) > 0);
    fclose(f);
    REQUIRE(strstr(content, "[alpha]") != NULL);
    REQUIRE(strstr(content, "[beta]") != NULL);

    // A password with leading/trailing spaces round-trips.
    REQUIRE(pb_kv_open("alpha", true, &store) == PB_KV_OK);
    REQUIRE(pb_kv_set_string(store, "password", " secret ") == PB_KV_OK);
    REQUIRE(pb_kv_commit(store) == PB_KV_OK);
    pb_kv_close(store);

    REQUIRE(pb_kv_open("alpha", false, &store) == PB_KV_OK);
    char password[32];
    size_t password_len = sizeof(password);
    REQUIRE(pb_kv_get_string(store, "password", password, &password_len)
            == PB_KV_OK);
    REQUIRE(strcmp(password, " secret ") == 0);
    pb_kv_close(store);

    // Erasing "beta" (config_erase) leaves "alpha" alone.
    REQUIRE(pb_kv_open("beta", true, &store) == PB_KV_OK);
    REQUIRE(pb_kv_erase_all(store) == PB_KV_OK);
    REQUIRE(pb_kv_commit(store) == PB_KV_OK);
    pb_kv_close(store);
    REQUIRE(pb_kv_open("alpha", false, &store) == PB_KV_OK);
    user_len = sizeof(user);
    REQUIRE(pb_kv_get_string(store, "user", user, &user_len) == PB_KV_OK);
    REQUIRE(strcmp(user, "alice") == 0);
    pb_kv_close(store);

    unlink(path);
    unlink(lock_path);
    unsetenv("PHONEBLOCK_CONFIG_FILE");
}

int main(void)
{
    test_time();
    test_random();
    test_kv_store();
    test_kv_namespaces_and_whitespace();
    test_config_store();
    test_task();
    test_task_stack_floor();
    test_event();
    test_delay_until();
    test_mutex();
    test_mac();
    test_log_format();
    pb_log_info("test", "%s", "log ok");
    puts("test_platform_linux: all tests passed");
    return 0;
}
