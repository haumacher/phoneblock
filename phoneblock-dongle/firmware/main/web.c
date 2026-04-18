#include "web.h"

#include <stdio.h>
#include <string.h>

#include "esp_log.h"
#include "esp_timer.h"
#include "esp_app_desc.h"
#include "esp_netif.h"
#include "esp_http_server.h"
#include "lwip/sockets.h"     // INET_ADDRSTRLEN
#include "cJSON.h"

#include "config.h"
#include "stats.h"

static const char *TAG = "web";

static httpd_handle_t s_server = NULL;

// --- Embedded HTML (placeholder; full UI lands in the next step) ---

static const char PLACEHOLDER_HTML[] =
    "<!doctype html><html lang=\"de\"><head>"
    "<meta charset=\"utf-8\"><title>PhoneBlock Dongle</title></head>"
    "<body><h1>PhoneBlock Dongle</h1>"
    "<p>Web-UI wird gerade gebaut. Aktuell gibt es nur die JSON-Endpunkte:</p>"
    "<ul>"
    "<li><a href=\"/api/status\">GET /api/status</a></li>"
    "<li><a href=\"/api/calls\">GET /api/calls</a></li>"
    "<li><a href=\"/api/errors\">GET /api/errors</a></li>"
    "</ul></body></html>";

// --- Helpers --------------------------------------------------------

static void send_json(httpd_req_t *req, cJSON *root)
{
    char *body = cJSON_PrintUnformatted(root);
    httpd_resp_set_type(req, "application/json; charset=utf-8");
    httpd_resp_set_hdr(req, "Cache-Control", "no-store");
    if (body) {
        httpd_resp_send(req, body, HTTPD_RESP_USE_STRLEN);
        cJSON_free(body);
    } else {
        httpd_resp_send(req, "{}", 2);
    }
    cJSON_Delete(root);
}

static const char *verdict_string(verdict_t v)
{
    switch (v) {
        case VERDICT_SPAM:       return "SPAM";
        case VERDICT_LEGITIMATE: return "LEGITIMATE";
        default:                 return "ERROR";
    }
}

static void local_ip_str(char *out, size_t cap)
{
    out[0] = '\0';
    esp_netif_t *netif = esp_netif_get_default_netif();
    if (!netif) return;
    esp_netif_ip_info_t info;
    if (esp_netif_get_ip_info(netif, &info) != ESP_OK) return;
    esp_ip4addr_ntoa(&info.ip, out, cap);
}

// --- Handlers -------------------------------------------------------

static esp_err_t handle_root(httpd_req_t *req)
{
    httpd_resp_set_type(req, "text/html; charset=utf-8");
    httpd_resp_send(req, PLACEHOLDER_HTML, HTTPD_RESP_USE_STRLEN);
    return ESP_OK;
}

static esp_err_t handle_status(httpd_req_t *req)
{
    stats_counters_t c;
    stats_snapshot_counters(&c);

    int64_t now_us   = esp_timer_get_time();
    int64_t uptime_s = now_us / 1000000;
    int64_t registered_for_s =
        c.sip_registered ? (now_us - c.sip_registered_since_us) / 1000000 : 0;

    const esp_app_desc_t *app = esp_app_get_description();

    char ip[INET_ADDRSTRLEN] = "";
    local_ip_str(ip, sizeof(ip));

    cJSON *root = cJSON_CreateObject();
    cJSON_AddStringToObject(root, "firmware_version", app ? app->version : "");
    cJSON_AddStringToObject(root, "project_name",     app ? app->project_name : "");
    cJSON_AddNumberToObject(root, "uptime_s",         (double)uptime_s);
    cJSON_AddStringToObject(root, "ip_address",       ip);

    cJSON *sip = cJSON_AddObjectToObject(root, "sip");
    cJSON_AddBoolToObject  (sip,  "registered",        c.sip_registered);
    cJSON_AddNumberToObject(sip,  "registered_for_s",  (double)registered_for_s);
    cJSON_AddStringToObject(sip,  "host",              config_sip_host());
    cJSON_AddNumberToObject(sip,  "port",              config_sip_port());
    cJSON_AddStringToObject(sip,  "user",              config_sip_user());

    cJSON *pb = cJSON_AddObjectToObject(root, "phoneblock");
    cJSON_AddStringToObject(pb,   "base_url",           config_phoneblock_base_url());
    cJSON_AddBoolToObject  (pb,   "token_set",          strlen(config_phoneblock_token()) > 0);
    cJSON_AddNumberToObject(pb,   "last_api_ms",        (double)(c.last_api_duration_us / 1000));

    cJSON *cnt = cJSON_AddObjectToObject(root, "counters");
    cJSON_AddNumberToObject(cnt,  "total",        c.total_calls);
    cJSON_AddNumberToObject(cnt,  "spam_blocked", c.spam_blocked);
    cJSON_AddNumberToObject(cnt,  "legitimate",   c.legitimate);
    cJSON_AddNumberToObject(cnt,  "errors",       c.errors);

    send_json(req, root);
    return ESP_OK;
}

static esp_err_t handle_calls(httpd_req_t *req)
{
    stats_call_t calls[STATS_MAX_CALLS];
    int n = stats_snapshot_calls(calls, STATS_MAX_CALLS);
    int64_t now_us = esp_timer_get_time();

    cJSON *root = cJSON_CreateObject();
    cJSON *arr  = cJSON_AddArrayToObject(root, "calls");
    for (int i = 0; i < n; i++) {
        cJSON *o = cJSON_CreateObject();
        cJSON_AddNumberToObject(o, "age_s",   (double)((now_us - calls[i].at_us) / 1000000));
        cJSON_AddStringToObject(o, "number",  calls[i].number);
        cJSON_AddStringToObject(o, "display", calls[i].display);
        cJSON_AddStringToObject(o, "verdict", verdict_string(calls[i].verdict));
        cJSON_AddItemToArray(arr, o);
    }
    send_json(req, root);
    return ESP_OK;
}

static esp_err_t handle_errors(httpd_req_t *req)
{
    stats_error_t errs[STATS_MAX_ERRORS];
    int n = stats_snapshot_errors(errs, STATS_MAX_ERRORS);
    int64_t now_us = esp_timer_get_time();

    cJSON *root = cJSON_CreateObject();
    cJSON *arr  = cJSON_AddArrayToObject(root, "errors");
    for (int i = 0; i < n; i++) {
        cJSON *o = cJSON_CreateObject();
        cJSON_AddNumberToObject(o, "age_s",   (double)((now_us - errs[i].at_us) / 1000000));
        cJSON_AddStringToObject(o, "tag",     errs[i].tag);
        cJSON_AddStringToObject(o, "message", errs[i].message);
        cJSON_AddItemToArray(arr, o);
    }
    send_json(req, root);
    return ESP_OK;
}

// --- Server lifecycle -----------------------------------------------

static const httpd_uri_t URIS[] = {
    { .uri = "/",            .method = HTTP_GET, .handler = handle_root,    .user_ctx = NULL },
    { .uri = "/api/status",  .method = HTTP_GET, .handler = handle_status,  .user_ctx = NULL },
    { .uri = "/api/calls",   .method = HTTP_GET, .handler = handle_calls,   .user_ctx = NULL },
    { .uri = "/api/errors",  .method = HTTP_GET, .handler = handle_errors,  .user_ctx = NULL },
};

void web_start(void)
{
    if (s_server) {
        ESP_LOGW(TAG, "web_start() called twice — ignoring");
        return;
    }
    httpd_config_t cfg = HTTPD_DEFAULT_CONFIG();
    cfg.server_port = 80;
    cfg.max_uri_handlers = sizeof(URIS) / sizeof(URIS[0]) + 2;
    if (httpd_start(&s_server, &cfg) != ESP_OK) {
        ESP_LOGE(TAG, "httpd_start failed");
        return;
    }
    for (size_t i = 0; i < sizeof(URIS) / sizeof(URIS[0]); i++) {
        httpd_register_uri_handler(s_server, &URIS[i]);
    }
    ESP_LOGI(TAG, "HTTP server listening on :80");
}
