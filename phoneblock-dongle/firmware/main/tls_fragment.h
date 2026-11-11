#pragma once

#include <stdbool.h>

// The 4 KB record request of tls_fragment.c (#711) is meant for PhoneBlock's
// own HTTPS hosts — the CDN sends 16 KB records otherwise. A TLS session to a
// server the user configured (SMTP, SIP over TLS) must not ask for it (#734):
// a server that honours the request has to split a Certificate message above
// 4 KB across records, and the mbedTLS of IDF v5.3 (3.6.0) cannot reassemble
// a fragmented handshake message ("TLS handshake fragmentation not
// supported", -0x7080). Without the request such servers send the whole
// chain in one record, which fits the 8 KB receive buffer — the behaviour up
// to 1.6.0.
//
// Brackets the mbedtls_ssl_setup() of such a session (directly or through
// esp-tls) with exempt=true / exempt=false. Per task, so a concurrent HTTPS
// download in another task keeps its request.
void tls_fragment_exempt(bool exempt);
