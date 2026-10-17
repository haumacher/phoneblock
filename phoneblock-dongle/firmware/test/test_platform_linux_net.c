#define _POSIX_C_SOURCE 200809L

// Network / crypto half of the Linux platform layer
// (core/platform_linux_net.c, libcurl + OpenSSL). Built and run only by
// `make test-linux`; see test_platform_linux.c for the rest.

#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <unistd.h>
#include <openssl/ec.h>
#include <openssl/evp.h>
#include <openssl/x509.h>

#include "linux_test_util.h"
#include "platform.h"

static void test_crypto(void)
{
    static const uint8_t expected_md5[16] = {
        0x90, 0x01, 0x50, 0x98, 0x3c, 0xd2, 0x4f, 0xb0,
        0xd6, 0x96, 0x3f, 0x7d, 0x28, 0xe1, 0x7f, 0x72,
    };
    static const unsigned char message[] = "abc";
    uint8_t digest[16];
    REQUIRE(pb_md5(message, sizeof(message) - 1, digest) == PB_OK);
    REQUIRE(memcmp(digest, expected_md5, sizeof(digest)) == 0);

    static const uint8_t expected_sha1[20] = {
        0xa9, 0x99, 0x3e, 0x36, 0x47, 0x06, 0x81, 0x6a, 0xba, 0x3e,
        0x25, 0x71, 0x78, 0x50, 0xc2, 0x6c, 0x9c, 0xd0, 0xd8, 0x9d,
    };
    uint8_t sha1[20];
    REQUIRE(pb_sha1(message, sizeof(message) - 1, sha1) == PB_OK);
    REQUIRE(memcmp(sha1, expected_sha1, sizeof(sha1)) == 0);

    static const uint8_t expected_sha256[32] = {
        0xba, 0x78, 0x16, 0xbf, 0x8f, 0x01, 0xcf, 0xea,
        0x41, 0x41, 0x40, 0xde, 0x5d, 0xae, 0x22, 0x23,
        0xb0, 0x03, 0x61, 0xa3, 0x96, 0x17, 0x7a, 0x9c,
        0xb4, 0x10, 0xff, 0x61, 0xf2, 0x00, 0x15, 0xad,
    };
    pb_sha256_t *sha256 = pb_sha256_create();
    REQUIRE(sha256 != NULL);
    REQUIRE(pb_sha256_update(sha256, "a", 1) == PB_OK);
    REQUIRE(pb_sha256_update(sha256, "bc", 2) == PB_OK);
    uint8_t sha256_digest[32];
    REQUIRE(pb_sha256_finish(sha256, sha256_digest) == PB_OK);
    REQUIRE(memcmp(sha256_digest, expected_sha256, sizeof(sha256_digest)) == 0);
    pb_sha256_destroy(sha256);

    unsigned char encoded[16];
    size_t encoded_len = 0;
    REQUIRE(pb_base64_encode(encoded, sizeof(encoded), &encoded_len,
                            (const unsigned char *)"hello", 5) == 0);
    REQUIRE(encoded_len == 8 && memcmp(encoded, "aGVsbG8=", 8) == 0);

    unsigned char decoded[8];
    size_t decoded_len = 0;
    REQUIRE(pb_base64_decode(decoded, sizeof(decoded), &decoded_len,
                            encoded, encoded_len) == 0);
    REQUIRE(decoded_len == 5 && memcmp(decoded, "hello", 5) == 0);
}

static void test_ecdsa_verify(void)
{
    EVP_PKEY_CTX *key_context = EVP_PKEY_CTX_new_id(EVP_PKEY_EC, NULL);
    REQUIRE(key_context != NULL);
    REQUIRE(EVP_PKEY_keygen_init(key_context) > 0);
    REQUIRE(EVP_PKEY_CTX_set_ec_paramgen_curve_nid(key_context,
                                                   NID_X9_62_prime256v1) > 0);
    EVP_PKEY *key = NULL;
    REQUIRE(EVP_PKEY_keygen(key_context, &key) > 0);
    EVP_PKEY_CTX_free(key_context);

    int public_key_len = i2d_PUBKEY(key, NULL);
    REQUIRE(public_key_len > 0);
    uint8_t *public_key = malloc((size_t)public_key_len);
    REQUIRE(public_key != NULL);
    unsigned char *public_key_cursor = public_key;
    REQUIRE(i2d_PUBKEY(key, &public_key_cursor) == public_key_len);

    static const char message[] = "platform ECDSA verification";
    uint8_t hash[32];
    pb_sha256_t *sha = pb_sha256_create();
    REQUIRE(sha != NULL);
    REQUIRE(pb_sha256_update(sha, message, sizeof(message) - 1) == PB_OK);
    REQUIRE(pb_sha256_finish(sha, hash) == PB_OK);
    pb_sha256_destroy(sha);

    EVP_PKEY_CTX *sign_context = EVP_PKEY_CTX_new(key, NULL);
    REQUIRE(sign_context != NULL);
    REQUIRE(EVP_PKEY_sign_init(sign_context) > 0);
    REQUIRE(EVP_PKEY_CTX_set_signature_md(sign_context, EVP_sha256()) > 0);
    size_t signature_len = 0;
    REQUIRE(EVP_PKEY_sign(sign_context, NULL, &signature_len, hash, sizeof(hash)) > 0);
    uint8_t *signature = malloc(signature_len);
    REQUIRE(signature != NULL);
    REQUIRE(EVP_PKEY_sign(sign_context, signature, &signature_len,
                         hash, sizeof(hash)) > 0);
    EVP_PKEY_CTX_free(sign_context);

    int native = -1;
    REQUIRE(pb_ecdsa_p256_verify(public_key, (size_t)public_key_len, hash,
                                 signature, signature_len, &native)
            == PB_SIG_VALID);
    REQUIRE(native == 0);
    hash[0] ^= 1;
    REQUIRE(pb_ecdsa_p256_verify(public_key, (size_t)public_key_len, hash,
                                 signature, signature_len, NULL)
            == PB_SIG_MISMATCH);
    static const uint8_t not_a_key[] = { 0x30, 0x03, 0x02, 0x01, 0x00 };
    REQUIRE(pb_ecdsa_p256_verify(not_a_key, sizeof(not_a_key), hash,
                                 signature, signature_len, NULL)
            == PB_SIG_BAD_KEY);

    free(signature);
    free(public_key);
    EVP_PKEY_free(key);
}

static void test_http_errors(void)
{
    REQUIRE(strcmp(pb_http_err_name(PB_HTTP_OK), "OK") == 0);
    REQUIRE(!pb_http_is_connect_error(PB_HTTP_OK));

    // Nothing listens on port 1 of the loopback: a connect error, named.
    char body[16];
    int status = -1;
    int result = pb_http_request("GET", "http://127.0.0.1:1/", NULL, 0,
                                 NULL, 0, 2000, body, sizeof(body), NULL,
                                 &status, NULL, NULL, NULL);
    REQUIRE(result != PB_HTTP_OK && result != PB_HTTP_ERROR);
    REQUIRE(pb_http_is_connect_error(result));
    REQUIRE(pb_http_err_name(result)[0] != '\0');
    REQUIRE(status == 0);
}

static int server_fd;

static void *serve_redirect(void *unused)
{
    (void)unused;
    for (int request = 0; request < 2; request++) {
        int client = accept(server_fd, NULL, NULL);
        REQUIRE(client >= 0);
        char request_buf[1024] = {0};
        REQUIRE(recv(client, request_buf, sizeof(request_buf) - 1, 0) > 0);
        const char *body = strstr(request_buf, "GET /new ") != NULL
            ? "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok"
            : "HTTP/1.1 302 Found\r\nLocation: /new\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
        REQUIRE(send(client, body, strlen(body), 0) == (ssize_t)strlen(body));
        close(client);
    }
    return NULL;
}

// Redirects are followed, and loopback never goes through a proxy: the test
// sets http_proxy to an address where nothing listens.
static void test_http_redirect_no_proxy(void)
{
    server_fd = socket(AF_INET, SOCK_STREAM, 0);
    REQUIRE(server_fd >= 0);
    struct sockaddr_in address = {
        .sin_family = AF_INET,
        .sin_addr.s_addr = htonl(INADDR_LOOPBACK),
        .sin_port = 0,
    };
    REQUIRE(bind(server_fd, (struct sockaddr *)&address, sizeof(address)) == 0);
    REQUIRE(listen(server_fd, 2) == 0);
    socklen_t address_len = sizeof(address);
    REQUIRE(getsockname(server_fd, (struct sockaddr *)&address, &address_len) == 0);

    REQUIRE(setenv("http_proxy", "http://127.0.0.1:1", 1) == 0);
    pthread_t server_thread;
    REQUIRE(pthread_create(&server_thread, NULL, serve_redirect, NULL) == 0);

    char url[64];
    snprintf(url, sizeof(url), "http://127.0.0.1:%u/old",
             (unsigned)ntohs(address.sin_port));
    char body[16];
    size_t body_len = 0;
    int status = 0;
    REQUIRE(pb_http_request("GET", url, NULL, 0, NULL, 0, 2000,
                            body, sizeof(body), &body_len, &status,
                            NULL, NULL, NULL) == PB_OK);
    REQUIRE(status == 200);
    REQUIRE(body_len == 2 && memcmp(body, "ok", 2) == 0);
    REQUIRE(pthread_join(server_thread, NULL) == 0);

    close(server_fd);
    unsetenv("http_proxy");
}

int main(void)
{
    test_crypto();
    test_ecdsa_verify();
    test_http_errors();
    test_http_redirect_no_proxy();
    puts("test_platform_linux_net: all tests passed");
    return 0;
}
