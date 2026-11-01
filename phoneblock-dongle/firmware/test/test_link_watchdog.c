// Host test for link_watchdog.c — the decision logic of the Wi-Fi link
// watchdog (issue #585): when a dongle that holds an IP gets reconnected
// because the registrar went silent or the signal stayed too weak, and how
// the backoff keeps a bad location from turning into a reconnect storm.
// Time is simulated; the device feeds a sample every LINK_WD_TICK_S.
#include <assert.h>
#include <stdio.h>

#include "link_watchdog.h"

#define DAY_S (24u * 60u * 60u)

static link_wd_input_t healthy_input(void)
{
    link_wd_input_t in = {
        .has_ip = true,
        .pairing_active = false,
        .rssi_valid = true,
        .rssi = -60,
        .sip_configured = true,
        .registrar_unreachable_s = 0,
    };
    return in;
}

// Feed the same kind of sample every tick in [from, to); returns the number
// of actions and the time of the first one (or UINT32_MAX).
typedef link_wd_input_t (*input_fn)(uint32_t now_s, void *ctx);

static int run(link_wd_t *wd, uint32_t from, uint32_t to, input_fn fn, void *ctx,
               uint32_t *first_action)
{
    int actions = 0;
    if (first_action) *first_action = UINT32_MAX;
    for (uint32_t t = from; t < to; t += LINK_WD_TICK_S) {
        link_wd_input_t in = fn(t, ctx);
        if (link_wd_tick(wd, t, &in) != LINK_WD_NONE) {
            if (actions == 0 && first_action) *first_action = t;
            actions++;
        }
    }
    return actions;
}

static link_wd_input_t always_healthy(uint32_t now_s, void *ctx)
{
    (void)now_s; (void)ctx;
    return healthy_input();
}

// Registrar silent since *ctx (monotonic seconds), as sip_register reports it.
static link_wd_input_t registrar_dead_since(uint32_t now_s, void *ctx)
{
    uint32_t since = *(uint32_t *)ctx;
    link_wd_input_t in = healthy_input();
    in.registrar_unreachable_s = now_s >= since ? now_s - since : 0;
    return in;
}

static link_wd_input_t weak_signal(uint32_t now_s, void *ctx)
{
    (void)now_s;
    link_wd_input_t in = healthy_input();
    in.rssi = *(int *)ctx;
    return in;
}

static link_wd_input_t everything_bad(uint32_t now_s, void *ctx)
{
    (void)ctx;
    link_wd_input_t in = healthy_input();
    in.rssi = -90;
    in.registrar_unreachable_s = now_s + 1000;
    return in;
}

static link_wd_action_t tick_at(link_wd_t *wd, uint32_t now_s, link_wd_input_t in)
{
    return link_wd_tick(wd, now_s, &in);
}

static void test_registrar_threshold(void)
{
    link_wd_t wd;
    link_wd_init(&wd);
    // Link up and healthy for an hour, then the registrar goes silent.
    assert(run(&wd, 0, 3600, always_healthy, NULL, NULL) == 0);
    uint32_t dead = 3600;
    link_wd_input_t in = healthy_input();

    in.registrar_unreachable_s = 299;          // 4:59
    assert(tick_at(&wd, dead + 299, in) == LINK_WD_NONE);
    in.registrar_unreachable_s = 300;          // 5:00
    assert(tick_at(&wd, dead + 300, in) == LINK_WD_RECONNECT_REGISTRAR);
    assert(link_wd_last_gap_s(&wd) == LINK_WD_BACKOFF_MIN_S);

    // Fed tick by tick: the first action lands on the first tick at 5:00+.
    link_wd_init(&wd);
    assert(run(&wd, 0, 3600, always_healthy, NULL, NULL) == 0);
    uint32_t first;
    assert(run(&wd, 3600, 3600 + 600, registrar_dead_since, &dead, &first) == 1);
    assert(first == dead + 300);
}

static void test_registrar_silent_before_ip(void)
{
    // The registrar went silent while Wi-Fi was down (router reboot): its
    // silence counts only from the moment the link is back.
    link_wd_t wd;
    link_wd_init(&wd);
    link_wd_input_t in = healthy_input();
    in.has_ip = false;
    in.registrar_unreachable_s = 1000;
    assert(tick_at(&wd, 1000, in) == LINK_WD_NONE);

    in.has_ip = true;
    for (uint32_t t = 1030; t < 1030 + 300; t += LINK_WD_TICK_S) {
        in.registrar_unreachable_s = t;        // long silent, link just up
        assert(tick_at(&wd, t, in) == LINK_WD_NONE);
    }
    in.registrar_unreachable_s = 1330;
    assert(tick_at(&wd, 1330, in) == LINK_WD_RECONNECT_REGISTRAR);
}

static void test_sip_not_configured_never_triggers(void)
{
    link_wd_t wd;
    link_wd_init(&wd);
    link_wd_input_t in = healthy_input();
    in.sip_configured = false;
    for (uint32_t t = 0; t < DAY_S; t += LINK_WD_TICK_S) {
        in.registrar_unreachable_s = t + 3600;
        assert(tick_at(&wd, t, in) == LINK_WD_NONE);
    }
}

static void test_rssi_threshold(void)
{
    link_wd_t wd;
    int rssi = -85;
    uint32_t first;

    // -85 dBm: nothing for 9 minutes, the reconnect at 10.
    link_wd_init(&wd);
    assert(run(&wd, 0, 9 * 60 + 1, weak_signal, &rssi, NULL) == 0);
    link_wd_input_t in = weak_signal(600, &rssi);
    assert(tick_at(&wd, 600, in) == LINK_WD_RECONNECT_WEAK_SIGNAL);

    link_wd_init(&wd);
    assert(run(&wd, 0, 11 * 60, weak_signal, &rssi, &first) == 1);
    assert(first == 600);

    // Exactly -80 dBm is not "below -80".
    rssi = -80;
    link_wd_init(&wd);
    assert(run(&wd, 0, DAY_S, weak_signal, &rssi, NULL) == 0);

    // Fluctuating: one sample above -80 restarts the window.
    link_wd_init(&wd);
    in = healthy_input();
    for (uint32_t t = 0; t <= 540; t += LINK_WD_TICK_S) {
        in.rssi = -85;
        assert(tick_at(&wd, t, in) == LINK_WD_NONE);
    }
    in.rssi = -75;
    assert(tick_at(&wd, 570, in) == LINK_WD_NONE);
    in.rssi = -85;
    for (uint32_t t = 600; t < 600 + 600; t += LINK_WD_TICK_S) {
        assert(tick_at(&wd, t, in) == LINK_WD_NONE);
    }
    assert(tick_at(&wd, 1200, in) == LINK_WD_RECONNECT_WEAK_SIGNAL);

    // Losing the association (no RSSI) also restarts it.
    link_wd_init(&wd);
    in = healthy_input();
    in.rssi = -85;
    for (uint32_t t = 0; t <= 540; t += LINK_WD_TICK_S) {
        assert(tick_at(&wd, t, in) == LINK_WD_NONE);
    }
    in.rssi_valid = false;
    assert(tick_at(&wd, 570, in) == LINK_WD_NONE);
    in.rssi_valid = true;
    assert(tick_at(&wd, 600, in) == LINK_WD_NONE);
}

static void test_backoff(void)
{
    link_wd_t wd;
    link_wd_init(&wd);
    uint32_t dead = 0;

    // Registrar permanently dead: actions at 5:00, then gaps of 10, 20, 40,
    // 80 min, then the 2 h cap.
    uint32_t times[16];
    int n = 0;
    for (uint32_t t = 0; t < 16u * 3600u; t += LINK_WD_TICK_S) {
        link_wd_input_t in = registrar_dead_since(t, &dead);
        if (link_wd_tick(&wd, t, &in) != LINK_WD_NONE) {
            assert(n < 16);
            times[n++] = t;
        }
    }
    const uint32_t gaps[] = { 600, 1200, 2400, 4800, 7200, 7200, 7200 };
    assert(n >= 8);
    assert(times[0] == 300);
    for (int i = 0; i < 7; i++) {
        assert(times[i + 1] - times[i] == gaps[i]);
    }
    for (int i = 7; i + 1 < n; i++) {
        assert(times[i + 1] - times[i] == LINK_WD_BACKOFF_MAX_S);
    }

    // A second trigger before 10 min is suppressed, even though the
    // condition holds.
    link_wd_init(&wd);
    link_wd_input_t in = healthy_input();
    in.rssi = -90;
    assert(run(&wd, 0, 600, weak_signal, &in.rssi, NULL) == 0);
    assert(tick_at(&wd, 600, in) == LINK_WD_RECONNECT_WEAK_SIGNAL);
    in.registrar_unreachable_s = 100000;       // registrar dead too
    for (uint32_t t = 630; t < 1200; t += LINK_WD_TICK_S) {
        assert(tick_at(&wd, t, in) == LINK_WD_NONE);
    }
    assert(tick_at(&wd, 1200, in) != LINK_WD_NONE);
    assert(link_wd_last_gap_s(&wd) == 1200);
}

static void test_backoff_reset_after_healthy_period(void)
{
    link_wd_t wd;
    link_wd_init(&wd);
    uint32_t dead = 0;
    int actions = run(&wd, 0, 5u * 3600u, registrar_dead_since, &dead, NULL);
    assert(actions >= 5);
    assert(link_wd_last_gap_s(&wd) >= 4800);

    // Healthy for only half an hour: the long gap stays.
    uint32_t t0 = 5u * 3600u;
    assert(run(&wd, t0, t0 + 1800, always_healthy, NULL, NULL) == 0);
    dead = t0 + 1800;
    uint32_t first = UINT32_MAX;
    for (uint32_t t = dead; t < dead + 3 * 3600 && first == UINT32_MAX; t += LINK_WD_TICK_S) {
        link_wd_input_t in = registrar_dead_since(t, &dead);
        if (link_wd_tick(&wd, t, &in) != LINK_WD_NONE) first = t;
    }
    assert(first != UINT32_MAX);
    assert(link_wd_last_gap_s(&wd) == LINK_WD_BACKOFF_MAX_S);

    // Healthy for an hour, starting right after that action: the backoff
    // starts over at 10 min — and the 2 h wait it had armed is gone too.
    t0 = first + LINK_WD_TICK_S;
    assert(run(&wd, t0, t0 + 3600 + LINK_WD_TICK_S, always_healthy, NULL, NULL) == 0);
    dead = t0 + 3600 + LINK_WD_TICK_S;
    uint32_t times[2];
    int n = 0;
    for (uint32_t t = dead; t < dead + 3600 && n < 2; t += LINK_WD_TICK_S) {
        link_wd_input_t in = registrar_dead_since(t, &dead);
        if (link_wd_tick(&wd, t, &in) != LINK_WD_NONE) times[n++] = t;
    }
    assert(n == 2);
    assert(times[0] == dead + 300);            // no leftover wait
    assert(times[1] - times[0] == LINK_WD_BACKOFF_MIN_S);
}

static void test_no_trigger_while_pairing_or_before_connect(void)
{
    link_wd_t wd;

    // Never connected: no IP for a whole day while everything looks dead.
    link_wd_init(&wd);
    link_wd_input_t in = everything_bad(0, NULL);
    in.has_ip = false;
    in.rssi_valid = false;
    for (uint32_t t = 0; t < DAY_S; t += LINK_WD_TICK_S) {
        assert(tick_at(&wd, t, in) == LINK_WD_NONE);
    }

    // WPS / failsafe / provisioning active (the flag wifi.c derives from
    // them) — even with an IP and every trigger condition true.
    link_wd_init(&wd);
    for (uint32_t t = 0; t < DAY_S; t += LINK_WD_TICK_S) {
        in = everything_bad(t, NULL);
        in.pairing_active = true;
        assert(tick_at(&wd, t, in) == LINK_WD_NONE);
    }
    // Pairing over: the windows start from scratch (registrar: 5 min).
    uint32_t first;
    assert(run(&wd, DAY_S, DAY_S + 3600, everything_bad, NULL, &first) >= 1);
    assert(first == DAY_S + 300);
}

static void test_healthy_link_never_triggers(void)
{
    link_wd_t wd;
    link_wd_init(&wd);
    assert(run(&wd, 0, DAY_S, always_healthy, NULL, NULL) == 0);

    // Brief registrar blips (a refresh timing out once) and a dip of the
    // signal for a few minutes are not enough either.
    link_wd_init(&wd);
    int actions = 0;
    for (uint32_t t = 0; t < DAY_S; t += LINK_WD_TICK_S) {
        link_wd_input_t in = healthy_input();
        uint32_t phase = t % 3600;
        if (phase >= 600 && phase < 840) in.registrar_unreachable_s = phase - 600;
        if (phase >= 1800 && phase < 2280) in.rssi = -84;
        if (link_wd_tick(&wd, t, &in) != LINK_WD_NONE) actions++;
    }
    assert(actions == 0);
}

// A call the dongle answered defers the action to the first tick after it.
static void test_call_defers_action(void)
{
    link_wd_t wd;
    link_wd_input_t in;

    // Weak signal from t=0 (due at 600), a call from 300 to 900.
    link_wd_init(&wd);
    for (uint32_t t = 0; t < 900; t += LINK_WD_TICK_S) {
        in = healthy_input();
        in.rssi = -85;
        in.call_active = t >= 300;
        assert(tick_at(&wd, t, in) == LINK_WD_NONE);
    }
    in = healthy_input();
    in.rssi = -85;
    assert(tick_at(&wd, 900, in) == LINK_WD_RECONNECT_WEAK_SIGNAL);
    assert(link_wd_last_gap_s(&wd) == LINK_WD_BACKOFF_MIN_S);

    // Same for a dead registrar (due at 300), call from 200 to 1000.
    link_wd_init(&wd);
    for (uint32_t t = 0; t < 1000; t += LINK_WD_TICK_S) {
        in = healthy_input();
        in.registrar_unreachable_s = t;
        in.call_active = t >= 200;
        assert(tick_at(&wd, t, in) == LINK_WD_NONE);
    }
    in = healthy_input();
    in.registrar_unreachable_s = 1000;
    assert(tick_at(&wd, 1000, in) == LINK_WD_RECONNECT_REGISTRAR);

    // The condition healed before the call ended: no action at all.
    link_wd_init(&wd);
    for (uint32_t t = 0; t < DAY_S; t += LINK_WD_TICK_S) {
        in = healthy_input();
        in.rssi = t < 810 ? -85 : -60;
        in.call_active = t >= 300 && t < 900;
        assert(tick_at(&wd, t, in) == LINK_WD_NONE);
    }

    // The deferral does not touch the backoff: actions at 300 and, deferred
    // by a call over 800..1500, at 1500 — with the second gap (20 min) armed
    // there, not a doubled one, and the third action 20 min later.
    link_wd_init(&wd);
    uint32_t times[3];
    int n = 0;
    for (uint32_t t = 0; t < 4000 && n < 3; t += LINK_WD_TICK_S) {
        in = healthy_input();
        in.registrar_unreachable_s = t;
        in.call_active = t >= 800 && t < 1500;
        if (link_wd_tick(&wd, t, &in) != LINK_WD_NONE) {
            times[n++] = t;
            if (n == 2) assert(link_wd_last_gap_s(&wd) == 2 * LINK_WD_BACKOFF_MIN_S);
        }
    }
    assert(n == 3);
    assert(times[0] == 300 && times[1] == 1500);
    assert(times[2] - times[1] == 2 * LINK_WD_BACKOFF_MIN_S);
}

int main(void)
{
    test_registrar_threshold();
    test_registrar_silent_before_ip();
    test_sip_not_configured_never_triggers();
    test_rssi_threshold();
    test_backoff();
    test_backoff_reset_after_healthy_period();
    test_no_trigger_while_pairing_or_before_connect();
    test_healthy_link_never_triggers();
    test_call_defers_action();
    printf("test_link_watchdog: all tests passed\n");
    return 0;
}
