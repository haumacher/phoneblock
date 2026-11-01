// Device-code link to a PhoneBlock account (#621): server calls and the poll
// task. Linux only (see link.h); the parsing and the state machine are in
// link_proto.c.
//
// Lifecycle. All state is in `s` under `s_lock`. Each link_start() bumps
// s.generation; the poll task copies the generation it works on and, after
// every wait and every server call, drops its result when the generation
// moved on — a replaced link is thus cancelled without stopping the task.
// The task runs while the current link is PENDING and clears
// s.task_running under the lock as it ends; link_start() creates a task
// only when that flag is clear, setting it first. So there is at most one
// task, and it only ever reads the static state — nothing it holds can be
// freed under it.

#include "link.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "api.h"
#include "cJSON.h"
#include "config.h"
#include "http_util.h"
#include "platform.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

static const char *TAG = "link";

#define LINK_HTTP_TIMEOUT_MS 10000
#define LINK_RESPONSE_CAP    1024
// The task wakes this often while it waits, to notice a replaced link.
#define LINK_WAIT_SLICE_MS   200

static pb_mutex_t *s_lock;

static struct {
    link_machine_t machine;
    uint32_t generation;
    bool     task_running;
    uint32_t expires_in_s;
    char     device_code[LINK_DEVICE_CODE_CAP];
    char     user_code[LINK_USER_CODE_CAP];
    char     verify_url[LINK_VERIFY_URL_CAP];
    char     base_url[128];
} s;

static uint32_t s_second_ms = 1000;

void link_set_second_ms(uint32_t ms)
{
    s_second_ms = ms ? ms : 1;
}

void link_init(void)
{
    if (!s_lock) s_lock = pb_mutex_create();
}

// The protocol clock: monotonic time in protocol microseconds, so intervals
// and the expiry scale with link_set_second_ms() alike.
static uint64_t protocol_now_us(void)
{
    return pb_monotonic_us() * 1000ULL / s_second_ms;
}

static void wipe(void *p, size_t n)
{
    volatile unsigned char *b = p;
    while (n--) *b++ = 0;
}

static void snapshot_locked(link_status_t *out)
{
    memset(out, 0, sizeof(*out));
    out->state = s.machine.state;
    out->expires_in_s = s.expires_in_s;
    snprintf(out->user_code, sizeof(out->user_code), "%s", s.user_code);
    snprintf(out->verify_url, sizeof(out->verify_url), "%s", s.verify_url);
    snprintf(out->message, sizeof(out->message), "%s", s.machine.message);
}

void link_get_status(link_status_t *out)
{
    link_init();
    pb_mutex_lock(s_lock);
    snapshot_locked(out);
    pb_mutex_unlock(s_lock);
}

// A short, printable excerpt of a server message for the UI and the log:
// the "message" member of a JSON answer, else the text itself.
static void server_message(const char *body, char *out, size_t cap)
{
    out[0] = '\0';
    if (!body || !body[0]) return;
    const char *text = body;
    cJSON *root = cJSON_Parse(body);
    const cJSON *m = root ? cJSON_GetObjectItemCaseSensitive(root, "message") : NULL;
    if (cJSON_IsString(m) && m->valuestring) text = m->valuestring;
    size_t n = 0;
    for (; text[n] && n + 1 < cap && n < 100; n++) {
        unsigned char c = (unsigned char)text[n];
        out[n] = (c >= 0x20 && c < 0x7f) ? (char)c : ' ';
    }
    out[n] = '\0';
    cJSON_Delete(root);
}

// POSTs `body` (JSON) to <base_url><path>. Anonymous: the device has no
// token yet, so the answer says nothing about the token's health either.
static int post_json(const char *base_url, const char *path, const char *body,
                     char *resp, size_t resp_cap, int *status)
{
    char url[192];
    int n = snprintf(url, sizeof(url), "%s%s", base_url, path);
    if (n < 0 || (size_t)n >= sizeof(url)) return PB_HTTP_ERROR;
    const pb_http_header_t headers[] = {
        { "User-Agent",   http_util_user_agent() },
        { "Content-Type", "application/json" },
        { "Accept",       "application/json" },
    };
    size_t resp_len = 0;
    *status = 0;
    resp[0] = '\0';
    int err = pb_http_request("POST", url, headers,
                              sizeof(headers) / sizeof(headers[0]),
                              body, strlen(body), LINK_HTTP_TIMEOUT_MS,
                              resp, resp_cap - 1, &resp_len, status,
                              NULL, NULL, NULL);
    resp[resp_len < resp_cap ? resp_len : resp_cap - 1] = '\0';
    if (err != PB_HTTP_OK) *status = 0;
    return err;
}

// Waits `interval_s` protocol seconds. Returns false when the link was
// replaced meanwhile.
static bool wait_interval(uint32_t generation, uint32_t interval_s)
{
    uint64_t until = protocol_now_us() + (uint64_t)interval_s * 1000000ULL;
    for (;;) {
        pb_mutex_lock(s_lock);
        bool current = s.generation == generation;
        pb_mutex_unlock(s_lock);
        if (!current) return false;
        uint64_t now = protocol_now_us();
        if (now >= until) return true;
        uint64_t remaining_ms = (until - now) / 1000ULL * s_second_ms / 1000ULL;
        if (remaining_ms == 0) remaining_ms = 1;
        pb_task_sleep_ms(remaining_ms < LINK_WAIT_SLICE_MS
                             ? (uint32_t)remaining_ms : LINK_WAIT_SLICE_MS);
    }
}

static void poll_task(void *arg)
{
    (void)arg;
    // Polls of the current link that failed in transport (or with a 5xx),
    // and the last such failure: one INFO line each, the WARN only when
    // the link expires with the server unreachable.
    uint32_t failures = 0;
    uint32_t failures_generation = 0;
    char last_failure[48] = "";
    char *resp = malloc(LINK_RESPONSE_CAP);

    for (;;) {
        pb_mutex_lock(s_lock);
        if (s.machine.state != LINK_PENDING || !resp) {
            if (!resp && s.machine.state == LINK_PENDING) {
                link_machine_fail(&s.machine, LINK_ERROR, "out of memory");
                pb_log_warn(TAG, "poll task: out of memory");
            }
            wipe(s.device_code, sizeof(s.device_code));
            s.task_running = false;
            pb_mutex_unlock(s_lock);
            break;
        }
        uint32_t generation = s.generation;
        uint32_t interval_s = s.machine.interval_s;
        pb_mutex_unlock(s_lock);
        if (generation != failures_generation) {
            failures = 0;
            failures_generation = generation;
            last_failure[0] = '\0';
        }

        if (!wait_interval(generation, interval_s)) continue;

        char base_url[sizeof(s.base_url)];
        char body[LINK_DEVICE_CODE_CAP + 32];
        pb_mutex_lock(s_lock);
        if (s.generation != generation) {
            pb_mutex_unlock(s_lock);
            continue;
        }
        if (link_machine_check_expiry(&s.machine, protocol_now_us())) {
            if (failures) {
                pb_log_warn(TAG, "code expired, PhoneBlock unreachable "
                            "(%u failed polls, last: %s)",
                            (unsigned)failures, last_failure);
                snprintf(s.machine.message, sizeof(s.machine.message),
                         "The code expired; PhoneBlock was not reachable (%s).",
                         last_failure);
            } else {
                pb_log_warn(TAG, "code expired before it was confirmed");
            }
            pb_mutex_unlock(s_lock);
            continue;
        }
        snprintf(base_url, sizeof(base_url), "%s", s.base_url);
        // deviceCode is base64url (link_parse_start() checked it), so it
        // needs no JSON escaping.
        int n = snprintf(body, sizeof(body), "{\"deviceCode\":\"%s\"}", s.device_code);
        pb_mutex_unlock(s_lock);
        if (n < 0 || (size_t)n >= sizeof(body)) n = 0;   // cannot happen: sized above

        int status = 0;
        int err = post_json(base_url, "/api/link/poll", body, resp,
                            LINK_RESPONSE_CAP, &status);
        wipe(body, sizeof(body));

        link_poll_resp_t poll;
        memset(&poll, 0, sizeof(poll));
        if (err != PB_HTTP_OK || status >= 500) {
            // The network or the server is down for a moment: keep polling
            // until the code expires.
            failures++;
            if (err != PB_HTTP_OK) {
                snprintf(last_failure, sizeof(last_failure), "%s", pb_http_err_name(err));
            } else {
                snprintf(last_failure, sizeof(last_failure), "HTTP %d", status);
            }
            pb_log_info(TAG, "poll failed (%s), retrying", last_failure);
            continue;
        }
        if (status != 200) {
            poll.status = LINK_POLL_ERROR;
            char detail[104];
            server_message(resp, detail, sizeof(detail));
            snprintf(poll.reason, sizeof(poll.reason), "HTTP %d%s%s", status,
                     detail[0] ? ": " : "", detail);
        } else {
            link_parse_poll(resp, &poll);
        }
        wipe(resp, LINK_RESPONSE_CAP);

        bool stored = true;
        if (poll.status == LINK_POLL_APPROVED) {
            pb_mutex_lock(s_lock);
            bool current = s.generation == generation;
            pb_mutex_unlock(s_lock);
            // A token collected for a link the user has replaced since is
            // dropped: they asked for another one.
            if (current) stored = phoneblock_adopt_token(poll.token, "link");
            wipe(poll.token, sizeof(poll.token));
        }

        pb_mutex_lock(s_lock);
        if (s.generation == generation) {
            if (!stored) {
                link_machine_fail(&s.machine, LINK_ERROR, "The token could not be stored.");
                pb_log_warn(TAG, "link approved, but the token could not be stored");
            } else {
                link_state_t state = link_machine_apply(&s.machine, &poll);
                switch (state) {
                    case LINK_APPROVED:
                        pb_log_info(TAG, "link approved, PhoneBlock account linked");
                        break;
                    case LINK_DENIED:
                        pb_log_warn(TAG, "link declined on PhoneBlock");
                        break;
                    case LINK_EXPIRED:
                        pb_log_warn(TAG, "code expired or already used");
                        break;
                    case LINK_ERROR:
                        pb_log_warn(TAG, "link failed: %s", s.machine.message);
                        break;
                    default:
                        break;
                }
            }
        }
        pb_mutex_unlock(s_lock);
    }
    free(resp);
}

bool link_start(link_status_t *out)
{
    link_init();
    char base_url[sizeof(s.base_url)];
    snprintf(base_url, sizeof(base_url), "%s", config_phoneblock_base_url());

    char *body = NULL;
    cJSON *req = cJSON_CreateObject();
    if (req) {
        cJSON_AddStringToObject(req, "appId", "PhoneBlockDongle");
        cJSON_AddStringToObject(req, "label", PB_TOKEN_LABEL);
        body = cJSON_PrintUnformatted(req);
        cJSON_Delete(req);
    }
    char *resp = malloc(LINK_RESPONSE_CAP);
    char reason[LINK_MESSAGE_CAP] = "";
    link_start_resp_t start;
    memset(&start, 0, sizeof(start));
    bool ok = false;

    if (!body || !resp) {
        snprintf(reason, sizeof(reason), "out of memory");
    } else {
        pb_log_info(TAG, "POST %s/api/link/start", base_url);
        int status = 0;
        int err = post_json(base_url, "/api/link/start", body, resp,
                            LINK_RESPONSE_CAP, &status);
        if (err != PB_HTTP_OK) {
            snprintf(reason, sizeof(reason), "PhoneBlock not reachable (%s)",
                     pb_http_err_name(err));
        } else if (status != 200) {
            char detail[104];
            server_message(resp, detail, sizeof(detail));
            snprintf(reason, sizeof(reason), "HTTP %d%s%s", status,
                     detail[0] ? ": " : "", detail);
        } else {
            ok = link_parse_start(resp, &start, reason, sizeof(reason));
        }
        wipe(resp, LINK_RESPONSE_CAP);
    }
    if (body) cJSON_free(body);
    free(resp);

    bool create_task = false;
    pb_mutex_lock(s_lock);
    // Ends a running link either way: the task drops it at its next step.
    s.generation++;
    wipe(s.device_code, sizeof(s.device_code));
    if (ok) {
        link_machine_begin(&s.machine, &start, protocol_now_us());
        s.expires_in_s = start.expires_in_s;
        snprintf(s.device_code, sizeof(s.device_code), "%s", start.device_code);
        snprintf(s.user_code, sizeof(s.user_code), "%s", start.user_code);
        snprintf(s.verify_url, sizeof(s.verify_url), "%s", start.verify_url);
        snprintf(s.base_url, sizeof(s.base_url), "%s", base_url);
        if (!s.task_running) {
            s.task_running = true;
            create_task = true;
        }
    } else {
        link_machine_fail(&s.machine, LINK_ERROR, reason);
        s.expires_in_s = 0;
        s.user_code[0] = '\0';
        s.verify_url[0] = '\0';
    }
    pb_mutex_unlock(s_lock);
    uint32_t expires_in_s = start.expires_in_s;
    wipe(&start, sizeof(start));

    if (create_task
            && pb_task_create(poll_task, NULL, "pb_link", 8192,
                              PB_PRIO_BACKGROUND) != PB_OK) {
        pb_mutex_lock(s_lock);
        s.task_running = false;
        wipe(s.device_code, sizeof(s.device_code));
        link_machine_fail(&s.machine, LINK_ERROR, "could not start the poll task");
        pb_mutex_unlock(s_lock);
        ok = false;
        snprintf(reason, sizeof(reason), "could not start the poll task");
    }

    if (ok) {
        pb_log_info(TAG, "started, waiting for the confirmation "
                    "(expires in %u s)", (unsigned)expires_in_s);
    } else {
        pb_log_warn(TAG, "link start failed: %s", reason);
    }
    link_get_status(out);
    return ok;
}
