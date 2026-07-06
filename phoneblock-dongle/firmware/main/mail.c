#include "mail.h"

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

#include "mbedtls/base64.h"
#include "mbedtls/ctr_drbg.h"
#include "mbedtls/entropy.h"
#include "mbedtls/net_sockets.h"
#include "mbedtls/ssl.h"

#include "config.h"
#include "mail_html.h"
#include "smtp_body.h"
#include "stats.h"
#include "time_sync.h"
#include "wifi.h"

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
// the assembled header block). Comfortably larger than the longest line.
#define MAIL_LINE_CAP       640
// Upper bound for the assembled status body. The status mail is a single
// text/html document — summary + up to STATS_MAX_CALLS calls + the log
// window. text/html (not multipart/alternative) keeps the body — held in
// RAM across the heap-hungry TLS handshake — small. 8 KB is comfortable
// headroom for the worst case (10 calls + the full 32-entry log ring).
#define MAIL_BODY_CAP       8192

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

// High-water marks, RAM-only (reset on reboot — intended: a reboot's own
// crash ERROR then ships once more, which is the point). Independent of
// logreport.c's marks: this is a separate sink.
static int64_t  s_reported_through_us = 0;  // newest log at_us already mailed
static int64_t  s_reported_calls_us   = 0;  // newest call at_us already mailed

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
    return config_smtp_host()[0] && config_smtp_user()[0]
        && config_smtp_pass()[0] && config_smtp_to()[0];
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
    const char *to       = config_smtp_to();
    const bool  starttls = strcmp(config_smtp_security(), "starttls") == 0;
    // Stored 0 = "auto": the conventional submission port for the mode.
    int         port     = config_smtp_port();
    if (port <= 0) port = starttls ? 587 : 465;

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
    if (mbedtls_ssl_setup(&c.ssl, &conf) != 0) goto done;

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
    if (ret < 200 || ret > 299) goto done;
    ret = smtp_cmd(&c, "EHLO phoneblock-dongle\r\n", deadline);
    if (ret < 200 || ret > 299) goto done;

    if (starttls) {
        ret = smtp_cmd(&c, "STARTTLS\r\n", deadline);
        if (ret < 200 || ret > 299) goto done;
        if (do_handshake(&c, deadline) != 0) goto done;
        c.tls_up = true;
        ret = smtp_cmd(&c, "EHLO phoneblock-dongle\r\n", deadline);
        if (ret < 200 || ret > 299) goto done;
    }

    // AUTH LOGIN: username then password, each base64 on its own line.
    ret = smtp_cmd(&c, "AUTH LOGIN\r\n", deadline);
    if (ret != 334) goto done;
    if (mbedtls_base64_encode((unsigned char *)buf, MAIL_LINE_CAP - 2, &b64len,
                              (const unsigned char *)user, strlen(user)) != 0)
        goto done;
    buf[b64len] = '\r'; buf[b64len + 1] = '\n';
    ret = smtp_cmd_n(&c, buf, b64len + 2, deadline);
    if (ret != 334) goto done;
    if (mbedtls_base64_encode((unsigned char *)buf, MAIL_LINE_CAP - 2, &b64len,
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
    if (ret < 200 || ret > 299) goto done;
    snprintf(buf, MAIL_LINE_CAP, "RCPT TO:<%s>\r\n", to);
    ret = smtp_cmd(&c, buf, deadline);
    if (ret < 200 || ret > 299) goto done;
    ret = smtp_cmd(&c, "DATA\r\n", deadline);
    if (ret != 354) goto done;

    // Header block + body. UTF-8 body declared via Content-Type; the
    // Subject stays ASCII so no encoded-word is needed.
    {
        int hlen = snprintf(buf, MAIL_LINE_CAP,
            "From: PhoneBlock Dongle <%s>\r\n"
            "To: <%s>\r\n"
            "Subject: %s\r\n"
            "MIME-Version: 1.0\r\n"
            "Content-Type: %s\r\n"
            "\r\n",
            from, to, subject, content_type);
        if (hlen < 0 || chan_write_all(&c, (unsigned char *)buf, (size_t)hlen,
                                       deadline) != 0)
            goto done;
    }
    if (body && body[0] && smtp_write_body(&c, body, deadline) != 0)
        goto done;

    ret = smtp_cmd(&c, "\r\n.\r\n", deadline);            // end of DATA
    if (ret < 200 || ret > 299) goto done;

    smtp_cmd(&c, "QUIT\r\n", deadline);                   // best-effort
    ok = true;
    ESP_LOGI(TAG, "status mail sent to %s", to);

done:
    if (c.tls_up) mbedtls_ssl_close_notify(&c.ssl);
    mbedtls_net_free(&c.net);
    mbedtls_ssl_free(&c.ssl);
    mbedtls_ssl_config_free(&conf);
    mbedtls_ctr_drbg_free(&drbg);
    mbedtls_entropy_free(&entropy);
    free(buf);
    if (!ok) ESP_LOGW(TAG, "status mail to %s failed", to);
    return ok;
}

// --- body assembly (shared by the daily flush and the test mail) -----
//
// The body is a single text/html document. The pure string builders
// (append_str / append_html_escaped / append_url_encoded) live in
// mail_html.c so the injection-guarding escaping is host-tested; the
// helpers below add the device-specific formatting on top. All are bounded
// by `cap` and never write past it, so a full body truncates cleanly.

// Render a call/log instant as local wall-clock once SNTP is valid, else the
// "+Ns since boot" fallback. at_us is esp_timer uptime; see append_log_html.
static void format_event_time(int64_t at_us, char *out, size_t cap)
{
    if (time_sync_valid()) {
        int64_t now_us    = esp_timer_get_time();
        time_t  now_epoch = (time_t)time_sync_now_epoch();
        time_t  ev = now_epoch - (time_t)((now_us - at_us) / 1000000);
        struct tm lt;
        localtime_r(&ev, &lt);
        strftime(out, cap, "%Y-%m-%d %H:%M:%S", &lt);
    } else {
        snprintf(out, cap, "+%llds", (long long)(at_us / 1000000));
    }
}

// Verdict label matching the web UI's wording (status.calls.verdict.*).
static void verdict_label(const stats_call_t *c, char *out, size_t cap)
{
    if (c->white_listed)            snprintf(out, cap, "Whitelist");
    else if (c->black_listed)       snprintf(out, cap, "Blacklist");
    else if (c->verdict == VERDICT_SPAM)
                                    snprintf(out, cap, "SPAM (%d Stimmen)", c->votes);
    else if (c->suspected)          snprintf(out, cap, "SPAM-VERDACHT (%d Stimmen)", c->votes);
    else if (c->verdict == VERDICT_ERROR)
                                    snprintf(out, cap, "Fehler");
    else                            snprintf(out, cap, "durchgestellt");
}

// Append the summary block (device id, uptime, call counters) as HTML.
static size_t append_summary_html(char *body, size_t cap, size_t len,
                                  const stats_counters_t *cnt)
{
    int64_t up_s = esp_timer_get_time() / 1000000;
    char line[320];
    snprintf(line, sizeof(line),
        "<p style=\"font-size:14px;line-height:1.5\">"
        "Ger&auml;t: <b>%s</b><br>"
        "Laufzeit: %lldh %lldmin<br>"
        "Anrufe gesamt: %u &nbsp;|&nbsp; SPAM blockiert: %u &nbsp;|&nbsp; durchgestellt: %u"
        "</p>",
        config_device_id(),
        (long long)(up_s / 3600), (long long)((up_s % 3600) / 60),
        (unsigned)cnt->total_calls, (unsigned)cnt->spam_blocked,
        (unsigned)cnt->legitimate);
    return append_str(body, cap, len, line);
}

// Append the recent-calls table. Dialable numbers link to their PhoneBlock
// detail page (<base>/nums/<number>); the server normalises the number and
// redirects to its canonical form, exactly as the web UI's links do. Renders
// nothing when there are no calls.
static size_t append_calls_html(char *body, size_t cap, size_t len,
                                const stats_call_t *calls, int ncalls)
{
    if (ncalls <= 0) return len;
    const char *base = config_phoneblock_base_url();

    len = append_str(body, cap, len,
        "<h3 style=\"font-size:15px;margin:1.2em 0 .3em\">Letzte Anrufe</h3>"
        "<table cellpadding=\"5\" cellspacing=\"0\" "
        "style=\"border-collapse:collapse;font-size:14px\">"
        "<tr style=\"text-align:left;color:#555;border-bottom:1px solid #ccc\">"
        "<th>Zeit</th><th>Nummer</th><th>Name</th><th>Bewertung</th></tr>");

    for (int i = 0; i < ncalls && len < cap - 400; i++) {
        const stats_call_t *c = &calls[i];
        char when[24]; format_event_time(c->at_us, when, sizeof(when));
        char vl[48];   verdict_label(c, vl, sizeof(vl));
        // API-checked entries carry a PhoneBlock label/location; phone-book
        // and internal-code entries fall back to the raw number/display.
        bool checked     = c->label[0] != '\0';
        const char *num  = checked ? c->label    : c->number;
        const char *name = checked ? c->location : c->display;
        bool dialable = c->number[0] == '+' ||
                        (c->number[0] >= '0' && c->number[0] <= '9');

        len = append_str(body, cap, len, "<tr style=\"border-bottom:1px solid #eee\"><td>");
        len = append_html_escaped(body, cap, len, when);
        len = append_str(body, cap, len, "</td><td>");
        if (dialable && base && base[0]) {
            len = append_str(body, cap, len, "<a href=\"");
            len = append_str(body, cap, len, base);          // our own URL, no escaping needed
            len = append_str(body, cap, len, "/nums/");
            len = append_url_encoded(body, cap, len, c->number);
            len = append_str(body, cap, len, "\">");
            len = append_html_escaped(body, cap, len, num);
            len = append_str(body, cap, len, "</a>");
        } else {
            len = append_html_escaped(body, cap, len, num);
        }
        len = append_str(body, cap, len, "</td><td>");
        len = append_html_escaped(body, cap, len, name ? name : "");
        len = append_str(body, cap, len, "</td><td>");
        len = append_html_escaped(body, cap, len, vl);
        len = append_str(body, cap, len, "</td></tr>");
    }
    return append_str(body, cap, len, "</table>");
}

// Append the "Neue Meldungen im Protokoll" section as an HTML <pre> block:
// every WARN/ERROR in `errs` (newest first) with at_us > since_us. The header
// is emitted only if at least one entry qualifies. Updates *newest_us to the
// newest at_us appended, which the daily flush uses as its high-water mark.
//
// Log entries store esp_timer uptime (at_us), not wall-clock; format_event_time
// recovers the real instant once SNTP is valid (time() and esp_timer share the
// same monotonic source), far more useful in a mail read minutes-to-days later.
static size_t append_log_html(char *body, size_t cap, size_t len,
                              const stats_error_t *errs, int n,
                              int64_t since_us, int64_t *newest_us)
{
    bool header = false;
    for (int i = n - 1; i >= 0 && len < cap - 220; i--) {
        const stats_error_t *e = &errs[i];
        if (e->at_us <= since_us) continue;
        char lvl = (e->level == ESP_LOG_ERROR) ? 'E'
                 : (e->level == ESP_LOG_WARN)  ? 'W' : 0;
        if (!lvl) continue;
        if (!header) {
            len = append_str(body, cap, len,
                "<h3 style=\"font-size:15px;margin:1.2em 0 .3em\">Neue Meldungen im Protokoll</h3>"
                "<pre style=\"font-size:13px;white-space:pre-wrap;word-break:break-word;margin:0\">");
            header = true;
        }
        char when[24];
        format_event_time(e->at_us, when, sizeof(when));
        char prefix[56];
        snprintf(prefix, sizeof(prefix), "%c %s %s: ", lvl, when, e->tag);
        len = append_html_escaped(body, cap, len, prefix);   // tag is ours, but cheap to escape
        len = append_html_escaped(body, cap, len, e->message);
        len = append_str(body, cap, len, "\n");
        if (e->at_us > *newest_us) *newest_us = e->at_us;
    }
    if (header) len = append_str(body, cap, len, "</pre>");
    return len;
}

// Assemble the full text/html status body. `include_log` gates the log
// section (the daily flush only attaches it once a new ERROR has fired;
// the test mail always shows the whole ring). `new_calls` > 0 adds the
// "since the last mail" line. Returns the assembled length.
static size_t build_status_html(char *body, size_t cap,
                                const stats_counters_t *cnt,
                                const stats_call_t *calls, int ncalls,
                                const stats_error_t *errs, int nerr,
                                int64_t since_us, int64_t *newest_us,
                                bool include_log, int new_calls)
{
    size_t len = append_str(body, cap, 0,
        "<html><body style=\"font-family:Arial,Helvetica,sans-serif;color:#222\">"
        "<p>Statusmeldung deines PhoneBlock-Dongles.</p>");
    len = append_summary_html(body, cap, len, cnt);
    if (new_calls > 0) {
        char line[160];
        snprintf(line, sizeof(line), new_calls == 1
            ? "<p>Seit der letzten Meldung ist <b>1</b> neuer Anruf eingegangen.</p>"
            : "<p>Seit der letzten Meldung sind <b>%d</b> neue Anrufe eingegangen.</p>",
            new_calls);
        len = append_str(body, cap, len, line);
    }
    len = append_calls_html(body, cap, len, calls, ncalls);
    if (include_log)
        len = append_log_html(body, cap, len, errs, nerr, since_us, newest_us);
    return append_str(body, cap, len, "</body></html>\n");
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
    stats_error_t errs[STATS_MAX_ERRORS];
    int  n = on_error ? stats_snapshot_errors(errs, STATS_MAX_ERRORS) : 0;
    bool have_new_error = false;
    for (int i = 0; i < n; i++) {
        if (errs[i].at_us > s_reported_through_us
                && errs[i].level == ESP_LOG_ERROR) {
            have_new_error = true;
            break;
        }
    }

    // New calls since the last mail. The snapshot is newest-first and at_us
    // is monotonic, so the new calls are the leading run above the mark —
    // all verdicts, not just spam. mail_on_spam is the "report calls" opt-in.
    stats_call_t calls[STATS_MAX_CALLS];
    int ncalls = stats_snapshot_calls(calls, STATS_MAX_CALLS);
    int new_calls = 0;
    while (new_calls < ncalls && calls[new_calls].at_us > s_reported_calls_us)
        new_calls++;
    bool have_new_calls = on_spam && new_calls > 0;

    if (!have_new_error && !have_new_calls) return;   // nothing noteworthy
    if (esp_get_free_heap_size() < MAIL_MIN_FREE_HEAP) return;

    char *body = malloc(MAIL_BODY_CAP);
    if (!body) return;

    stats_counters_t cnt;
    stats_snapshot_counters(&cnt);

    // Advance the log high-water mark over every WARN/ERROR we append, so
    // the same window isn't re-mailed; the *trigger* is still a new ERROR.
    // The call table shows only the new calls (0 when calls aren't reported).
    int64_t newest_us = s_reported_through_us;
    build_status_html(body, MAIL_BODY_CAP, &cnt, calls,
                      have_new_calls ? new_calls : 0, errs, n,
                      s_reported_through_us, &newest_us, have_new_error,
                      have_new_calls ? new_calls : 0);

    const char *subject =
        (have_new_error && have_new_calls) ? "PhoneBlock-Dongle: Fehler und neue Anrufe"
      : have_new_error                     ? "PhoneBlock-Dongle: Fehler im Protokoll"
      :                                      "PhoneBlock-Dongle: Neue Anrufe";

    if (mail_send(subject, MAIL_BODY_CT, body)) {
        // Advance the marks only after a confirmed send, so a failure
        // retries the same window next cycle.
        if (have_new_error) s_reported_through_us = newest_us;
        if (have_new_calls) s_reported_calls_us = calls[0].at_us;
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
    stats_error_t errs[STATS_MAX_ERRORS];
    int     n      = stats_snapshot_errors(errs, STATS_MAX_ERRORS);
    stats_call_t calls[STATS_MAX_CALLS];
    int     ncalls = stats_snapshot_calls(calls, STATS_MAX_CALLS);
    int64_t newest = 0;

    build_status_html(body, MAIL_BODY_CAP, &cnt, calls, ncalls, errs, n,
                      0, &newest, true, 0);

    bool ok = mail_send("PhoneBlock-Dongle: Statusmeldung", MAIL_BODY_CT, body);
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

    // One sentence: "Die Firmware auf deinem <Dongle> wurde auf <Version>
    // aktualisiert." — the dongle name links to its own web UI (by IP, the
    // address that is guaranteed reachable on the LAN) and the version links
    // to that release's changelog. Each link degrades to plain text when its
    // target is unavailable (no IP / a dev build with no changelog page).
    size_t len = append_str(body, MAIL_BODY_CAP, 0,
        "<html><body style=\"font-family:Arial,Helvetica,sans-serif;color:#222\">"
        "<p>Die Firmware auf deinem ");

    char ip[16];
    if (wifi_get_ip_str(ip, sizeof(ip))) {
        len = append_str(body, MAIL_BODY_CAP, len, "<a href=\"http://");
        len = append_html_escaped(body, MAIL_BODY_CAP, len, ip);
        len = append_str(body, MAIL_BODY_CAP, len,
            "/\" target=\"_blank\" rel=\"noopener\">PhoneBlock-Dongle</a>");
    } else {
        len = append_str(body, MAIL_BODY_CAP, len, "PhoneBlock-Dongle");
    }

    len = append_str(body, MAIL_BODY_CAP, len, " wurde auf ");

    char url[160];
    if (mail_changelog_url(s_update_version, url, sizeof(url))) {
        len = append_str(body, MAIL_BODY_CAP, len, "<a href=\"");
        len = append_html_escaped(body, MAIL_BODY_CAP, len, url);
        len = append_str(body, MAIL_BODY_CAP, len,
            "\" target=\"_blank\" rel=\"noopener\">Version ");
        len = append_html_escaped(body, MAIL_BODY_CAP, len, s_update_version);
        len = append_str(body, MAIL_BODY_CAP, len, "</a>");
    } else {
        len = append_str(body, MAIL_BODY_CAP, len, "Version ");
        len = append_html_escaped(body, MAIL_BODY_CAP, len, s_update_version);
    }

    append_str(body, MAIL_BODY_CAP, len, " aktualisiert.</p></body></html>\n");

    // Clear the latch only after a confirmed send, so a transient SMTP
    // failure retries on the next mail run instead of losing the notice.
    if (mail_send("PhoneBlock-Dongle: Firmware aktualisiert", MAIL_BODY_CT, body))
        s_update_pending = false;
    free(body);
}
