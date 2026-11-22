#include "mail.h"

#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <sys/time.h>
#include <time.h>
#include "lwip/sockets.h"

#include "esp_crt_bundle.h"
#include "esp_log.h"
#include "esp_system.h"
#include "esp_timer.h"

#include "mbedtls/ctr_drbg.h"
#include "mbedtls/entropy.h"
#include "mbedtls/net_sockets.h"
#include "mbedtls/ssl.h"

#include "config.h"
#include "heap_guard.h"
#include "mail_calls.h"
#include "mail_i18n.h"
#include "mail_html.h"
#include "mail_status.h"
#include "mail_rcpt.h"
#include "platform.h"
#include "smtp_body.h"
#include "stats.h"
#include "strbuf.h"
#include "tls_fragment.h"
#include "time_sync.h"
#include "wifi.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

static const char *TAG = "mail";

// Same heap headroom rationale as crashreport.c / logreport.c: the TLS
// handshake wants ~30 KB of bignum scratch for the cert-chain verify.
// Below this, skip rather than fail the handshake noisily.
#define MAIL_MIN_FREE_HEAP  (64 * 1024)

// Per-syscall block bound (SO_RCVTIMEO/SO_SNDTIMEO) and the overall
// wall-clock deadline for one send. The deadline keeps a slow/broken
// server from holding the scheduler task: every blocking step is capped
// by the socket timeout, and the loops give up once the deadline passes.
#define MAIL_SOCK_TIMEO_S   10
#define MAIL_DEADLINE_S     45

// Buffer for one command / header line (commands, base64 credentials,
// the assembled header block). Comfortably larger than the longest line:
// the header block is the biggest consumer, and its "To:" alone can carry
// MAIL_RCPT_MAX addresses (~330 B) on top of Date/From/Subject/Content-Type.
#define MAIL_LINE_CAP       1024
// Upper bound for the assembled status body. The status mail is a single
// text/html document — summary + the calls since the last mail + the log
// window. text/html (not multipart/alternative) keeps the body — held in
// RAM across the heap-hungry TLS handshake — small. 16 KB takes ~50 call
// rows (~300 B each) or, next to the log, ~35 of them and the log's
// 47 entries; rows that do not fit are counted in the "not listed" line.
// The free-heap check (MAIL_MIN_FREE_HEAP) runs before this is allocated,
// so the handshake keeps 48 KB.
#define MAIL_BODY_CAP       16384

// Content-Type for the status mail body.
#define MAIL_BODY_CT        "text/html; charset=utf-8"

// Connection wrapping the raw socket plus the TLS session. `tls_up`
// switches the read/write helpers between plaintext (the pre-STARTTLS
// greeting/EHLO) and the encrypted channel (everything after).
typedef struct {
    mbedtls_net_context net;
    mbedtls_ssl_context ssl;
    bool                tls_up;
} smtp_conn_t;

// High-water mark of the log, RAM-only (reset on reboot — intended: a
// reboot's own crash ERROR then ships once more, which is the point).
// Independent of logreport.c's marks: this is a separate sink. The calls'
// mark is in the call file (stats_set_mailed_seq, mail_calls.h, #589), so
// calls not yet mailed before a reboot are mailed afterwards.
static int64_t  s_reported_through_us = 0;  // newest log at_us already mailed

// One-shot "we just updated" latch. Set once at boot (mail_note_update, from
// main.c) when the running image is a freshly-installed OTA build, consumed
// by mail_report_update() on the next scheduler mail run. RAM-only like the
// marks above: lost on reboot, which only drops the notice if the device
// reboots again within minutes of the update — acceptable for an
// informational mail.
static bool s_update_pending = false;
static char s_update_version[32];

bool mail_configured(void)
{
    if (!(config_smtp_host()[0] && config_smtp_user()[0] && config_smtp_pass()[0]))
        return false;
    // The recipient field is a ';'-separated list; it counts as configured
    // only if at least one usable address comes out of it (a spec of just
    // separators or whitespace does not).
    mail_rcpt_list_t rcpts;
    return mail_rcpt_parse(config_smtp_to(), &rcpts) > 0;
}

// --- low-level channel I/O ------------------------------------------

static int chan_read(smtp_conn_t *c, unsigned char *b, size_t n)
{
    return c->tls_up ? mbedtls_ssl_read(&c->ssl, b, n)
                     : mbedtls_net_recv(&c->net, b, n);
}

static int chan_write_all(smtp_conn_t *c, const unsigned char *b, size_t n,
                          int64_t deadline)
{
    size_t off = 0;
    while (off < n) {
        if (esp_timer_get_time() > deadline) return -1;
        int ret = c->tls_up
            ? mbedtls_ssl_write(&c->ssl, b + off, n - off)
            : mbedtls_net_send(&c->net, b + off, n - off);
        if (ret == MBEDTLS_ERR_SSL_WANT_READ || ret == MBEDTLS_ERR_SSL_WANT_WRITE)
            continue;
        if (ret <= 0) return -1;
        off += (size_t)ret;
    }
    return 0;
}

// Read one SMTP reply and return its 3-digit status code (or -1). Handles
// multi-line replies: the final line is "NNN <text>" (space after the
// code), continuation lines are "NNN-<text>".
static int read_reply(smtp_conn_t *c, int64_t deadline)
{
    unsigned char data[128];
    char code[4];
    int  idx = 0;
    while (1) {
        if (esp_timer_get_time() > deadline) return -1;
        int ret = chan_read(c, data, sizeof(data) - 1);
        if (ret == MBEDTLS_ERR_SSL_WANT_READ || ret == MBEDTLS_ERR_SSL_WANT_WRITE)
            continue;   // socket timeout fired; deadline above bounds the loop
        if (ret <= 0) return -1;
        for (int i = 0; i < ret; i++) {
            if (data[i] != '\n') {
                if (idx < 4) code[idx++] = (char)data[i];
                continue;
            }
            if (idx >= 4 && code[0] >= '0' && code[0] <= '9' && code[3] == ' ') {
                code[3] = '\0';
                return atoi(code);
            }
            idx = 0;   // continuation line (or noise) — keep reading
        }
    }
}

static int smtp_cmd_n(smtp_conn_t *c, const char *buf, size_t n, int64_t deadline)
{
    if (chan_write_all(c, (const unsigned char *)buf, n, deadline) != 0) return -1;
    return read_reply(c, deadline);
}

static int smtp_cmd(smtp_conn_t *c, const char *line, int64_t deadline)
{
    return smtp_cmd_n(c, line, strlen(line), deadline);
}

static int do_handshake(smtp_conn_t *c, int64_t deadline)
{
    int ret;
    while ((ret = mbedtls_ssl_handshake(&c->ssl)) != 0) {
        if (ret == MBEDTLS_ERR_SSL_WANT_READ || ret == MBEDTLS_ERR_SSL_WANT_WRITE) {
            if (esp_timer_get_time() > deadline) {
                ESP_LOGW(TAG, "TLS handshake timed out");
                return -1;
            }
            continue;
        }
        ESP_LOGW(TAG, "TLS handshake failed: -0x%x", -ret);
        return -1;
    }
    uint32_t flags = mbedtls_ssl_get_verify_result(&c->ssl);
    if (flags != 0) {
        ESP_LOGW(TAG, "server certificate not trusted (flags 0x%x)",
                 (unsigned)flags);
        return -1;
    }
    return 0;
}

// Adapter so smtp_encode_body() can stream straight onto the TLS channel:
// each encoded chunk is written with chan_write_all under the send
// deadline. The LF->CRLF and dot-stuffing logic itself lives in
// smtp_body.c, where it is host-tested.
struct body_sink_ctx { smtp_conn_t *c; int64_t deadline; };

static int body_chan_sink(void *ctx, const char *data, size_t len)
{
    struct body_sink_ctx *b = ctx;
    return chan_write_all(b->c, (const unsigned char *)data, len, b->deadline);
}

static int smtp_write_body(smtp_conn_t *c, const char *body, int64_t deadline)
{
    struct body_sink_ctx ctx = { c, deadline };
    return smtp_encode_body(body, body_chan_sink, &ctx);
}

// --- send -----------------------------------------------------------

// Synchronous SMTP send. Internal: it runs the mbedTLS handshake (with
// X.509 chain verification) inline on the caller's stack and blocks on the
// network for up to MAIL_DEADLINE_S, so it must only ever be driven from
// the scheduler task — the daily flush and the test trigger both do.
static bool mail_send(const char *subject, const char *content_type, const char *body)
{
    if (!mail_configured()) {
        ESP_LOGW(TAG, "mail not configured (host/user/pass/recipient)");
        return false;
    }
    // Breadcrumb for the heap sentinel: assembles a multi-KB body and runs an
    // SMTP/TLS conversation, both heap-heavy.
    heap_guard_note("mail:send");
    if (!wifi_has_ip()) {
        ESP_LOGW(TAG, "no network — mail not sent");
        return false;
    }
    size_t free_heap = esp_get_free_heap_size();
    if (free_heap < MAIL_MIN_FREE_HEAP) {
        ESP_LOGW(TAG, "heap %u B below floor — mail not sent", (unsigned)free_heap);
        return false;
    }

    const char *host     = config_smtp_host();
    const char *user     = config_smtp_user();
    const char *pass     = config_smtp_pass();
    const char *from     = config_smtp_from();
    const bool  starttls = strcmp(config_smtp_security(), "starttls") == 0;
    // Stored 0 = "auto": the conventional submission port for the mode.
    int         port     = config_smtp_port();
    if (port <= 0) port = starttls ? 587 : 465;

    // Recipients: the stored spec is a ';'-separated list, one RCPT TO per
    // address below. mail_configured() already guaranteed at least one.
    mail_rcpt_list_t rcpts;
    mail_rcpt_parse(config_smtp_to(), &rcpts);
    if (rcpts.dropped)
        ESP_LOGW(TAG, "recipient list: entries dropped (over %d, or longer than %d B)",
                 MAIL_RCPT_MAX, MAIL_RCPT_ADDR_CAP - 1);
    // Comma-separated form for the RFC 5322 "To:" header and the log lines.
    char to_hdr[MAIL_RCPT_MAX * (MAIL_RCPT_ADDR_CAP + 4)];
    mail_rcpt_header(&rcpts, to_hdr, sizeof(to_hdr));

    int64_t deadline = esp_timer_get_time() + (int64_t)MAIL_DEADLINE_S * 1000000;

    char *buf = malloc(MAIL_LINE_CAP);
    if (!buf) return false;

    smtp_conn_t c = { 0 };
    bool ok = false;
    int  ret;
    size_t b64len = 0;
    char   port_s[12];   // room for any int ("%d") — appeases -Wformat-truncation
    struct timeval tv = { .tv_sec = MAIL_SOCK_TIMEO_S, .tv_usec = 0 };

    mbedtls_entropy_context  entropy;
    mbedtls_ctr_drbg_context drbg;
    mbedtls_ssl_config       conf;

    mbedtls_ssl_init(&c.ssl);
    mbedtls_net_init(&c.net);
    mbedtls_ssl_config_init(&conf);
    mbedtls_ctr_drbg_init(&drbg);
    mbedtls_entropy_init(&entropy);

    if (mbedtls_ctr_drbg_seed(&drbg, mbedtls_entropy_func, &entropy, NULL, 0) != 0)
        goto done;
    if (mbedtls_ssl_config_defaults(&conf, MBEDTLS_SSL_IS_CLIENT,
                                    MBEDTLS_SSL_TRANSPORT_STREAM,
                                    MBEDTLS_SSL_PRESET_DEFAULT) != 0)
        goto done;
    mbedtls_ssl_conf_authmode(&conf, MBEDTLS_SSL_VERIFY_REQUIRED);
    if (esp_crt_bundle_attach(&conf) != ESP_OK) goto done;
    mbedtls_ssl_conf_rng(&conf, mbedtls_ctr_drbg_random, &drbg);
    // SNI must be the service hostname the user configured — this is what
    // the server matches its certificate against.
    if (mbedtls_ssl_set_hostname(&c.ssl, host) != 0) goto done;
    // No 4 KB record request towards the user's mail server (#734,
    // tls_fragment.h): its certificate chain may not fit one 4 KB record.
    tls_fragment_exempt(true);
    int setup_rc = mbedtls_ssl_setup(&c.ssl, &conf);
    tls_fragment_exempt(false);
    if (setup_rc != 0) goto done;

    snprintf(port_s, sizeof(port_s), "%d", port);
    ESP_LOGI(TAG, "connecting to %s:%d (%s)", host, port,
             starttls ? "starttls" : "tls");
    if (mbedtls_net_connect(&c.net, host, port_s, MBEDTLS_NET_PROTO_TCP) != 0) {
        ESP_LOGW(TAG, "connect to %s:%d failed", host, port);
        goto done;
    }
    setsockopt(c.net.fd, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));
    setsockopt(c.net.fd, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof(tv));
    mbedtls_ssl_set_bio(&c.ssl, &c.net, mbedtls_net_send, mbedtls_net_recv, NULL);

    // Implicit TLS: handshake before any SMTP byte. STARTTLS: stay
    // plaintext for the greeting/EHLO/STARTTLS, upgrade below.
    if (!starttls) {
        if (do_handshake(&c, deadline) != 0) goto done;
        c.tls_up = true;
    }

    ret = read_reply(&c, deadline);                       // server greeting
    if (ret < 200 || ret > 299) {
        ESP_LOGW(TAG, "SMTP greeting failed (code %d)", ret);
        goto done;
    }
    ret = smtp_cmd(&c, "EHLO phoneblock-dongle\r\n", deadline);
    if (ret < 200 || ret > 299) {
        ESP_LOGW(TAG, "SMTP EHLO failed (code %d)", ret);
        goto done;
    }

    if (starttls) {
        ret = smtp_cmd(&c, "STARTTLS\r\n", deadline);
        if (ret < 200 || ret > 299) {
            ESP_LOGW(TAG, "SMTP STARTTLS failed (code %d)", ret);
            goto done;
        }
        if (do_handshake(&c, deadline) != 0) goto done;
        c.tls_up = true;
        ret = smtp_cmd(&c, "EHLO phoneblock-dongle\r\n", deadline);
        if (ret < 200 || ret > 299) {
            ESP_LOGW(TAG, "SMTP EHLO after STARTTLS failed (code %d)", ret);
            goto done;
        }
    }

    // AUTH LOGIN: username then password, each base64 on its own line.
    ret = smtp_cmd(&c, "AUTH LOGIN\r\n", deadline);
    if (ret != 334) {
        ESP_LOGW(TAG, "SMTP AUTH LOGIN failed (code %d)", ret);
        goto done;
    }
    if (pb_base64_encode((unsigned char *)buf, MAIL_LINE_CAP - 2, &b64len,
                              (const unsigned char *)user, strlen(user)) != 0)
        goto done;
    buf[b64len] = '\r'; buf[b64len + 1] = '\n';
    ret = smtp_cmd_n(&c, buf, b64len + 2, deadline);
    if (ret != 334) {
        ESP_LOGW(TAG, "SMTP AUTH username failed (code %d)", ret);
        goto done;
    }
    if (pb_base64_encode((unsigned char *)buf, MAIL_LINE_CAP - 2, &b64len,
                              (const unsigned char *)pass, strlen(pass)) != 0)
        goto done;
    buf[b64len] = '\r'; buf[b64len + 1] = '\n';
    ret = smtp_cmd_n(&c, buf, b64len + 2, deadline);
    if (ret != 235) {
        ESP_LOGW(TAG, "SMTP authentication rejected (code %d)", ret);
        goto done;
    }

    // Envelope.
    snprintf(buf, MAIL_LINE_CAP, "MAIL FROM:<%s>\r\n", from);
    ret = smtp_cmd(&c, buf, deadline);
    if (ret < 200 || ret > 299) {
        ESP_LOGW(TAG, "SMTP MAIL FROM failed (code %d)", ret);
        goto done;
    }
    // One RCPT TO per recipient (RFC 5321 §3.3). A single rejected address
    // must not sink the whole mail — providers reject individual recipients
    // for their own reasons (unknown mailbox, per-recipient policy) — so log
    // each refusal and go on; only an envelope with no accepted recipient at
    // all is fatal, since DATA would then be rejected anyway.
    int accepted = 0;
    for (int i = 0; i < rcpts.count; i++) {
        snprintf(buf, MAIL_LINE_CAP, "RCPT TO:<%s>\r\n", rcpts.addr[i]);
        ret = smtp_cmd(&c, buf, deadline);
        if (ret >= 200 && ret <= 299) accepted++;
        else ESP_LOGW(TAG, "recipient %s rejected (code %d)", rcpts.addr[i], ret);
    }
    if (accepted == 0) {
        ESP_LOGW(TAG, "no recipient accepted — mail not sent");
        goto done;
    }

    // RFC 5322 Date header, in UTC so it is independent of the configured
    // timezone. Without it, receiving clients (e.g. Thunderbird) fall back to
    // the arrival time or show no send date at all (issue #488). Emitted only
    // when the clock is SNTP-synced; the "C" locale that ESP-IDF/newlib use by
    // default makes strftime's %a/%b the English abbreviations the RFC wants.
    // If the clock is unset the header is omitted and the receiving MTA adds
    // its own Date — better than stamping a bogus time.
    char date_hdr[48] = "";
    if (time_sync_valid()) {
        time_t    now = (time_t)time_sync_now_epoch();
        struct tm gmt;
        gmtime_r(&now, &gmt);
        strftime(date_hdr, sizeof(date_hdr),
                 "Date: %a, %d %b %Y %H:%M:%S +0000\r\n", &gmt);
    }

    // Header block. UTF-8 body declared via Content-Type; the Subject stays
    // ASCII so no encoded-word is needed. Assembled before DATA so that a
    // header block which does not fit is caught while the transaction can
    // still be abandoned cleanly — a dropped header would be malformed mail.
    {
        strbuf_t sb = sb_init(buf, MAIL_LINE_CAP);
        sb_appendf(&sb, "%s", date_hdr);
        sb_appendf(&sb, "From: PhoneBlock Dongle <%s>\r\n", from);
        sb_appendf(&sb, "To: %s\r\n", to_hdr);
        sb_appendf(&sb, "Subject: %s\r\n", subject);
        sb_appendf(&sb, "MIME-Version: 1.0\r\n");
        sb_appendf(&sb, "Content-Type: %s\r\n", content_type);
        sb_appendf(&sb, "\r\n");
        if (sb.truncated) {
            ESP_LOGW(TAG, "mail header too long — mail not sent");
            goto done;
        }
        ret = smtp_cmd(&c, "DATA\r\n", deadline);
        if (ret != 354) {
            ESP_LOGW(TAG, "SMTP DATA failed (code %d)", ret);
            goto done;
        }
        if (chan_write_all(&c, (unsigned char *)buf, (size_t)sb.len, deadline) != 0) {
            ESP_LOGW(TAG, "SMTP DATA headers write failed");
            goto done;
        }
    }
    if (body && body[0] && smtp_write_body(&c, body, deadline) != 0) {
        ESP_LOGW(TAG, "SMTP DATA body write failed");
        goto done;
    }

    ret = smtp_cmd(&c, "\r\n.\r\n", deadline);            // end of DATA
    if (ret < 200 || ret > 299) {
        ESP_LOGW(TAG, "SMTP end-of-DATA failed (code %d)", ret);
        goto done;
    }

    // Best-effort: the mail is already accepted, so a server that just
    // closes the connection is no failure worth a user-visible warning.
    ret = smtp_cmd(&c, "QUIT\r\n", deadline);
    if (ret < 200 || ret > 299)
        ESP_LOGD(TAG, "SMTP QUIT failed (code %d)", ret);
    ok = true;
    ESP_LOGI(TAG, "status mail sent to %s (%d of %d recipient(s))",
             to_hdr, accepted, rcpts.count);

done:
    if (c.tls_up) mbedtls_ssl_close_notify(&c.ssl);
    mbedtls_net_free(&c.net);
    mbedtls_ssl_free(&c.ssl);
    mbedtls_ssl_config_free(&conf);
    mbedtls_ctr_drbg_free(&drbg);
    mbedtls_entropy_free(&entropy);
    free(buf);
    if (!ok) ESP_LOGW(TAG, "status mail to %s failed", to_hdr);
    return ok;
}

// --- daily evaluation -----------------------------------------------

void mail_daily_flush(void)
{
    bool on_error = config_mail_on_error();
    bool on_spam  = config_mail_on_spam();
    if (!on_error && !on_spam) return;          // feature off
    if (!mail_configured()) return;
    if (!wifi_has_ip()) return;                 // retry next cycle

    // New ERROR since the last mail? (WARN alone never triggers, matching
    // logreport.c; WARNs still ride along as context once an error fires.)
    // The ring's copy on the heap (7.5 KB), not on the scheduler's stack.
    stats_error_t *errs = malloc(STATS_MAX_ERRORS * sizeof(*errs));
    if (!errs) return;
    int  n = on_error ? stats_snapshot_errors(errs, STATS_MAX_ERRORS) : 0;
    bool have_new_error = false;
    for (int i = 0; i < n; i++) {
        if (errs[i].at_us > s_reported_through_us
                && errs[i].level == ESP_LOG_ERROR) {
            have_new_error = true;
            break;
        }
    }

    // New calls since the last mail: every call above the mark kept in the
    // call file (mail_calls.h) — all verdicts, not just spam. mail_on_spam
    // is the "report calls" opt-in. mail_spam_only (issue #556, default off)
    // narrows the report to the calls the dongle blocked; then legitimate
    // calls alone send no mail. The mark advances past every call of the
    // window, reported or filtered out.
    bool spam_only = config_mail_spam_only();
    mail_calls_window_t window;
    mail_calls_window(spam_only, false, &window);
    bool have_new_calls = on_spam && window.new_calls > 0;

    if (!have_new_error && !have_new_calls) { free(errs); return; }   // nothing noteworthy
    if (esp_get_free_heap_size() < MAIL_MIN_FREE_HEAP) { free(errs); return; }

    char *body = malloc(MAIL_BODY_CAP);
    if (!body) { free(errs); return; }

    stats_counters_t cnt;
    stats_snapshot_counters(&cnt);

    // Advance the log high-water mark over every WARN/ERROR we append, so
    // the same window isn't re-mailed; the *trigger* is still a new ERROR.
    // The call table shows only the new calls (none when calls aren't
    // reported).
    int64_t newest_us = s_reported_through_us;
    mail_status_build_html(body, MAIL_BODY_CAP, &cnt,
                           have_new_calls ? &window : NULL, true, errs, n,
                           s_reported_through_us, &newest_us, have_new_error);
    free(errs);

    const char *subject =
        (have_new_error && have_new_calls)
            ? mail_i18n_str(spam_only ? "subj.error_and_spam" : "subj.error_and_calls")
      : have_new_error ? mail_i18n_str("subj.error")
      : mail_i18n_str(spam_only ? "subj.spam" : "subj.calls");

    if (mail_send(subject, MAIL_BODY_CT, body)) {
        // Advance the marks only after a confirmed send, so a failure
        // retries the same window next cycle.
        if (have_new_error) s_reported_through_us = newest_us;
        if (have_new_calls) mail_calls_mark_sent(&window);
    }
    free(body);
}

bool mail_send_test(void)
{
    char *body = malloc(MAIL_BODY_CAP);
    if (!body) return false;

    // Just send *the* status mail, built exactly like the daily flush —
    // summary, the recent calls, and the current WARN/ERROR log window (the
    // whole ring, hence since_us = 0). No test-only wording: the point of the
    // button is to see the real mail on demand.
    stats_counters_t cnt;
    stats_snapshot_counters(&cnt);
    stats_error_t *errs = malloc(STATS_MAX_ERRORS * sizeof(*errs));
    if (!errs) { free(body); return false; }
    int     n      = stats_snapshot_errors(errs, STATS_MAX_ERRORS);
    int64_t newest = 0;
    // Same call selection as the daily mail (issue #556), over the whole
    // list; the mark stays where it is.
    mail_calls_window_t window;
    mail_calls_window(config_mail_spam_only(), true, &window);

    mail_status_build_html(body, MAIL_BODY_CAP, &cnt, &window, false, errs, n,
                           0, &newest, true);
    free(errs);

    bool ok = mail_send(mail_i18n_str("subj.status"), MAIL_BODY_CT, body);
    free(body);
    return ok;
}

void mail_note_update(const char *version)
{
    if (!version || !version[0]) return;
    snprintf(s_update_version, sizeof(s_update_version), "%s", version);
    s_update_pending = true;
}

void mail_report_update(void)
{
    if (!s_update_pending) return;

    // Opted out (default on) or not configured: drop the latch. There is
    // nothing to send and nothing to retry — leaving it set would re-check
    // every daily run for the rest of this boot.
    if (!config_mail_on_update() || !mail_configured()) {
        s_update_pending = false;
        return;
    }
    if (!wifi_has_ip()) return;                 // retry next cycle
    if (esp_get_free_heap_size() < MAIL_MIN_FREE_HEAP) return;

    char *body = malloc(MAIL_BODY_CAP);
    if (!body) return;

    // One translatable sentence with two placeholders: {device} (the dongle
    // name, linked to its own web UI by LAN IP) and {version} (linked to the
    // release changelog). Build each fragment here — linked, or plain text
    // when the target is unavailable (no IP / a dev build with no changelog) —
    // then substitute into the localized message so the translation controls
    // word order. "PhoneBlock-Dongle" is a product name, not translated.
    char dev[128];
    size_t dl = 0;
    char ip[16];
    if (wifi_get_ip_str(ip, sizeof(ip))) {
        dl = append_str(dev, sizeof(dev), dl, "<a href=\"http://");
        dl = append_html_escaped(dev, sizeof(dev), dl, ip);
        dl = append_str(dev, sizeof(dev), dl,
            "/\" target=\"_blank\" rel=\"noopener\">PhoneBlock-Dongle</a>");
    } else {
        dl = append_str(dev, sizeof(dev), dl, "PhoneBlock-Dongle");
    }
    (void)dl;

    char ver[224];
    size_t vl = 0;
    char url[160];
    if (mail_changelog_url(s_update_version, url, sizeof(url))) {
        vl = append_str(ver, sizeof(ver), vl, "<a href=\"");
        vl = append_html_escaped(ver, sizeof(ver), vl, url);
        vl = append_str(ver, sizeof(ver), vl, "\" target=\"_blank\" rel=\"noopener\">");
        vl = append_html_escaped(ver, sizeof(ver), vl, s_update_version);
        vl = append_str(ver, sizeof(ver), vl, "</a>");
    } else {
        vl = append_html_escaped(ver, sizeof(ver), vl, s_update_version);
    }
    (void)vl;

    char sentence[512];
    mail_render(sentence, sizeof(sentence), mail_i18n_str("update.body"),
                "device", dev, "version", ver, (const char *)NULL);

    size_t len = append_str(body, MAIL_BODY_CAP, 0,
        "<html><body style=\"font-family:Arial,Helvetica,sans-serif;color:#222\"><p>");
    len = append_str(body, MAIL_BODY_CAP, len, sentence);
    append_str(body, MAIL_BODY_CAP, len, "</p></body></html>\n");

    // Clear the latch only after a confirmed send, so a transient SMTP
    // failure retries on the next mail run instead of losing the notice.
    if (mail_send(mail_i18n_str("subj.update"), MAIL_BODY_CT, body))
        s_update_pending = false;
    free(body);
}
