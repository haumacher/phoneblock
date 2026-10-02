#define _POSIX_C_SOURCE 200809L

#include <assert.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <openssl/ec.h>
#include <openssl/evp.h>
#include <openssl/x509.h>

#include "log_capture.h"
#include "config.h"
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

static void test_crypto(void)
{
    static const uint8_t expected_md5[16] = {
        0x90, 0x01, 0x50, 0x98, 0x3c, 0xd2, 0x4f, 0xb0,
        0xd6, 0x96, 0x3f, 0x7d, 0x28, 0xe1, 0x7f, 0x72,
    };
    static const unsigned char message[] = "abc";
    uint8_t digest[16];
    assert(pb_md5(message, sizeof(message) - 1, digest));
    assert(memcmp(digest, expected_md5, sizeof(digest)) == 0);

    static const uint8_t expected_sha1[20] = {
        0xa9, 0x99, 0x3e, 0x36, 0x47, 0x06, 0x81, 0x6a, 0xba, 0x3e,
        0x25, 0x71, 0x78, 0x50, 0xc2, 0x6c, 0x9c, 0xd0, 0xd8, 0x9d,
    };
    uint8_t sha1[20];
    assert(pb_sha1(message, sizeof(message) - 1, sha1));
    assert(memcmp(sha1, expected_sha1, sizeof(sha1)) == 0);

    static const uint8_t expected_sha256[32] = {
        0xba, 0x78, 0x16, 0xbf, 0x8f, 0x01, 0xcf, 0xea,
        0x41, 0x41, 0x40, 0xde, 0x5d, 0xae, 0x22, 0x23,
        0xb0, 0x03, 0x61, 0xa3, 0x96, 0x17, 0x7a, 0x9c,
        0xb4, 0x10, 0xff, 0x61, 0xf2, 0x00, 0x15, 0xad,
    };
    pb_sha256_t *sha256 = pb_sha256_create();
    assert(sha256 != NULL);
    assert(pb_sha256_update(sha256, "a", 1));
    assert(pb_sha256_update(sha256, "bc", 2));
    uint8_t sha256_digest[32];
    assert(pb_sha256_finish(sha256, sha256_digest));
    assert(memcmp(sha256_digest, expected_sha256, sizeof(sha256_digest)) == 0);
    pb_sha256_destroy(sha256);

    unsigned char encoded[16];
    size_t encoded_len = 0;
    assert(pb_base64_encode(encoded, sizeof(encoded), &encoded_len,
                            (const unsigned char *)"hello", 5) == 0);
    assert(encoded_len == 8 && memcmp(encoded, "aGVsbG8=", 8) == 0);

    unsigned char decoded[8];
    size_t decoded_len = 0;
    assert(pb_base64_decode(decoded, sizeof(decoded), &decoded_len,
                            encoded, encoded_len) == 0);
    assert(decoded_len == 5 && memcmp(decoded, "hello", 5) == 0);
}

static void test_ecdsa_verify(void)
{
    EVP_PKEY_CTX *key_context = EVP_PKEY_CTX_new_id(EVP_PKEY_EC, NULL);
    assert(key_context != NULL);
    assert(EVP_PKEY_keygen_init(key_context) > 0);
    assert(EVP_PKEY_CTX_set_ec_paramgen_curve_nid(key_context,
                                                   NID_X9_62_prime256v1) > 0);
    EVP_PKEY *key = NULL;
    assert(EVP_PKEY_keygen(key_context, &key) > 0);
    EVP_PKEY_CTX_free(key_context);

    int public_key_len = i2d_PUBKEY(key, NULL);
    assert(public_key_len > 0);
    uint8_t *public_key = malloc((size_t)public_key_len);
    assert(public_key != NULL);
    unsigned char *public_key_cursor = public_key;
    assert(i2d_PUBKEY(key, &public_key_cursor) == public_key_len);

    static const char message[] = "platform ECDSA verification";
    uint8_t hash[32];
    pb_sha256_t *sha = pb_sha256_create();
    assert(sha != NULL);
    assert(pb_sha256_update(sha, message, sizeof(message) - 1));
    assert(pb_sha256_finish(sha, hash));
    pb_sha256_destroy(sha);

    EVP_PKEY_CTX *sign_context = EVP_PKEY_CTX_new(key, NULL);
    assert(sign_context != NULL);
    assert(EVP_PKEY_sign_init(sign_context) > 0);
    assert(EVP_PKEY_CTX_set_signature_md(sign_context, EVP_sha256()) > 0);
    size_t signature_len = 0;
    assert(EVP_PKEY_sign(sign_context, NULL, &signature_len, hash, sizeof(hash)) > 0);
    uint8_t *signature = malloc(signature_len);
    assert(signature != NULL);
    assert(EVP_PKEY_sign(sign_context, signature, &signature_len,
                         hash, sizeof(hash)) > 0);
    EVP_PKEY_CTX_free(sign_context);

    assert(pb_ecdsa_p256_verify(public_key, (size_t)public_key_len, hash,
                                signature, signature_len));
    hash[0] ^= 1;
    assert(!pb_ecdsa_p256_verify(public_key, (size_t)public_key_len, hash,
                                 signature, signature_len));

    free(signature);
    free(public_key);
    EVP_PKEY_free(key);
}

static void test_kv_store(void)
{
    const char *path = "/tmp/phoneblock-platform-kv-test.conf";
    unlink(path);
    assert(setenv("PHONEBLOCK_CONFIG_FILE", path, 1) == 0);

    pb_kv_t *store = NULL;
    assert(pb_kv_open("phoneblock", true, &store) == PB_KV_OK);
    assert(pb_kv_set_string(store, "sip_host", "fritz.box") == PB_KV_OK);
    assert(pb_kv_set_i32(store, "sip_port", 5060) == PB_KV_OK);
    assert(pb_kv_commit(store) == PB_KV_OK);
    pb_kv_close(store);

    assert(pb_kv_open("phoneblock", false, &store) == PB_KV_OK);
    char host[32];
    size_t host_len = sizeof(host);
    int32_t port = 0;
    assert(pb_kv_get_string(store, "sip_host", host, &host_len) == PB_KV_OK);
    assert(strcmp(host, "fritz.box") == 0);
    assert(pb_kv_get_i32(store, "sip_port", &port) == PB_KV_OK);
    assert(port == 5060);
    pb_kv_close(store);
    unlink(path);
    unsetenv("PHONEBLOCK_CONFIG_FILE");
}

static void test_config_store(void)
{
    const char *path = "/tmp/phoneblock-config-test.conf";
    unlink(path);
    assert(setenv("PHONEBLOCK_CONFIG_FILE", path, 1) == 0);

    config_load();
    assert(config_sip_port() == 5060);
    assert(config_sip_host()[0] == '\0');
    assert(config_device_id()[0] != '\0');

    config_update_t update = {
        .sip_host = "fritz.box",
        .sip_user = "phoneblock-test",
        .has_sip_port = true,
        .sip_port = 5061,
    };
    assert(config_update(&update) == PB_KV_OK);
    assert(strcmp(config_sip_host(), "fritz.box") == 0);
    assert(config_sip_port() == 5061);

    config_load();
    assert(strcmp(config_sip_user(), "phoneblock-test") == 0);
    assert(config_sip_port() == 5061);
    assert(config_set_last_failed_ota("1.2.3") == PB_KV_OK);
    assert(strcmp(config_last_failed_ota(), "1.2.3") == 0);
    assert(config_erase() == PB_KV_OK);

    config_load();
    assert(config_sip_host()[0] == '\0');
    assert(config_sip_port() == 5060);
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

static void signal_event(void *arg)
{
    pb_task_sleep_ms(2);
    pb_event_set(arg, 1u << 3);
}

static void test_event(void)
{
    pb_event_t *event = pb_event_create();
    assert(event != NULL);
    assert(pb_event_wait(event, 1) == 0);

    pb_event_set(event, (1u << 0) | (1u << 1));
    assert(pb_event_wait(event, 0) == ((1u << 0) | (1u << 1)));
    assert(pb_event_wait(event, 0) == 0);

    assert(pb_task_create(signal_event, event, "event", 1024,
                          PB_PRIO_NORMAL));
    assert(pb_event_wait(event, 1000) == (1u << 3));
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
    test_crypto();
    test_ecdsa_verify();
    test_kv_store();
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
