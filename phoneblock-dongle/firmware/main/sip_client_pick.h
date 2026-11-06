#pragma once

#include <stdbool.h>

// Which Fritz!Box IP-phone entry the setup wizard writes (#708).
//
// Pure decision layer of tr064_provision_sip_client(): tr064.c reads the
// box's SIP clients (X_AVM-DE_GetClient3) and executes the outcome with
// X_AVM-DE_SetClient4; the rule itself lives here, free of SOAP and
// ESP-IDF, so the host tests in test/test_sip_client_pick.c cover it.
//
// The rule ("ask only on conflict", docs/plans/2026-11-06-dongle-second-
// device-design.md):
//
//   1. Own entry: the stored SIP user starts with "phoneblock-", the stored
//      registrar host is this run's Fritz!Box host, and the box has a
//      client with that username → overwrite it, keep the username. Covers
//      legacy MAC-derived names ("phoneblock-5fff54").
//   2. No PhoneBlock entry ("phoneblock-dongle", "phoneblock-dongle-<n>")
//      on the box → create "phoneblock-dongle" at the next free slot.
//   3. PhoneBlock entries the dongle cannot claim → conflict: nothing is
//      written, the user decides between replacing one of them and adding
//      the lowest free "phoneblock-dongle-<n>" (n >= 2, "PhoneBlock <n>").
//
// The user's decision comes back as `on_conflict`:
//   ""/NULL                no decision yet
//   "add"                  add as a further device
//   "replace:<username>"   overwrite that PhoneBlock entry, adopt its name

// Username of the first dongle on a box; further ones get "-<n>", n >= 2.
#define SIP_PICK_BASE_USER "phoneblock-dongle"

// Room for a client's fields as read from the box. A username that does
// not fit is not one of ours (ours are at most 22 characters); tr064.c
// stores it empty then, so it can match nothing.
#define SIP_PICK_USER_CAP   32
#define SIP_PICK_NAME_CAP   64
#define SIP_PICK_NUMBER_CAP 16

// Upper bound on scanned clients. A Fritz!Box has ten IP-phone slots
// (internal numbers 620..629); tr064.c refuses a larger count visibly
// instead of deciding on a truncated list.
#define SIP_PICK_MAX_CLIENTS 20

// One SIP client of the box, at the index of its position in the list.
typedef struct {
    char username[SIP_PICK_USER_CAP];
    char name[SIP_PICK_NAME_CAP];       // NewX_AVM-DE_PhoneName, "" if absent
    char number[SIP_PICK_NUMBER_CAP];   // NewX_AVM-DE_InternalNumber ("620"), "" if absent
} sip_client_t;

typedef enum {
    SIP_PICK_OVERWRITE,  // SetClient4 at `index` (an existing client)
    SIP_PICK_CREATE,     // SetClient4 at `index` == client count (new client)
    SIP_PICK_CONFLICT,   // write nothing, ask the user (entries, add_*)
    SIP_PICK_REFUSE,     // write nothing, `reason` says why
} sip_pick_action_t;

typedef struct {
    sip_pick_action_t action;

    // OVERWRITE / CREATE: where and what to write.
    int  index;
    char username[SIP_PICK_USER_CAP];
    char name[SIP_PICK_NAME_CAP];

    // The PhoneBlock entries on the box (copies, in index order) and the
    // identity "add" would use. Filled for every outcome except a refusal
    // of a malformed on_conflict; a CONFLICT lists them to the user.
    int          entry_count;
    sip_client_t entries[SIP_PICK_MAX_CLIENTS];
    int          entry_index[SIP_PICK_MAX_CLIENTS];
    char add_username[SIP_PICK_USER_CAP];
    char add_name[SIP_PICK_NAME_CAP];

    // REFUSE: English message for the user (static string).
    const char *reason;
} sip_pick_t;

// Number of a PhoneBlock username: 1 for "phoneblock-dongle", n for
// "phoneblock-dongle-<n>" with n >= 2 written without leading zeros (at
// most four digits). 0 for everything else, including "phoneblock-dongle-1",
// "phoneblock-dongle-x", "phoneblock-dongleXYZ" and legacy MAC names.
int sip_pick_user_number(const char *username);

// Decide which entry to write. `clients[0..count)` is the box's client
// list (count <= SIP_PICK_MAX_CLIENTS); `stored_user` / `stored_host` are
// the dongle's saved SIP user and registrar host ("" when never set up);
// `host` is the Fritz!Box host of this run; `base_name` is the display
// name of a first device ("PhoneBlock"), further ones get " <n>".
void sip_pick_decide(const sip_client_t *clients, int count,
                     const char *stored_user, const char *stored_host,
                     const char *host, const char *on_conflict,
                     const char *base_name, sip_pick_t *out);
