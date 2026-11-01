// Probe for link_watchdog.c (#585): compositions the unit test does not
// cover — an outage during which the registrar silence keeps counting, and a
// working but weak link over a whole day.
#include <assert.h>
#include <stdio.h>

#include "link_watchdog.h"

static link_wd_input_t base(void)
{
    link_wd_input_t in = {
        .has_ip = true, .pairing_active = false, .rssi_valid = true,
        .rssi = -60, .sip_configured = true, .registrar_unreachable_s = 0,
    };
    return in;
}

// The registrar went silent at t=1000 (router reboot), Wi-Fi is gone from
// t=1030 to t=3000. sip_register keeps reporting the silence since 1000, so
// at t=3000 it reads 2000 s. The watchdog must still wait a full
// LINK_WD_REGISTRAR_DEAD_S of IP time before acting.
static void test_outage_silence_needs_full_window_after_ip_returns(void)
{
    link_wd_t wd;
    link_wd_init(&wd);
    uint32_t t = 0;
    for (; t < 1000; t += LINK_WD_TICK_S) {
        link_wd_input_t in = base();
        assert(link_wd_tick(&wd, t, &in) == LINK_WD_NONE);
    }
    for (; t < 3000; t += LINK_WD_TICK_S) {
        link_wd_input_t in = base();
        in.has_ip = t < 1030;
        in.rssi_valid = in.has_ip;
        in.registrar_unreachable_s = t - 1000;
        assert(link_wd_tick(&wd, t, &in) == LINK_WD_NONE);
    }
    uint32_t first = 0;
    for (; t < 3000 + 2 * LINK_WD_REGISTRAR_DEAD_S; t += LINK_WD_TICK_S) {
        link_wd_input_t in = base();
        in.registrar_unreachable_s = t - 1000;
        if (link_wd_tick(&wd, t, &in) != LINK_WD_NONE) { first = t; break; }
    }
    assert(first >= 3000 + LINK_WD_REGISTRAR_DEAD_S);
    assert(first <= 3000 + LINK_WD_REGISTRAR_DEAD_S + LINK_WD_TICK_S);
    printf("  ok: silence from before an outage needs a full window of IP time\n");
}

// A dongle at a poor but working spot: -83 dBm all day, registrar answering.
// The backoff must bound the reconnects (10, 20, 40, 80 min, then every 2 h).
static void test_weak_but_working_link_is_bounded(void)
{
    link_wd_t wd;
    link_wd_init(&wd);
    int actions = 0;
    for (uint32_t t = 0; t < 24u * 3600u; t += LINK_WD_TICK_S) {
        link_wd_input_t in = base();
        in.rssi = -83;
        if (link_wd_tick(&wd, t, &in) != LINK_WD_NONE) actions++;
    }
    printf("  weak but working link: %d reconnects in 24 h\n", actions);
    assert(actions >= 5 && actions <= 16);
    printf("  ok: a weak but working link reconnects at most every 2 h\n");
}

// Composition: the weak but working link of the previous case, plus a long
// dialog call (no time limit) from 1.5 h to 3 h, spanning the 2 h point where
// a reconnect would otherwise fall (at 9630 s; the low-signal window restarts
// with the first sample after each reconnect, hence the extra 30 s per step).
// Nothing may happen during the call; the due reconnect fires on the first
// tick after it, and the backoff continues from there unchanged.
static void test_weak_link_with_long_call_over_2h(void)
{
    link_wd_t wd;
    link_wd_init(&wd);
    const uint32_t call_from = 5400, call_to = 10800;
    uint32_t times[32];
    int n = 0;
    for (uint32_t t = 0; t < 24u * 3600u; t += LINK_WD_TICK_S) {
        link_wd_input_t in = base();
        in.rssi = -83;
        in.call_active = t >= call_from && t < call_to;
        if (link_wd_tick(&wd, t, &in) != LINK_WD_NONE) {
            assert(!in.call_active);
            assert(n < 32);
            times[n++] = t;
        }
    }
    // Without the call: 600, 1230, 2430, 4830, 9630, 16830, ...
    assert(n >= 6);
    assert(times[0] == 600 && times[1] == 1230 && times[2] == 2430 && times[3] == 4830);
    assert(times[4] == call_to);                      // deferred from 9630
    assert(times[5] - times[4] == LINK_WD_BACKOFF_MAX_S);
    printf("  weak link + long call: %d reconnects in 24 h, none during the call\n", n);
    printf("  ok: a reconnect due during a call fires on the first tick after it\n");
}

int main(void)
{
    printf("test_link_watchdog_probe\n");
    test_outage_silence_needs_full_window_after_ip_returns();
    test_weak_but_working_link_is_bounded();
    test_weak_link_with_long_call_over_2h();
    printf("test_link_watchdog_probe: all tests passed\n");
    return 0;
}
