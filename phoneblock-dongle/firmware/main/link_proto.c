#include "link_proto.h"

#include <stdio.h>
#include <string.h>

#include "cJSON.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

const char *link_state_name(link_state_t state)
{
    switch (state) {
        case LINK_IDLE:     return "idle";
        case LINK_PENDING:  return "pending";
        case LINK_APPROVED: return "approved";
        case LINK_DENIED:   return "denied";
        case LINK_EXPIRED:  return "expired";
        case LINK_ERROR:    return "error";
    }
    return "error";
}

static void set_reason(char *reason, size_t cap, const char *text)
{
    if (reason && cap) snprintf(reason, cap, "%s", text);
}

// Copies the string member `name` of `root` into `out` when every byte
// passes `allowed` and it fits. Returns false otherwise (missing, wrong
// type, empty, too long or a byte outside the set), leaving `out` empty.
static bool copy_member(const cJSON *root, const char *name, char *out,
                        size_t cap, bool (*allowed)(unsigned char))
{
    out[0] = '\0';
    const cJSON *item = cJSON_GetObjectItemCaseSensitive(root, name);
    if (!cJSON_IsString(item) || !item->valuestring) return false;
    const char *s = item->valuestring;
    size_t len = strlen(s);
    if (len == 0 || len >= cap) return false;
    for (size_t i = 0; i < len; i++) {
        if (!allowed((unsigned char)s[i])) return false;
    }
    memcpy(out, s, len + 1);
    return true;
}

static bool is_alnum(unsigned char c)
{
    return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
}

// base64url (RFC 4648 §5), padding tolerated.
static bool is_base64url(unsigned char c)
{
    return is_alnum(c) || c == '-' || c == '_' || c == '=';
}

static bool is_user_code(unsigned char c)
{
    return is_alnum(c) || c == '-';
}

// Printable ASCII without space: what fits a line of the settings store and
// an Authorization header.
static bool is_visible(unsigned char c)
{
    return c > 0x20 && c < 0x7f;
}

// Positive number member, or `fallback` when absent. -1 when present but
// not a positive number.
static long number_member(const cJSON *root, const char *name, long fallback)
{
    const cJSON *item = cJSON_GetObjectItemCaseSensitive(root, name);
    if (!item) return fallback;
    if (!cJSON_IsNumber(item) || item->valuedouble < 1) return -1;
    if (item->valuedouble > 1e6) return 1000000;
    return (long)item->valuedouble;
}

bool link_parse_start(const char *json, link_start_resp_t *out,
                      char *reason, size_t reason_cap)
{
    memset(out, 0, sizeof(*out));
    set_reason(reason, reason_cap, "");
    cJSON *root = json ? cJSON_Parse(json) : NULL;
    if (!cJSON_IsObject(root)) {
        cJSON_Delete(root);
        set_reason(reason, reason_cap, "malformed server response");
        return false;
    }
    bool ok = false;
    long interval = number_member(root, "interval", LINK_DEFAULT_INTERVAL_S);
    long expires  = number_member(root, "expiresIn", -1);
    if (!copy_member(root, "deviceCode", out->device_code,
                     sizeof(out->device_code), is_base64url)) {
        set_reason(reason, reason_cap, "server response without a valid deviceCode");
    } else if (!copy_member(root, "userCode", out->user_code,
                            sizeof(out->user_code), is_user_code)) {
        set_reason(reason, reason_cap, "server response without a valid userCode");
    } else if (!copy_member(root, "verifyUrl", out->verify_url,
                            sizeof(out->verify_url), is_visible)
               || (strncmp(out->verify_url, "https://", 8) != 0
                   && strncmp(out->verify_url, "http://", 7) != 0)) {
        // The browser opens it: anything but http(s) (javascript:, data:)
        // is refused.
        set_reason(reason, reason_cap, "server response without a valid verifyUrl");
    } else if (interval < 0) {
        set_reason(reason, reason_cap, "server response with an invalid interval");
    } else if (expires < 0) {
        set_reason(reason, reason_cap, "server response without a valid expiresIn");
    } else {
        if (interval < LINK_MIN_INTERVAL_S) interval = LINK_MIN_INTERVAL_S;
        if (interval > LINK_MAX_INTERVAL_S) interval = LINK_MAX_INTERVAL_S;
        if (expires > LINK_MAX_EXPIRES_S) expires = LINK_MAX_EXPIRES_S;
        out->interval_s = (uint32_t)interval;
        out->expires_in_s = (uint32_t)expires;
        ok = true;
    }
    cJSON_Delete(root);
    if (!ok) {
        // Never leave a half-parsed device code behind.
        memset(out, 0, sizeof(*out));
    }
    return ok;
}

void link_parse_poll(const char *json, link_poll_resp_t *out)
{
    memset(out, 0, sizeof(*out));
    out->status = LINK_POLL_ERROR;
    cJSON *root = json ? cJSON_Parse(json) : NULL;
    if (!cJSON_IsObject(root)) {
        cJSON_Delete(root);
        snprintf(out->reason, sizeof(out->reason), "malformed server response");
        return;
    }
    const cJSON *status = cJSON_GetObjectItemCaseSensitive(root, "status");
    const char *s = cJSON_IsString(status) ? status->valuestring : NULL;
    if (!s) {
        snprintf(out->reason, sizeof(out->reason), "server response without a status");
    } else if (strcmp(s, "pending") == 0) {
        out->status = LINK_POLL_PENDING;
    } else if (strcmp(s, "slow_down") == 0) {
        out->status = LINK_POLL_SLOW_DOWN;
    } else if (strcmp(s, "denied") == 0) {
        out->status = LINK_POLL_DENIED;
    } else if (strcmp(s, "expired") == 0) {
        out->status = LINK_POLL_EXPIRED;
    } else if (strcmp(s, "approved") == 0) {
        if (copy_member(root, "token", out->token, sizeof(out->token), is_visible)) {
            out->status = LINK_POLL_APPROVED;
        } else {
            snprintf(out->reason, sizeof(out->reason),
                     "approved without a valid token");
        }
    } else {
        // The status is server-chosen text: quote only a bounded, printable
        // prefix of it.
        char shown[24];
        size_t n = 0;
        for (; s[n] && n + 1 < sizeof(shown); n++) {
            shown[n] = is_visible((unsigned char)s[n]) ? s[n] : '?';
        }
        shown[n] = '\0';
        snprintf(out->reason, sizeof(out->reason), "unknown status \"%s\"", shown);
    }
    cJSON_Delete(root);
}

uint32_t link_next_interval(uint32_t interval_s, link_poll_status_t status)
{
    if (status != LINK_POLL_SLOW_DOWN) return interval_s;
    uint32_t next = interval_s + LINK_SLOW_DOWN_STEP_S;
    return next > LINK_MAX_INTERVAL_S ? LINK_MAX_INTERVAL_S : next;
}

void link_machine_begin(link_machine_t *m, const link_start_resp_t *start,
                        uint64_t now_us)
{
    memset(m, 0, sizeof(*m));
    m->state = LINK_PENDING;
    m->interval_s = start->interval_s ? start->interval_s : LINK_DEFAULT_INTERVAL_S;
    m->deadline_us = now_us + (uint64_t)start->expires_in_s * 1000000ULL;
}

void link_machine_fail(link_machine_t *m, link_state_t state, const char *message)
{
    m->state = state;
    snprintf(m->message, sizeof(m->message), "%s", message ? message : "");
}

bool link_machine_check_expiry(link_machine_t *m, uint64_t now_us)
{
    if (m->state != LINK_PENDING || now_us < m->deadline_us) return false;
    link_machine_fail(m, LINK_EXPIRED, "The code expired before it was confirmed.");
    return true;
}

link_state_t link_machine_apply(link_machine_t *m, const link_poll_resp_t *resp)
{
    if (m->state != LINK_PENDING) return m->state;
    switch (resp->status) {
        case LINK_POLL_PENDING:
            break;
        case LINK_POLL_SLOW_DOWN:
            m->interval_s = link_next_interval(m->interval_s, resp->status);
            break;
        case LINK_POLL_APPROVED:
            link_machine_fail(m, LINK_APPROVED, "");
            break;
        case LINK_POLL_DENIED:
            link_machine_fail(m, LINK_DENIED, "The link was declined on PhoneBlock.");
            break;
        case LINK_POLL_EXPIRED:
            link_machine_fail(m, LINK_EXPIRED, "The code expired or was already used.");
            break;
        case LINK_POLL_ERROR:
            link_machine_fail(m, LINK_ERROR, resp->reason);
            break;
    }
    return m->state;
}
