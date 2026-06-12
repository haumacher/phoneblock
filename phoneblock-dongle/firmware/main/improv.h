#pragma once

// Improv Wi-Fi Serial provisioning service (issue #372).
//
// Listens on the console UART for Improv packets from the browser-side
// installer (esp-web-tools on phoneblock.net/dongle-install) and lets
// the user submit Wi-Fi credentials without WPS — the only setup path
// for routers/APs that do not offer a WPS button (UniFi & friends).
//
// Must be started before wifi_connect(), which blocks app_main until
// the first IP: provisioning has to be available exactly while the
// device is still waiting in WPS pairing mode. The service itself
// guards against the not-yet-started Wi-Fi driver via
// wifi_sta_started().
//
// Side effect: installs the UART driver on the console UART and
// routes the log/stdout VFS through it, so that log lines and Improv
// packets serialize per-write instead of interleaving byte-wise.
void improv_start(void);
