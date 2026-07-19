#pragma once

#include <stdbool.h>

#include "esp_http_server.h"

// Decides whether a request originates from the local network, honouring
// a trusted local reverse proxy's X-Forwarded-For / Forwarded header.
// See docs/network-access-control.md for the model.
//
// Returns true iff:
//   - the immediate TCP peer is a local address AND
//   - either no forwarding header is present (direct local client), or
//     the forwarding header's real client address is itself local.
//
// A remote immediate peer, a getpeername() failure, or a forwarding
// header we cannot parse all return false (fail closed). The forwarding
// header is consulted ONLY when the immediate peer is local, so a remote
// client can never spoof its way to a "local" verdict.
bool web_client_is_local(httpd_req_t *req);
