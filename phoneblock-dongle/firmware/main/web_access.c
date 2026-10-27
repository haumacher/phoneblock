#include "web_access.h"

#include <stdio.h>
#include <string.h>

#ifdef ESP_PLATFORM
#include "esp_log.h"
#include "esp_netif.h"
#include "lwip/sockets.h"
#else
#include <ifaddrs.h>
#include <limits.h>
#include <net/if.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#endif

#include "net_local.h"
#include "platform.h"

// Must be last: bans unsafe string APIs for the rest of this file.
#include "banned_apis.h"

static const char *TAG = "web_access";

// Read the request's immediate TCP peer into `out` (v4-mapped
// normalized). Returns false on any failure (fail closed).
static bool peer_addr(web_http_request_t *req, net_addr_t *out)
{
    int fd = web_http_request_to_sockfd(req);
    if (fd < 0) return false;

    struct sockaddr_storage sa;
    socklen_t len = sizeof(sa);
    if (getpeername(fd, (struct sockaddr *)&sa, &len) != 0) return false;

    if (sa.ss_family == AF_INET6) {
        const struct sockaddr_in6 *v6 = (const struct sockaddr_in6 *)&sa;
        memcpy(out->bytes, v6->sin6_addr.s6_addr, 16);
        return true;
    }
    if (sa.ss_family == AF_INET) {
        // Non-dual-stack path: build the v4-mapped form by hand.
        const struct sockaddr_in *v4 = (const struct sockaddr_in *)&sa;
        memset(out->bytes, 0, 10);
        out->bytes[10] = 0xff;
        out->bytes[11] = 0xff;
        memcpy(out->bytes + 12, &v4->sin_addr.s_addr, 4);
        return true;
    }
    return false;
}

// Snapshot the dongle's own interface addresses for the same-subnet /
// same-prefix "local" checks.
#ifndef ESP_PLATFORM
// Name of the interface carrying the IPv4 default route (lowest metric), or
// "" when there is none. Read from /proc/net/route, whose columns are
// Iface Destination Gateway Flags RefCnt Use Metric Mask ...
static void default_route_interface(char *name, size_t capacity)
{
    name[0] = '\0';
    FILE *routes = fopen("/proc/net/route", "r");
    if (!routes) return;
    char line[256];
    unsigned long best_metric = ULONG_MAX;
    if (fgets(line, sizeof(line), routes)) {   // column header
        while (fgets(line, sizeof(line), routes)) {
            char iface[IF_NAMESIZE + 1];
            unsigned long destination, mask, metric;
            unsigned flags;
            if (sscanf(line, "%16s %lx %*x %x %*d %*d %lu %lx",
                       iface, &destination, &flags, &metric, &mask) != 5)
                continue;
            if (destination != 0 || mask != 0 || !(flags & 0x1u)) continue;
            if (metric < best_metric) {
                best_metric = metric;
                snprintf(name, capacity, "%s", iface);
            }
        }
    }
    fclose(routes);
}
#endif

static void load_self(netloc_self_t *self)
{
    memset(self, 0, sizeof(*self));

#ifdef ESP_PLATFORM
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
#else
    // IPv4: the interface with the default route, like ESP-IDF's default
    // netif (a Linux host often has more: Docker or Home Assistant bridges,
    // a VPN). Without a default route, the first interface with an IPv4
    // address. IPv6: the prefixes of all interfaces, as there are several
    // per interface anyway.
    char default_if[IF_NAMESIZE + 1];
    default_route_interface(default_if, sizeof(default_if));
    bool v4_from_default = false;
    struct ifaddrs *interfaces = NULL;
    if (getifaddrs(&interfaces) != 0) return;
    for (struct ifaddrs *entry = interfaces; entry; entry = entry->ifa_next) {
        if (!entry->ifa_addr || (entry->ifa_flags & IFF_LOOPBACK)) continue;
        if (entry->ifa_addr->sa_family == AF_INET && entry->ifa_netmask) {
            bool is_default = default_if[0]
                && strcmp(entry->ifa_name, default_if) == 0;
            if (v4_from_default || (self->have_v4 && !is_default)) continue;
            const struct sockaddr_in *address =
                (const struct sockaddr_in *)entry->ifa_addr;
            const struct sockaddr_in *mask =
                (const struct sockaddr_in *)entry->ifa_netmask;
            self->have_v4 = true;
            v4_from_default = is_default;
            memcpy(self->v4_addr, &address->sin_addr, sizeof(self->v4_addr));
            memcpy(self->v4_mask, &mask->sin_addr, sizeof(self->v4_mask));
        } else if (entry->ifa_addr->sa_family == AF_INET6
                   && entry->ifa_netmask && self->n_v6 < NETLOC_MAX_V6) {
            const struct sockaddr_in6 *address =
                (const struct sockaddr_in6 *)entry->ifa_addr;
            const struct sockaddr_in6 *mask =
                (const struct sockaddr_in6 *)entry->ifa_netmask;
            uint8_t prefix_len = 0;
            for (size_t byte = 0; byte < sizeof(mask->sin6_addr); byte++) {
                uint8_t value = mask->sin6_addr.s6_addr[byte];
                for (int bit = 7; bit >= 0 && (value & (1u << bit)); bit--)
                    prefix_len++;
            }
            memcpy(self->v6[self->n_v6].addr, &address->sin6_addr,
                   sizeof(self->v6[self->n_v6].addr));
            self->v6[self->n_v6].prefix_len = prefix_len;
            self->n_v6++;
        }
    }
    freeifaddrs(interfaces);
#endif
}

bool web_local_ipv4_string(char *out, size_t capacity)
{
    if (!out || !capacity) return false;
    out[0] = '\0';
#ifdef ESP_PLATFORM
    // Straight from the default netif, as before the port: load_self() would
    // put the IPv6 list on the stack of /api/status as well.
    esp_netif_t *netif = esp_netif_get_default_netif();
    if (!netif) return false;
    esp_netif_ip_info_t info;
    if (esp_netif_get_ip_info(netif, &info) != ESP_OK) return false;
    esp_ip4addr_ntoa(&info.ip, out, capacity);
    return out[0] != '\0';
#else
    netloc_self_t self;
    load_self(&self);
    if (!self.have_v4) return false;
    int length = snprintf(out, capacity, "%u.%u.%u.%u",
                          self.v4_addr[0], self.v4_addr[1],
                          self.v4_addr[2], self.v4_addr[3]);
    if (length < 0 || (size_t)length >= capacity) {
        out[0] = '\0';
        return false;
    }
    return true;
#endif
}

bool web_client_is_local(web_http_request_t *req)
{
    net_addr_t peer;
    if (!peer_addr(req, &peer)) {
        pb_log_warn(TAG, "peer address unavailable; treating as remote");
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
    if (web_http_get_header(req, "X-Forwarded-For",
                            hdr, sizeof(hdr)) == 0) {
        if (!netloc_xff_rightmost(hdr, &client)) return false;
        return netloc_is_local(&client, &self);
    }
    if (web_http_get_header(req, "Forwarded",
                            hdr, sizeof(hdr)) == 0) {
        if (!netloc_forwarded_last(hdr, &client)) return false;
        return netloc_is_local(&client, &self);
    }

    // Direct client on the LAN.
    return true;
}
