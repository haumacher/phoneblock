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
// Upper bound for the assembled status body (summary + new log window).
#define MAIL_BODY_CAP       4096

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
static uint32_t s_reported_spam       = 0;  // spam_blocked count at last mail

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
static bool mail_send(const char *subject, const char *body)
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
            "Content-Type: text/plain; charset=utf-8\r\n"
            "\r\n",
            from, to, subject);
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

// Append the status summary block — device id, uptime, call counters —
// at body[len]. Returns the new length (clamped to cap on truncation).
static size_t append_summary(char *body, size_t cap, size_t len,
                             const stats_counters_t *cnt)
{
    if (len >= cap) return len;
    int64_t up_s = esp_timer_get_time() / 1000000;
    int w = snprintf(body + len, cap - len,
        "Gerät: %s\n"
        "Laufzeit: %lldh %lldmin\n"
        "Anrufe gesamt: %u | SPAM blockiert: %u | durchgestellt: %u\n",
        config_device_id(),
        (long long)(up_s / 3600), (long long)((up_s % 3600) / 60),
        (unsigned)cnt->total_calls, (unsigned)cnt->spam_blocked,
        (unsigned)cnt->legitimate);
    if (w < 0) return len;
    len += (size_t)w;
    return len > cap ? cap : len;
}

// Append the "Neue Meldungen im Protokoll" section: every WARN/ERROR in
// `errs` (newest first) with at_us > since_us, formatted one per line. The
// header is written only if at least one entry qualifies, so an empty
// window adds nothing. Returns the new length; updates *newest_us to the
// newest at_us appended, which the daily flush uses as its high-water mark.
static size_t append_log_window(char *body, size_t cap, size_t len,
                                const stats_error_t *errs, int n,
                                int64_t since_us, int64_t *newest_us)
{
    // Log entries store esp_timer uptime (at_us), not wall-clock. Once the
    // SNTP clock is valid we recover each entry's real instant —
    // now_epoch - (now_us - at_us) — since time() and esp_timer share the
    // same monotonic source. A mail is read minutes-to-days later, so an
    // absolute local timestamp is far more useful than "+Ns since boot";
    // keep the uptime form only as a pre-sync fallback.
    int64_t now_us    = esp_timer_get_time();
    bool    clock_ok  = time_sync_valid();
    time_t  now_epoch = clock_ok ? (time_t)time_sync_now_epoch() : 0;

    bool header = false;
    for (int i = n - 1; i >= 0 && len < cap - 160; i--) {
        const stats_error_t *e = &errs[i];
        if (e->at_us <= since_us) continue;
        char lvl = (e->level == ESP_LOG_ERROR) ? 'E'
                 : (e->level == ESP_LOG_WARN)  ? 'W' : 0;
        if (!lvl) continue;
        if (!header) {
            int hw = snprintf(body + len, cap - len,
                              "\nNeue Meldungen im Protokoll:\n");
            if (hw < 0) break;
            len += (size_t)hw;
            header = true;
        }
        char when[24];
        if (clock_ok) {
            time_t ev = now_epoch - (time_t)((now_us - e->at_us) / 1000000);
            struct tm lt;
            localtime_r(&ev, &lt);
            strftime(when, sizeof(when), "%Y-%m-%d %H:%M:%S", &lt);
        } else {
            snprintf(when, sizeof(when), "+%llds",
                     (long long)(e->at_us / 1000000));
        }
        int w = snprintf(body + len, cap - len, "%c %s %s: %s\n",
                         lvl, when, e->tag, e->message);
        if (w < 0 || (size_t)w >= cap - len) break;
        len += (size_t)w;
        if (e->at_us > *newest_us) *newest_us = e->at_us;
    }
    return len;
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

    stats_counters_t cnt;
    stats_snapshot_counters(&cnt);
    bool have_new_spam = on_spam && (cnt.spam_blocked > s_reported_spam);

    if (!have_new_error && !have_new_spam) return;   // nothing noteworthy
    if (esp_get_free_heap_size() < MAIL_MIN_FREE_HEAP) return;

    char *body = malloc(MAIL_BODY_CAP);
    if (!body) return;

    size_t len = snprintf(body, MAIL_BODY_CAP,
        "Statusmeldung deines PhoneBlock-Dongles.\n\n");
    len = append_summary(body, MAIL_BODY_CAP, len, &cnt);

    if (have_new_spam && len < MAIL_BODY_CAP) {
        len += snprintf(body + len, MAIL_BODY_CAP - len,
            "\nSeit der letzten Meldung wurden %u SPAM-Anrufe blockiert.\n",
            (unsigned)(cnt.spam_blocked - s_reported_spam));
    }

    // Advance the log high-water mark over every WARN/ERROR we append, so
    // the same window isn't re-mailed; the *trigger* is still a new ERROR.
    int64_t newest_us = s_reported_through_us;
    if (have_new_error)
        len = append_log_window(body, MAIL_BODY_CAP, len, errs, n,
                                s_reported_through_us, &newest_us);

    const char *subject =
        (have_new_error && have_new_spam) ? "PhoneBlock-Dongle: Fehler und SPAM-Anrufe"
      : have_new_error                    ? "PhoneBlock-Dongle: Fehler im Protokoll"
      :                                     "PhoneBlock-Dongle: SPAM-Anrufe blockiert";

    if (mail_send(subject, body)) {
        // Advance the marks only after a confirmed send, so a failure
        // retries the same window next cycle.
        if (have_new_error) s_reported_through_us = newest_us;
        if (have_new_spam)  s_reported_spam = cnt.spam_blocked;
    }
    free(body);
}

bool mail_send_test(void)
{
    char *body = malloc(MAIL_BODY_CAP);
    if (!body) return false;

    // Just send *the* status mail, built exactly like the daily flush —
    // summary plus the current WARN/ERROR log window (the whole ring, hence
    // since_us = 0). No test-only wording: the point of the button is to
    // see the real mail on demand.
    stats_counters_t cnt;
    stats_snapshot_counters(&cnt);
    stats_error_t errs[STATS_MAX_ERRORS];
    int     n      = stats_snapshot_errors(errs, STATS_MAX_ERRORS);
    int64_t newest = 0;

    size_t len = snprintf(body, MAIL_BODY_CAP,
        "Statusmeldung deines PhoneBlock-Dongles.\n\n");
    len = append_summary(body, MAIL_BODY_CAP, len, &cnt);
    len = append_log_window(body, MAIL_BODY_CAP, len, errs, n, 0, &newest);

    bool ok = mail_send("PhoneBlock-Dongle: Statusmeldung", body);
    free(body);
    return ok;
}
