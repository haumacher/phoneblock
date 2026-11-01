// Classification of a finished PhoneBlock API request into success or a
// user-facing failure cause (issue #551). Pure — no network, no ESP-IDF — so
// it is host-tested in test/test_stats.c together with the recording in
// stats.c. The platform-specific half (which native error means DNS, TCP,
// TLS or timeout) is pb_http_fail_class() in core/platform_*.c.
#include "api.h"

#include "platform.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

api_fail_kind_t api_fail_kind(bool transport_ok, int fail_class, int http_status)
{
    if (transport_ok) {
        return (http_status >= 200 && http_status < 300) ? API_FAIL_NONE
                                                         : API_FAIL_HTTP;
    }
    switch (fail_class) {
    case PB_HTTP_FAIL_DNS:     return API_FAIL_DNS;
    case PB_HTTP_FAIL_CONNECT: return API_FAIL_CONNECT;
    case PB_HTTP_FAIL_TLS:     return API_FAIL_TLS;
    case PB_HTTP_FAIL_TIMEOUT: return API_FAIL_TIMEOUT;
    default:                   return API_FAIL_OTHER;
    }
}

const char *api_fail_kind_name(api_fail_kind_t kind)
{
    switch (kind) {
    case API_FAIL_NONE:    return "";
    case API_FAIL_DNS:     return "dns";
    case API_FAIL_CONNECT: return "connect";
    case API_FAIL_TLS:     return "tls";
    case API_FAIL_TIMEOUT: return "timeout";
    case API_FAIL_HTTP:    return "http";
    default:               return "other";
    }
}
