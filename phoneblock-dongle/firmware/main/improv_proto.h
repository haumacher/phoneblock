#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

// Improv Wi-Fi Serial protocol — pure packet layer, no ESP-IDF
// dependencies so it can be host-tested (see test/test_improv_proto.c).
//
// Spec: https://www.improv-wifi.com/serial/
//
// Wire format of every packet (both directions):
//
//   "IMPROV" (6 bytes)  version (1)  type (1)  length (1)
//   data (length bytes)  checksum (1)
//
// The checksum is the sum of all preceding bytes — including the
// "IMPROV" header — truncated to 8 bits.
//
// Framing gotcha (improv-wifi-serial-sdk): the browser-side parser
// only recognizes the header when its first byte arrives at the start
// of a "line", i.e. directly after a 0x0A byte. The transport layer
// must therefore frame each device-to-client packet in '\n'…'\n';
// improv.c does that when writing to the UART.

// Packet types.
#define IMPROV_TYPE_CURRENT_STATE 0x01  // device -> client
#define IMPROV_TYPE_ERROR_STATE   0x02  // device -> client
#define IMPROV_TYPE_RPC_COMMAND   0x03  // client -> device
#define IMPROV_TYPE_RPC_RESULT    0x04  // device -> client

// Current-state values.
#define IMPROV_STATE_READY        0x02
#define IMPROV_STATE_PROVISIONING 0x03
#define IMPROV_STATE_PROVISIONED  0x04

// Error-state values.
#define IMPROV_ERROR_NONE              0x00
#define IMPROV_ERROR_INVALID_RPC       0x01
#define IMPROV_ERROR_UNKNOWN_RPC       0x02
#define IMPROV_ERROR_UNABLE_TO_CONNECT 0x03
#define IMPROV_ERROR_UNKNOWN           0xFF

// RPC command ids (first data byte of an RPC_COMMAND packet).
#define IMPROV_CMD_WIFI_SETTINGS  0x01
#define IMPROV_CMD_GET_STATE      0x02
#define IMPROV_CMD_GET_INFO       0x03
#define IMPROV_CMD_SCAN_NETWORKS  0x04

// Header (6) + version (1) + type (1) + length (1) + data (<= 255)
// + checksum (1).
#define IMPROV_MAX_PACKET (9 + 255 + 1)

// Incremental parser for the client-to-device stream. Bytes that do
// not line up with an "IMPROV" header are discarded silently — the
// UART carries log output noise in the other direction only, but a
// client may send stray newlines around its packets.
typedef struct {
    uint8_t buf[IMPROV_MAX_PACKET];
    size_t  len;       // bytes collected so far
    size_t  expected;  // total packet size, valid once len >= 9
} improv_parser_t;

void improv_parser_reset(improv_parser_t *p);

// Feed one byte. Returns true when a complete packet with a valid
// version and checksum has been received; *type, *data and *data_len
// then point into the parser's buffer and stay valid until the next
// feed/reset call. Packets with a bad checksum are dropped silently
// (the client never learns a sequence number, so there is nothing to
// NAK — it will time out and retry).
bool improv_parser_feed(improv_parser_t *p, uint8_t byte,
                        uint8_t *type, const uint8_t **data,
                        uint8_t *data_len);

// Packet builders. Each writes a complete packet (header through
// checksum, no '\n' framing) into out and returns its size, or 0 if
// cap is too small or the payload does not fit the one-byte length
// field.
size_t improv_build_current_state(uint8_t state, uint8_t *out, size_t cap);
size_t improv_build_error(uint8_t error, uint8_t *out, size_t cap);

// RPC result: data = [command, strings_total_len, (len, bytes)*].
// n_strings == 0 produces the empty result that terminates a scan
// response sequence.
size_t improv_build_rpc_result(uint8_t command,
                               const char *const *strings, size_t n_strings,
                               uint8_t *out, size_t cap);

// Parse the payload of a WIFI_SETTINGS RPC command:
//   [ssid_len, ssid…, pass_len, pass…]
// data/data_len are the RPC data *after* the command and rpc-length
// bytes. Returns false on malformed input or when a value exceeds the
// destination capacity (ssid_cap/pass_cap include the NUL).
bool improv_parse_wifi_settings(const uint8_t *data, uint8_t data_len,
                                char *ssid, size_t ssid_cap,
                                char *pass, size_t pass_cap);
