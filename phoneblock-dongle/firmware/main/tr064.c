#include "tr064.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "esp_log.h"
#include "esp_http_client.h"
#include "esp_random.h"
#include "esp_mac.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "mbedtls/md5.h"

#include "config.h"
#include "http_util.h"
#include "tr064_parse.h"

// Short aliases for the parser helpers — keeps call sites compact.
#define xml_find_text(...)       tr064_xml_find_text(__VA_ARGS__)
#define xml_unescape_inplace(...) tr064_xml_unescape_inplace(__VA_ARGS__)
#define xml_escape(...)          tr064_xml_escape(__VA_ARGS__)
#define pick_default_user(...)   tr064_pick_default_user(__VA_ARGS__)

static const char *TAG = "tr064";

#define X_VOIP_SERVICE   "urn:dslforum-org:service:X_VoIP:1"
#define X_VOIP_CONTROL   "/upnp/control/x_voip"
#define X_AUTH_SERVICE   "urn:dslforum-org:service:X_AVM-DE_Auth:1"
#define X_AUTH_CONTROL   "/upnp/control/x_auth"
#define LANSEC_SERVICE   "urn:dslforum-org:service:LANConfigSecurity:1"
#define LANSEC_CONTROL   "/upnp/control/lanconfigsecurity"
#define APPSETUP_SERVICE "urn:dslforum-org:service:X_AVM-DE_AppSetup:1"
#define APPSETUP_CONTROL "/upnp/control/x_appsetup"
#define ONTEL_SERVICE    "urn:dslforum-org:service:X_AVM-DE_OnTel:1"
#define ONTEL_CONTROL    "/upnp/control/x_contact"

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

// Parser helpers (tr064_xml_find_text, tr064_xml_unescape_inplace,
// tr064_xml_escape, tr064_pick_default_user) live in tr064_parse.c so
// the host test harness can exercise them — see tr064_parse.h for the
// interface and the macro aliases at the top of this file.

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
    esp_err_t err    = ESP_FAIL;
    int       status = 0;

    // ESP_ERR_HTTP_CONNECT here often means lwip's socket pool is
    // momentarily exhausted (browser polling /api/status in parallel
    // hogs several slots). Give it up to three attempts with a short
    // back-off; a real network failure still surfaces after that.
    for (int attempt = 0; attempt < 3; attempt++) {
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

        http_util_set_user_agent(c);
        esp_http_client_set_header(c, "Content-Type",
                                   "text/xml; charset=\"utf-8\"");
        esp_http_client_set_header(c, "SoapAction", soap_action);
        esp_http_client_set_post_field(c, body, (int)strlen(body));

        err    = esp_http_client_perform(c);
        status = esp_http_client_get_status_code(c);
        esp_http_client_cleanup(c);

        if (err == ESP_OK) break;
        if (err != ESP_ERR_HTTP_CONNECT || attempt == 2) {
            ESP_LOGE(TAG, "POST %s: %s", url, esp_err_to_name(err));
            break;
        }
        ESP_LOGW(TAG, "POST %s: %s, retry %d/2", url,
                 esp_err_to_name(err), attempt + 1);
        vTaskDelay(pdMS_TO_TICKS(250));
    }

    if (out_status) *out_status = status;
    return err;
}

// ---------------------------------------------------------------------------
// SOAP envelope builders
// ---------------------------------------------------------------------------

// Optional <avm:token> SOAP header, needed for 2FA-protected calls
// after the user completed the second factor.
static int append_token_header(char *buf, size_t cap, const char *token)
{
    if (!token || !*token) return 0;
    return snprintf(buf, cap,
        "<avm:token xmlns:avm=\"avm.de\" s:mustUnderstand=\"1\">%s</avm:token>",
        token);
}

static void build_init_challenge(char *out, size_t cap,
                                 const char *service,
                                 const char *action,
                                 const char *user,
                                 const char *args_xml,
                                 const char *token_2fa)
{
    char token_hdr[160] = "";
    append_token_header(token_hdr, sizeof(token_hdr), token_2fa);
    snprintf(out, cap,
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
        "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\""
        " s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">"
        "<s:Header>"
        "<h:InitChallenge xmlns:h=\"http://soap-authentication.org/digest/2001/10/\""
        " s:mustUnderstand=\"1\"><UserID>%s</UserID></h:InitChallenge>"
        "%s"
        "</s:Header>"
        "<s:Body><u:%s xmlns:u=\"%s\">%s</u:%s></s:Body>"
        "</s:Envelope>",
        user, token_hdr, action, service, args_xml ? args_xml : "", action);
}

static void build_client_auth(char *out, size_t cap,
                              const char *service,
                              const char *action,
                              const char *user, const char *realm,
                              const char *nonce, const char *auth_hex,
                              const char *args_xml,
                              const char *token_2fa)
{
    char token_hdr[160] = "";
    append_token_header(token_hdr, sizeof(token_hdr), token_2fa);
    snprintf(out, cap,
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
        "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\""
        " s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">"
        "<s:Header>"
        "<h:ClientAuth xmlns:h=\"http://soap-authentication.org/digest/2001/10/\""
        " s:mustUnderstand=\"1\">"
        "<Nonce>%s</Nonce><Auth>%s</Auth><UserID>%s</UserID><Realm>%s</Realm>"
        "</h:ClientAuth>"
        "%s"
        "</s:Header>"
        "<s:Body><u:%s xmlns:u=\"%s\">%s</u:%s></s:Body>"
        "</s:Envelope>",
        nonce, auth_hex, user, realm, token_hdr,
        action, service, args_xml ? args_xml : "", action);
}

// ---------------------------------------------------------------------------
// Two-step authenticated call:
//   1) POST InitChallenge → expect 503 + nonce + realm in header.
//   2) Compute response, POST ClientAuth → expect 200 + action result.
// ---------------------------------------------------------------------------

static esp_err_t call_action(const char *url,
                             const char *service,
                             const char *admin_user, const char *admin_pass,
                             const char *action, const char *args_xml,
                             const char *token_2fa,
                             char *resp, int resp_cap,
                             int *out_err_code,
                             char *out_err_msg, size_t err_msg_cap)
{
    char *env = malloc(SOAP_ENVELOPE_CAP);
    if (!env) return ESP_ERR_NO_MEM;

    char soap_action[160];
    snprintf(soap_action, sizeof(soap_action), "%s#%s", service, action);

    // Sentinel setter for the non-UPnPError failure paths, so the web
    // layer can show a specific hint instead of a generic "check
    // address, user, password" catch-all.
    #define SET_SENTINEL(code, msg) do {                                   \
        if (out_err_code) *out_err_code = (code);                          \
        if (out_err_msg && err_msg_cap) {                                  \
            strncpy(out_err_msg, (msg), err_msg_cap - 1);                  \
            out_err_msg[err_msg_cap - 1] = '\0';                           \
        }                                                                  \
    } while (0)

    // --- Step 1: InitChallenge ---
    build_init_challenge(env, SOAP_ENVELOPE_CAP, service, action,
                         admin_user, args_xml, token_2fa);
    int status = 0;
    esp_err_t err = post_soap(url, soap_action, env, resp, resp_cap, &status);
    if (err != ESP_OK) {
        SET_SENTINEL(TR064_ERR_TRANSPORT, esp_err_to_name(err));
        free(env);
        return err;
    }
    ESP_LOGI(TAG, "InitChallenge %s → HTTP %d, %d bytes", action, status, (int)strlen(resp));

    char nonce[64] = "";
    char realm[64] = "";
    if (xml_find_text(resp, "Nonce", nonce, sizeof(nonce)) < 0
        || xml_find_text(resp, "Realm", realm, sizeof(realm)) < 0) {
        // Some Fritz!Box services (observed on LANConfigSecurity's
        // X_AVM-DE_GetUserList) answer the InitChallenge directly with
        // the action result — no challenge at all. Treat that as
        // success when the expected <actionResponse> body is present.
        char resp_tag[64];
        snprintf(resp_tag, sizeof(resp_tag), "%sResponse", action);
        if (status == 200 && strstr(resp, resp_tag)) {
            ESP_LOGI(TAG, "%s: answered without challenge", action);
            free(env);
            return ESP_OK;
        }
        ESP_LOGE(TAG, "%s: no Nonce/Realm in InitChallenge response (HTTP %d):\n%s",
                 action, status, resp);
        SET_SENTINEL(TR064_ERR_PARSE,
                     "InitChallenge response without Nonce/Realm");
        free(env);
        return ESP_FAIL;
    }
    ESP_LOGI(TAG, "%s challenge: realm=\"%s\" nonce=\"%s\"", action, realm, nonce);

    // --- Step 2: ClientAuth with computed response ---
    char auth_hex[33];
    compute_auth_response(admin_user, realm, admin_pass, nonce, auth_hex);

    build_client_auth(env, SOAP_ENVELOPE_CAP, service, action,
                      admin_user, realm, nonce, auth_hex, args_xml, token_2fa);
    resp[0] = '\0';
    err = post_soap(url, soap_action, env, resp, resp_cap, &status);
    free(env);
    if (err != ESP_OK) {
        SET_SENTINEL(TR064_ERR_TRANSPORT, esp_err_to_name(err));
        return err;
    }
    ESP_LOGI(TAG, "ClientAuth %s → HTTP %d, %d bytes", action, status, (int)strlen(resp));

    // AVM's digest scheme returns HTTP 200 *even on auth failure* — it
    // embeds <Status>Unauthenticated</Status> plus a <s:Fault> with
    // errorCode 503 "Auth. failed" in the body and a fresh Nonce for
    // the next attempt. So we have to inspect the body regardless of
    // the HTTP status to tell success from failure.
    char fault[128]     = "";
    char err_code_s[16] = "";
    char err_desc[128]  = "";
    xml_find_text(resp, "faultstring",       fault,      sizeof(fault));
    xml_find_text(resp, "errorCode",         err_code_s, sizeof(err_code_s));
    xml_find_text(resp, "errorDescription",  err_desc,   sizeof(err_desc));
    bool has_fault = fault[0] || err_code_s[0];
    // Match only inside the h:Challenge header to avoid false positives
    // on any <Status> element a future SOAP action body might contain.
    bool auth_rejected = strstr(resp, "<Status>Unauthenticated</Status>") != NULL;

    if (status == 200 && !has_fault && !auth_rejected) {
        return ESP_OK;
    }

    int code = atoi(err_code_s);
    ESP_LOGE(TAG, "%s rejected: HTTP %d, auth=%s fault='%s' code=%d desc='%s'\n%s",
             action, status, auth_rejected ? "Unauthenticated" : "(ok)",
             fault, code, err_desc, resp);

    // AVM signals a bad admin password two ways:
    //   - HTTP 503 + faultstring "Unauthenticated"         (old behaviour)
    //   - HTTP 200 + <Status>Unauthenticated</Status>
    //     + UPnPError code 503 "Auth. failed"              (seen in the wild)
    // Both map to the same user-facing hint.
    bool auth_failure =
        auth_rejected
        || (status == 503 && strstr(fault, "nauthenticated"))
        || (code == 503 && strstr(err_desc, "Auth"));
    if (auth_failure) {
        SET_SENTINEL(TR064_ERR_AUTH,
                     err_desc[0] ? err_desc :
                     fault[0]    ? fault    : "Unauthenticated");
    } else if (code > 0) {
        if (out_err_code) *out_err_code = code;
        if (out_err_msg && err_msg_cap) {
            strncpy(out_err_msg, err_desc[0] ? err_desc : fault, err_msg_cap - 1);
            out_err_msg[err_msg_cap - 1] = '\0';
        }
    } else {
        char detail[160];
        snprintf(detail, sizeof(detail), "HTTP %d%s%s", status,
                 fault[0] ? " " : "", fault);
        SET_SENTINEL(TR064_ERR_HTTP, detail);
    }
    return ESP_FAIL;

    #undef SET_SENTINEL
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
    esp_err_t err = call_action(url, X_VOIP_SERVICE,
                                admin_user, admin_pass,
                                "X_AVM-DE_GetNumberOfClients", NULL,
                                NULL,
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

// Look for an existing SIP client with the given username. Returns
// its index in *out_index if found; otherwise returns the next free
// slot (== num_clients) so SetClient4 creates a new entry. Lets the
// dongle re-run setup idempotently: the second attempt overwrites
// the entry it created before instead of colliding with it (which
// the Fritz!Box reports as UPnPError 820).
static esp_err_t find_client_slot(const char *url,
                                  const char *admin_user, const char *admin_pass,
                                  const char *username,
                                  int *out_index,
                                  int *out_err_code,
                                  char *out_err_msg, size_t err_msg_cap)
{
    int num = 0;
    esp_err_t err = get_num_clients(url, admin_user, admin_pass, &num,
                                    out_err_code, out_err_msg, err_msg_cap);
    if (err != ESP_OK) return err;

    for (int i = 0; i < num; i++) {
        char *resp = malloc(SOAP_RESPONSE_CAP);
        if (!resp) return ESP_ERR_NO_MEM;
        char args[64];
        snprintf(args, sizeof(args),
                 "<NewX_AVM-DE_ClientIndex>%d</NewX_AVM-DE_ClientIndex>", i);
        err = call_action(url, X_VOIP_SERVICE,
                          admin_user, admin_pass,
                          "X_AVM-DE_GetClient3", args, NULL,
                          resp, SOAP_RESPONSE_CAP,
                          out_err_code, out_err_msg, err_msg_cap);
        if (err != ESP_OK) { free(resp); return err; }
        char found[64] = "";
        xml_find_text(resp, "NewX_AVM-DE_ClientUsername", found, sizeof(found));
        free(resp);
        if (strcmp(found, username) == 0) {
            ESP_LOGI(TAG, "found existing SIP client '%s' at index %d — will overwrite",
                     username, i);
            *out_index = i;
            return ESP_OK;
        }
    }
    ESP_LOGI(TAG, "no existing SIP client '%s', using next free slot %d",
             username, num);
    *out_index = num;
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
    // Delegated to config_dongle_username: on first call it derives
    // the suffix from the base MAC (or esp_random when eFuse is
    // zeroed, e.g. in QEMU) and persists the result in NVS. All
    // later setups reuse that exact name so find_client_slot finds
    // and overwrites the Fritz!Box entry instead of piling up new
    // ones.
    config_dongle_username(out, cap);
}

// ---------------------------------------------------------------------------
// Public entry point
// ---------------------------------------------------------------------------

esp_err_t tr064_get_default_username(const char *host, int port,
                                     const char *admin_user, const char *admin_pass,
                                     char *out, size_t cap,
                                     int *out_err_code,
                                     char *out_err_msg, size_t err_msg_cap)
{
    if (out && cap) out[0] = '\0';
    char url[96];
    snprintf(url, sizeof(url), "http://%s:%d" LANSEC_CONTROL, host, port);

    char *resp = malloc(SOAP_RESPONSE_CAP);
    if (!resp) return ESP_ERR_NO_MEM;
    esp_err_t err = call_action(url, LANSEC_SERVICE,
                                admin_user, admin_pass,
                                "X_AVM-DE_GetUserList", NULL, NULL,
                                resp, SOAP_RESPONSE_CAP,
                                out_err_code, out_err_msg, err_msg_cap);
    if (err != ESP_OK) { free(resp); return err; }

    // The <NewX_AVM-DE_UserList> value is a serialised <List>…</List>
    // XML payload with its angle brackets entity-escaped.
    char *list = malloc(SOAP_RESPONSE_CAP);
    if (!list) { free(resp); return ESP_ERR_NO_MEM; }
    list[0] = '\0';
    xml_find_text(resp, "NewX_AVM-DE_UserList", list, SOAP_RESPONSE_CAP);
    free(resp);

    xml_unescape_inplace(list);
    bool ok = pick_default_user(list, out, cap);
    free(list);
    if (!ok) {
        ESP_LOGE(TAG, "GetUserList: no Username found");
        return ESP_FAIL;
    }
    ESP_LOGI(TAG, "Fritz!Box default user: %s", out);
    return ESP_OK;
}

// Generate an app password that satisfies AVM's RegisterApp strength
// rules: ≥8 ASCII-printable chars with at least one digit, one upper,
// one lower, one special. We go 20 chars long for comfort. Special
// is fixed to '!' to avoid XML-escape surprises in the SOAP body.
static void gen_app_password(char *out, size_t cap)
{
    static const char alnum[] =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    const size_t alnum_n = sizeof(alnum) - 1;
    size_t want = 20;
    if (want + 1 > cap) want = cap - 1;
    if (want < 8) want = 8;

    // Seed the four mandatory classes.
    out[0] = '0' + (esp_random() % 10);
    out[1] = 'A' + (esp_random() % 26);
    out[2] = 'a' + (esp_random() % 26);
    out[3] = '!';
    for (size_t i = 4; i < want; i++) out[i] = alnum[esp_random() % alnum_n];

    // Fisher-Yates shuffle so the required chars aren't predictable.
    for (size_t i = want - 1; i > 0; i--) {
        size_t j = esp_random() % (i + 1);
        char tmp = out[i]; out[i] = out[j]; out[j] = tmp;
    }
    out[want] = '\0';
}

esp_err_t tr064_register_dongle_app(const char *host, int port,
                                    const char *admin_user, const char *admin_pass,
                                    const char *token_2fa,
                                    char *out_user, size_t user_cap,
                                    char *out_pass, size_t pass_cap,
                                    int  *out_err_code,
                                    char *out_err_msg, size_t err_msg_cap)
{
    if (!out_user || user_cap < 20 || !out_pass || pass_cap < 16) {
        return ESP_ERR_INVALID_ARG;
    }

    // Fixed username — AppId is unique-per-box and re-registering
    // overwrites the previous entry. "phoneblockdongle" is 16 chars,
    // starts with a letter (required), only [a-z].
    strncpy(out_user, "phoneblockdongle", user_cap - 1);
    out_user[user_cap - 1] = '\0';
    gen_app_password(out_pass, pass_cap);

    char url[96];
    snprintf(url, sizeof(url), "http://%s:%d" APPSETUP_CONTROL, host, port);

    // Read-write Phone rights, everything else NO, no internet access.
    // PhoneBlock sync only needs OnTel's CallBarring API, which lives
    // under the Phone right.
    char *args = malloc(512);
    if (!args) return ESP_ERR_NO_MEM;
    snprintf(args, 512,
        // No dashes: the Fritz!Box rejects "phoneblock-dongle" with
        // errorCode 823 "AppID contains invalid characters". AppID
        // allowed charset is the alphanumeric subset, same as the
        // username. Display name keeps the dash/space for the admin
        // UI.
        "<NewAppId>phoneblockdongle</NewAppId>"
        "<NewAppDisplayName>PhoneBlock Dongle</NewAppDisplayName>"
        "<NewAppDeviceMAC></NewAppDeviceMAC>"
        "<NewAppUsername>%s</NewAppUsername>"
        "<NewAppPassword>%s</NewAppPassword>"
        "<NewAppRight>NO</NewAppRight>"
        "<NewNasRight>NO</NewNasRight>"
        "<NewPhoneRight>RW</NewPhoneRight>"
        "<NewHomeautoRight>NO</NewHomeautoRight>"
        "<NewAppInternetRights>0</NewAppInternetRights>",
        out_user, out_pass);

    char *resp = malloc(SOAP_RESPONSE_CAP);
    if (!resp) { free(args); return ESP_ERR_NO_MEM; }
    esp_err_t err = call_action(url, APPSETUP_SERVICE,
                                admin_user, admin_pass,
                                "RegisterApp", args, token_2fa,
                                resp, SOAP_RESPONSE_CAP,
                                out_err_code, out_err_msg, err_msg_cap);
    free(args);
    free(resp);
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "RegisterApp failed");
        return err;
    }
    ESP_LOGI(TAG, "dongle app registered (user='%s', %zu-char password)",
             out_user, strlen(out_pass));
    return ESP_OK;
}

// --- Call-barring (X_AVM-DE_OnTel) ---------------------------------

esp_err_t tr064_call_barring_list_url(const char *host, int port,
                                      const char *user, const char *pass,
                                      char *out_url, size_t url_cap,
                                      int *out_err_code,
                                      char *out_err_msg, size_t err_msg_cap)
{
    if (!out_url || url_cap == 0) return ESP_ERR_INVALID_ARG;
    out_url[0] = '\0';

    char url[96];
    snprintf(url, sizeof(url), "http://%s:%d" ONTEL_CONTROL, host, port);

    char *resp = malloc(SOAP_RESPONSE_CAP);
    if (!resp) return ESP_ERR_NO_MEM;
    esp_err_t err = call_action(url, ONTEL_SERVICE,
                                user, pass,
                                "GetCallBarringList", NULL, NULL,
                                resp, SOAP_RESPONSE_CAP,
                                out_err_code, out_err_msg, err_msg_cap);
    if (err != ESP_OK) { free(resp); return err; }

    xml_find_text(resp, "NewPhonebookURL", out_url, url_cap);
    free(resp);
    if (!out_url[0]) {
        ESP_LOGE(TAG, "GetCallBarringList: no PhonebookURL in response");
        return ESP_FAIL;
    }
    return ESP_OK;
}

esp_err_t tr064_call_barring_delete(const char *host, int port,
                                    const char *user, const char *pass,
                                    const char *uid,
                                    int *out_err_code,
                                    char *out_err_msg, size_t err_msg_cap)
{
    if (!uid || !*uid) return ESP_ERR_INVALID_ARG;

    char url[96];
    snprintf(url, sizeof(url), "http://%s:%d" ONTEL_CONTROL, host, port);

    char args[128];
    char uid_esc[32];
    xml_escape(uid, uid_esc, sizeof(uid_esc));
    snprintf(args, sizeof(args),
        "<NewPhonebookEntryUniqueID>%s</NewPhonebookEntryUniqueID>", uid_esc);

    char *resp = malloc(SOAP_RESPONSE_CAP);
    if (!resp) return ESP_ERR_NO_MEM;
    esp_err_t err = call_action(url, ONTEL_SERVICE,
                                user, pass,
                                "DeleteCallBarringEntryUID", args, NULL,
                                resp, SOAP_RESPONSE_CAP,
                                out_err_code, out_err_msg, err_msg_cap);
    free(resp);
    return err;
}

// --- 2FA (X_AVM-DE_Auth) -------------------------------------------

// Best-effort: cancel any stale 2FA session before starting a fresh
// one. A previous failed SetClient4 attempt leaves the Fritz!Box in a
// "busy" state (errorCode 868) that blocks the next SetConfig(start)
// until either the user responds or the server-side timeout expires.
// Ignore any errors — this is housekeeping.
static void auth_cancel_pending(const char *url,
                                const char *admin_user, const char *admin_pass)
{
    char *resp = malloc(SOAP_RESPONSE_CAP);
    if (!resp) return;
    (void)call_action(url, X_AUTH_SERVICE,
                      admin_user, admin_pass,
                      "SetConfig", "<NewAction>stop</NewAction>", NULL,
                      resp, SOAP_RESPONSE_CAP,
                      NULL, NULL, 0);
    free(resp);
}

esp_err_t tr064_auth_start(const char *host, int port,
                           const char *admin_user, const char *admin_pass,
                           char *out_token, size_t token_cap,
                           char *out_state, size_t state_cap,
                           char *out_methods, size_t methods_cap)
{
    char url[96];
    snprintf(url, sizeof(url), "http://%s:%d" X_AUTH_CONTROL, host, port);

    auth_cancel_pending(url, admin_user, admin_pass);

    const char *args = "<NewAction>start</NewAction>";

    char *resp = malloc(SOAP_RESPONSE_CAP);
    if (!resp) return ESP_ERR_NO_MEM;
    esp_err_t err = call_action(url, X_AUTH_SERVICE,
                                admin_user, admin_pass,
                                "SetConfig", args, NULL,
                                resp, SOAP_RESPONSE_CAP,
                                NULL, NULL, 0);
    if (err != ESP_OK) { free(resp); return err; }

    if (out_token   && token_cap)   xml_find_text(resp, "NewToken",   out_token,   token_cap);
    if (out_state   && state_cap)   xml_find_text(resp, "NewState",   out_state,   state_cap);
    if (out_methods && methods_cap) xml_find_text(resp, "NewMethods", out_methods, methods_cap);

    ESP_LOGI(TAG, "2FA start → token=%.16s… state=%s methods=%s",
             out_token ? out_token : "", out_state ? out_state : "",
             out_methods ? out_methods : "");
    free(resp);
    return ESP_OK;
}

esp_err_t tr064_auth_get_state(const char *host, int port,
                               const char *admin_user, const char *admin_pass,
                               const char *token_2fa,
                               char *out_state, size_t state_cap)
{
    char url[96];
    snprintf(url, sizeof(url), "http://%s:%d" X_AUTH_CONTROL, host, port);

    char *resp = malloc(SOAP_RESPONSE_CAP);
    if (!resp) return ESP_ERR_NO_MEM;
    esp_err_t err = call_action(url, X_AUTH_SERVICE,
                                admin_user, admin_pass,
                                "GetState", NULL, token_2fa,
                                resp, SOAP_RESPONSE_CAP,
                                NULL, NULL, 0);
    if (err != ESP_OK) { free(resp); return err; }

    char state_local[32] = "";
    xml_find_text(resp, "NewState", state_local, sizeof(state_local));
    ESP_LOGI(TAG, "2FA state → %s", state_local);
    if (out_state && state_cap) {
        strncpy(out_state, state_local, state_cap - 1);
        out_state[state_cap - 1] = '\0';
    }
    free(resp);
    return ESP_OK;
}

esp_err_t tr064_provision_sip_client(const char *host, int port,
                                     const char *admin_user, const char *admin_pass,
                                     const char *phone_name,
                                     const char *token_2fa,
                                     tr064_sip_result_t *out)
{
    if (!host || !admin_user || !admin_pass || !out) return ESP_ERR_INVALID_ARG;
    out->error_code = 0;
    out->error_message[0] = '\0';

    char url[96];
    snprintf(url, sizeof(url), "http://%s:%d" X_VOIP_CONTROL, host, port);
    ESP_LOGI(TAG, "provisioning SIP client on %s (user=%s)", url, admin_user);

    // 1) Generate credentials. Username is MAC-derived and stable across
    //    re-runs — that is what lets us find and reuse an existing slot
    //    on a second setup attempt.
    char user_buf[32];
    char pass_buf[24];   // 23 chars + NUL
    gen_sip_username(user_buf, sizeof(user_buf));
    gen_random_password(pass_buf, sizeof(pass_buf));

    // 2) Pick a client slot: existing index if our username is already
    //    registered, else the next free slot. Avoids UPnPError 820 on a
    //    re-run.
    int client_index = 0;
    esp_err_t err = find_client_slot(url, admin_user, admin_pass, user_buf,
                                     &client_index,
                                     &out->error_code,
                                     out->error_message,
                                     sizeof(out->error_message));
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "find_client_slot failed");
        return err;
    }

    // 3) SetClient4 at the chosen index (creates or overwrites). All
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
        client_index, pass_esc, user_esc, phone_name_esc);

    char *resp = malloc(SOAP_RESPONSE_CAP);
    if (!resp) { free(args); return ESP_ERR_NO_MEM; }
    err = call_action(url, X_VOIP_SERVICE,
                      admin_user, admin_pass,
                      "X_AVM-DE_SetClient4", args,
                      token_2fa,
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
