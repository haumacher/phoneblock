#include <assert.h>
#include <pthread.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>

#include "log_capture.h"
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
    assert(after >= before + 2000);
}

static void test_random(void)
{
    uint32_t first = pb_random_u32();
    uint32_t second = pb_random_u32();
    assert(first != second);
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
    assert(pb_task_create(mark_done, &value, "test", 0, PB_PRIO_NORMAL));
    wait_done();
    assert(value == 1);
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
    assert(pb_task_create(use_big_stack, &ok, "big", 1024, PB_PRIO_MEDIA));
    wait_done();
    assert(ok == 1);
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
    assert(elapsed >= 200000);
    assert(elapsed < 240000);

    // A missed deadline returns at once and the next one still advances by
    // one interval (catch-up, like vTaskDelayUntil).
    pb_deadline_init(&deadline);
    pb_task_sleep_ms(50);
    uint64_t before = pb_monotonic_us();
    pb_task_delay_until(&deadline, 20);
    assert(pb_monotonic_us() - before < 5000);
}

static void test_mutex(void)
{
    pb_mutex_t *mutex = pb_mutex_create();
    assert(mutex != NULL);
    pb_mutex_lock(mutex);
    pb_mutex_unlock(mutex);
}

static void test_mac(void)
{
    uint8_t first[6], second[6];
    int result = pb_get_mac(first);
    assert(result == 0 || result == -1);
    if (result == 0) {
        // Deterministic, not "whatever getifaddrs() listed first".
        assert(pb_get_mac(second) == 0);
        assert(memcmp(first, second, 6) == 0);
    }
}

// Linux log lines use the ESP-IDF layout, so the shared parser (which feeds
// the web UI's error ring on the ESP32) reads them unchanged — including a
// message that itself contains ": ".
static void test_log_format(void)
{
    FILE *capture = tmpfile();
    assert(capture);
    fflush(stderr);
    int saved = dup(fileno(stderr));
    assert(dup2(fileno(capture), fileno(stderr)) >= 0);
    pb_log_warn("sip", "registrar %s: %d", "fritz.box", 401);
    fflush(stderr);
    assert(dup2(saved, fileno(stderr)) >= 0);
    close(saved);

    char line[256] = "";
    rewind(capture);
    assert(fgets(line, sizeof(line), capture));
    fclose(capture);

    char tag[32], msg[128];
    assert(log_capture_parse(line, tag, sizeof(tag), msg, sizeof(msg)) == 'W');
    assert(strcmp(tag, "sip") == 0);
    assert(strcmp(msg, "registrar fritz.box: 401") == 0);
    assert(pb_task_stack_free() == -1);
}

int main(void)
{
    test_time();
    test_random();
    test_task();
    test_task_stack_floor();
    test_delay_until();
    test_mutex();
    test_mac();
    test_log_format();
    pb_log_info("test", "%s", "log ok");
    puts("test_platform_linux: all tests passed");
    return 0;
}
