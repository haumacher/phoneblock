// Host test for sip_client_pick.c — which Fritz!Box IP-phone entry the
// setup wizard writes when several dongles share one box (#708).
#include <stdio.h>
#include <string.h>

#include "sip_client_pick.h"

static int s_tests = 0;
static int s_failures = 0;

#define CHECK(cond) do {                                                   \
    s_tests++;                                                             \
    if (!(cond)) {                                                         \
        s_failures++;                                                      \
        fprintf(stderr, "%s:%d: FAIL: %s\n", __FILE__, __LINE__, #cond);   \
    }                                                                      \
} while (0)

#define CHECK_STR(actual, expected) do {                                   \
    s_tests++;                                                             \
    if (strcmp((actual), (expected)) != 0) {                               \
        s_failures++;                                                      \
        fprintf(stderr, "%s:%d: FAIL: %s == \"%s\", expected \"%s\"\n",    \
                __FILE__, __LINE__, #actual, (actual), (expected));        \
    }                                                                      \
} while (0)

// A box's client list, built from username/name/number triples.
static sip_client_t s_clients[SIP_PICK_MAX_CLIENTS];
static int s_count;

static void box_clear(void) { memset(s_clients, 0, sizeof(s_clients)); s_count = 0; }

static void box_add(const char *user, const char *name, const char *number)
{
    sip_client_t *c = &s_clients[s_count++];
    snprintf(c->username, sizeof(c->username), "%s", user);
    snprintf(c->name, sizeof(c->name), "%s", name);
    snprintf(c->number, sizeof(c->number), "%s", number);
}

// A box with two ordinary IP phones (a softphone and a desk phone) whose
// usernames are the box's own numeric style.
static void box_with_other_phones(void)
{
    box_clear();
    box_add("620", "Softphone", "620");
    box_add("621", "Schreibtisch", "621");
}

static sip_pick_t s_pick;

static const sip_pick_t *decide(const char *stored_user, const char *stored_host,
                                const char *host, const char *on_conflict)
{
    sip_pick_decide(s_clients, s_count, stored_user, stored_host, host,
                    on_conflict, "PhoneBlock", &s_pick);
    return &s_pick;
}

static void test_user_number(void)
{
    CHECK(sip_pick_user_number("phoneblock-dongle") == 1);
    CHECK(sip_pick_user_number("phoneblock-dongle-2") == 2);
    CHECK(sip_pick_user_number("phoneblock-dongle-10") == 10);
    CHECK(sip_pick_user_number("phoneblock-dongle-9999") == 9999);
    // Strict about the pattern: none of these is a numbered PhoneBlock entry.
    CHECK(sip_pick_user_number("phoneblock-dongle-x") == 0);
    CHECK(sip_pick_user_number("phoneblock-dongleXYZ") == 0);
    CHECK(sip_pick_user_number("phoneblock-dongle-") == 0);
    CHECK(sip_pick_user_number("phoneblock-dongle-1") == 0);
    CHECK(sip_pick_user_number("phoneblock-dongle-02") == 0);
    CHECK(sip_pick_user_number("phoneblock-dongle-2x") == 0);
    CHECK(sip_pick_user_number("phoneblock-dongle-12345") == 0);
    CHECK(sip_pick_user_number("phoneblock-dongle--2") == 0);
    CHECK(sip_pick_user_number("phoneblock-5fff54") == 0);
    CHECK(sip_pick_user_number("PhoneBlock-dongle") == 0);
    CHECK(sip_pick_user_number("620") == 0);
    CHECK(sip_pick_user_number("") == 0);
    CHECK(sip_pick_user_number(NULL) == 0);
}

// Fresh dongle, box without a PhoneBlock entry: today's behaviour.
static void test_fresh_no_phoneblock(void)
{
    box_with_other_phones();
    const sip_pick_t *p = decide("", "", "fritz.box", "");
    CHECK(p->action == SIP_PICK_CREATE);
    CHECK(p->index == 2);
    CHECK_STR(p->username, "phoneblock-dongle");
    CHECK_STR(p->name, "PhoneBlock");
    CHECK(p->entry_count == 0);

    // An empty box: slot 0. NULL inputs behave like empty ones.
    box_clear();
    p = decide(NULL, NULL, "fritz.box", NULL);
    CHECK(p->action == SIP_PICK_CREATE);
    CHECK(p->index == 0);
    CHECK_STR(p->username, "phoneblock-dongle");

    // "add" with nothing to add beside is just the first device.
    box_with_other_phones();
    p = decide("", "", "fritz.box", "add");
    CHECK(p->action == SIP_PICK_CREATE);
    CHECK(p->index == 2);
    CHECK_STR(p->username, "phoneblock-dongle");
}

// Fresh dongle, the box already has another dongle's entry: ask.
static void test_fresh_conflict(void)
{
    box_with_other_phones();
    box_add("622", "Küche", "622");
    box_add("phoneblock-dongle", "PhoneBlock", "623");
    const sip_pick_t *p = decide("", "", "fritz.box", "");
    CHECK(p->action == SIP_PICK_CONFLICT);
    CHECK(p->entry_count == 1);
    CHECK(p->entry_index[0] == 3);
    CHECK_STR(p->entries[0].username, "phoneblock-dongle");
    CHECK_STR(p->entries[0].name, "PhoneBlock");
    CHECK_STR(p->entries[0].number, "623");
    CHECK_STR(p->add_username, "phoneblock-dongle-2");
    CHECK_STR(p->add_name, "PhoneBlock 2");

    // A dongle set up by hand (stored user, but not ours) asks as well.
    p = decide("someuser", "fritz.box", "fritz.box", "");
    CHECK(p->action == SIP_PICK_CONFLICT);
}

// The #708 case: re-running the wizard on dongle A overwrites A's entry.
static void test_own_entry(void)
{
    box_with_other_phones();
    box_add("phoneblock-dongle", "PhoneBlock", "622");
    const sip_pick_t *p = decide("phoneblock-dongle", "fritz.box", "fritz.box", "");
    CHECK(p->action == SIP_PICK_OVERWRITE);
    CHECK(p->index == 2);
    CHECK_STR(p->username, "phoneblock-dongle");
    CHECK_STR(p->name, "PhoneBlock");

    // The host is compared without regard to case.
    p = decide("phoneblock-dongle", "Fritz.Box", "fritz.box", "");
    CHECK(p->action == SIP_PICK_OVERWRITE);
    CHECK(p->index == 2);

    // Dongle 2 re-running: its own numbered entry, name kept, while dongle
    // 1's entry stays untouched.
    box_add("phoneblock-dongle-2", "PhoneBlock 2", "623");
    p = decide("phoneblock-dongle-2", "fritz.box", "fritz.box", "");
    CHECK(p->action == SIP_PICK_OVERWRITE);
    CHECK(p->index == 3);
    CHECK_STR(p->username, "phoneblock-dongle-2");
    CHECK_STR(p->name, "PhoneBlock 2");

    // A renamed entry keeps the name the user gave it in the box UI; a
    // nameless one gets the default.
    box_clear();
    box_add("phoneblock-dongle", "Spamfilter Flur", "620");
    p = decide("phoneblock-dongle", "fritz.box", "fritz.box", "");
    CHECK(p->action == SIP_PICK_OVERWRITE);
    CHECK_STR(p->name, "Spamfilter Flur");
    box_clear();
    box_add("phoneblock-dongle", "", "620");
    p = decide("phoneblock-dongle", "fritz.box", "fritz.box", "");
    CHECK_STR(p->name, "PhoneBlock");
}

// Same stored identity, but the wizard runs against another host name:
// the dongle cannot tell it is the same box, so it asks.
static void test_own_entry_other_host(void)
{
    box_with_other_phones();
    box_add("phoneblock-dongle", "PhoneBlock", "622");
    const sip_pick_t *p = decide("phoneblock-dongle", "fritz.box", "192.168.178.1", "");
    CHECK(p->action == SIP_PICK_CONFLICT);
    CHECK(p->entry_count == 1);
    CHECK(p->entry_index[0] == 2);

    // Stored identity, but the entry is gone from the box (deleted there,
    // or the dongle was set up against another box): first device again.
    box_with_other_phones();
    p = decide("phoneblock-dongle", "fritz.box", "fritz.box", "");
    CHECK(p->action == SIP_PICK_CREATE);
    CHECK(p->index == 2);
    CHECK_STR(p->username, "phoneblock-dongle");
}

static void test_add(void)
{
    box_with_other_phones();
    box_add("phoneblock-dongle", "PhoneBlock", "622");
    box_add("phoneblock-dongle-2", "PhoneBlock 2", "623");
    const sip_pick_t *p = decide("", "", "fritz.box", "");
    CHECK(p->action == SIP_PICK_CONFLICT);
    CHECK(p->entry_count == 2);
    CHECK_STR(p->entries[0].username, "phoneblock-dongle");
    CHECK_STR(p->entries[1].username, "phoneblock-dongle-2");
    CHECK_STR(p->add_username, "phoneblock-dongle-3");

    p = decide("", "", "fritz.box", "add");
    CHECK(p->action == SIP_PICK_CREATE);
    CHECK(p->index == 4);
    CHECK_STR(p->username, "phoneblock-dongle-3");
    CHECK_STR(p->name, "PhoneBlock 3");

    // A gap is filled first.
    box_with_other_phones();
    box_add("phoneblock-dongle", "PhoneBlock", "622");
    box_add("phoneblock-dongle-3", "PhoneBlock 3", "623");
    p = decide("", "", "fritz.box", "add");
    CHECK(p->action == SIP_PICK_CREATE);
    CHECK(p->index == 4);
    CHECK_STR(p->username, "phoneblock-dongle-2");
    CHECK_STR(p->name, "PhoneBlock 2");

    // Only "-<n>" entries left (the first dongle's entry was deleted):
    // still a conflict, and "add" takes the lowest free number, not the
    // base name, which is unambiguous only as the very first device.
    box_with_other_phones();
    box_add("phoneblock-dongle-2", "PhoneBlock 2", "622");
    p = decide("", "", "fritz.box", "");
    CHECK(p->action == SIP_PICK_CONFLICT);
    p = decide("", "", "fritz.box", "add");
    CHECK(p->action == SIP_PICK_CREATE);
    CHECK_STR(p->username, "phoneblock-dongle-3");
}

static void test_replace(void)
{
    box_with_other_phones();
    box_add("phoneblock-dongle", "PhoneBlock", "622");
    box_add("phoneblock-dongle-2", "PhoneBlock 2", "623");

    const sip_pick_t *p = decide("", "", "fritz.box", "replace:phoneblock-dongle-2");
    CHECK(p->action == SIP_PICK_OVERWRITE);
    CHECK(p->index == 3);
    CHECK_STR(p->username, "phoneblock-dongle-2");
    CHECK_STR(p->name, "PhoneBlock 2");

    p = decide("", "", "fritz.box", "replace:phoneblock-dongle");
    CHECK(p->action == SIP_PICK_OVERWRITE);
    CHECK(p->index == 2);
    CHECK_STR(p->username, "phoneblock-dongle");

    // Not a PhoneBlock entry, or not on the box: refused, nothing written.
    p = decide("", "", "fritz.box", "replace:620");
    CHECK(p->action == SIP_PICK_REFUSE);
    CHECK(p->reason != NULL);
    p = decide("", "", "fritz.box", "replace:phoneblock-dongle-5");
    CHECK(p->action == SIP_PICK_REFUSE);
    p = decide("", "", "fritz.box", "replace:");
    CHECK(p->action == SIP_PICK_REFUSE);
    p = decide("", "", "fritz.box", "replace:phoneblock-dongle-x");
    CHECK(p->action == SIP_PICK_REFUSE);

    // Refused even when there would be nothing to ask about.
    box_with_other_phones();
    p = decide("", "", "fritz.box", "replace:621");
    CHECK(p->action == SIP_PICK_REFUSE);

    // An unknown decision is refused, not ignored.
    p = decide("", "", "fritz.box", "overwrite");
    CHECK(p->action == SIP_PICK_REFUSE);
    CHECK(p->reason != NULL);
}

// A dongle provisioned with a MAC-derived name before 64765eb9 keeps it.
static void test_legacy_name(void)
{
    box_with_other_phones();
    box_add("phoneblock-5fff54", "PhoneBlock", "622");
    const sip_pick_t *p = decide("phoneblock-5fff54", "fritz.box", "fritz.box", "");
    CHECK(p->action == SIP_PICK_OVERWRITE);
    CHECK(p->index == 2);
    CHECK_STR(p->username, "phoneblock-5fff54");

    // Even with another dongle's entry next to it, no question.
    box_add("phoneblock-dongle", "PhoneBlock", "623");
    p = decide("phoneblock-5fff54", "fritz.box", "fritz.box", "");
    CHECK(p->action == SIP_PICK_OVERWRITE);
    CHECK(p->index == 2);
    CHECK_STR(p->username, "phoneblock-5fff54");

    // A legacy entry is no PhoneBlock entry of the numbered scheme: a
    // fresh dongle next to it just becomes "phoneblock-dongle".
    box_with_other_phones();
    box_add("phoneblock-5fff54", "PhoneBlock", "622");
    p = decide("", "", "fritz.box", "");
    CHECK(p->action == SIP_PICK_CREATE);
    CHECK(p->index == 3);
    CHECK_STR(p->username, "phoneblock-dongle");
}

// Look-alike usernames are neither listed nor counted as taken numbers.
static void test_lookalikes(void)
{
    box_with_other_phones();
    box_add("phoneblock-dongle-x", "Test", "622");
    box_add("phoneblock-dongleXYZ", "Test", "623");
    const sip_pick_t *p = decide("", "", "fritz.box", "");
    CHECK(p->action == SIP_PICK_CREATE);
    CHECK(p->index == 4);
    CHECK_STR(p->username, "phoneblock-dongle");
    CHECK(p->entry_count == 0);

    // A stored look-alike still counts as the dongle's own entry: it starts
    // with "phoneblock-" and the box has it, so rule 1 applies.
    p = decide("phoneblock-dongle-x", "fritz.box", "fritz.box", "");
    CHECK(p->action == SIP_PICK_OVERWRITE);
    CHECK(p->index == 2);
}

// A full list of ten dongles: the add candidate is the eleventh.
static void test_many(void)
{
    box_clear();
    box_add("phoneblock-dongle", "PhoneBlock", "620");
    for (int n = 2; n <= 10; n++) {
        char user[32], name[32], num[8];
        snprintf(user, sizeof user, "phoneblock-dongle-%d", n);
        snprintf(name, sizeof name, "PhoneBlock %d", n);
        snprintf(num, sizeof num, "%d", 618 + n);
        box_add(user, name, num);
    }
    const sip_pick_t *p = decide("", "", "fritz.box", "");
    CHECK(p->action == SIP_PICK_CONFLICT);
    CHECK(p->entry_count == 10);
    CHECK_STR(p->add_username, "phoneblock-dongle-11");
    CHECK_STR(p->add_name, "PhoneBlock 11");
}

int main(void)
{
    test_user_number();
    test_fresh_no_phoneblock();
    test_fresh_conflict();
    test_own_entry();
    test_own_entry_other_host();
    test_add();
    test_replace();
    test_legacy_name();
    test_lookalikes();
    test_many();
    printf("test_sip_client_pick: %d tests, %d failures\n", s_tests, s_failures);
    return s_failures ? 1 : 0;
}
