#include "web_access.h"

#include <string.h>

#include "esp_log.h"
#include "esp_netif.h"
#include "lwip/sockets.h"

#include "net_local.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

static const char *TAG = "web_access";

// Read the request's immediate TCP peer into `out` (v4-mapped
// normalized). Returns false on any failure (fail closed).
static bool peer_addr(httpd_req_t *req, net_addr_t *out)
{
    int fd = httpd_req_to_sockfd(req);
    if (fd < 0) return false;

    struct sockaddr_in6 sa;
    socklen_t len = sizeof(sa);
    if (getpeername(fd, (struct sockaddr *)&sa, &len) != 0) return false;

    if (sa.sin6_family == AF_INET6) {
        memcpy(out->bytes, sa.sin6_addr.s6_addr, 16);
        return true;
    }
    if (sa.sin6_family == AF_INET) {
        // Non-dual-stack path: build the v4-mapped form by hand.
        const struct sockaddr_in *s4 = (const struct sockaddr_in *)&sa;
        memset(out->bytes, 0, 10);
        out->bytes[10] = 0xff;
        out->bytes[11] = 0xff;
        memcpy(out->bytes + 12, &s4->sin_addr.s_addr, 4);
        return true;
    }
    return false;
}

// Snapshot the dongle's own interface addresses for the same-subnet /
// same-prefix "local" checks.
static void load_self(netloc_self_t *self)
{
    memset(self, 0, sizeof(*self));

    esp_netif_t *netif = esp_netif_get_default_netif();
    if (!netif) return;

    esp_netif_ip_info_t ip;
    if (esp_netif_get_ip_info(netif, &ip) == ESP_OK && ip.ip.addr != 0) {
        self->have_v4 = true;
        memcpy(self->v4_addr, &ip.ip.addr,      4);
        memcpy(self->v4_mask, &ip.netmask.addr, 4);
    }

    esp_ip6_addr_t v6[NETLOC_MAX_V6];
    int n = esp_netif_get_all_ip6(netif, v6);
    if (n > NETLOC_MAX_V6) n = NETLOC_MAX_V6;
    for (int i = 0; i < n; i++) {
        memcpy(self->v6[self->n_v6].addr, v6[i].addr, 16);
        self->v6[self->n_v6].prefix_len = 64;   // interface prefix
        self->n_v6++;
    }
}

bool web_client_is_local(httpd_req_t *req)
{
    net_addr_t peer;
    if (!peer_addr(req, &peer)) {
        ESP_LOGW(TAG, "peer address unavailable — treating as remote");
        return false;
    }

    netloc_self_t self;
    load_self(&self);

    // Remote immediate peer: never trust forwarding headers.
    if (!netloc_is_local(&peer, &self)) return false;

    // Local peer. If a trusted local proxy forwarded a real client, that
    // client's own locality decides; a header we can't parse fails closed.
    char hdr[256];
    net_addr_t client;
    if (httpd_req_get_hdr_value_str(req, "X-Forwarded-For",
                                    hdr, sizeof(hdr)) == ESP_OK) {
        if (!netloc_xff_rightmost(hdr, &client)) return false;
        return netloc_is_local(&client, &self);
    }
    if (httpd_req_get_hdr_value_str(req, "Forwarded",
                                    hdr, sizeof(hdr)) == ESP_OK) {
        if (!netloc_forwarded_last(hdr, &client)) return false;
        return netloc_is_local(&client, &self);
    }

    // Direct client on the LAN.
    return true;
}
