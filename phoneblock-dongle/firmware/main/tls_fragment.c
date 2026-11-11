// Ask every TLS server for records of at most 4 KB (RFC 6066 Max Fragment
// Length), so a record always fits the 8 KB receive buffer
// (CONFIG_MBEDTLS_SSL_IN_CONTENT_LEN, see sdkconfig.defaults).
//
// Why (#711): the CDN serving firmware, language packs and audio began
// sending the full 16 KB TLS records in November 2026. Small responses (the
// OTA manifest, API answers) still fit, but every large download failed on
// the first big record with MBEDTLS_ERR_SSL_BAD_INPUT_DATA (-0x7100,
// "esp_https_ota_begin: ESP_FAIL"). The CDN honours the extension; a server
// that does not simply ignores it and nothing changes for that connection.
// The buffer itself stays at 8 KB: it is allocated contiguously for every
// TLS connection, and 16 KB does not fit a fragmented heap on the classic
// ESP32.
//
// esp-tls has no hook to adjust its mbedtls_ssl_config, so the call it makes
// right after building the client configuration, mbedtls_ssl_setup(), is
// wrapped at link time (-Wl,--wrap=mbedtls_ssl_setup, main/CMakeLists.txt).
// Only client endpoints are touched, and only those whose task has not
// exempted its next sessions (tls_fragment_exempt(), #734: SMTP and SIP over
// TLS to servers the user configured, see tls_fragment.h).

#include "tls_fragment.h"

#include "mbedtls/ssl.h"
#include "esp_log.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

static const char *TAG = "tls";

// Thread-local: each FreeRTOS task has its own copy.
static __thread bool s_exempt;

void tls_fragment_exempt(bool exempt)
{
    s_exempt = exempt;
}

int __real_mbedtls_ssl_setup(mbedtls_ssl_context *ssl, const mbedtls_ssl_config *conf);

int __wrap_mbedtls_ssl_setup(mbedtls_ssl_context *ssl, const mbedtls_ssl_config *conf)
{
    if (conf != NULL && !s_exempt
            && mbedtls_ssl_conf_get_endpoint(conf) == MBEDTLS_SSL_IS_CLIENT) {
        // esp-tls owns this configuration (tls->conf) and builds it per
        // connection, so adjusting it here affects only this connection.
        int rc = mbedtls_ssl_conf_max_frag_len((mbedtls_ssl_config *)conf,
                                               MBEDTLS_SSL_MAX_FRAG_LEN_4096);
        if (rc != 0) {
            // Not fatal: the connection still works with servers that send
            // records small enough on their own.
            ESP_LOGW(TAG, "max fragment length not set: -0x%04x", (unsigned)-rc);
        }
    }
    return __real_mbedtls_ssl_setup(ssl, conf);
}
