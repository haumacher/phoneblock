#include "tr064.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "esp_log.h"
#include "esp_http_client.h"
#include "esp_random.h"
#include "esp_mac.h"
#include "mbedtls/md5.h"

static const char *TAG = "tr064";

#define X_VOIP_SERVICE   "urn:dslforum-org:service:X_VoIP:1"
#define X_VOIP_CONTROL   "/upnp/control/x_voip"

#define SOAP_ENVELOPE_CAP 2048
#define SOAP_RESPONSE_CAP 4096

// ---------------------------------------------------------------------------
// MD5 → lowercase hex (mbedtls wrapper)
// ---------------------------------------------------------------------------

static void md5_hex(const char *input, char out_hex[33])
{
    unsigned char digest[16];
    mbedtls_md5((const unsigned char *)input, strlen(input), digest);
    static const char hex[] = "0123456789abcdef";
    for (int i = 0; i < 16; i++) {
        out_hex[i * 2]     = hex[digest[i] >> 4];
        out_hex[i * 2 + 1] = hex[digest[i] & 0x0f];
    }
    out_hex[32] = '\0';
}

// response = MD5( MD5(user:realm:pass) : nonce ) — per AVM's
// soap-authentication.org/digest/2001/10 scheme. MD5, not SHA.
static void compute_auth_response(const char *user, const char *realm,
                                  const char *pass, const char *nonce,
                                  char out_hex[33])
{
    char secret_input[256];
    char secret_hex[33];
    snprintf(secret_input, sizeof(secret_input), "%s:%s:%s", user, realm, pass);
    md5_hex(secret_input, secret_hex);

    char final_input[128];
    snprintf(final_input, sizeof(final_input), "%s:%s", secret_hex, nonce);
    md5_hex(final_input, out_hex);
}

// ---------------------------------------------------------------------------
// Minimal XML tag text extractor
//
// Good enough for the handful of well-formed Fritz!Box responses we
// care about. Finds the first element whose local name matches `tag`
// (ignoring namespace prefix) and copies its text content into out.
// Returns -1 if the tag wasn't found.
// ---------------------------------------------------------------------------

static int xml_find_text(const char *xml, const char *tag,
                         char *out, size_t cap)
{
    size_t name_len = strlen(tag);
    const char *p = xml;
    while ((p = strchr(p, '<')) != NULL) {
        if (p[1] == '/' || p[1] == '?' || p[1] == '!') { p++; continue; }
        p++;
        // Skip any "prefix:" before the local name.
        const char *tag_end = p;
        while (*tag_end && *tag_end != '>' && *tag_end != ' '
               && *tag_end != '\t' && *tag_end != '/') tag_end++;
        if (*tag_end == '\0') return -1;
        const char *local = p;
        const char *colon = memchr(p, ':', tag_end - p);
        if (colon) local = colon + 1;
        if ((size_t)(tag_end - local) == name_len
            && strncmp(local, tag, name_len) == 0) {
            // Found the opening tag. Skip attributes to the '>'.
            const char *gt = strchr(tag_end, '>');
            if (!gt) return -1;
            if (gt[-1] == '/') {
                // Self-closing empty element.
                out[0] = '\0';
                return 0;
            }
            const char *content_start = gt + 1;
            // Find first "</…localname>" after content_start.
            const char *q = content_start;
            while ((q = strstr(q, "</")) != NULL) {
                q += 2;
                const char *qgt = strchr(q, '>');
                if (!qgt) return -1;
                const char *qlocal = q;
                const char *qcolon = memchr(q, ':', qgt - q);
                if (qcolon) qlocal = qcolon + 1;
                if ((size_t)(qgt - qlocal) == name_len
                    && strncmp(qlocal, tag, name_len) == 0) {
                    size_t content_len = (q - 2) - content_start;
                    if (content_len >= cap) content_len = cap - 1;
                    memcpy(out, content_start, content_len);
                    out[content_len] = '\0';
                    return (int)content_len;
                }
                q = qgt + 1;
            }
            return -1;
        }
        p = tag_end;
    }
    return -1;
}

static void xml_escape(const char *in, char *out, size_t cap)
{
    size_t o = 0;
    for (const char *p = in; *p && o + 6 < cap; p++) {
        switch (*p) {
            case '&':  strcpy(out + o, "&amp;");  o += 5; break;
            case '<':  strcpy(out + o, "&lt;");   o += 4; break;
            case '>':  strcpy(out + o, "&gt;");   o += 4; break;
            case '"':  strcpy(out + o, "&quot;"); o += 6; break;
            case '\'': strcpy(out + o, "&apos;"); o += 6; break;
            default:   out[o++] = *p; break;
        }
    }
    out[o] = '\0';
}

// ---------------------------------------------------------------------------
// HTTP POST of a SOAP envelope; accumulates response body in a heap buffer.
// ---------------------------------------------------------------------------

typedef struct {
    char *buf;
    int   len;
    int   cap;
} resp_buf_t;

static esp_err_t http_evt_cb(esp_http_client_event_t *evt)
{
    resp_buf_t *r = evt->user_data;
    if (evt->event_id == HTTP_EVENT_ON_DATA
        && !esp_http_client_is_chunked_response(evt->client)) {
        int remaining = r->cap - r->len - 1;
        int copy = evt->data_len < remaining ? evt->data_len : remaining;
        if (copy > 0) {
            memcpy(r->buf + r->len, evt->data, copy);
            r->len += copy;
            r->buf[r->len] = '\0';
        }
    }
    return ESP_OK;
}

static esp_err_t post_soap(const char *url, const char *soap_action,
                           const char *body, char *resp, int resp_cap,
                           int *out_status)
{
    resp_buf_t rb = { .buf = resp, .len = 0, .cap = resp_cap };
    resp[0] = '\0';

    esp_http_client_config_t cfg = {
        .url            = url,
        .method         = HTTP_METHOD_POST,
        .event_handler  = http_evt_cb,
        .user_data      = &rb,
        .timeout_ms     = 5000,
    };
    esp_http_client_handle_t c = esp_http_client_init(&cfg);
    if (!c) return ESP_FAIL;

    esp_http_client_set_header(c, "Content-Type",
                               "text/xml; charset=\"utf-8\"");
    esp_http_client_set_header(c, "SoapAction", soap_action);
    esp_http_client_set_post_field(c, body, (int)strlen(body));

    esp_err_t err = esp_http_client_perform(c);
    int status = esp_http_client_get_status_code(c);
    esp_http_client_cleanup(c);

    if (out_status) *out_status = status;
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "POST %s: %s", url, esp_err_to_name(err));
        return err;
    }
    return ESP_OK;
}

// ---------------------------------------------------------------------------
// SOAP envelope builders
// ---------------------------------------------------------------------------

static void build_init_challenge(char *out, size_t cap,
                                 const char *action,
                                 const char *user,
                                 const char *args_xml)
{
    snprintf(out, cap,
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
        "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\""
        " s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">"
        "<s:Header>"
        "<h:InitChallenge xmlns:h=\"http://soap-authentication.org/digest/2001/10/\""
        " s:mustUnderstand=\"1\"><UserID>%s</UserID></h:InitChallenge>"
        "</s:Header>"
        "<s:Body><u:%s xmlns:u=\"" X_VOIP_SERVICE "\">%s</u:%s></s:Body>"
        "</s:Envelope>",
        user, action, args_xml ? args_xml : "", action);
}

static void build_client_auth(char *out, size_t cap,
                              const char *action,
                              const char *user, const char *realm,
                              const char *nonce, const char *auth_hex,
                              const char *args_xml)
{
    snprintf(out, cap,
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
        "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\""
        " s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">"
        "<s:Header>"
        "<h:ClientAuth xmlns:h=\"http://soap-authentication.org/digest/2001/10/\""
        " s:mustUnderstand=\"1\">"
        "<Nonce>%s</Nonce><Auth>%s</Auth><UserID>%s</UserID><Realm>%s</Realm>"
        "</h:ClientAuth>"
        "</s:Header>"
        "<s:Body><u:%s xmlns:u=\"" X_VOIP_SERVICE "\">%s</u:%s></s:Body>"
        "</s:Envelope>",
        nonce, auth_hex, user, realm,
        action, args_xml ? args_xml : "", action);
}

// ---------------------------------------------------------------------------
// Two-step authenticated call:
//   1) POST InitChallenge → expect 503 + nonce + realm in header.
//   2) Compute response, POST ClientAuth → expect 200 + action result.
// ---------------------------------------------------------------------------

static esp_err_t call_action(const char *url,
                             const char *admin_user, const char *admin_pass,
                             const char *action, const char *args_xml,
                             char *resp, int resp_cap,
                             int *out_err_code,
                             char *out_err_msg, size_t err_msg_cap)
{
    char *env = malloc(SOAP_ENVELOPE_CAP);
    if (!env) return ESP_ERR_NO_MEM;

    char soap_action[128];
    snprintf(soap_action, sizeof(soap_action),
             X_VOIP_SERVICE "#%s", action);

    // --- Step 1: InitChallenge ---
    build_init_challenge(env, SOAP_ENVELOPE_CAP, action, admin_user, args_xml);
    int status = 0;
    esp_err_t err = post_soap(url, soap_action, env, resp, resp_cap, &status);
    if (err != ESP_OK) { free(env); return err; }
    ESP_LOGI(TAG, "InitChallenge %s → HTTP %d, %d bytes", action, status, (int)strlen(resp));

    char nonce[64] = "";
    char realm[64] = "";
    if (xml_find_text(resp, "Nonce", nonce, sizeof(nonce)) < 0
        || xml_find_text(resp, "Realm", realm, sizeof(realm)) < 0) {
        ESP_LOGE(TAG, "%s: no Nonce/Realm in InitChallenge response (HTTP %d):\n%s",
                 action, status, resp);
        free(env);
        return ESP_FAIL;
    }
    ESP_LOGI(TAG, "%s challenge: realm=\"%s\" nonce=\"%s\"", action, realm, nonce);

    // --- Step 2: ClientAuth with computed response ---
    char auth_hex[33];
    compute_auth_response(admin_user, realm, admin_pass, nonce, auth_hex);

    build_client_auth(env, SOAP_ENVELOPE_CAP, action,
                      admin_user, realm, nonce, auth_hex, args_xml);
    resp[0] = '\0';
    err = post_soap(url, soap_action, env, resp, resp_cap, &status);
    free(env);
    if (err != ESP_OK) return err;
    ESP_LOGI(TAG, "ClientAuth %s → HTTP %d, %d bytes", action, status, (int)strlen(resp));

    if (status != 200) {
        // Typically 503 with faultstring "Unauthenticated" on bad password,
        // or 500 with UPnPError errorCode (e.g. 866 = 2FA required).
        char fault[128]     = "";
        char err_code_s[16] = "";
        char err_desc[128]  = "";
        xml_find_text(resp, "faultstring",       fault,      sizeof(fault));
        xml_find_text(resp, "errorCode",         err_code_s, sizeof(err_code_s));
        xml_find_text(resp, "errorDescription",  err_desc,   sizeof(err_desc));
        ESP_LOGE(TAG, "%s rejected: HTTP %d, fault='%s' code=%s desc='%s'\n%s",
                 action, status, fault, err_code_s, err_desc, resp);
        if (out_err_code) *out_err_code = atoi(err_code_s);
        if (out_err_msg && err_msg_cap) {
            strncpy(out_err_msg, err_desc[0] ? err_desc : fault, err_msg_cap - 1);
            out_err_msg[err_msg_cap - 1] = '\0';
        }
        return ESP_FAIL;
    }
    return ESP_OK;
}

// ---------------------------------------------------------------------------
// Higher-level helpers
// ---------------------------------------------------------------------------

static esp_err_t get_num_clients(const char *url,
                                 const char *admin_user, const char *admin_pass,
                                 int *out_count,
                                 int *out_err_code,
                                 char *out_err_msg, size_t err_msg_cap)
{
    char *resp = malloc(SOAP_RESPONSE_CAP);
    if (!resp) return ESP_ERR_NO_MEM;
    esp_err_t err = call_action(url, admin_user, admin_pass,
                                "X_AVM-DE_GetNumberOfClients", NULL,
                                resp, SOAP_RESPONSE_CAP,
                                out_err_code, out_err_msg, err_msg_cap);
    if (err != ESP_OK) { free(resp); return err; }

    char count_str[16] = "";
    if (xml_find_text(resp, "NewX_AVM-DE_NumberOfClients",
                      count_str, sizeof(count_str)) < 0) {
        ESP_LOGE(TAG, "GetNumberOfClients: no count in response:\n%s", resp);
        free(resp);
        return ESP_FAIL;
    }
    *out_count = atoi(count_str);
    free(resp);
    return ESP_OK;
}

static void gen_random_password(char *out, size_t len)
{
    // Alphanumeric only — Fritz!Box's SIP-client password validator
    // rejects punctuation (errorCode 803 "Argument contains invalid
    // characters"). Matches the Dart reference library's _generatePassword.
    static const char alpha[] =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    size_t n = strlen(alpha);
    for (size_t i = 0; i < len - 1; i++) {
        out[i] = alpha[esp_random() % n];
    }
    out[len - 1] = '\0';
}

static void gen_sip_username(char *out, size_t cap)
{
    uint8_t mac[6];
    esp_read_mac(mac, ESP_MAC_WIFI_STA);
    snprintf(out, cap, "phoneblock-%02x%02x%02x", mac[3], mac[4], mac[5]);
}

// ---------------------------------------------------------------------------
// Public entry point
// ---------------------------------------------------------------------------

esp_err_t tr064_provision_sip_client(const char *host, int port,
                                     const char *admin_user, const char *admin_pass,
                                     const char *phone_name,
                                     tr064_sip_result_t *out)
{
    if (!host || !admin_user || !admin_pass || !out) return ESP_ERR_INVALID_ARG;
    out->error_code = 0;
    out->error_message[0] = '\0';

    char url[96];
    snprintf(url, sizeof(url), "http://%s:%d" X_VOIP_CONTROL, host, port);
    ESP_LOGI(TAG, "provisioning SIP client on %s (user=%s)", url, admin_user);

    // 1) Learn the next free client index.
    int num_clients = 0;
    esp_err_t err = get_num_clients(url, admin_user, admin_pass, &num_clients,
                                    &out->error_code,
                                    out->error_message, sizeof(out->error_message));
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "GetNumberOfClients failed");
        return err;
    }
    ESP_LOGI(TAG, "Fritz!Box has %d existing SIP clients", num_clients);

    // 2) Generate credentials.
    char user_buf[32];
    char pass_buf[24];   // 23 chars + NUL
    gen_sip_username(user_buf, sizeof(user_buf));
    gen_random_password(pass_buf, sizeof(pass_buf));

    // 3) SetClient4 at index num_clients (creates a new entry). All
    //    string-shaped values get XML-escaped on the way in; user_buf
    //    and pass_buf are currently alphanumeric so this is defensive,
    //    but phone_name may come from user input.
    char phone_name_esc[96];
    char user_esc[64];
    char pass_esc[96];
    xml_escape(user_buf,  user_esc,       sizeof(user_esc));
    xml_escape(pass_buf,  pass_esc,       sizeof(pass_esc));
    xml_escape(phone_name ? phone_name : "Answerbot",
               phone_name_esc, sizeof(phone_name_esc));

    char *args = malloc(768);
    if (!args) return ESP_ERR_NO_MEM;
    snprintf(args, 768,
        "<NewX_AVM-DE_ClientIndex>%d</NewX_AVM-DE_ClientIndex>"
        "<NewX_AVM-DE_ClientPassword>%s</NewX_AVM-DE_ClientPassword>"
        "<NewX_AVM-DE_ClientUsername>%s</NewX_AVM-DE_ClientUsername>"
        "<NewX_AVM-DE_PhoneName>%s</NewX_AVM-DE_PhoneName>"
        "<NewX_AVM-DE_ClientId></NewX_AVM-DE_ClientId>"
        "<NewX_AVM-DE_OutGoingNumber></NewX_AVM-DE_OutGoingNumber>"
        "<NewX_AVM-DE_InComingNumbers></NewX_AVM-DE_InComingNumbers>",
        num_clients, pass_esc, user_esc, phone_name_esc);

    char *resp = malloc(SOAP_RESPONSE_CAP);
    if (!resp) { free(args); return ESP_ERR_NO_MEM; }
    err = call_action(url, admin_user, admin_pass,
                      "X_AVM-DE_SetClient4", args,
                      resp, SOAP_RESPONSE_CAP,
                      &out->error_code,
                      out->error_message, sizeof(out->error_message));
    free(args);
    if (err != ESP_OK) { free(resp); return err; }

    char internal_num[16] = "";
    xml_find_text(resp, "NewX_AVM-DE_InternalNumber",
                  internal_num, sizeof(internal_num));
    free(resp);

    strncpy(out->sip_user, user_buf,     sizeof(out->sip_user) - 1);
    out->sip_user[sizeof(out->sip_user) - 1] = '\0';
    strncpy(out->sip_pass, pass_buf,     sizeof(out->sip_pass) - 1);
    out->sip_pass[sizeof(out->sip_pass) - 1] = '\0';
    strncpy(out->internal_number, internal_num,
            sizeof(out->internal_number) - 1);
    out->internal_number[sizeof(out->internal_number) - 1] = '\0';

    ESP_LOGI(TAG, "provisioned user='%s' internal='%s'",
             out->sip_user, out->internal_number);
    return ESP_OK;
}
