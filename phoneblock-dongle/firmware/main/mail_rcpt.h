#pragma once

#include <stdbool.h>
#include <stddef.h>

// Recipient list for the status mail.
//
// The web UI stores the recipients as a single ';'-separated string (one NVS
// key, one form field — see config_smtp_to()), so a household can send the
// status mail to more than one address. This module is the only place that
// knows the syntax: it splits the stored spec into individual addresses for
// the SMTP envelope (one RCPT TO per address, RFC 5321 §3.3) and renders the
// comma-separated form the RFC 5322 "To:" header wants.
//
// Pure string handling with no I/O, so it is host-tested (test_mail_rcpt.c).

// Per-address capacity, matching the single-address field the stored spec
// used to hold. Longer addresses are dropped, never truncated — a clipped
// address is a different (possibly someone else's) mailbox.
#define MAIL_RCPT_ADDR_CAP 64

// Upper bound on recipients per mail. The status mail is a household
// notification, not a mailing list; the cap keeps the envelope conversation
// and the "To:" header bounded, and the stored spec fits config's field.
#define MAIL_RCPT_MAX 5

// Capacity that holds the longest acceptable spec: MAIL_RCPT_MAX addresses of
// MAIL_RCPT_ADDR_CAP - 1 chars each, the ';' between them, and the NUL. The
// NVS field and the form buffer are both sized from this, so a list the UI
// accepts round-trips through a save without ever being truncated.
#define MAIL_RCPT_SPEC_CAP (MAIL_RCPT_MAX * MAIL_RCPT_ADDR_CAP)

typedef struct {
    char addr[MAIL_RCPT_MAX][MAIL_RCPT_ADDR_CAP];
    int  count;                  // usable addresses in addr[]
    bool dropped;                // an entry was discarded (too long, or over MAIL_RCPT_MAX)
} mail_rcpt_list_t;

// Split `spec` on ';' into `out`. Surrounding whitespace is trimmed and
// empty entries are skipped, so "a@b.de;" and " a@b.de ; c@d.de " both parse
// cleanly. Entries that do not fit MAIL_RCPT_ADDR_CAP, and any beyond
// MAIL_RCPT_MAX, are dropped and flagged in out->dropped. A NULL or
// all-empty spec yields count == 0. Returns out->count.
int mail_rcpt_parse(const char *spec, mail_rcpt_list_t *out);

// Render the list as the value of an RFC 5322 "To:" header — the addresses
// comma-separated and angle-bracketed, e.g. "<a@b.de>, <c@d.de>". Bounded by
// `cap`; returns false (and leaves a valid, possibly clamped string) when the
// list did not fit entirely.
bool mail_rcpt_header(const mail_rcpt_list_t *l, char *buf, size_t cap);
