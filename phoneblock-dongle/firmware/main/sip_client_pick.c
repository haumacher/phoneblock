#include "sip_client_pick.h"

#include <stdio.h>
#include <string.h>
#include <strings.h>

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

int sip_pick_user_number(const char *username)
{
    static const size_t base_len = sizeof(SIP_PICK_BASE_USER) - 1;
    if (!username || strncmp(username, SIP_PICK_BASE_USER, base_len) != 0) {
        return 0;
    }
    const char *rest = username + base_len;
    if (rest[0] == '\0') return 1;
    if (rest[0] != '-') return 0;
    rest++;
    // Digits only, no leading zero, at most four of them: the names this
    // rule hands out, nothing a user typed that merely looks similar.
    if (rest[0] < '1' || rest[0] > '9') return 0;
    int n = 0;
    int digits = 0;
    for (const char *p = rest; *p; p++) {
        if (*p < '0' || *p > '9' || ++digits > 4) return 0;
        n = n * 10 + (*p - '0');
    }
    return n >= 2 ? n : 0;
}

static void copy_str(char *dst, size_t cap, const char *src)
{
    snprintf(dst, cap, "%s", src ? src : "");
}

static int find_user(const sip_client_t *clients, int count, const char *username)
{
    for (int i = 0; i < count; i++) {
        if (clients[i].username[0] && strcmp(clients[i].username, username) == 0) {
            return i;
        }
    }
    return -1;
}

// Overwrite client `i` under `username`. The display name the box shows is
// kept (the user may have renamed the entry there, and "PhoneBlock 2" must
// stay "PhoneBlock 2"); only a nameless entry gets the default.
static void set_overwrite(sip_pick_t *out, const sip_client_t *clients, int i,
                          const char *username, const char *base_name)
{
    out->action = SIP_PICK_OVERWRITE;
    out->index = i;
    copy_str(out->username, sizeof(out->username), username);
    copy_str(out->name, sizeof(out->name),
             clients[i].name[0] ? clients[i].name : base_name);
}

static void set_create(sip_pick_t *out, int count,
                       const char *username, const char *name)
{
    out->action = SIP_PICK_CREATE;
    out->index = count;
    copy_str(out->username, sizeof(out->username), username);
    copy_str(out->name, sizeof(out->name), name);
}

static void refuse(sip_pick_t *out, const char *reason)
{
    out->action = SIP_PICK_REFUSE;
    out->index = -1;
    out->reason = reason;
}

void sip_pick_decide(const sip_client_t *clients, int count,
                     const char *stored_user, const char *stored_host,
                     const char *host, const char *on_conflict,
                     const char *base_name, sip_pick_t *out)
{
    memset(out, 0, sizeof(*out));
    out->index = -1;
    if (!base_name || !base_name[0]) base_name = "PhoneBlock";
    if (count < 0) count = 0;
    if (count > SIP_PICK_MAX_CLIENTS) count = SIP_PICK_MAX_CLIENTS;

    // The user's decision, if any. Anything but the two known forms is
    // refused, not ignored: a request that says something we don't
    // understand must not end in a write the user didn't choose.
    static const char REPLACE[] = "replace:";
    const char *replace_user = NULL;
    bool add = false;
    if (on_conflict && on_conflict[0]) {
        if (strcmp(on_conflict, "add") == 0) {
            add = true;
        } else if (strncmp(on_conflict, REPLACE, sizeof(REPLACE) - 1) == 0) {
            replace_user = on_conflict + sizeof(REPLACE) - 1;
        } else {
            refuse(out, "Unknown on_conflict value; expected 'add' or "
                        "'replace:<username>'.");
            return;
        }
    }

    // The PhoneBlock entries and the lowest free number for "add".
    for (int i = 0; i < count; i++) {
        if (sip_pick_user_number(clients[i].username) > 0) {
            out->entries[out->entry_count] = clients[i];
            out->entry_index[out->entry_count] = i;
            out->entry_count++;
        }
    }
    for (int n = 2; ; n++) {
        char candidate[SIP_PICK_USER_CAP];
        snprintf(candidate, sizeof(candidate), SIP_PICK_BASE_USER "-%d", n);
        if (find_user(clients, count, candidate) < 0) {
            copy_str(out->add_username, sizeof(out->add_username), candidate);
            snprintf(out->add_name, sizeof(out->add_name), "%.48s %d", base_name, n);
            break;
        }
    }

    // An explicit "replace" must name one of the PhoneBlock entries the
    // question listed — never some other client of the box.
    if (replace_user) {
        int i = sip_pick_user_number(replace_user) > 0
              ? find_user(clients, count, replace_user) : -1;
        if (i < 0) {
            refuse(out, "The entry to replace is not a PhoneBlock entry on "
                        "this Fritz!Box. Please start the setup again.");
            return;
        }
        set_overwrite(out, clients, i, replace_user, base_name);
        return;
    }

    // 1. Own entry from an earlier setup against this very box.
    if (stored_user && strncmp(stored_user, "phoneblock-", 11) == 0
            && stored_host && host && stored_host[0]
            && strcasecmp(stored_host, host) == 0) {
        int i = find_user(clients, count, stored_user);
        if (i >= 0) {
            set_overwrite(out, clients, i, stored_user, base_name);
            return;
        }
    }

    // 2. First PhoneBlock device on the box.
    if (out->entry_count == 0) {
        set_create(out, count, SIP_PICK_BASE_USER, base_name);
        return;
    }

    // 3. Someone else's PhoneBlock entries: ask, or do what was chosen.
    if (add) {
        set_create(out, count, out->add_username, out->add_name);
        return;
    }
    out->action = SIP_PICK_CONFLICT;
}
