// Host-side unit tests for main/link_proto.{c,h}: the server answers of the
// device-code link (#621) and its state machine. No network, no task —
// test_link_linux.c runs the real poll loop against a fake server.

#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>

#include "link_proto.h"

static int g_tests = 0;
static int g_failures = 0;

#define CHECK(name, cond) do {                                       \
    g_tests++;                                                       \
    if (!(cond)) {                                                   \
        fprintf(stderr, "FAIL %s: %s\n", name, #cond);               \
        g_failures++;                                                \
    }                                                                \
} while (0)

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
        fprintf(stderr, "FAIL %s: expected \"%s\" got \"%s\"\n",     \
                name, (expected), (got));                            \
        g_failures++;                                                \
    }                                                                \
} while (0)

#define DEVICE_CODE "q3Jw0dQ-Hk3d9pLm2vXcT8yZb1nR5sF7aE4uW6oK_Ig"
#define VERIFY_URL  "https://phoneblock.net/phoneblock/link?code=BCDF-GHJK"

static const char START_OK[] =
    "{\"deviceCode\":\"" DEVICE_CODE "\","
    "\"userCode\":\"BCDF-GHJK\","
    "\"verifyUrl\":\"" VERIFY_URL "\","
    "\"interval\":5,\"expiresIn\":600}";

static void test_start(void)
{
    link_start_resp_t r;
    char reason[LINK_MESSAGE_CAP];

    CHECK("start ok", link_parse_start(START_OK, &r, reason, sizeof(reason)));
    CHECK_STR("start deviceCode", DEVICE_CODE, r.device_code);
    CHECK_STR("start userCode", "BCDF-GHJK", r.user_code);
    CHECK_STR("start verifyUrl", VERIFY_URL, r.verify_url);
    CHECK_INT("start interval", 5, r.interval_s);
    CHECK_INT("start expiresIn", 600, r.expires_in_s);
    CHECK_STR("start no reason", "", reason);

    // No interval: RFC 8628's default.
    CHECK("start no interval", link_parse_start(
        "{\"deviceCode\":\"abc\",\"userCode\":\"BCDF-GHJK\","
        "\"verifyUrl\":\"http://localhost:8080/phoneblock/link\",\"expiresIn\":60}",
        &r, reason, sizeof(reason)));
    CHECK_INT("start default interval", LINK_DEFAULT_INTERVAL_S, r.interval_s);

    // Clamped: interval to the maximum, the lifetime to the local cap.
    CHECK("start clamps", link_parse_start(
        "{\"deviceCode\":\"abc\",\"userCode\":\"BCDF-GHJK\","
        "\"verifyUrl\":\"https://x/link\",\"interval\":3600,\"expiresIn\":86400}",
        &r, reason, sizeof(reason)));
    CHECK_INT("start interval clamped", LINK_MAX_INTERVAL_S, r.interval_s);
    CHECK_INT("start expires clamped", LINK_MAX_EXPIRES_S, r.expires_in_s);

    static const struct { const char *name, *json; } bad[] = {
        { "start malformed",     "{\"deviceCode\":" },
        { "start not object",    "[1,2]" },
        { "start no deviceCode", "{\"userCode\":\"BCDF-GHJK\",\"verifyUrl\":\"https://x\",\"expiresIn\":600}" },
        { "start bad deviceCode","{\"deviceCode\":\"a b\",\"userCode\":\"BCDF-GHJK\",\"verifyUrl\":\"https://x\",\"expiresIn\":600}" },
        { "start no userCode",   "{\"deviceCode\":\"abc\",\"verifyUrl\":\"https://x\",\"expiresIn\":600}" },
        { "start html userCode", "{\"deviceCode\":\"abc\",\"userCode\":\"<b>\",\"verifyUrl\":\"https://x\",\"expiresIn\":600}" },
        { "start js verifyUrl",  "{\"deviceCode\":\"abc\",\"userCode\":\"BCDF-GHJK\",\"verifyUrl\":\"javascript:alert(1)\",\"expiresIn\":600}" },
        { "start no expiresIn",  "{\"deviceCode\":\"abc\",\"userCode\":\"BCDF-GHJK\",\"verifyUrl\":\"https://x\"}" },
        { "start zero expiresIn","{\"deviceCode\":\"abc\",\"userCode\":\"BCDF-GHJK\",\"verifyUrl\":\"https://x\",\"expiresIn\":0}" },
        { "start bad interval",  "{\"deviceCode\":\"abc\",\"userCode\":\"BCDF-GHJK\",\"verifyUrl\":\"https://x\",\"interval\":\"5\",\"expiresIn\":600}" },
    };
    for (size_t i = 0; i < sizeof(bad) / sizeof(bad[0]); i++) {
        reason[0] = '\0';
        CHECK(bad[i].name, !link_parse_start(bad[i].json, &r, reason, sizeof(reason)));
        CHECK(bad[i].name, reason[0] != '\0');
        // A refused answer leaves no device code behind.
        CHECK(bad[i].name, r.device_code[0] == '\0');
    }
    CHECK("start NULL", !link_parse_start(NULL, &r, reason, sizeof(reason)));
}

static void test_poll(void)
{
    link_poll_resp_t p;

    link_parse_poll("{\"status\":\"pending\"}", &p);
    CHECK_INT("poll pending", LINK_POLL_PENDING, p.status);
    link_parse_poll("{\"status\":\"slow_down\"}", &p);
    CHECK_INT("poll slow_down", LINK_POLL_SLOW_DOWN, p.status);
    link_parse_poll("{\"status\":\"denied\"}", &p);
    CHECK_INT("poll denied", LINK_POLL_DENIED, p.status);
    link_parse_poll("{\"status\":\"expired\"}", &p);
    CHECK_INT("poll expired", LINK_POLL_EXPIRED, p.status);
    link_parse_poll("{\"status\":\"approved\",\"token\":\"dongle-test-token_0123\"}", &p);
    CHECK_INT("poll approved", LINK_POLL_APPROVED, p.status);
    CHECK_STR("poll approved token", "dongle-test-token_0123", p.token);

    static const struct { const char *name, *json; } bad[] = {
        { "poll malformed",           "{\"status\":" },
        { "poll empty",               "" },
        { "poll no status",           "{\"token\":\"abc\"}" },
        { "poll status not string",   "{\"status\":1}" },
        { "poll unknown status",      "{\"status\":\"maybe\"}" },
        { "poll approved no token",   "{\"status\":\"approved\"}" },
        { "poll approved empty token","{\"status\":\"approved\",\"token\":\"\"}" },
        { "poll approved bad token",  "{\"status\":\"approved\",\"token\":\"a\\nb\"}" },
        // Longer than the config's pb_token field: would be stored truncated.
        { "poll approved long token", "{\"status\":\"approved\",\"token\":\""
          "0123456789012345678901234567890123456789012345678901234567890123\"}" },
    };
    for (size_t i = 0; i < sizeof(bad) / sizeof(bad[0]); i++) {
        link_parse_poll(bad[i].json, &p);
        CHECK_INT(bad[i].name, LINK_POLL_ERROR, p.status);
        CHECK(bad[i].name, p.reason[0] != '\0');
        CHECK(bad[i].name, p.token[0] == '\0');
    }
    link_parse_poll(NULL, &p);
    CHECK_INT("poll NULL", LINK_POLL_ERROR, p.status);
}

static void test_interval(void)
{
    CHECK_INT("slow_down +5", 10, link_next_interval(5, LINK_POLL_SLOW_DOWN));
    CHECK_INT("pending keeps", 5, link_next_interval(5, LINK_POLL_PENDING));
    CHECK_INT("slow_down capped", LINK_MAX_INTERVAL_S,
              link_next_interval(LINK_MAX_INTERVAL_S - 2, LINK_POLL_SLOW_DOWN));
}

static void test_machine(void)
{
    link_start_resp_t start;
    char reason[LINK_MESSAGE_CAP];
    CHECK("machine start", link_parse_start(START_OK, &start, reason, sizeof(reason)));

    const uint64_t t0 = 1000000000ULL;
    link_machine_t m;
    link_machine_begin(&m, &start, t0);
    CHECK_INT("begin pending", LINK_PENDING, m.state);
    CHECK_INT("begin interval", 5, m.interval_s);
    CHECK_INT("begin deadline", (long)(t0 + 600ULL * 1000000ULL), (long)m.deadline_us);

    link_poll_resp_t p = { .status = LINK_POLL_PENDING };
    CHECK_INT("pending stays", LINK_PENDING, link_machine_apply(&m, &p));
    p.status = LINK_POLL_SLOW_DOWN;
    CHECK_INT("slow_down stays", LINK_PENDING, link_machine_apply(&m, &p));
    CHECK_INT("slow_down raises interval", 10, m.interval_s);
    CHECK_INT("slow_down raises again", LINK_PENDING, link_machine_apply(&m, &p));
    CHECK_INT("slow_down interval 15", 15, m.interval_s);

    // Not yet expired one microsecond before the deadline, expired at it.
    CHECK("not expired", !link_machine_check_expiry(&m, m.deadline_us - 1));
    CHECK_INT("still pending", LINK_PENDING, m.state);
    CHECK("expired", link_machine_check_expiry(&m, m.deadline_us));
    CHECK_INT("expired state", LINK_EXPIRED, m.state);
    CHECK("expired message", m.message[0] != '\0');
    // Terminal: neither a second expiry nor a late answer change it.
    CHECK("expired once", !link_machine_check_expiry(&m, m.deadline_us + 1));
    p.status = LINK_POLL_APPROVED;
    CHECK_INT("late approval ignored", LINK_EXPIRED, link_machine_apply(&m, &p));

    static const struct {
        const char *name;
        link_poll_status_t status;
        link_state_t state;
    } ends[] = {
        { "approved", LINK_POLL_APPROVED, LINK_APPROVED },
        { "denied",   LINK_POLL_DENIED,   LINK_DENIED },
        { "expired",  LINK_POLL_EXPIRED,  LINK_EXPIRED },
        { "error",    LINK_POLL_ERROR,    LINK_ERROR },
    };
    for (size_t i = 0; i < sizeof(ends) / sizeof(ends[0]); i++) {
        link_machine_begin(&m, &start, t0);
        link_poll_resp_t e = { .status = ends[i].status };
        snprintf(e.reason, sizeof(e.reason), "test reason");
        CHECK_INT(ends[i].name, ends[i].state, link_machine_apply(&m, &e));
        CHECK_STR(ends[i].name, link_state_name(ends[i].state),
                  link_state_name(m.state));
    }
    // An error carries the parse reason to the UI.
    CHECK_STR("error message", "test reason", m.message);

    link_machine_fail(&m, LINK_ERROR, "HTTP 429: too many open links");
    CHECK_INT("fail state", LINK_ERROR, m.state);
    CHECK_STR("fail message", "HTTP 429: too many open links", m.message);

    CHECK_STR("name idle", "idle", link_state_name(LINK_IDLE));
    CHECK_STR("name pending", "pending", link_state_name(LINK_PENDING));
}

int main(void)
{
    test_start();
    test_poll();
    test_interval();
    test_machine();
    printf("%d tests, %d failures\n", g_tests, g_failures);
    return g_failures ? 1 : 0;
}
