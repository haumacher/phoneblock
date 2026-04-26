#include "sip_transport.h"

#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <errno.h>

#include "esp_log.h"
#include "esp_netif.h"

#include "lwip/netdb.h"

static const char *TAG = "sip_transport";

struct sip_transport {
    int  sock;
    struct sockaddr_in registrar;
    char local_ip[INET_ADDRSTRLEN];
    int  local_port;
    char via_token[8];
};

static bool discover_local_ip(struct sip_transport *t)
{
    esp_netif_t *netif = esp_netif_get_default_netif();
    if (!netif) {
        ESP_LOGE(TAG, "no default netif");
        return false;
    }
    esp_netif_ip_info_t ip;
    if (esp_netif_get_ip_info(netif, &ip) != ESP_OK) {
        ESP_LOGE(TAG, "get_ip_info failed");
        return false;
    }
    esp_ip4addr_ntoa(&ip.ip, t->local_ip, sizeof(t->local_ip));
    return true;
}

bool sip_transport_resolve(sip_transport_t *t,
                           const char *host, int port)
{
    struct addrinfo hints = { .ai_family = AF_INET, .ai_socktype = SOCK_DGRAM };
    struct addrinfo *res = NULL;

    char port_str[8];
    snprintf(port_str, sizeof(port_str), "%d", port);

    int err = getaddrinfo(host, port_str, &hints, &res);
    if (err != 0 || !res) {
        ESP_LOGE(TAG, "DNS lookup of %s failed: %d", host, err);
        return false;
    }
    memcpy(&t->registrar, res->ai_addr, sizeof(t->registrar));
    freeaddrinfo(res);

    char ip[INET_ADDRSTRLEN];
    inet_ntoa_r(t->registrar.sin_addr, ip, sizeof(ip));
    ESP_LOGI(TAG, "registrar %s:%d → %s", host, port, ip);
    return true;
}

sip_transport_t *sip_transport_open(const char *transport,
                                    const char *registrar_host,
                                    int registrar_port,
                                    int local_port)
{
    if (transport && transport[0] && strcasecmp(transport, "udp") != 0) {
        ESP_LOGW(TAG,
                 "transport \"%s\" not yet implemented, falling back to UDP",
                 transport);
    }

    struct sip_transport *t = calloc(1, sizeof(*t));
    if (!t) return NULL;
    t->sock = -1;
    t->local_port = local_port;
    strcpy(t->via_token, "UDP");

    if (!discover_local_ip(t)) {
        free(t);
        return NULL;
    }
    if (!sip_transport_resolve(t, registrar_host, registrar_port)) {
        free(t);
        return NULL;
    }

    t->sock = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
    if (t->sock < 0) {
        ESP_LOGE(TAG, "socket(): %s", strerror(errno));
        free(t);
        return NULL;
    }

    struct sockaddr_in local = {
        .sin_family      = AF_INET,
        .sin_addr.s_addr = htonl(INADDR_ANY),
        .sin_port        = htons(local_port),
    };
    if (bind(t->sock, (struct sockaddr *)&local, sizeof(local)) < 0) {
        ESP_LOGE(TAG, "bind(): %s", strerror(errno));
        close(t->sock);
        free(t);
        return NULL;
    }

    ESP_LOGI(TAG, "local IP %s, SIP UDP port %d", t->local_ip, local_port);
    return t;
}

void sip_transport_close(sip_transport_t *t)
{
    if (!t) return;
    if (t->sock >= 0) close(t->sock);
    free(t);
}

int sip_transport_send(sip_transport_t *t, const void *buf, int len)
{
    int n = sendto(t->sock, buf, len, 0,
                   (struct sockaddr *)&t->registrar, sizeof(t->registrar));
    if (n < 0) {
        ESP_LOGE(TAG, "sendto(registrar): %s", strerror(errno));
    }
    return n;
}

int sip_transport_send_to(sip_transport_t *t, const struct sockaddr_in *peer,
                          const void *buf, int len)
{
    int n = sendto(t->sock, buf, len, 0,
                   (struct sockaddr *)peer, sizeof(*peer));
    if (n < 0) {
        ESP_LOGE(TAG, "sendto(peer): %s", strerror(errno));
    }
    return n;
}

int sip_transport_recv(sip_transport_t *t, int timeout_ms,
                       void *buf, int cap,
                       struct sockaddr_in *from)
{
    if (timeout_ms >= 0) {
        struct timeval tv = {
            .tv_sec  = timeout_ms / 1000,
            .tv_usec = (timeout_ms % 1000) * 1000,
        };
        fd_set rfds;
        FD_ZERO(&rfds);
        FD_SET(t->sock, &rfds);
        int s = select(t->sock + 1, &rfds, NULL, NULL, &tv);
        if (s < 0) {
            ESP_LOGE(TAG, "select(): %s", strerror(errno));
            return -1;
        }
        if (s == 0) return 0;
    }

    socklen_t from_len = sizeof(*from);
    int r = recvfrom(t->sock, buf, cap, 0,
                     (struct sockaddr *)from, &from_len);
    if (r < 0) {
        ESP_LOGW(TAG, "recvfrom(): %s", strerror(errno));
        return -1;
    }
    return r;
}

const char *sip_transport_local_ip(const sip_transport_t *t)
{
    return t->local_ip;
}

int sip_transport_local_port(const sip_transport_t *t)
{
    return t->local_port;
}

const char *sip_transport_via_token(const sip_transport_t *t)
{
    return t->via_token;
}
