#pragma once

// Device-code link to a PhoneBlock account (#621) — the Linux service's
// sign-in behind Home Assistant ingress. Linux only: link.c is built by
// linux/Makefile alone, the ESP32 keeps the redirect flow. The protocol and
// the state machine are in link_proto.h; this module talks to the server
// and runs the poll task.
//
// One link at a time. link_start() asks the server for a code pair and
// hands the device code to the poll task; a second start replaces the
// running link (the task drops the old one at its next step — there is
// never more than one task). The device code stays in this module's RAM:
// it is in no answer and no log line, and it is wiped when the link ends.
// On approval the task runs phoneblock_adopt_token() (api.h), the same
// steps as the redirect flow's /token-callback.

#include <stdbool.h>
#include <stdint.h>

#include "link_proto.h"

// What /api/link/status reports.
typedef struct {
    link_state_t state;
    char         user_code[LINK_USER_CODE_CAP];
    char         verify_url[LINK_VERIFY_URL_CAP];
    char         message[LINK_MESSAGE_CAP];
    uint32_t     expires_in_s;   // of the start answer; 0 before a start
} link_status_t;

// Creates the lock. Called once by web_start() before the first request.
void link_init(void);

// Starts a new link against config_phoneblock_base_url() (blocks for the
// server's answer, at most 10 s). Returns true with `out` describing the
// pending link, or false with state LINK_ERROR and the reason in `message`
// (also logged at WARN). Either way a previously running link is ended.
bool link_start(link_status_t *out);

// A snapshot of the current link.
void link_get_status(link_status_t *out);

// Test seam: the length of one protocol second in milliseconds (default
// 1000). The Linux test shortens it so the real poll loop runs through
// intervals and the local expiry in a fraction of a second.
void link_set_second_ms(uint32_t ms);
