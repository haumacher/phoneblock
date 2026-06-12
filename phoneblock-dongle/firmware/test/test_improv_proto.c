// Host-side unit tests for main/improv_proto.{c,h}.
//
// The builders and the parser are exercised against each other
// (round-trip) and against the hand-computed example from the Improv
// serial spec, so a checksum or framing regression cannot hide behind
// a symmetric bug.

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdbool.h>
#include <stdint.h>

#include "improv_proto.h"

static int g_tests = 0;
static int g_failures = 0;

#define CHECK_INT(name, expected, got) do {                          \
    g_tests++;                                                       \
    if ((long)(expected) != (long)(got)) {                           \
        fprintf(stderr, "FAIL %s: expected %ld got %ld\n",           \
                name, (long)(expected), (long)(got));                \
        g_failures++;                                                \
    }                                                                \
} while (0)

#define CHECK_STR(name, expected, got) do {                          \
    g_tests++;                                                       \
    if (strcmp((expected), (got)) != 0) {                            \
        fprintf(stderr, "FAIL %s: expected <<<%s>>> got <<<%s>>>\n", \
                name, (expected), (got));                            \
        g_failures++;                                                \
    }                                                                \
} while (0)

// Feed a byte sequence; returns true when exactly one packet came out
// at the *last* byte and copies its type/data to the out params.
static bool feed_all(improv_parser_t *p, const uint8_t *bytes, size_t n,
                     uint8_t *type, uint8_t *data, uint8_t *data_len)
{
    bool got = false;
    for (size_t i = 0; i < n; i++) {
        const uint8_t *d;
        uint8_t t, dl;
        if (improv_parser_feed(p, bytes[i], &t, &d, &dl)) {
            if (got || i + 1 != n) {
                return false;  // packet finished early or twice
            }
            *type = t;
            *data_len = dl;
            memcpy(data, d, dl);
            got = true;
        }
    }
    return got;
}

// Build a client-style RPC command packet (header + checksum) the way
// improv-wifi-serial-sdk does.
static size_t build_rpc(uint8_t cmd, const uint8_t *cmd_data, uint8_t n,
                        uint8_t *out)
{
    size_t len = 0;
    memcpy(out, "IMPROV", 6);
    len = 6;
    out[len++] = 1;           // version
    out[len++] = 0x03;        // RPC command
    out[len++] = (uint8_t)(n + 2);
    out[len++] = cmd;
    out[len++] = n;
    if (n) {
        memcpy(&out[len], cmd_data, n);
        len += n;
    }
    uint8_t sum = 0;
    for (size_t i = 0; i < len; i++) sum += out[i];
    out[len++] = sum;
    return len;
}

static void test_roundtrip_current_state(void)
{
    uint8_t pkt[IMPROV_MAX_PACKET];
    size_t n = improv_build_current_state(IMPROV_STATE_READY, pkt, sizeof(pkt));
    CHECK_INT("state packet size", 9 + 1 + 1, n);

    improv_parser_t p;
    improv_parser_reset(&p);
    uint8_t type, data[256], data_len;
    CHECK_INT("state roundtrip parses", 1,
              feed_all(&p, pkt, n, &type, data, &data_len));
    CHECK_INT("state type", IMPROV_TYPE_CURRENT_STATE, type);
    CHECK_INT("state data_len", 1, data_len);
    CHECK_INT("state value", IMPROV_STATE_READY, data[0]);
}

static void test_roundtrip_error(void)
{
    uint8_t pkt[IMPROV_MAX_PACKET];
    size_t n = improv_build_error(IMPROV_ERROR_UNABLE_TO_CONNECT,
                                  pkt, sizeof(pkt));

    improv_parser_t p;
    improv_parser_reset(&p);
    uint8_t type, data[256], data_len;
    CHECK_INT("error roundtrip parses", 1,
              feed_all(&p, pkt, n, &type, data, &data_len));
    CHECK_INT("error type", IMPROV_TYPE_ERROR_STATE, type);
    CHECK_INT("error value", IMPROV_ERROR_UNABLE_TO_CONNECT, data[0]);
}

static void test_rpc_result_strings(void)
{
    const char *strings[] = { "http://192.168.178.123/" };
    uint8_t pkt[IMPROV_MAX_PACKET];
    size_t n = improv_build_rpc_result(IMPROV_CMD_WIFI_SETTINGS,
                                       strings, 1, pkt, sizeof(pkt));
    size_t url_len = strlen(strings[0]);
    CHECK_INT("result size", 9 + 2 + 1 + url_len + 1, n);
    CHECK_INT("result cmd", IMPROV_CMD_WIFI_SETTINGS, pkt[9]);
    CHECK_INT("result total_len", 1 + url_len, pkt[10]);
    CHECK_INT("result str_len", url_len, pkt[11]);
    CHECK_INT("result str byte0", 'h', pkt[12]);

    // Empty result (scan terminator): data = [cmd, 0].
    n = improv_build_rpc_result(IMPROV_CMD_SCAN_NETWORKS, NULL, 0,
                                pkt, sizeof(pkt));
    CHECK_INT("empty result size", 9 + 2 + 1, n);
    CHECK_INT("empty result total_len", 0, pkt[10]);
}

static void test_parse_wifi_settings_spec_example(void)
{
    // Spec: SSID = MyWirelessAP, Password = mysecurepassword
    //   01 1E 0C {MyWirelessAP} 10 {mysecurepassword}
    uint8_t payload[64];
    size_t n = 0;
    payload[n++] = 0x0C;
    memcpy(&payload[n], "MyWirelessAP", 12);
    n += 12;
    payload[n++] = 0x10;
    memcpy(&payload[n], "mysecurepassword", 16);
    n += 16;
    CHECK_INT("spec payload len", 0x1E, n);

    char ssid[33], pass[65];
    CHECK_INT("spec parses", 1,
              improv_parse_wifi_settings(payload, (uint8_t)n,
                                         ssid, sizeof(ssid),
                                         pass, sizeof(pass)));
    CHECK_STR("spec ssid", "MyWirelessAP", ssid);
    CHECK_STR("spec pass", "mysecurepassword", pass);

    // Open network: empty password is legal.
    uint8_t open_net[] = { 2, 'A', 'B', 0 };
    CHECK_INT("open net parses", 1,
              improv_parse_wifi_settings(open_net, sizeof(open_net),
                                         ssid, sizeof(ssid),
                                         pass, sizeof(pass)));
    CHECK_STR("open net ssid", "AB", ssid);
    CHECK_STR("open net pass", "", pass);
}

static void test_parse_wifi_settings_malformed(void)
{
    char ssid[33], pass[65];

    CHECK_INT("empty payload rejected", 0,
              improv_parse_wifi_settings(NULL, 0,
                                         ssid, sizeof(ssid),
                                         pass, sizeof(pass)));

    // ssid_len runs past the payload.
    uint8_t truncated[] = { 10, 'A', 'B' };
    CHECK_INT("truncated ssid rejected", 0,
              improv_parse_wifi_settings(truncated, sizeof(truncated),
                                         ssid, sizeof(ssid),
                                         pass, sizeof(pass)));

    // Trailing junk after the password.
    uint8_t trailing[] = { 1, 'A', 1, 'B', 'X' };
    CHECK_INT("trailing junk rejected", 0,
              improv_parse_wifi_settings(trailing, sizeof(trailing),
                                         ssid, sizeof(ssid),
                                         pass, sizeof(pass)));

    // SSID longer than the destination buffer.
    uint8_t long_ssid[40] = { 35 };
    memset(&long_ssid[1], 'S', 35);
    long_ssid[36] = 0;
    CHECK_INT("oversize ssid rejected", 0,
              improv_parse_wifi_settings(long_ssid, 37,
                                         ssid, sizeof(ssid),
                                         pass, sizeof(pass)));
}

static void test_parser_rpc_and_noise(void)
{
    improv_parser_t p;
    improv_parser_reset(&p);

    uint8_t cmd_data[] = { 1, 'X', 1, 'Y' };
    uint8_t pkt[64];
    size_t n = build_rpc(IMPROV_CMD_WIFI_SETTINGS, cmd_data,
                         sizeof(cmd_data), pkt);

    // Log noise before, stray newline after (the SDK appends '\n').
    uint8_t stream[128];
    size_t s = 0;
    memcpy(&stream[s], "I (123) boot: IMPROVing...\n", 27);
    s += 27;
    memcpy(&stream[s], pkt, n);
    s += n;

    uint8_t type, data[256], data_len;
    CHECK_INT("rpc after noise parses", 1,
              feed_all(&p, stream, s, &type, data, &data_len));
    CHECK_INT("rpc type", IMPROV_TYPE_RPC_COMMAND, type);
    CHECK_INT("rpc data_len", 6, data_len);
    CHECK_INT("rpc cmd", IMPROV_CMD_WIFI_SETTINGS, data[0]);
    CHECK_INT("rpc inner len", 4, data[1]);

    // Trailing newline must not confuse the next packet.
    const uint8_t *d;
    uint8_t t, dl;
    CHECK_INT("newline ignored", 0, improv_parser_feed(&p, '\n', &t, &d, &dl));
    n = build_rpc(IMPROV_CMD_GET_STATE, NULL, 0, pkt);
    CHECK_INT("second rpc parses", 1,
              feed_all(&p, pkt, n, &type, data, &data_len));
    CHECK_INT("second rpc cmd", IMPROV_CMD_GET_STATE, data[0]);
}

static void test_parser_bad_checksum(void)
{
    improv_parser_t p;
    improv_parser_reset(&p);

    uint8_t pkt[64];
    size_t n = build_rpc(IMPROV_CMD_GET_INFO, NULL, 0, pkt);
    pkt[n - 1] ^= 0xFF;  // corrupt checksum

    uint8_t type, data[256], data_len;
    CHECK_INT("bad checksum dropped", 0,
              feed_all(&p, pkt, n, &type, data, &data_len));

    // Parser must have resynced: a good packet right after parses.
    n = build_rpc(IMPROV_CMD_GET_INFO, NULL, 0, pkt);
    CHECK_INT("good packet after bad parses", 1,
              feed_all(&p, pkt, n, &type, data, &data_len));
}

static void test_parser_header_resync(void)
{
    improv_parser_t p;
    improv_parser_reset(&p);

    uint8_t pkt[64];
    size_t n = build_rpc(IMPROV_CMD_GET_STATE, NULL, 0, pkt);

    // "IIMPROV…": the second 'I' restarts the header match.
    uint8_t stream[80];
    stream[0] = 'I';
    memcpy(&stream[1], pkt, n);

    uint8_t type, data[256], data_len;
    CHECK_INT("resync on repeated I", 1,
              feed_all(&p, stream, n + 1, &type, data, &data_len));
    CHECK_INT("resync cmd", IMPROV_CMD_GET_STATE, data[0]);
}

static void test_parser_wrong_version(void)
{
    improv_parser_t p;
    improv_parser_reset(&p);

    uint8_t pkt[64];
    size_t n = build_rpc(IMPROV_CMD_GET_STATE, NULL, 0, pkt);
    pkt[6] = 2;  // unsupported version

    uint8_t type, data[256], data_len;
    CHECK_INT("wrong version dropped", 0,
              feed_all(&p, pkt, n, &type, data, &data_len));
}

int main(void)
{
    test_roundtrip_current_state();
    test_roundtrip_error();
    test_rpc_result_strings();
    test_parse_wifi_settings_spec_example();
    test_parse_wifi_settings_malformed();
    test_parser_rpc_and_noise();
    test_parser_bad_checksum();
    test_parser_header_resync();
    test_parser_wrong_version();

    if (g_failures) {
        fprintf(stderr, "%d/%d checks FAILED\n", g_failures, g_tests);
        return 1;
    }
    printf("all %d checks passed\n", g_tests);
    return 0;
}
