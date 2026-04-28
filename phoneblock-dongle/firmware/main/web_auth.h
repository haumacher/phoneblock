#pragma once

#include <stdbool.h>

#include "esp_http_server.h"

// "Login with PhoneBlock" SSO gate for the dongle's web UI.
//
// During initial setup the UI is open: there is no PhoneBlock token
// yet, no user identity to bind to, and the box must be reachable
// from any browser on the LAN. Once the user has a token AND has
// completed one successful round-trip through the SSO flow, the UI
// can be locked: every request without a valid session cookie is
// bounced through phoneblock.net's login page first, and the
// returned identity assertion is verified against the dongle's own
// API token (so only the human whose account owns that token can
// log in).
//
// Sessions live in RAM only — a session cookie is a Session-Cookie
// (no Max-Age) by design, so a reboot or browser restart logs the
// user out, which matches the expected attack model: anyone with
// physical access can already factory-reset the box.

void web_auth_setup(void);

// True iff the request carries a Cookie header with a session ID
// that matches one of our active sessions. Does NOT consult
// config_auth_enabled(); callers must do that themselves if open
// access is acceptable.
bool web_auth_session_valid(httpd_req_t *req);

// Gating helper: returns true if the request should be allowed to
// proceed. Returns false after sending a 302 (HTML routes) or 401
// (API routes) itself; the caller should `return ESP_OK` then.
//
// `is_api`:
//   - true  → unauthenticated requests get a 401 + JSON body
//   - false → unauthenticated requests are 302-redirected to "/" so
//             index.html can render its in-page login state.
bool web_auth_required(httpd_req_t *req, bool is_api);

// HTTP handlers (registered in web.c's URI table).

// GET /auth/start — start an authentication round-trip. Used both
// for first activation (called from the UI's Enable toggle) and for
// re-login after the session expired (called from the in-page login
// button). The "activate" semantics — i.e. flip config_auth_enabled
// to true on a successful round-trip — are driven entirely from the
// web UI: it calls /auth/start with `?activate=1`, which the
// callback respects.
esp_err_t web_auth_handle_start(httpd_req_t *req);

// GET /auth/callback — receive ?code=&state=&[activate=1] from the
// loopback redirect, verify the JWT against PhoneBlock, and on
// success either (a) drop a session cookie and 302 to "/" for a
// pure login, or (b) additionally persist auth_enabled=1 in NVS
// for an activation round-trip.
esp_err_t web_auth_handle_callback(httpd_req_t *req);

// POST /auth/logout — drop the current session, redirect to "/" so
// the SPA renders its in-page login state.
esp_err_t web_auth_handle_logout(httpd_req_t *req);

// POST /auth/disable — turn the gate off, drop all sessions. Only
// reachable while the caller is itself authenticated.
esp_err_t web_auth_handle_disable(httpd_req_t *req);

// True iff the user is currently logged in via a valid session
// cookie. Used by /api/status to surface the state to the UI.
bool web_auth_is_logged_in(httpd_req_t *req);

// GET /auth/login-link?next=/nums/<id> — broker for browser auto-
// login on phoneblock.net. Exchanges the dongle's API token for a
// short-lived login ticket bound to `next` and 302s the browser to
// the redemption URL. The long-lived API token never reaches the
// browser. Gated behind the dongle's own SSO session.
esp_err_t web_auth_handle_login_link(httpd_req_t *req);
