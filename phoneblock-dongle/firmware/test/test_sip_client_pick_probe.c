// Probe for #708: one simulated Fritz!Box shared by several dongles, every
// setup run executed through sip_pick_decide() and applied to the box like
// tr064.c does (SetClient4 at the decided index). Checks the rule over a
// sequence of runs rather than single decisions: re-runs stay silent, a
// reset dongle can take its entry back, and no dongle ever loses its entry
// without someone answering the conflict question.
#include <stdio.h>
#include <string.h>

#include "sip_client_pick.h"

static int s_tests = 0;
static int s_failures = 0;

#define CHECK(cond) do {                                                   \
    s_tests++;                                                             \
    if (!(cond)) {                                                         \
        s_failures++;                                                      \
        fprintf(stderr, "%s:%d: CHECK(%s) failed\n", __FILE__, __LINE__, #cond); \
    }                                                                      \
} while (0)

// --- the simulated box ------------------------------------------------

static sip_client_t s_box[SIP_PICK_MAX_CLIENTS];
static int s_box_count;

static void box_add_foreign(const char *user, const char *name, const char *number)
{
    sip_client_t *c = &s_box[s_box_count++];
    memset(c, 0, sizeof(*c));
    snprintf(c->username, sizeof(c->username), "%s", user);
    snprintf(c->name, sizeof(c->name), "%s", name);
    snprintf(c->number, sizeof(c->number), "%s", number);
}

static int box_find(const char *user)
{
    for (int i = 0; i < s_box_count; i++) {
        if (strcmp(s_box[i].username, user) == 0) return i;
    }
    return -1;
}

// --- a dongle: its stored identity, as web.c persists it --------------

typedef struct {
    char user[SIP_PICK_USER_CAP];
    char host[64];
} dongle_t;

// One wizard run against host `host`; applies the decision to the box and,
// on success, stores the identity like handle_fritzbox_setup does.
static sip_pick_action_t run(dongle_t *d, const char *host, const char *on_conflict)
{
    static sip_pick_t p;
    sip_pick_decide(s_box, s_box_count, d->user, d->host, host,
                    on_conflict, "PhoneBlock", &p);
    if (p.action == SIP_PICK_OVERWRITE || p.action == SIP_PICK_CREATE) {
        if (p.action == SIP_PICK_CREATE) {
            CHECK(p.index == s_box_count);
            s_box_count++;
        } else {
            CHECK(p.index >= 0 && p.index < s_box_count);
        }
        sip_client_t *c = &s_box[p.index];
        snprintf(c->username, sizeof(c->username), "%s", p.username);
        snprintf(c->name, sizeof(c->name), "%s", p.name);
        snprintf(c->number, sizeof(c->number), "%d", 620 + p.index);
        snprintf(d->user, sizeof(d->user), "%s", p.username);
        snprintf(d->host, sizeof(d->host), "%s", host);
    }
    return p.action;
}

int main(void)
{
    // A box that already has an ordinary IP phone.
    box_add_foreign("620", "Küche", "620");

    dongle_t a = {"", ""}, b = {"", ""};

    // A: first dongle, no question.
    CHECK(run(&a, "fritz.box", NULL) == SIP_PICK_CREATE);
    CHECK(strcmp(a.user, "phoneblock-dongle") == 0);

    // B: the #708 situation — must ask, must not touch A's entry.
    CHECK(run(&b, "fritz.box", NULL) == SIP_PICK_CONFLICT);
    CHECK(s_box_count == 2);
    CHECK(b.user[0] == '\0');
    CHECK(run(&b, "fritz.box", "add") == SIP_PICK_CREATE);
    CHECK(strcmp(b.user, "phoneblock-dongle-2") == 0);
    CHECK(s_box_count == 3);

    // Both re-run the wizard (e.g. after a password change): silent, in place.
    CHECK(run(&a, "fritz.box", NULL) == SIP_PICK_OVERWRITE);
    CHECK(run(&b, "fritz.box", NULL) == SIP_PICK_OVERWRITE);
    CHECK(s_box_count == 3);
    CHECK(box_find("phoneblock-dongle") == 1);
    CHECK(box_find("phoneblock-dongle-2") == 2);
    CHECK(strcmp(s_box[2].name, "PhoneBlock 2") == 0);

    // The user renamed B's entry in the Fritz!Box UI; a re-run keeps it.
    snprintf(s_box[2].name, sizeof(s_box[2].name), "%s", "PhoneBlock Flur");
    CHECK(run(&b, "fritz.box", NULL) == SIP_PICK_OVERWRITE);
    CHECK(strcmp(s_box[2].name, "PhoneBlock Flur") == 0);

    // B is factory-reset: it no longer knows its entry. Asking is right;
    // replacing its old entry takes it back without a fourth entry.
    memset(&b, 0, sizeof(b));
    CHECK(run(&b, "fritz.box", NULL) == SIP_PICK_CONFLICT);
    CHECK(run(&b, "fritz.box", "replace:phoneblock-dongle-2") == SIP_PICK_OVERWRITE);
    CHECK(s_box_count == 3);
    CHECK(strcmp(b.user, "phoneblock-dongle-2") == 0);
    // ...and from then on it is silent again, A still owns its entry.
    CHECK(run(&b, "fritz.box", NULL) == SIP_PICK_OVERWRITE);
    CHECK(run(&a, "fritz.box", NULL) == SIP_PICK_OVERWRITE);
    CHECK(box_find("phoneblock-dongle") == 1);

    // A replay of the reset dongle's answer must not hit the foreign phone.
    CHECK(run(&b, "fritz.box", "replace:620") == SIP_PICK_REFUSE);
    CHECK(strcmp(s_box[0].username, "620") == 0);

    // The user addresses the box differently this time (IP instead of
    // fritz.box): ask rather than guess, nothing written.
    CHECK(run(&a, "192.168.178.1", NULL) == SIP_PICK_CONFLICT);
    CHECK(s_box_count == 3);

    // A third dongle fills the gap rule: -2 exists, so it gets -3.
    dongle_t c = {"", ""};
    CHECK(run(&c, "fritz.box", "add") == SIP_PICK_CREATE);
    CHECK(strcmp(c.user, "phoneblock-dongle-3") == 0);

    // A dongle that was once set up *manually* with the box's own phone
    // account ("620") is not "own entry" for the wizard: it must ask.
    dongle_t m = {"620", "fritz.box"};
    CHECK(run(&m, "fritz.box", NULL) == SIP_PICK_CONFLICT);
    CHECK(strcmp(s_box[0].username, "620") == 0);

    printf("test_sip_client_pick_probe: %d tests, %d failures\n", s_tests, s_failures);
    return s_failures ? 1 : 0;
}
